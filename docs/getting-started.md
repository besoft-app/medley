# Getting started

Scaffold a new Medley application with the Maven archetype. Everything is local — Medley is
published to your Maven Local repository (`~/.m2`), not a remote repo.

## Prerequisites

- **Java 21**
- **Maven 3.8+**
- A clone of this repository (the framework source)

## 1. Publish Medley to Maven Local

From the repository root:

```bash
./gradlew publishToMavenLocal
```

This installs `app.besoft.medley:medley-core:0.6.0` and
`app.besoft.medley:medley-spring-boot-starter:0.6.0` into `~/.m2`.

## 2. Install the archetype

```bash
cd tools/medley-archetype
mvn install
```

## 3. Generate a project

From any directory:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=app.besoft.medley \
  -DarchetypeArtifactId=medley-archetype \
  -DarchetypeVersion=0.6.0 \
  -DgroupId=com.example -DartifactId=my-app -Dversion=0.1.0 -Dpackage=com.example.myapp \
  -DinteractiveMode=false
```

(The archetype version and the Medley starter version it wires in both track the framework
version — `0.6.0` here. Bump `-DarchetypeVersion` when the framework version bumps.)

## 4. Run it

```bash
cd my-app
mvn spring-boot:run
```

Open:

- **http://localhost:8080/** — a server-driven counter (each click sends one small patch)
- **http://localhost:8080/signup** — server-authoritative form validation
- **http://localhost:8080/chart** — a client island (hover is 100% client-side; a click commits)

## What you got

- `App` — the `@SpringBootApplication` entry point
- `CounterComponent` / `counter.html` — the SSR → WebSocket → single-patch loop
- `SignupComponent` / `signup.html` — validation in an `@Action`, errors via `*if` + `{{ }}`
- `ChartComponent` / `chart.html` / `SparklineIsland` / `sparkline-island.js` — a hybrid island

See [configuration.md](configuration.md) for the `medley.*` settings, and the repository `README.md`
for the component model and template syntax.
