"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { makeDom } = require("./dom-shim");
const medley = require("../../main/resources/static/medley/medley.js");

// applyPatch mutates the real DOM from the compact patch protocol. Covered here: the ops that do not
// need HTML parsing (text/attr/removeAttr/event/removeEvent/remove). `replace`/`insert` need
// htmlToElement (real fragment parsing) and are out of scope for this lightweight shim — see README.

// Runs fn with a fresh DOM installed as the globals medley.js reads (document, and window for the
// cssEscape fallback). Restores the previous globals afterward.
function withDom(fn) {
  const dom = makeDom();
  const prevDocument = global.document;
  const prevWindow = global.window;
  global.document = dom.document;
  global.window = {}; // cssEscape checks window.CSS; {} makes it fall back to the regex escaper
  try {
    return fn(dom);
  } finally {
    global.document = prevDocument;
    global.window = prevWindow;
  }
}

test("text patch sets textContent", () => withDom((dom) => {
  const node = dom.element("root.0", "span");
  medley.applyPatch({ op: "text", id: "root.0", value: "5" });
  assert.equal(node.textContent, "5");
}));

test("attr patch sets an attribute", () => withDom((dom) => {
  const node = dom.element("root.1", "div");
  medley.applyPatch({ op: "attr", id: "root.1", name: "class", value: "counter active" });
  assert.equal(node.getAttribute("class"), "counter active");
}));

test("removeAttr patch removes an attribute", () => withDom((dom) => {
  const node = dom.element("root.1", "div");
  node.setAttribute("disabled", "disabled");
  medley.applyPatch({ op: "removeAttr", id: "root.1", name: "disabled" });
  assert.equal(node.getAttribute("disabled"), null);
}));

test("event patch wires a listener and reflects the binding attribute", () => withDom((dom) => {
  const node = dom.element("root.1", "button");
  medley.applyPatch({ op: "event", id: "root.1", event: "click", action: "increment" });
  assert.equal(node.getAttribute("data-medley-on-click"), "increment");
  assert.equal(node.listenerCount("click"), 1);
}));

test("removeEvent patch unwires the listener and clears the attribute", () => withDom((dom) => {
  const node = dom.element("root.1", "button");
  medley.applyPatch({ op: "event", id: "root.1", event: "click", action: "increment" });
  medley.applyPatch({ op: "removeEvent", id: "root.1", event: "click" });
  assert.equal(node.getAttribute("data-medley-on-click"), null);
  assert.equal(node.listenerCount("click"), 0);
}));

test("remove patch detaches the node", () => withDom((dom) => {
  dom.element("root.7", "div");
  medley.applyPatch({ op: "remove", id: "root.7" });
  assert.equal(dom.document.querySelector('[data-medley-id="root.7"]'), null);
}));

test("a patch addressed to a missing id is a no-op, not a throw", () => withDom(() => {
  medley.applyPatch({ op: "text", id: "nope", value: "x" });
}));

test("applyPatches applies a batch in order", () => withDom((dom) => {
  const a = dom.element("root.0", "span");
  const b = dom.element("root.1", "span");
  medley.applyPatches([
    { op: "text", id: "root.0", value: "one" },
    { op: "text", id: "root.1", value: "two" }
  ]);
  assert.equal(a.textContent, "one");
  assert.equal(b.textContent, "two");
}));
