"use strict";

/*
 * A tiny, dependency-free DOM shim — just enough surface for medley.js's applyPatch to run under
 * Node's test runner. It backs querySelector('[data-medley-id="X"]') with a Map, and gives elements
 * the members applyPatch touches (textContent, get/set/removeAttribute, add/removeEventListener,
 * remove, querySelectorAll). It deliberately does NOT parse HTML, so the `replace`/`insert` patch ops
 * (which need htmlToElement) are out of scope here — see the harness README.
 */
function makeDom() {
  const byId = new Map();

  function element(id, tag) {
    const attrs = new Map();
    const listeners = {};
    const node = {
      tagName: (tag || "div").toUpperCase(),
      textContent: "",
      parentElement: null,
      attrs,
      listeners,
      get value() { return attrs.get("value"); },
      set value(v) { attrs.set("value", String(v)); },
      getAttribute(name) { return attrs.has(name) ? attrs.get(name) : null; },
      setAttribute(name, value) {
        attrs.set(name, String(value));
        if (name === "data-medley-id") byId.set(String(value), node);
      },
      removeAttribute(name) { attrs.delete(name); },
      hasAttribute(name) { return attrs.has(name); },
      addEventListener(ev, fn) { (listeners[ev] = listeners[ev] || []).push(fn); },
      removeEventListener(ev, fn) {
        listeners[ev] = (listeners[ev] || []).filter(function (f) { return f !== fn; });
      },
      listenerCount(ev) { return (listeners[ev] || []).length; },
      querySelector() { return null; },
      querySelectorAll() { return []; },
      matches() { return false; },
      remove() {
        const self = attrs.get("data-medley-id");
        if (self != null) byId.delete(String(self));
      }
    };
    if (id != null) { node.setAttribute("data-medley-id", id); }
    return node;
  }

  const document = {
    querySelector(selector) {
      const m = /\[data-medley-id="(.*)"\]/.exec(selector);
      return m ? (byId.get(m[1]) || null) : null;
    },
    createElement(tag) { return element(null, tag); }
  };

  return { document, element, byId };
}

module.exports = { makeDom };
