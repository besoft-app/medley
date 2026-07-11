/*
 * medley.js — client runtime for the Medley framework.
 *
 * Responsibilities:
 *   1. hydrate()        wire DOM events from the server-rendered HTML to the WebSocket
 *   2. WebSocket client connect, send events, receive + apply patches, auto-reconnect
 *   3. applyPatches()   mutate the real DOM from the compact patch protocol
 *   4. islands          mount <medley-island> Web Components (client-only, no round-trips)
 *
 * No build step, no dependencies. Served as a static asset by the starter.
 */
(function () {
  "use strict";

  const ROOT_ID = "root"; // the single root component id used by the PoC controller

  // ---- DOM addressing -------------------------------------------------------
  // Every server-rendered element carries data-medley-id. We look nodes up by it.
  function byMedleyId(id) {
    return document.querySelector('[data-medley-id="' + cssEscape(id) + '"]');
  }

  function cssEscape(s) {
    if (window.CSS && CSS.escape) return CSS.escape(s);
    return s.replace(/["\\\]\[]/g, "\\$&");
  }

  // ---- WebSocket transport --------------------------------------------------
  let socket = null;
  let reconnectDelay = 500;
  const maxReconnectDelay = 8000;

  function wsUrl(path) {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    return proto + "//" + location.host + path;
  }

  function connect(path) {
    socket = new WebSocket(wsUrl(path));

    socket.addEventListener("open", function () {
      reconnectDelay = 500; // reset backoff on success
    });

    socket.addEventListener("message", function (event) {
      handleServerMessage(event.data);
    });

    socket.addEventListener("close", function () {
      // Exponential backoff reconnect. State lives on the server tied to the HTTP
      // session, so a transient drop reconnects to the same component tree.
      setTimeout(function () {
        reconnectDelay = Math.min(reconnectDelay * 2, maxReconnectDelay);
        connect(path);
      }, reconnectDelay);
    });

    socket.addEventListener("error", function () {
      socket.close();
    });
  }

  function sendEvent(componentId, action, args) {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    socket.send(JSON.stringify({
      componentId: componentId,
      action: action,
      args: args || []
    }));
  }

  // ---- server -> client messages -------------------------------------------
  function handleServerMessage(raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (e) {
      console.error("[medley] bad message", raw);
      return;
    }
    // A control object (not an array) signals reload/error.
    if (!Array.isArray(msg)) {
      if (msg.op === "reload") {
        location.reload();
      } else if (msg.op === "error") {
        console.error("[medley] server error:", msg.message);
      }
      return;
    }
    applyPatches(msg);
  }

  // ---- patch application ----------------------------------------------------
  function applyPatches(patches) {
    for (const p of patches) {
      try {
        applyPatch(p);
      } catch (e) {
        console.error("[medley] failed to apply patch", p, e);
      }
    }
  }

  function applyPatch(p) {
    switch (p.op) {
      case "text": {
        const node = byMedleyId(p.id);
        if (node) node.textContent = p.value;
        break;
      }
      case "attr": {
        const node = byMedleyId(p.id);
        if (node) node.setAttribute(p.name, p.value);
        break;
      }
      case "removeAttr": {
        const node = byMedleyId(p.id);
        if (node) node.removeAttribute(p.name);
        break;
      }
      case "event": {
        const node = byMedleyId(p.id);
        if (node) wireEvent(node, p.event, p.action);
        break;
      }
      case "removeEvent": {
        const node = byMedleyId(p.id);
        if (node) unwireEvent(node, p.event);
        break;
      }
      case "replace": {
        const node = byMedleyId(p.id);
        if (node) {
          unmountIslandsIn(node);
          const fresh = htmlToElement(p.html);
          node.replaceWith(fresh);
          hydrateTree(fresh);
        }
        break;
      }
      case "insert": {
        const parent = byMedleyId(p.parentId);
        if (parent) {
          const fresh = htmlToElement(p.html);
          const ref = parent.children[p.index] || null;
          parent.insertBefore(fresh, ref);
          hydrateTree(fresh);
        }
        break;
      }
      case "remove": {
        const node = byMedleyId(p.id);
        if (node) {
          unmountIslandsIn(node);
          node.remove();
        }
        break;
      }
      default:
        console.warn("[medley] unknown patch op", p.op);
    }
  }

  function htmlToElement(html) {
    const tpl = document.createElement("template");
    tpl.innerHTML = html.trim();
    return tpl.content.firstElementChild;
  }

  // ---- event wiring (hydration) --------------------------------------------
  // Server emits events as data-medley-on-<event>="binding". The binding is either a bare action
  // name ("increment") or a call form carrying arg specs ("setQuery($value)"). We attach a real
  // listener that extracts the declared args from the DOM event and forwards them to the server.
  // We track attached handlers to allow rewiring.
  const handlerRegistry = new WeakMap(); // node -> { event -> fn }

  // preventDefault would swallow the keystroke on keyboard events and is pointless for input/change
  // (they fire after the value is committed). Everywhere else (click, submit, ...) we keep it so a
  // server-driven control doesn't also trigger native navigation/submission.
  const NO_PREVENT_DEFAULT = { input: 1, change: 1, keydown: 1, keyup: 1, keypress: 1 };

  // Parse "action" or "action($value, 'literal', 3)" into { action, argSpecs }. A malformed
  // binding degrades to a bare, argument-less action rather than throwing.
  // KNOWN LIMITATION (Stage 4, increment 2): the arg list is split naively on "," and captured with
  // [^)]*, so a literal containing a comma or a ")" will misparse. Keep the common cases ($value,
  // $checked, $key, simple string/number/boolean literals); a full tokenizer is a later increment.
  function parseBinding(binding) {
    const m = /^\s*([A-Za-z_$][\w$]*)\s*(?:\(([^)]*)\))?\s*$/.exec(binding || "");
    if (!m) return { action: binding, argSpecs: [] };
    const inside = m[2];
    if (inside == null || inside.trim() === "") return { action: m[1], argSpecs: [] };
    const argSpecs = inside.split(",").map(function (s) { return s.trim(); })
      .filter(function (s) { return s.length > 0; });
    return { action: m[1], argSpecs: argSpecs };
  }

  // Resolve one arg spec against the live DOM event/target. Supported tokens: $value, $checked,
  // $key. Anything else is treated as a literal (quoted string, number, or boolean).
  function extractArg(spec, domEvent, node) {
    switch (spec) {
      case "$value": return node.value;
      case "$checked": return !!node.checked;
      case "$key": return domEvent.key;
      default:
        if (/^'.*'$/.test(spec) || /^".*"$/.test(spec)) return spec.slice(1, -1);
        if (spec === "true") return true;
        if (spec === "false") return false;
        if (/^-?\d+(?:\.\d+)?$/.test(spec)) return Number(spec);
        return spec; // unknown token — send verbatim as a string
    }
  }

  function wireEvent(node, eventName, binding) {
    let map = handlerRegistry.get(node);
    if (!map) { map = {}; handlerRegistry.set(node, map); }
    if (map[eventName]) node.removeEventListener(eventName, map[eventName]);

    const parsed = parseBinding(binding);
    const fn = function (domEvent) {
      if (!NO_PREVENT_DEFAULT[eventName]) domEvent.preventDefault();
      const componentId = ownerComponentId(node);
      const args = parsed.argSpecs.map(function (s) { return extractArg(s, domEvent, node); });
      sendEvent(componentId, parsed.action, args);
    };
    map[eventName] = fn;
    node.addEventListener(eventName, fn);
    node.setAttribute("data-medley-on-" + eventName, binding);
  }

  function unwireEvent(node, eventName) {
    const map = handlerRegistry.get(node);
    if (map && map[eventName]) {
      node.removeEventListener(eventName, map[eventName]);
      delete map[eventName];
    }
    node.removeAttribute("data-medley-on-" + eventName);
  }

  // Resolve which server component owns an event: walk up from the node to the nearest
  // <medley-component> boundary host, whose data-medley-cid is that child instance's id (Stage 4,
  // increment 4b.2). No boundary above the node -> the root component. This is what routes a nested
  // child's action straight to the child instance instead of the root.
  function ownerComponentId(node) {
    let el = node;
    while (el && el.getAttribute) {
      const cid = el.getAttribute("data-medley-cid");
      if (cid) return cid;
      el = el.parentElement;
    }
    return ROOT_ID;
  }

  function hydrateTree(root) {
    if (!root) return;
    // Wire the root itself, then every descendant that declares server events.
    collectAndWire(root);
    if (root.querySelectorAll) {
      root.querySelectorAll("*").forEach(collectAndWire);
    }
    // Mount any islands inside the subtree.
    mountIslands(root);
  }

  function collectAndWire(el) {
    if (!el.attributes) return;
    for (const attr of Array.from(el.attributes)) {
      const m = attr.name.match(/^data-medley-on-(.+)$/);
      if (m) wireEvent(el, m[1], attr.value);
    }
  }

  // ---- islands (client-owned regions) ---------------------------------------
  // A <medley-island name="x" ...> host is claimed by the island class registered for "x".
  // The island owns its subtree and never round-trips per interaction. It may call
  // this.commit(action, payload) to persist a coarse result on the server (-> @IslandAction),
  // and it receives server prop pushes via onProp(name, value) when the host's attributes change
  // (the server pushes props as a plain attribute patch on the host).

  // Base class developers extend. Subclass and override mount()/onProp()/unmount().
  class MedleyIsland {
    constructor(host) { this.host = host; }
    get islandName() { return this.host.getAttribute("name"); }
    get islandId() { return this.host.getAttribute("data-medley-id"); }
    /** Read a prop (host attribute); returns null if absent. */
    prop(name) { return this.host.getAttribute(name); }
    /** Lifecycle hooks — override as needed. */
    mount() {}
    onProp(_name, _value) {}
    unmount() {}
    /** Persist coarse state to the server; routed to a @IslandAction on the owning component. */
    commit(action, payload) { islandCommit(this, action, payload); }
  }

  const islandRegistry = Object.create(null);

  function mountIslands(root) {
    const scope = (root && root.querySelectorAll) ? root : document;
    scope.querySelectorAll("medley-island").forEach(mountIsland);
    if (root && root.matches && root.matches("medley-island")) mountIsland(root);
  }

  function mountIsland(host) {
    if (host.__medleyMounted) return;
    const name = host.getAttribute("name");
    const ctor = islandRegistry[name];
    if (!ctor) return; // no client implementation registered yet; stays an inert placeholder
    host.__medleyMounted = true;
    try {
      const instance = new ctor(host);
      host.__medleyIsland = instance;
      observeProps(host, instance);
      instance.mount();
    } catch (e) {
      console.error("[medley] island '" + name + "' failed to mount", e);
      // Fully tear down so a later pass can retry cleanly (no leaked observer/instance).
      teardownIsland(host, false);
    }
  }

  // Turn server prop-pushes (attribute patches on the host) into onProp() calls. We compare
  // against the old value so a no-op setAttribute (or an attribute the island writes to its own
  // host) does not trigger a spurious onProp / feedback loop.
  function observeProps(host, instance) {
    const observer = new MutationObserver(function (mutations) {
      for (const m of mutations) {
        if (m.type !== "attributes") continue;
        const value = host.getAttribute(m.attributeName);
        if (value !== m.oldValue) instance.onProp(m.attributeName, value);
      }
    });
    observer.observe(host, { attributes: true, attributeOldValue: true });
    host.__medleyPropObserver = observer;
  }

  // Release an island host's instance and observer. callUnmount=false when mount() itself failed
  // (the island never fully mounted, so don't invoke its unmount()).
  function teardownIsland(host, callUnmount) {
    if (callUnmount && host.__medleyIsland) {
      try { host.__medleyIsland.unmount(); } catch (e) { console.error("[medley] island unmount failed", e); }
    }
    if (host.__medleyPropObserver) {
      host.__medleyPropObserver.disconnect();
      host.__medleyPropObserver = null;
    }
    host.__medleyMounted = false;
    host.__medleyIsland = null;
  }

  // Tear down islands in a subtree before it is removed/replaced, so state and observers are freed.
  function unmountIslandsIn(node) {
    if (!node) return;
    const hosts = [];
    if (node.__medleyIsland) hosts.push(node);
    if (node.querySelectorAll) {
      node.querySelectorAll("medley-island").forEach(function (h) {
        if (h.__medleyIsland) hosts.push(h);
      });
    }
    hosts.forEach(function (h) { teardownIsland(h, true); });
  }

  function registerIsland(name, islandClass) {
    islandRegistry[name] = islandClass;
    // Mount any already-present hosts for this island.
    mountIslands(document);
  }

  function islandCommit(island, action, payload) {
    if (!socket || socket.readyState !== WebSocket.OPEN) return;
    socket.send(JSON.stringify({
      type: "island-commit",
      componentId: ownerComponentId(island.host),
      island: island.islandName,
      id: island.islandId,
      action: action,
      payload: payload || {}
    }));
  }

  // ---- public API + bootstrap ----------------------------------------------
  window.medley = {
    MedleyIsland: MedleyIsland,
    registerIsland: registerIsland,
    _sendEvent: sendEvent
  };

  function boot() {
    const rootEl = document.getElementById("medley-root");
    if (!rootEl) {
      console.error("[medley] #medley-root not found");
      return;
    }
    const wsPath = rootEl.getAttribute("data-ws") || "/medley/ws";
    hydrateTree(rootEl);
    connect(wsPath);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", boot);
  } else {
    boot();
  }
})();
