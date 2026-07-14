# client-side test harness

Automated tests for the client scripts — the runtime (`src/main/resources/static/medley/medley.js`),
which had no automated coverage before (it was exercised only by the demo pages), and the dev-tools
overlay (`static/medley/devtools.js`, Stage 5 increment 2).

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
- `devtools.js` (Stage 5.2) — `isMedleySocket` (which sockets get tapped), `describePatch` (pinned
  against every op `PatchEncoder` emits, with the field names it really puts on the wire), `summarize` /
  `formatOps`, `patchTargets`, the log ring buffer, `treeDepth`. The DOM half (overlay panel, flashing)
  needs a browser and is demo-verified.

## Two DOMs, and which to use

- **`html-parse.js`** — a real (if small) HTML fragment parser. Use it whenever a test depends on a patch
  actually **finding its target in server output**. It models the two things a browser does and a shim
  cannot fake: **adjacent text runs merge into one node**, and **only elements are addressable** by
  `data-medley-id`. `dom-after-patch.test.js` uses it to apply a patch to real SSR markup (pinned on the
  Java side by `MedleySsrTest`) and assert **what the user sees**.
- **`dom-shim.js`** — a hand-built fake DOM, fine for exercising `applyPatch`'s mechanics in isolation.
  **It cannot prove deliverability**: it lets a test fabricate an element carrying *any* id, including one
  the server only gives to a **text node**. That is exactly how a critical bug shipped green — text
  patches were addressed to text-node ids that no real DOM exposes, and every one was silently dropped in
  the browser while this harness stayed green (MEDLEY_DESIGN §11a).

**Lesson, worth repeating: assert the DOM after the patch, not the patch.**

## Deferred (follow-up)

- `applyPatch` `replace` / `insert` and full `wireEvent` end-to-end. `html-parse.js` now provides the
  fragment parsing these need, but not yet the mutation surface (`insertBefore`, `replaceWith`) — a small
  extension, no new dependency. Migrating `applyPatch.test.js` off `dom-shim.js` onto the real parser
  (and then deleting the shim) would close the blind spot above for good.
