"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const medley = require("../../main/resources/static/medley/medley.js");

// ownerComponentId walks up from a node to the nearest <medley-component> boundary's data-medley-cid,
// or "root" if there is none (Stage 4, increment 4b.2). This is the client half of nested-component
// action routing, previously covered by the /cards demo only.

function el(cid, parent) {
  return {
    getAttribute(name) { return name === "data-medley-cid" ? cid : null; },
    parentElement: parent || null
  };
}

test("no boundary above the node -> the root component", () => {
  const node = el(null, el(null, null));
  assert.equal(medley.ownerComponentId(node), "root");
});

test("a data-medley-cid on the node itself is used", () => {
  assert.equal(medley.ownerComponentId(el("root.1::x", null)), "root.1::x");
});

test("walks up through plain elements to the nearest cid", () => {
  const host = el("root.1::x", null);
  const leaf = el(null, el(null, host));
  assert.equal(medley.ownerComponentId(leaf), "root.1::x");
});

test("stops at the nearest boundary when boundaries are nested", () => {
  const outer = el("root::a", null);
  const inner = el("root::a.0::b", outer);
  const leaf = el(null, inner);
  assert.equal(medley.ownerComponentId(leaf), "root::a.0::b");
});
