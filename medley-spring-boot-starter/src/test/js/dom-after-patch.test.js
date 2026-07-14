"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parseFragment } = require("./html-parse");
const medley = require("../../main/resources/static/medley/medley.js");

/*
 * The test that was missing — and whose absence let a critical bug ship (MEDLEY_DESIGN §11a).
 *
 * Every other layer asserted its own half: the Java tests asserted the patch the server emits, and the
 * old shim let a test fabricate an element carrying that patch's id. Nobody checked that the patch lands
 * on the HTML the server actually serves. So the server emitted `{"op":"text","id":"root.3.2"}` — a TEXT
 * NODE's id — no element carried it, `querySelector` returned null, and every text update was silently
 * dropped in the browser while the suite stayed green.
 *
 * These tests parse REAL server output (pinned on the Java side by MedleySsrTest) and assert what the
 * user would see after the patch is applied.
 */

// Exactly what the server serves for /counter (span at root.3; `{{ label }}: {{ count }}`), pinned by
// MedleySsrTest.firstRequestRendersComponentAtItsRoute.
const COUNTER_SSR =
  '<span data-medley-id="root.3">' +
  '<medley-text data-medley-id="root.3.0">n</medley-text>' +
  ': ' +
  '<medley-text data-medley-id="root.3.2">0</medley-text>' +
  "</span>";

// The pre-fix markup, kept as a regression guard: dynamic text with no host of its own.
const COUNTER_SSR_BROKEN = '<span data-medley-id="root.3">n: 0</span>';

function withDom(html, fn) {
  const dom = parseFragment(html);
  const prevDocument = global.document;
  const prevWindow = global.window;
  global.document = dom.document;
  global.window = {}; // no window.CSS → medley.js falls back to its regex escaper
  try {
    return fn(dom);
  } finally {
    global.document = prevDocument;
    global.window = prevWindow;
  }
}

test("a text patch updates what the user actually sees", () => withDom(COUNTER_SSR, (dom) => {
  assert.equal(dom.root.textContent, "n: 0");

  medley.applyPatch({ op: "text", id: "root.3.2", value: "1" });

  // The whole point: the span the user is looking at now reads the new value, and only the
  // interpolated part changed — the literal ": " between the two interpolations is untouched.
  assert.equal(dom.root.textContent, "n: 1");
}));

test("the patched host is an element, so the client can find it at all", () =>
  withDom(COUNTER_SSR, (dom) => {
    const host = dom.document.querySelector('[data-medley-id="root.3.2"]');
    assert.ok(host, "the text node's id must be carried by an element, or the patch is undeliverable");
    assert.equal(host.tagName, "MEDLEY-TEXT");
  }));

test("REGRESSION: bare dynamic text is unaddressable — the patch would be dropped", () =>
  withDom(COUNTER_SSR_BROKEN, (dom) => {
    // A real browser merges `n: ` and `0` into ONE text node, and a text node cannot carry an
    // attribute — so the id the server addresses simply does not exist in the DOM.
    assert.equal(dom.document.querySelector('[data-medley-id="root.3.2"]'), null);

    medley.applyPatch({ op: "text", id: "root.3.2", value: "1" });

    assert.equal(dom.root.textContent, "n: 0", "this is the bug: the DOM never updates");
  }));

test("a patch addressed to the element itself still works (raw-text elements)", () =>
  withDom('<textarea data-medley-id="root.5">draft</textarea>', (dom) => {
    medley.applyPatch({ op: "text", id: "root.5", value: "edited" });
    assert.equal(dom.root.textContent, "edited");
  }));
