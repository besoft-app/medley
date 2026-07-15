# Medley

> Medley is a server-driven, hybrid UI framework for Spring Boot (Blazor Server / Vaadin Flow in
> spirit). Component logic and state live on the server; the browser is a thin client applying UI
> diffs (patches) over a WebSocket. Heavy interactions run in autonomous client islands. Java 21,
> hand-written template engine, VNode diffing.

A server-driven, hybrid UI framework for Spring Boot — working in the spirit of Blazor Server,
similar in operation to Vaadin Flow. Component logic and state live on the server by default;
only a **diff** (a list of patches) travels over the wire, not the whole HTML. For heavy
interaction there are **client islands** (`<medley-island>`) that run without round-trips.

This is a **PoC** (Proof of Concept) — a deliberately small, readable core, ready for further work.

---

## Getting started

The Gradle wrapper jar **is committed** (`gradle/wrapper/gradle-wrapper.jar`), so a fresh clone can
run `./gradlew` immediately — no bootstrap step needed. If the wrapper is ever missing, regenerate it
once:

```bash
# if you have Gradle 8.x installed:
gradle wrapper --gradle-version 8.10.2

# or use the bundled script (requires a system gradle):
./bootstrap.sh
```

**Java 21** is required. Gradle install: https://gradle.org/install/ (e.g. `brew install gradle`,
`sdk install gradle 8.10.2`).

---

## Running the demo

```bash
./gradlew :examples:counter-demo:bootRun
```

Open **http://localhost:8080/counter**.

What you'll see: a counter rendered on the server (SSR). Clicking `+` sends an event over the
WebSocket, the server changes state, computes the diff, and sends back a single `text` patch. In the
Network (WS) tab you can see that each subsequent click sends exactly one small operation.

---

## Structure

```
medley/
├── medley-core/                      # engine independent of Spring
│   └── app/besoft/medley/core/
│       ├── vnode/        VNode (VElement, VText)
│       ├── template/     template parser + expression evaluator + renderer
│       ├── diff/         Differ, Patch, HtmlSerializer
│       └── component/    Component, ComponentInstance, annotations, ActionScanner
│
├── medley-spring-boot-starter/       # Spring Boot integration
│   ├── app/besoft/medley/spring/            auto-configuration, WS, SSR, session, routing
│   └── resources/
│       ├── static/medley/medley.js   client runtime (hydration + patch + islands)
│       └── META-INF/spring/...imports  auto-configuration registration
│
└── examples/counter-demo/            # demonstration application
```

---

## How it works (the loop)

```
1. GET /counter            → SSR: the server renders full HTML with data-medley-id
2. medley.js               → hydration (wiring listeners) + opening the WebSocket
3. click "+"               → WS: { componentId:"root", action:"increment" }
4. server                  → invokeAction → mutate @State → re-render → diff
5. WS                      → [ { "op":"text", "id":"root.3.2", "value":"1" } ]
6. medley.js               → applyPatches: one change in the DOM
```

Component state lives in `MedleySession` (pinned to the HTTP session). The WebSocket inherits that
session via `MedleyHandshakeInterceptor`, so authorization and state are shared with the page.

---

## Component model

```java
@MedleyRoute("/counter")
@MedleyComponent("counter")
@Scope("prototype")
public class CounterComponent extends Component {
    @State int count = 0;
    @Param String label = "Clicks";

    @Action void increment() { count++; }
    @Action void reset()     { count = 0; }
}
```

```html
<!-- templates/medley/counter.html -->
<div class="counter">
  <span>{{ label }}: {{ count }}</span>
  <button @click="increment">+</button>
  <button @click="reset" *if="count > 0">reset</button>
</div>
```

Template syntax: `{{ expr }}` interpolation · `@event="action"` event → `@Action` ·
`:attr="expr"` computed attribute · `*if="expr"` condition · `*for="x : items"` loop (with `key`).

---

## Client islands (hybrid)

