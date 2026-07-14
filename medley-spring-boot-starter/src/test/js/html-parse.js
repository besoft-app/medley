"use strict";

/*
 * A minimal, dependency-free HTML fragment parser producing a DOM faithful in the two ways that matter
 * for Medley — and that `dom-shim.js` got wrong:
 *
 *   1. ADJACENT TEXT RUNS MERGE. The server writes `count: ` and `0` as separate VText nodes; a browser
 *      parses the resulting markup into ONE text node. A shim that lets a test create a text node per
 *      VText models a DOM that never exists.
 *   2. ONLY ELEMENTS ARE ADDRESSABLE. `data-medley-id` is an attribute, so `querySelector` can only ever
 *      return an element. A shim that lets a test fabricate an "element" carrying a *text node's* id
 *      hides exactly the bug that shipped (MEDLEY_DESIGN §11a).
 *
 * Scope: what medley.js's applyPatch actually touches. Selector support is the single form it uses,
 * `[data-medley-id="…"]`.
 */

const VOID_TAGS = new Set(["area", "base", "br", "col", "hr", "img", "input", "link", "meta", "source"]);

function decode(s) {
  return s.replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&amp;/g, "&");
}

function textNode(value) {
  return { nodeType: 3, nodeValue: value, parentElement: null };
}

function makeElement(tag, attrs) {
  const el = {
    nodeType: 1,
    tagName: tag.toUpperCase(),
    attrs: new Map(attrs || []),
    childNodes: [],
    parentElement: null,
    listeners: {},

    get textContent() {
      return el.childNodes.map(function (n) {
        return n.nodeType === 3 ? n.nodeValue : n.textContent;
      }).join("");
    },
    // Setting textContent replaces all children with a single text node — the DOM's own semantics,
    // and exactly what applyPatch's `text` op relies on.
    set textContent(v) {
      const t = textNode(String(v));
      t.parentElement = el;
      el.childNodes = [t];
    },

    getAttribute(name) { return el.attrs.has(name) ? el.attrs.get(name) : null; },
    setAttribute(name, value) { el.attrs.set(name, String(value)); },
    removeAttribute(name) { el.attrs.delete(name); },
    hasAttribute(name) { return el.attrs.has(name); },
    addEventListener(type, fn) { (el.listeners[type] = el.listeners[type] || []).push(fn); },
    removeEventListener(type) { delete el.listeners[type]; },
    remove() {
      const p = el.parentElement;
      if (p) p.childNodes = p.childNodes.filter(function (n) { return n !== el; });
      el.parentElement = null;
    },

    get children() {
      return el.childNodes.filter(function (n) { return n.nodeType === 1; });
    },
    querySelector(sel) { return query(el, sel)[0] || null; },
    querySelectorAll(sel) { return query(el, sel); }
  };
  return el;
}

/** Only the selector form medley.js uses: [data-medley-id="…"] (and the attribute-presence form). */
function query(root, sel) {
  const exact = /^\[data-medley-([\w-]+)="(.*)"\]$/.exec(sel);
  const present = /^\[data-medley-([\w-]+)\]$/.exec(sel);
  const out = [];
  walk(root, function (el) {
    if (exact) {
      const want = exact[2].replace(/\\(.)/g, "$1"); // undo the client's CSS escaping
      if (el.getAttribute("data-medley-" + exact[1]) === want) out.push(el);
    } else if (present && el.hasAttribute("data-medley-" + present[1])) {
      out.push(el);
    }
  });
  return out;
}

function walk(el, fn) {
  for (const child of el.childNodes) {
    if (child.nodeType === 1) {
      fn(child);
      walk(child, fn);
    }
  }
}

/** Parse a fragment into a document whose querySelector behaves like the browser's. */
function parseFragment(html) {
  const root = makeElement("body");
  const stack = [root];
  const tagRe = /<(\/?)([a-zA-Z][\w-]*)((?:[^>"']|"[^"]*"|'[^']*')*?)(\/?)>/g;
  let cursor = 0;
  let m;

  while ((m = tagRe.exec(html)) !== null) {
    appendText(stack[stack.length - 1], html.slice(cursor, m.index));
    cursor = tagRe.lastIndex;

    const [, closing, tag, rawAttrs, selfClosed] = m;
    if (closing) {
      if (stack.length > 1) stack.pop();
      continue;
    }
    const el = makeElement(tag, parseAttrs(rawAttrs));
    const parent = stack[stack.length - 1];
    el.parentElement = parent;
    parent.childNodes.push(el);
    if (!selfClosed && !VOID_TAGS.has(tag.toLowerCase())) {
      stack.push(el);
    }
  }
  appendText(stack[stack.length - 1], html.slice(cursor));

  return { document: root, root: root.children[0] };
}

/**
 * Append a text run, MERGING it into the preceding text node if there is one — the behaviour that makes
 * a text node's position unaddressable in a real browser.
 */
function appendText(parent, raw) {
  if (raw === "") return;
  const value = decode(raw);
  const last = parent.childNodes[parent.childNodes.length - 1];
  if (last && last.nodeType === 3) {
    last.nodeValue += value;
    return;
  }
  const t = textNode(value);
  t.parentElement = parent;
  parent.childNodes.push(t);
}

function parseAttrs(raw) {
  const attrs = [];
  const re = /([\w:.-]+)\s*=\s*"([^"]*)"/g;
  let m;
  while ((m = re.exec(raw)) !== null) {
    attrs.push([m[1], decode(m[2])]);
  }
  return attrs;
}

module.exports = { parseFragment };
