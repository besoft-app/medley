"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const medley = require("../../main/resources/static/medley/medley.js");

// extractArg resolves one arg spec against the live DOM event/target (Stage 4, increment 2).

test("$value reads the target's value", () => {
  assert.equal(medley.extractArg("$value", {}, { value: "abc" }), "abc");
});

test("$checked reads the target's checked flag as a boolean", () => {
  assert.equal(medley.extractArg("$checked", {}, { checked: true }), true);
  assert.equal(medley.extractArg("$checked", {}, {}), false);
});

test("$key reads the keyboard event key", () => {
  assert.equal(medley.extractArg("$key", { key: "Enter" }, {}), "Enter");
});

test("single-quoted string literal", () => {
  assert.equal(medley.extractArg("'hi'", {}, {}), "hi");
});

test("numeric literal is coerced to a Number", () => {
  assert.equal(medley.extractArg("42", {}, {}), 42);
  assert.equal(medley.extractArg("-3.5", {}, {}), -3.5);
});

test("boolean literals", () => {
  assert.equal(medley.extractArg("true", {}, {}), true);
  assert.equal(medley.extractArg("false", {}, {}), false);
});

test("unknown token is sent verbatim as a string", () => {
  assert.equal(medley.extractArg("foo", {}, {}), "foo");
});
