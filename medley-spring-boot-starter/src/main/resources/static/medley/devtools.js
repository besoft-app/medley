/*
 * devtools.js — Medley development inspector (Stage 5, increment 2).
 *
 * Loaded ONLY when `medley.devtools.enabled=true` (MedleyController then references it from the SSR
 * shell, before medley.js). It shows, in the page:
 *   1. the patch log      every event sent and every patch batch received, with op summary + round-trip
 *   2. DOM highlighting   the elements a patch actually touched, flashed for a moment
 *   3. the component tree  a snapshot of this session's server state (GET /medley/devtools/tree)
 *
 * How it taps the stream WITHOUT touching medley.js: it wraps window.WebSocket before the runtime
 * constructs one, so the production client runtime carries no dev-tools hook and no dead code. The
 * inbound listener is registered at construction, i.e. before medley.js registers its own, so patch
 * application happens after ours returns — DOM highlighting is therefore deferred by a macrotask, by
 * which point the patched nodes exist.
 *
 * No build step, no dependencies. Never enable in production: the tree endpoint exposes @State.
 */
(function () {
  "use strict";

  const MAX_LOG = 100;     // entries kept in the ring buffer
  const FLASH_MS = 700;    // how long a patched element stays highlighted
  const TREE_URL = "/medley/devtools/tree";

  // ---- pure helpers (exercised by the node:test harness) ---------------------

  /** True when a socket URL belongs to Medley's own endpoint (absolute or relative). */
  function isMedleySocket(url, wsPath) {
    if (typeof url !== "string" || !wsPath) return false;
    const noQuery = url.split("?")[0];
    return noQuery === wsPath || noQuery.endsWith(wsPath);
  }

  /** One patch as a short human-readable line, e.g. `text root.3.2 = "2"`. */
  function describePatch(p) {
    if (!p || typeof p !== "object") return String(p);
    switch (p.op) {
      case "text":   return 'text ' + p.id + ' = "' + p.value + '"';
      case "attr":   return "attr " + p.id + " " + p.name + '="' + p.value + '"';
      case "event":  return "event " + p.id + " " + (p.name || "");
      case "prop":   return "prop " + p.id + " " + p.name + '="' + p.value + '"';
      case "replace": return "replace " + p.id;
      case "insert": return "insert " + p.id + (p.index !== undefined ? " @" + p.index : "");
      case "remove": return "remove " + p.id;
      case "reload": return "reload";
      case "error":  return "error: " + p.message;
      default:       return String(p.op) + " " + (p.id || "");
    }
  }

  /** `{ count, ops }` for a patch batch — ops maps an op name to how many times it occurred. */
  function summarize(patches) {
    const ops = {};
    const list = Array.isArray(patches) ? patches : [];
    for (const p of list) {
      const op = p && p.op ? p.op : "?";
      ops[op] = (ops[op] || 0) + 1;
    }
    return { count: list.length, ops: ops };
  }

  /** The op histogram as text, e.g. `text x2, attr`. */
  function formatOps(ops) {
    return Object.keys(ops)
      .map(function (op) { return ops[op] > 1 ? op + " x" + ops[op] : op; })
      .join(", ");
  }

  /** The data-medley-ids a batch touched — the elements worth flashing. */
  function patchTargets(patches) {
    const list = Array.isArray(patches) ? patches : [];
    const ids = [];
    for (const p of list) {
      if (p && p.id && ids.indexOf(p.id) < 0) ids.push(p.id);
    }
    return ids;
  }

  /** Append to the ring buffer, dropping the oldest beyond `cap`. Mutates and returns `log`. */
  function pushEntry(log, entry, cap) {
    log.push(entry);
    while (log.length > cap) log.shift();
    return log;
  }

  /** Indent depth of a component in the tree: one level per nested-component boundary. */
  function treeDepth(id) {
    return (String(id).match(/::/g) || []).length;
  }

  // ---- browser-only below ---------------------------------------------------
  if (typeof window === "undefined" || typeof document === "undefined") {
    if (typeof module !== "undefined" && module.exports) {
      module.exports = {
        isMedleySocket: isMedleySocket,
        describePatch: describePatch,
        summarize: summarize,
        formatOps: formatOps,
        patchTargets: patchTargets,
        pushEntry: pushEntry,
        treeDepth: treeDepth
      };
    }
    return;
  }

  const log = [];
  let sentAt = 0;          // timestamp of the last outbound event, for a round-trip estimate
  let panel = null;
  let logBody = null;
  let treeBody = null;
  let tab = "patches";

  function wsPath() {
    const rootEl = document.getElementById("medley-root");
    return (rootEl && rootEl.getAttribute("data-ws")) || "/medley/ws";
  }

  // ---- the tap ---------------------------------------------------------------
  function installTap() {
    const Native = window.WebSocket;
    const path = wsPath();

    function Tapped(url, protocols) {
      const socket = protocols === undefined ? new Native(url) : new Native(url, protocols);
      if (!isMedleySocket(url, path)) {
        return socket;
      }
      // Registered before medley.js adds its own listener, so this runs first; patch application
      // happens synchronously after, which is why highlighting is deferred below.
      socket.addEventListener("message", function (event) {
        onInbound(event.data);
      });
      const nativeSend = socket.send.bind(socket);
      socket.send = function (data) {
        onOutbound(data);
        return nativeSend(data);
      };
      return socket;
    }
    Tapped.prototype = Native.prototype;
    Tapped.CONNECTING = Native.CONNECTING;
    Tapped.OPEN = Native.OPEN;
    Tapped.CLOSING = Native.CLOSING;
    Tapped.CLOSED = Native.CLOSED;
    window.WebSocket = Tapped;
  }

  function onOutbound(raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (e) {
      return;
    }
    sentAt = Date.now();
    const kind = msg.type || "action";
    const what = msg.type
      ? (msg.island ? msg.island + "." + msg.action : "")
      : msg.componentId + " · " + msg.action + "(" + (msg.args || []).join(", ") + ")";
    record({ dir: "out", kind: kind, text: what, count: null, rtt: null });
  }

  function onInbound(raw) {
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch (e) {
      return;
    }
    const rtt = sentAt ? Date.now() - sentAt : null;
    sentAt = 0;

    if (!Array.isArray(msg)) { // a control object: reload / error
      record({ dir: "in", kind: msg.op || "control", text: describePatch(msg), count: null, rtt: rtt });
      return;
    }
    const s = summarize(msg);
    record({
      dir: "in",
      kind: "patches",
      text: msg.map(describePatch).join("\n"),
      ops: formatOps(s.ops),
      count: s.count,
      rtt: rtt
    });
    // medley.js applies the patches in the listener that runs right after this one; flash on the
    // next macrotask, when the touched (or newly inserted) elements are actually in the DOM.
    const ids = patchTargets(msg);
    setTimeout(function () { flash(ids); }, 0);
    if (tab === "tree") refreshTree(); // server state just changed
  }

  function record(entry) {
    entry.t = new Date();
    pushEntry(log, entry, MAX_LOG);
    renderLog();
  }

  // ---- DOM highlighting ------------------------------------------------------
  function flash(ids) {
    for (const id of ids) {
      const node = document.querySelector('[data-medley-id="' + cssEscape(id) + '"]');
      if (!node || !node.classList) continue;
      node.classList.remove("medley-dt-flash");
      void node.offsetWidth; // restart the animation if the node is flashed again mid-flight
      node.classList.add("medley-dt-flash");
      setTimeout(function (n) {
        return function () { n.classList.remove("medley-dt-flash"); };
      }(node), FLASH_MS);
    }
  }

  function cssEscape(s) {
    if (window.CSS && CSS.escape) return CSS.escape(s);
    return s.replace(/["\\\]\[]/g, "\\$&");
  }

  // ---- panel -----------------------------------------------------------------
  const STYLE = `
    #medley-devtools { position: fixed; right: 12px; bottom: 12px; width: 380px; max-height: 60vh;
      display: flex; flex-direction: column; z-index: 2147483000; font: 12px/1.45 ui-monospace,
      SFMono-Regular, Menlo, Consolas, monospace; color: #e6e6e6; background: #16181d;
      border: 1px solid #343841; border-radius: 8px; box-shadow: 0 8px 28px rgba(0,0,0,.35); }
    #medley-devtools[hidden] { display: none; }
    .medley-dt-head { display: flex; align-items: center; gap: 8px; padding: 7px 10px;
      border-bottom: 1px solid #343841; }
    .medley-dt-title { font-weight: 600; color: #9ad0ff; margin-right: auto; }
    .medley-dt-head button { font: inherit; color: #cfd3da; background: #22262e; border: 1px solid #343841;
      border-radius: 5px; padding: 2px 8px; cursor: pointer; }
    .medley-dt-head button.on { background: #2d4a63; color: #fff; border-color: #3d6c8f; }
    .medley-dt-body { overflow: auto; padding: 6px 8px; }
    .medley-dt-row { display: flex; gap: 6px; padding: 3px 2px; border-bottom: 1px solid #22262e;
      white-space: pre-wrap; word-break: break-word; }
    .medley-dt-time { color: #6c7280; flex: none; }
    .medley-dt-dir { flex: none; }
    .medley-dt-out .medley-dt-dir { color: #e0a75e; }
    .medley-dt-in .medley-dt-dir { color: #7fd18b; }
    .medley-dt-count { flex: none; color: #16181d; background: #7fd18b; border-radius: 4px;
      padding: 0 5px; font-weight: 600; }
    .medley-dt-count.many { background: #e0a75e; }
    .medley-dt-text { color: #c8ccd4; }
    .medley-dt-meta { color: #6c7280; }
    .medley-dt-empty { color: #6c7280; padding: 6px 2px; }
    .medley-dt-node { padding: 3px 2px; border-bottom: 1px solid #22262e; }
    .medley-dt-id { color: #9ad0ff; }
    .medley-dt-type { color: #6c7280; }
    .medley-dt-field { color: #c8ccd4; }
    .medley-dt-key { color: #d9a8e0; }
    .medley-dt-flash { outline: 2px solid #e0a75e !important; outline-offset: 1px;
      animation: medley-dt-fade ${FLASH_MS}ms ease-out; }
    @keyframes medley-dt-fade { from { background: rgba(224,167,94,.45); } to { background: transparent; } }
  `;

  function buildPanel() {
    const style = document.createElement("style");
    style.textContent = STYLE;
    document.head.appendChild(style);

    panel = document.createElement("div");
    panel.id = "medley-devtools";
    panel.innerHTML =
      '<div class="medley-dt-head">' +
      '  <span class="medley-dt-title">medley devtools</span>' +
      '  <button type="button" data-tab="patches" class="on">patches</button>' +
      '  <button type="button" data-tab="tree">tree</button>' +
      '  <button type="button" data-act="clear">clear</button>' +
      '  <button type="button" data-act="hide" title="Ctrl+Shift+M">×</button>' +
      "</div>" +
      '<div class="medley-dt-body" data-body="patches"></div>' +
      '<div class="medley-dt-body" data-body="tree" hidden></div>';
    document.body.appendChild(panel);

    logBody = panel.querySelector('[data-body="patches"]');
    treeBody = panel.querySelector('[data-body="tree"]');

    panel.addEventListener("click", function (e) {
      const btn = e.target.closest("button");
      if (!btn) return;
      if (btn.dataset.tab) selectTab(btn.dataset.tab);
      else if (btn.dataset.act === "clear") { log.length = 0; renderLog(); }
      else if (btn.dataset.act === "hide") panel.hidden = true;
    });

    document.addEventListener("keydown", function (e) {
      if (e.ctrlKey && e.shiftKey && (e.key === "M" || e.key === "m")) {
        panel.hidden = !panel.hidden;
      }
    });

    renderLog();
  }

  function selectTab(name) {
    tab = name;
    for (const b of panel.querySelectorAll("button[data-tab]")) {
      b.classList.toggle("on", b.dataset.tab === name);
    }
    logBody.hidden = name !== "patches";
    treeBody.hidden = name !== "tree";
    if (name === "tree") refreshTree();
  }

  function renderLog() {
    if (!logBody) return;
    if (log.length === 0) {
      logBody.innerHTML = '<div class="medley-dt-empty">No traffic yet — interact with the page.</div>';
      return;
    }
    const rows = [];
    for (let i = log.length - 1; i >= 0; i--) { // newest first
      const e = log[i];
      const badge = e.count === null ? ""
        : '<span class="medley-dt-count' + (e.count > 1 ? " many" : "") + '">' + e.count + "</span>";
      const meta = [e.ops, e.rtt !== null && e.rtt !== undefined ? e.rtt + "ms" : null]
        .filter(Boolean).join(" · ");
      rows.push(
        '<div class="medley-dt-row medley-dt-' + e.dir + '">' +
        '<span class="medley-dt-time">' + time(e.t) + "</span>" +
        '<span class="medley-dt-dir">' + (e.dir === "out" ? "▲" : "▼") + "</span>" +
        badge +
        '<span class="medley-dt-text">' + escapeHtml(e.text || e.kind) +
        (meta ? ' <span class="medley-dt-meta">(' + escapeHtml(meta) + ")</span>" : "") +
        "</span></div>"
      );
    }
    logBody.innerHTML = rows.join("");
  }

  function refreshTree() {
    fetch(TREE_URL, { credentials: "same-origin" })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(new Error("HTTP " + r.status)); })
      .then(renderTree)
      .catch(function (e) {
        treeBody.innerHTML = '<div class="medley-dt-empty">Tree unavailable: ' +
          escapeHtml(String(e.message)) + "</div>";
      });
  }

  function renderTree(snapshot) {
    const components = (snapshot && snapshot.components) || [];
    if (components.length === 0) {
      treeBody.innerHTML = '<div class="medley-dt-empty">No components in this session.</div>';
      return;
    }
    treeBody.innerHTML = components.map(function (c) {
      const indent = treeDepth(c.id) * 12;
      return '<div class="medley-dt-node" style="padding-left:' + indent + 'px">' +
        '<span class="medley-dt-id">' + escapeHtml(c.id) + "</span> " +
        '<span class="medley-dt-type">' + escapeHtml(c.name) + " · " + escapeHtml(c.type) + "</span>" +
        fieldsHtml("state", c.state) + fieldsHtml("param", c.params) +
        "</div>";
    }).join("");
  }

  function fieldsHtml(kind, fields) {
    const keys = Object.keys(fields || {});
    if (keys.length === 0) return "";
    return keys.map(function (k) {
      return '<div class="medley-dt-field">  <span class="medley-dt-key">' + kind + " " + escapeHtml(k) +
        "</span> = " + escapeHtml(JSON.stringify(fields[k])) + "</div>";
    }).join("");
  }

  function time(d) {
    return d.toTimeString().slice(3, 8) + "." + String(d.getMilliseconds()).padStart(3, "0");
  }

  function escapeHtml(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  // The tap must be installed before medley.js constructs its socket, so it runs at script load
  // (this script is placed before medley.js in the shell). The panel needs <body>, so it waits.
  installTap();
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", buildPanel);
  } else {
    buildPanel();
  }
})();
