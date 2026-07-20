"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const devtools = require("../../main/resources/static/medley/devtools.js");

// The pure half of the dev-tools overlay (Stage 5, increment 2): socket matching, patch
// summarising/formatting and the log ring buffer. The DOM half (panel, flashing) needs a browser and
// is demo-verified — see README.md. Requiring the file under Node must be side-effect-free: it
// installs no WebSocket tap and builds no panel without window/document.

// ---- isMedleySocket: only Medley's own endpoint is tapped ----
test("matches the medley endpoint, absolute or relative", () => {
  assert.equal(devtools.isMedleySocket("ws://localhost:8080/medley/ws", "/medley/ws"), true);
  assert.equal(devtools.isMedleySocket("/medley/ws", "/medley/ws"), true);
  assert.equal(devtools.isMedleySocket("wss://app.example.com/medley/ws?x=1", "/medley/ws"), true);
});

test("ignores sockets that are not medley's", () => {
  assert.equal(devtools.isMedleySocket("ws://localhost:8080/other/ws", "/medley/ws"), false);
  assert.equal(devtools.isMedleySocket("", "/medley/ws"), false);
  assert.equal(devtools.isMedleySocket(null, "/medley/ws"), false);
});

test("honours a custom configured websocket path", () => {
  assert.equal(devtools.isMedleySocket("ws://h/app/sock", "/app/sock"), true);
  assert.equal(devtools.isMedleySocket("ws://h/medley/ws", "/app/sock"), false);
});

// ---- describePatch: one readable line per wire op ----
// Every op PatchEncoder can emit is covered here, with the field names it actually puts on the wire:
// attr/removeAttr carry `name`, event/removeEvent carry `event` (+ `action`), insert carries
// `parentId`/`index`. Getting a field name wrong makes the inspector lie, which is worse than useless —
// so the whole op set is pinned.
test("describes every patch op the server can emit", () => {
  assert.equal(devtools.describePatch({ op: "text", id: "root.1.2", value: "2" }), 'text root.1.2 = "2"');
  assert.equal(devtools.describePatch({ op: "attr", id: "root.1", name: "class", value: "on" }),
    'attr root.1 class="on"');
  assert.equal(devtools.describePatch({ op: "removeAttr", id: "root.1", name: "class" }),
    "removeAttr root.1 class");
  assert.equal(devtools.describePatch({ op: "event", id: "root.5", event: "click", action: "increment" }),
    "event root.5 click -> increment");
  assert.equal(devtools.describePatch({ op: "removeEvent", id: "root.5", event: "click" }),
    "removeEvent root.5 click");
  assert.equal(devtools.describePatch({ op: "replace", id: "root.2" }), "replace root.2");
  assert.equal(devtools.describePatch({ op: "insert", id: "root.2[c]", parentId: "root.2", index: 1 }),
    "insert root.2[c] into root.2 @1");
  assert.equal(devtools.describePatch({ op: "remove", id: "root.2[a]" }), "remove root.2[a]");
});

test("describes the control messages", () => {
  assert.equal(devtools.describePatch({ op: "reload" }), "reload");
  assert.equal(devtools.describePatch({ op: "error", message: "boom" }), "error: boom");
});

// ---- summarize / formatOps: the minimal-diff property, made visible ----
test("summarizes the steady-state single text patch", () => {
  const s = devtools.summarize([{ op: "text", id: "root.1.2", value: "2" }]);
  assert.deepEqual(s, { count: 1, ops: { text: 1 } });
  assert.equal(devtools.formatOps(s.ops), "text");
});

test("counts repeated ops in a batch", () => {
  const s = devtools.summarize([
    { op: "text", id: "a" }, { op: "text", id: "b" }, { op: "attr", id: "c" }
  ]);
  assert.deepEqual(s, { count: 3, ops: { text: 2, attr: 1 } });
  assert.equal(devtools.formatOps(s.ops), "text x2, attr");
});

test("an empty batch summarizes to zero", () => {
  assert.deepEqual(devtools.summarize([]), { count: 0, ops: {} });
});

// ---- patchTargets: the elements to flash, de-duplicated ----
test("collects distinct patch ids", () => {
  const ids = devtools.patchTargets([
    { op: "text", id: "root.1" }, { op: "attr", id: "root.1" }, { op: "text", id: "root.2" }
  ]);
  assert.deepEqual(ids, ["root.1", "root.2"]);
});

test("skips patches with no id (a control op)", () => {
  assert.deepEqual(devtools.patchTargets([{ op: "reload" }]), []);
});

// ---- pushEntry: bounded log, oldest dropped ----
test("keeps the log bounded, dropping the oldest entries", () => {
  const log = [];
  for (let i = 1; i <= 5; i++) devtools.pushEntry(log, { n: i }, 3);
  assert.deepEqual(log, [{ n: 3 }, { n: 4 }, { n: 5 }]);
});

// ---- treeDepth: one indent level per nested-component boundary ----
test("tree depth counts component boundaries, not dom nesting", () => {
  assert.equal(devtools.treeDepth("root"), 0);
  assert.equal(devtools.treeDepth("root.1.4.2"), 0);
  assert.equal(devtools.treeDepth("root.1::counter-card"), 1);
  assert.equal(devtools.treeDepth("root.2::editor.1::inner"), 2);
});
