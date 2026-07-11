# medley.js test harness

Automated tests for the client runtime (`src/main/resources/static/medley/medley.js`), which had no
automated coverage before — it was exercised only by the demo pages.

## Running

Requires **Node 20+** (uses the built-in `node:test` runner and `node:assert`). No npm install, no
`node_modules`, no network — zero dependencies.

```bash
cd medley-spring-boot-starter/src/test/js
node --test
```

This suite is **not** wired into `./gradlew build` on purpose: a Node-dependent Gradle task would break
"the full build is green" on machines without Node. Run it manually (or from CI where Node is present).

## How it works

`medley.js` is a browser IIFE. Its bootstrap (`window.medley = …` + `boot()`) is guarded on
`window`/`document`, so `require()`-ing the file under plain Node is side-effect-free; a small
`module.exports` block (a no-op in the browser) exposes the pure helpers. `dom-shim.js` provides a tiny
fake `document` for the `applyPatch` tests — just enough surface (querySelector by `data-medley-id`,
element attribute/textContent/listener members), no HTML parsing.

## Covered

- `parseBinding` / `extractArg` — the increment-2 call-syntax parser (`action($value, 'lit', 3)`).
- `ownerComponentId` — the increment-4b.2 DOM walk-up to the nearest `data-medley-cid`.
- `applyPatch` / `applyPatches` — the `text` / `attr` / `removeAttr` / `event` / `removeEvent` /
  `remove` ops.

## Deferred (follow-up)

- `applyPatch` `replace` / `insert` and full `wireEvent` end-to-end need real HTML-fragment parsing
  (`htmlToElement` → `<template>.innerHTML`), which the lightweight shim does not provide. Decide
  between a hand-rolled fragment parser (zero dependency) or adding `jsdom` as a test-only devDependency
  before covering those.
