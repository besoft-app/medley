# Stage 5.5 — Documentation + Maven archetype

> Design spec. Approved by the owner (maw2be) on 2026-07-17.
> Branch: `stage5/inc5-docs-archetype` (off `dev`). This is the **last increment of Stage 5**;
> completing it makes Stage 5 complete and clears the way to a `dev`→`master` milestone promotion
> (done separately, not part of this spec).

## 1. Purpose and scope

The final Stage 5 increment delivers the "get a new app running" experience plus the reference
documentation that has been missing. Three deliverables:

1. **Local publishing** — publish `medley-core` and `medley-spring-boot-starter` (`0.5.0`) to
   **Maven Local** (`~/.m2`) via Gradle `maven-publish`.
2. **Maven archetype** — `app.besoft.medley:medley-archetype:0.5.0`, a real Maven archetype that
   scaffolds a working Spring Boot application (counter + form + island) which resolves Medley from
   `mavenLocal`.
3. **Documentation** — `docs/getting-started.md` (the archetype flow) and `docs/configuration.md`
   (the full `medley.*` property reference), plus a status-table update in `README.md`.

**Explicit non-goals / invariants preserved.** No change to `medley-core`, the component API
(`@MedleyComponent/@State/@Param/@Action/@MedleyRoute/@Output`), the wire protocol, `medley.js`, or
any load-bearing invariant (stable ids, `*if` placeholder, minimal diff, keyed reconciliation,
addressability). This increment is entirely build/release plumbing + scaffolding + docs. No new
**runtime** dependency is added to any shipped module (`maven-publish` is a built-in Gradle plugin;
the archetype is a dev-time artifact outside the Gradle build).

### Decisions locked during brainstorming (owner-approved)

| # | Decision | Choice |
|---|----------|--------|
| 1 | Scaffolding mechanism | **Real Maven archetype** (the literal plan), not a Gradle template or docs-only |
| 2 | Generated project's build tool | **Maven** (`pom.xml`, `mvn spring-boot:run`) |
| 3 | How the generated app resolves Medley | **Publish to Maven Local** (`~/.m2`) via Gradle `maven-publish`; no remote repo |
| 4 | Generated app content | **Counter + form + island** (lifted from `examples/counter-demo`) |
| 5 | Documentation scope | **Quickstart + property reference** (`docs/getting-started.md` + `docs/configuration.md`) + README status row |

Deferred (deliberately, not part of 5.5): remote publishing (Maven Central / GitHub Packages — the
project is repo-only, no publish infrastructure), a Gradle init template, GPG signing.

## 2. Local publishing (Gradle `maven-publish` → Maven Local)

Apply the `maven-publish` plugin to `medley-core` and `medley-spring-boot-starter` and register a
`MavenPublication` from the Java component so each module's POM carries its dependencies:

- `medley-core` → `app.besoft.medley:medley-core:0.5.0` (its only compile dependency is Jackson).
- `medley-spring-boot-starter` → `app.besoft.medley:medley-spring-boot-starter:0.5.0`, whose POM
  must list `medley-core` and the Spring Boot web/websocket/autoconfigure dependencies **with
  concrete versions**.

Publish target is **Maven Local only**: `./gradlew publishToMavenLocal`. No `repositories {}` block
for a remote, no signing.

**Implementation risk to verify (not redesign):** the starter uses
`io.spring.dependency-management` + a version catalog, so dependency versions are BOM-managed and
absent from the build script. Confirm the generated POM under `~/.m2` records concrete versions for
the Spring dependencies (open the published `.pom`). If versions come through empty, resolve it
within the established build conventions (e.g. rely on dependency-management's POM contribution, or
pin versions in the publication) — this is an implementation detail for the plan, not a design
change. A generated app failing to resolve transitive Spring versions is the failure mode to guard
against.

`group`/`version` already come from the root build (`app.besoft.medley` / `0.5.0`); publishing
inherits them. When the project version bumps, the archetype's `-DarchetypeVersion` and the
starter dependency version in the generated POM move with it — call this out in the docs so the two
never silently drift.

