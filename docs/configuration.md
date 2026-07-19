# Configuration

All settings live under the `medley.*` prefix in `application.yml` (bound by `MedleyProperties`).
Every key is optional; the defaults are production-safe.

| Property | Default | Meaning |
|---|---|---|
| `medley.websocket-path` | `/medley/ws` | WebSocket endpoint for events and patches. |
| `medley.template-location` | `templates/medley/` | Classpath location of component templates. |
| `medley.session.max-components` | `2000` | Cap on live component instances per session (root + nested). Mounting beyond it fails fast. `0` or negative = unlimited. |
| `medley.security.allowed-origins` | *(empty = same-origin only)* | Allowed WebSocket handshake origins. Empty rejects cross-origin handshakes (closes cross-site WS hijacking / CSRF). Add trusted origins (e.g. `https://app.example.com`); the single value `"*"` allows all (development only). |
| `medley.security.max-message-bytes` | `65536` | Max inbound WebSocket text length; a larger frame is rejected before parsing. `0` or negative disables the check. |
| `medley.security.require-authenticated-handshake` | `false` | When `true`, a handshake with no authenticated principal is rejected (401). The principal, when present, is always bound onto the socket regardless. |
| `medley.devtools.enabled` | `false` | Dev inspector: in-page patch/tree overlay + `GET /medley/devtools/tree`. **Keep off in production** — the snapshot exposes a session's `@State`/`@Param`. |

## Metrics

Metrics have **no property**. They activate automatically when the application puts Micrometer and a
`MeterRegistry` on the classpath — typically by adding `spring-boot-starter-actuator`. Medley then
publishes `medley.messages`, `medley.message.duration`, and `medley.patches` (visible at
`/actuator/metrics`). Without a `MeterRegistry`, the metrics path is a no-op.

## Wire compression

WebSocket frames are compressed with `permessage-deflate` (RFC 7692), negotiated automatically by
the embedded Tomcat whenever the browser offers it. There is nothing to configure.
