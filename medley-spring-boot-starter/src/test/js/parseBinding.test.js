"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const medley = require("../../main/resources/static/medley/medley.js");

// parseBinding turns a data-medley-on-* value into { action, argSpecs } (Stage 4, increment 2).
// This is the client parser that was previously covered by the /search demo only.

test("bare action name -> empty argSpecs", () => {
  assert.deepEqual(medley.parseBinding("increment"), { action: "increment", argSpecs: [] });
});

test("call form with one token arg", () => {
  assert.deepEqual(medley.parseBinding("setQuery($value)"), { action: "setQuery", argSpecs: ["$value"] });
});

test("call form with several args (tokens + literals)", () => {
  assert.deepEqual(
    medley.parseBinding("save($value, 'x', 3, true)"),
    { action: "save", argSpecs: ["$value", "'x'", "3", "true"] }
  );
});

test("empty parens -> empty argSpecs", () => {
  assert.deepEqual(medley.parseBinding("go()"), { action: "go", argSpecs: [] });
});

test("malformed binding degrades to a bare, argument-less action", () => {
  const r = medley.parseBinding("!!!");
  assert.deepEqual(r.argSpecs, []);
});