## 3. The archetype module — `tools/medley-archetype/`

A standalone **Maven** project (packaging `maven-archetype`), placed under `tools/` alongside
`tools/dev-server` — both are tooling that lives **outside** the Gradle multi-module build. It is
**not** added to `settings.gradle.kts`. Built/installed with its own Maven: `cd tools/medley-archetype
&& mvn install` (puts `app.besoft.medley:medley-archetype:0.5.0` into `~/.m2`).

Standard archetype layout:

```
tools/medley-archetype/
  pom.xml                                              # packaging: maven-archetype
  src/main/resources/
    META-INF/maven/archetype-metadata.xml             # fileSets, required properties, filtering flags
    archetype-resources/
      pom.xml                                          # prototype POM for the GENERATED project
      src/main/java/App.java                           # @SpringBootApplication
      src/main/java/CounterComponent.java
      src/main/java/SignupComponent.java
      src/main/java/ChartComponent.java
      src/main/java/SparklineIsland.java               # @MedleyIsland handler
      src/main/resources/application.yml
      src/main/resources/templates/medley/counter.html
      src/main/resources/templates/medley/signup.html
      src/main/resources/templates/medley/chart.html
      src/main/resources/static/sparkline-island.js
```

The archetype declares required properties `groupId`, `artifactId`, `version`, `package` (standard).
Java sources live under `archetype-resources/src/main/java` and land in the chosen package.

## 4. Generated application content

Lifted from `examples/counter-demo` (proven code, not invented). The generated app demonstrates the
three pillars of Medley in one runnable project:

- **`App`** — `@SpringBootApplication`, main method.
- **Counter** — `CounterComponent` (`@MedleyRoute("/")`, `@State int count`, `@Action increment/reset`)
  + `counter.html`. The end-to-end SSR→WS→single-text-patch loop.
- **Form** — `SignupComponent` (`@MedleyRoute("/signup")`, imperative validation in `@Action submit()`,
  errors in `@State Map<String,String> errors`) + `signup.html` (uses `@event="submit($value)"` +
  `*if` + `{{ errors.x }}`). Shows validation as server-authoritative.
- **Island** — `ChartComponent` (`@MedleyRoute("/chart")`) hosting `<medley-island name="sparkline">`
  + `chart.html`, `SparklineIsland` (`@MedleyIsland` + `@IslandAction`), and `sparkline-island.js`
  (extends `window.medley.MedleyIsland`, `registerIsland`). Shows client-autonomous interaction +
  coarse commit-up.
- **`application.yml`** — minimal (server port, and it is the place to point at for the `medley.*`
  reference in `docs/configuration.md`).
- **`pom.xml`** — `spring-boot-starter-parent`, one dependency
  `app.besoft.medley:medley-spring-boot-starter:0.5.0`, the `spring-boot-maven-plugin`. Java 21.

Package/coordinate tokens (`${package}`, `${groupId}`, `${artifactId}`, `${version}`) come from the
archetype. The lifted component *logic* is unchanged from the demo; only package declarations become
`${package}`.

## 5. Load-bearing gotcha — Velocity filtering must be OFF for templates and island JS

The Maven archetype plugin runs resource files through **Velocity** (`$foo`, `#directive`) by
default. Medley's authoring surface collides with Velocity syntax:

- HTML templates use `{{ expr }}` interpolation (harmless to Velocity) **but also** `@event`
  call-syntax with `$value` / `$checked` / `$key` tokens (e.g. `@change="submit($value)"`), which
  Velocity would try to resolve and blank out.
- `sparkline-island.js` contains `$` (jQuery-free, but `$`-prefixed identifiers / template literals
  are plausible and must survive verbatim).

**Requirement:** in `archetype-metadata.xml`, declare the `templates/**` and `static/**` fileSets
with **`filtered="false"`** so they are copied verbatim. Java sources stay **filtered** (so
`package` substitution works — Java does not contain `$value`-style tokens; the bindings live in the
HTML). This is not optional: without it, a generated app's templates render broken. The verification
step (§7) — actually generating and running the app — is what proves this is correct, because a
file-level "did it generate" check would not catch a mangled `{{ }}`/`$value`.

