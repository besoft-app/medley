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
        if (node) node.remove();
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
  // Server emits events as data-medley-on-<event>="actionName". We attach a real
  // listener that forwards to the server. We track attached handlers to allow rewiring.
  const handlerRegistry = new WeakMap(); // node -> { event -> fn }

  function wireEvent(node, eventName, action) {
    let map = handlerRegistry.get(node);
    if (!map) { map = {}; handlerRegistry.set(node, map); }
    if (map[eventName]) node.removeEventListener(eventName, map[eventName]);

    const fn = function (domEvent) {
      domEvent.preventDefault();
      const componentId = ownerComponentId(node);
      sendEvent(componentId, action, []);
    };
    map[eventName] = fn;
    node.addEventListener(eventName, fn);
    node.setAttribute("data-medley-on-" + eventName, action);
  }

  function unwireEvent(node, eventName) {
    const map = handlerRegistry.get(node);
    if (map && map[eventName]) {
      node.removeEventListener(eventName, map[eventName]);
      delete map[eventName];
    }
    node.removeAttribute("data-medley-on-" + eventName);
  }

  // The PoC uses a single root component; in a multi-component tree this would walk up
  // to the nearest component boundary marker. Kept simple and explicit here.
  function ownerComponentId(node) {
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

  // ---- islands (client-only Web Components) ---------------------------------
  // <medley-island name="x" ...> is upgraded to a custom element <medley-x> if registered.
  // Islands manage their own DOM and never round-trip per interaction; they may call
  // window.medley.islandCommit(id, state) to persist coarse-grained state on the server.
  function mountIslands(root) {
    const scope = root.querySelectorAll ? root : document;
    const islands = scope.querySelectorAll("medley-island");
    islands.forEach(function (host) {
      if (host.__medleyMounted) return;
      const name = host.getAttribute("name");
      const ctor = islandRegistry[name];
      if (!ctor) return; // no client implementation registered; leave as inert placeholder
      host.__medleyMounted = true;
      try {
        ctor(host);
      } catch (e) {
        console.error("[medley] island '" + name + "' failed to mount", e);
      }
    });
  }

  const islandRegistry = Object.create(null);

  function registerIsland(name, mountFn) {
    islandRegistry[name] = mountFn;
    // Mount any already-present hosts for this island.
    mountIslands(document);
  }

  function islandCommit(islandId, state) {
    sendEvent(ROOT_ID, "__island:" + islandId, [JSON.stringify(state)]);
  }

  // ---- public API + bootstrap ----------------------------------------------
  window.medley = {
    registerIsland: registerIsland,
    islandCommit: islandCommit,
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