`<medley-island name="x">` is a boundary behind which the server does not manage the DOM. The
developer extends the base `window.medley.MedleyIsland` and registers a class:

```js
class Sparkline extends window.medley.MedleyIsland {
  mount()           { /* render locally; handle interactions, zero round-trips */ }
  onProp(name, val) { /* the server pushed props (a host-attribute patch) */ }
  // this.commit(action, payload) — persist coarse state on the server
}
window.medley.registerIsland("sparkline", Sparkline);
```

On the server side: `@MedleyIsland("sparkline")` with `@IslandAction` methods (the starter module).
`this.commit(action, payload)` sends an `island-commit` message that mutates the owner component's
`@State`; its re-render pushes the changed props back onto the host. The island runs autonomously —
the server only receives rare commits, which fulfills the "little server state" goal for
high-frequency interaction. A working example: the **sparkline** island in
`examples/counter-demo` (route `/chart`).

---

## PoC implementation status

| Element | Status |
|---|---|
| VNode + template parser + evaluator | ✅ done |
| Differ + patches + HTML serializer | ✅ done |
| Component model (@State/@Param/@Action) | ✅ done |
| medley.js (hydration, WS, patch, reconnect, islands) | ✅ done |
| End-to-end loop over WebSocket | ✅ **proven** (tools/dev-server and a real Spring WS) |
| Starter: auto-configuration, WS, SSR, session, routing (**Stage 2**) | ✅ done |
| Client islands — `MedleyIsland`/`@MedleyIsland`/`@IslandAction` (**Stage 3**) | ✅ done |
| Demo: counter (`/counter`) + sparkline island (`/chart`) | ✅ done |
| Keyed `*for`, input-value binding, form validation, partials (**Stage 4**) | ✅ done |
| Nested server components + child→parent callbacks (`<medley-component>`, `@Param`/`@Output`) | ✅ done |
| Security (WS origin / auth / inbound hardening), reconnect resync + eviction (**Stage 4**) | ✅ done |
| Metrics — optional Micrometer (`medley.messages` / `.message.duration` / `.patches`) (**Stage 5**) | ✅ done |
| Dev-tools — patch log, DOM flash, component-tree inspector (`medley.devtools.enabled`) (**Stage 5**) | ✅ done |
| Wire compression — `permessage-deflate` (RFC 7692), on by default via Tomcat (**Stage 5**) | ✅ done |
| Redis session backend, Maven archetype | ⏳ Stage 5 |

The full `./gradlew build` is green: 3 modules compile, **140 core + 74 starter tests** pass (plus a
dependency-free 40-test client harness, run separately), and the demo bootJar builds.

---

## Core tests

```bash
./gradlew :medley-core:test
```

Unit tests covering: the expression evaluator, the template parser, the renderer (including `*if`
placeholder stability, `*for` keys and the opaque `<medley-island>`/`<medley-component>` hosts), the
differ (including keyed reconciliation and the opaque-boundary skip), the HTML serializer (including
XSS escaping) and the full component render → action → diff loop.

## End-to-end verification over WebSocket (without Spring)

Stage 1 was proven "on the wire" with a lightweight JDK-only harness — see
**`tools/dev-server/README.md`**. The server renders SSR, serves `medley.js`, handles the
WebSocket; a scripted client sends `increment`/`decrement`/`reset` and prints the returned
patches. The key result: the second `increment` returns **exactly one** patch
`{"op":"text","id":"root.3.2","value":"2"}`.

---

## Core test without Spring

The engine alone can run without Spring — the core has no dependency beyond Jackson.

---

## License

Medley is licensed under the **Apache License, Version 2.0** — the standard license across the
Spring ecosystem. You may use, modify, and embed it in your own applications (including closed-source
and commercial ones), subject to the terms of the license, which include an explicit patent grant.

See the [`LICENSE`](LICENSE) file for the full text and [`NOTICE`](NOTICE) for attribution.

```
Copyright 2026 Besoft

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
```