## 6. Documentation

### `docs/getting-started.md`
The archetype flow, start to finish:
0. Prerequisites: **Java 21**, **Maven 3.9+**, a clone of this repo.
1. Build & publish the framework to Maven Local: `./gradlew publishToMavenLocal`.
2. Install the archetype: `cd tools/medley-archetype && mvn install`.
3. Generate a project (from any directory):
   ```
   mvn archetype:generate \
     -DarchetypeGroupId=app.besoft.medley \
     -DarchetypeArtifactId=medley-archetype \
     -DarchetypeVersion=0.5.0 \
     -DgroupId=com.example -DartifactId=my-app -Dversion=0.1.0 -Dpackage=com.example.myapp
   ```
4. Run it: `cd my-app && mvn spring-boot:run`, open **http://localhost:8080/** (also `/signup`,
   `/chart`).
5. A short note that steps 1–2 are local-only (Maven Local), and that the archetype version tracks
   the framework version.

### `docs/configuration.md`
A reference table of every `medley.*` key, taken verbatim from `MedleyProperties`:

| Property | Default | Meaning |
|---|---|---|
| `medley.websocket-path` | `/medley/ws` | WS endpoint for events + patches |
| `medley.template-location` | `templates/medley/` | classpath location of component templates |
| `medley.session.max-components` | `2000` | per-session live-component cap (`0` = unlimited) |
| `medley.security.allowed-origins` | *(empty = same-origin only)* | allowed WS handshake origins; `"*"` = all (dev only) |
| `medley.security.max-message-bytes` | `65536` | max inbound WS text length (`0` = off) |
| `medley.security.require-authenticated-handshake` | `false` | reject an unauthenticated handshake (401) |
| `medley.devtools.enabled` | `false` | dev inspector overlay + `/medley/devtools/tree` (exposes `@State`) |

Plus a note: **metrics have no property** — they activate purely by the app putting Micrometer + a
`MeterRegistry` on the classpath (e.g. adding Actuator), per 5.1.

### `README.md`
- Flip the status-table row `Redis session backend, Maven archetype ⏳ Stage 5` to reflect reality:
  Maven archetype **done**, Redis session backend **rejected** (link the design decision).
- Add a short "Create a new project" pointer to `docs/getting-started.md`.

## 7. Verification (definition of done)

Beyond "the files exist", the loop must be proven end-to-end:

1. `./gradlew build` green (3 modules, 140 core + 79 starter tests, demo bootJar).
2. `./gradlew publishToMavenLocal` — confirm both artifacts land in `~/.m2` and inspect the
   starter's published `.pom` for concrete Spring versions (§2 risk).
3. `cd tools/medley-archetype && mvn install` — archetype installs.
4. **Generate into a temp dir** (the session scratchpad) and **run** the generated app:
   `mvn -q -DskipTests package` at minimum, ideally `mvn spring-boot:run` and hit `/`, `/signup`,
   `/chart`. This is the step that proves Velocity filtering (§5) and version wiring (§2) are right.
   *(Generation/run happens under the OS temp/scratchpad — never inside the repo tree.)*
5. No automated JUnit/JS test is added (there is no natural unit under test for a scaffold); the
   verification is the real generate-and-run. Note this explicitly in the increment write-up so the
   absence of a test is a documented decision, not an oversight.

## 8. Status/bookkeeping updates (same change)

Per the working agreement to keep the docs truthful:
- **`CLAUDE.md`** — mark 5.5 done; Stage 5 complete (all increments 5.1–5.5).
- **`MEDLEY_DESIGN.md` §11** — add the 5.5 bullet; mark Stage 5 complete.
- **`MEMORY.md`** index — update the progress line.

## 9. Branch / workflow

- Work on `stage5/inc5-docs-archetype` (already created off `dev`).
- Self-review (`/code-review` or `@reviewer`) + the verification above before merge.
- Merge `--no-ff` into `dev`. `dev`→`master` milestone promotion (Stage 5 complete, tag) is a
  **separate** step, not covered here.
