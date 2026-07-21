# Stage 5.5 — Documentation + Maven Archetype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the last Stage 5 increment — publish Medley to Maven Local, provide a Maven archetype that scaffolds a working counter+form+island app, and write the getting-started + configuration docs.

**Architecture:** Build/release plumbing + scaffolding + docs only. Gradle `maven-publish` puts `medley-core` and `medley-spring-boot-starter` into `~/.m2`. A standalone Maven archetype under `tools/medley-archetype/` (outside the Gradle build) generates a Maven Spring Boot project that resolves Medley from `mavenLocal`. No change to `medley-core`, the component API, the wire protocol, or `medley.js`.

**Tech Stack:** Gradle Kotlin DSL, `maven-publish`; Apache Maven (`maven-archetype-plugin` / `archetype-packaging` 3.2.1); Spring Boot 3.3.4; Java 21.

## Global Constraints

- **groupId** `app.besoft.medley`, **version** `0.5.0` (from the root `build.gradle.kts`; publications inherit both).
- **Java 21**; **Spring Boot 3.3.4** (from `gradle/libs.versions.toml`).
- **No runtime dependency** added to any shipped module; no change to core, component API, wire, or `medley.js`.
- **Publish to Maven Local only** (`publishToMavenLocal`). No remote repo, no signing.
- The archetype module lives under `tools/medley-archetype/`, is a **Maven** project (packaging `maven-archetype`), and is **NOT** added to `settings.gradle.kts`.
- **Velocity filtering MUST be OFF** (`filtered="false"`) for the archetype's `src/main/resources/**` (HTML templates + island JS + yml) — they contain `{{ }}` and `$value`/`$checked` tokens Velocity would mangle. Java sources stay `filtered="true" packaged="true"`.
- Generated app: counter at `/`, form at `/signup`, island at `/chart`; server port 8080.
- All temp generation/build during verification happens under the OS scratchpad (`C:\Users\maw2b\AppData\Local\Temp\claude\C--Users-maw2b-IdeaProjects-medley\<session>\scratchpad`), **never inside the repo tree**.
- Branch: `stage5/inc5-docs-archetype` (already created off `dev`).

---

### Task 1: Publish medley-core + starter to Maven Local

**Files:**
- Modify: `medley-core/build.gradle.kts`
- Modify: `medley-spring-boot-starter/build.gradle.kts`

**Interfaces:**
- Produces (into `~/.m2`): `app.besoft.medley:medley-core:0.5.0`, `app.besoft.medley:medley-spring-boot-starter:0.5.0`. The starter's published POM must carry `medley-core:0.5.0` plus concrete Spring dependency versions (BOM-resolved by `io.spring.dependency-management`'s default generated-POM customization).

- [ ] **Step 1: Add `maven-publish` to `medley-core/build.gradle.kts`**

Replace the whole file with:

```kotlin
plugins {
    `maven-publish`
}

dependencies {
    implementation(libs.jackson.databind)

    testImplementation(libs.junit.jupiter)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
```

(The root `subprojects {}` block still applies the `java` plugin and the Java-21 toolchain, so `components["java"]` exists. `group`/`version` come from the root `allprojects {}`.)

- [ ] **Step 2: Add `maven-publish` to `medley-spring-boot-starter/build.gradle.kts`**

Add `` `maven-publish` `` to the `plugins {}` block and a `publishing {}` block at the end. The block becomes:

```kotlin
plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spring.dependency.management)
}
```

and append at the end of the file:

```kotlin
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
```

Leave the existing `dependencyManagement {}` and `dependencies {}` blocks unchanged.

- [ ] **Step 3: Publish to Maven Local**

Run: `./gradlew publishToMavenLocal`
Expected: `BUILD SUCCESSFUL`. Both modules' artifacts appear under `~/.m2/repository/app/besoft/medley/`.

- [ ] **Step 4: Verify the published starter POM carries concrete versions (the §2 risk)**

Run: `cat ~/.m2/repository/app/besoft/medley/medley-spring-boot-starter/0.5.0/medley-spring-boot-starter-0.5.0.pom`
Expected: a `<dependency>` on `app.besoft.medley:medley-core` version `0.5.0`, AND `org.springframework.boot:spring-boot-starter-web` / `-websocket` with a concrete `<version>3.3.4</version>` (not empty).
If any Spring version is missing/empty: add explicit versions in the publication or confirm `io.spring.dependency-management`'s `generatedPomCustomization` is enabled — do NOT proceed to Task 2 until the POM resolves standalone.

- [ ] **Step 5: Confirm the Gradle build is still green**

Run: `./gradlew build`
Expected: `BUILD SUCCESSFUL`; 140 core + 79 starter tests pass; the demo bootJar builds.

- [ ] **Step 6: Commit**

```bash
git add medley-core/build.gradle.kts medley-spring-boot-starter/build.gradle.kts
git commit -m "build(stage5.5): publish medley-core + starter to Maven Local

maven-publish on both modules so the archetype's generated app can
resolve app.besoft.medley:* from ~/.m2 (publishToMavenLocal).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: Archetype module scaffolding (archetype POM + descriptor + prototype POM)

**Files:**
- Create: `tools/medley-archetype/pom.xml`
- Create: `tools/medley-archetype/src/main/resources/META-INF/maven/archetype-metadata.xml`
- Create: `tools/medley-archetype/src/main/resources/archetype-resources/pom.xml`

**Interfaces:**
- Produces: an installable archetype `app.besoft.medley:medley-archetype:0.5.0` whose required property `medleyVersion` defaults to `0.5.0`. The prototype (generated) POM depends on `medley-spring-boot-starter:${medleyVersion}`.
- Consumes: the Maven-Local artifacts from Task 1 (only at generate/run time, Task 4).

- [ ] **Step 1: Create the archetype's own POM**

Create `tools/medley-archetype/pom.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>app.besoft.medley</groupId>
  <artifactId>medley-archetype</artifactId>
  <version>0.5.0</version>
  <packaging>maven-archetype</packaging>

  <name>Medley Maven Archetype</name>
  <description>Scaffolds a Spring Boot application using the Medley server-driven UI framework.</description>

  <build>
    <extensions>
      <extension>
        <groupId>org.apache.maven.archetype</groupId>
        <artifactId>archetype-packaging</artifactId>
        <version>3.2.1</version>
      </extension>
    </extensions>
    <pluginManagement>
      <plugins>
        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-archetype-plugin</artifactId>
          <version>3.4.1</version>
        </plugin>
      </plugins>
    </pluginManagement>
  </build>
</project>
```

- [ ] **Step 2: Create the archetype descriptor (filtering flags — the load-bearing part)**

Create `tools/medley-archetype/src/main/resources/META-INF/maven/archetype-metadata.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<archetype-descriptor
    xmlns="https://maven.apache.org/plugins/maven-archetype-plugin/archetype-descriptor/1.1.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://maven.apache.org/plugins/maven-archetype-plugin/archetype-descriptor/1.1.0 https://maven.apache.org/xsd/archetype-descriptor-1.1.0.xsd"
    name="medley-archetype">

  <requiredProperties>
    <requiredProperty key="medleyVersion">
      <defaultValue>0.5.0</defaultValue>
    </requiredProperty>
  </requiredProperties>

  <fileSets>
    <!-- Java sources: filtered (so `package ${package};` is substituted) and packaged. -->
    <fileSet filtered="true" packaged="true" encoding="UTF-8">
      <directory>src/main/java</directory>
      <includes>
        <include>**/*.java</include>
      </includes>
    </fileSet>
    <!-- Templates / island JS / yml: NOT filtered (they contain {{ }} and $value tokens Velocity
         would mangle) and NOT packaged (fixed classpath locations). -->
    <fileSet filtered="false" packaged="false" encoding="UTF-8">
      <directory>src/main/resources</directory>
      <includes>
        <include>**/*.html</include>
        <include>**/*.js</include>
        <include>**/*.yml</include>
      </includes>
    </fileSet>
  </fileSets>
</archetype-descriptor>
```

- [ ] **Step 3: Create the prototype POM (the generated app's `pom.xml`)**

Create `tools/medley-archetype/src/main/resources/archetype-resources/pom.xml`. This file IS Velocity-filtered by the archetype plugin (it is the project root POM), so `${groupId}`/`${artifactId}`/`${version}`/`${medleyVersion}` resolve at generation time:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.4</version>
    <relativePath/>
  </parent>

  <groupId>${groupId}</groupId>
  <artifactId>${artifactId}</artifactId>
  <version>${version}</version>
  <name>${artifactId}</name>
  <description>A Medley application</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>app.besoft.medley</groupId>
      <artifactId>medley-spring-boot-starter</artifactId>
      <version>${medleyVersion}</version>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 4: Install the archetype (structure-only smoke)**

Run: `cd tools/medley-archetype && mvn -q install && cd /c/Users/maw2b/IdeaProjects/medley`
Expected: `BUILD SUCCESS`; `app.besoft.medley:medley-archetype:0.5.0` is installed to `~/.m2`.
(Full generate/run is Task 4, after the app content exists.)

- [ ] **Step 5: Commit**

```bash
git add tools/medley-archetype/pom.xml tools/medley-archetype/src/main/resources/META-INF tools/medley-archetype/src/main/resources/archetype-resources/pom.xml
git commit -m "feat(stage5.5): Medley Maven archetype scaffolding

Archetype POM (maven-archetype packaging), descriptor with
filtered=false for templates/static (Velocity gotcha), and the
prototype pom.xml (Spring Boot parent + medley starter from mavenLocal).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: Generated app content (Java + templates + island JS + yml)

**Files (all under `tools/medley-archetype/src/main/resources/archetype-resources/`):**
- Create: `src/main/java/App.java`
- Create: `src/main/java/CounterComponent.java`
- Create: `src/main/java/SignupComponent.java`
- Create: `src/main/java/ChartComponent.java`
- Create: `src/main/java/SparklineIsland.java`
- Create: `src/main/resources/application.yml`
- Create: `src/main/resources/templates/medley/counter.html`
- Create: `src/main/resources/templates/medley/signup.html`
- Create: `src/main/resources/templates/medley/chart.html`
- Create: `src/main/resources/static/sparkline-island.js`

**Interfaces:**
- Consumes: the descriptor from Task 2 (`packaged=true` java → each `.java` lands under `${package}`; resources copied verbatim to fixed classpath locations).
- Produces: a generated app with routes `/` (counter), `/signup` (form), `/chart` (island).

**Note on `package ${package};`:** every Java file below declares `package ${package};`. Because `packaged=true`, all four generated classes share one package, so `SparklineIsland` referring to `ChartComponent` needs no import (same package — mirrors the demo). Medley imports (`app.besoft.medley.*`) are absolute and unchanged.

- [ ] **Step 1: `App.java`**

```java
package ${package};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A Medley application. Run with {@code mvn spring-boot:run} and open
 * {@code http://localhost:8080/} (also {@code /signup} and {@code /chart}).
 */
@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

- [ ] **Step 2: `CounterComponent.java`** (route changed from the demo's `/counter` to `/`)

```java
package ${package};

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.Param;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * A counter mounted at {@code /}. Each page load gets a fresh instance (prototype scope, carried by
 * {@code @MedleyRoute}) with its own state. In a real component you would {@code @Autowired}
 * repositories/services and use them directly in actions — no REST layer in between.
 */
@MedleyRoute("/")
@MedleyComponent("counter")
public class CounterComponent extends Component {

    @State int count = 0;
    @Param String label = "Clicks";

    @Action void increment() { count++; }
    @Action void decrement() { if (count > 0) count--; }
    @Action void reset() { count = 0; }

    public int getCount() { return count; }
    public String getLabel() { return label; }
}
```

- [ ] **Step 3: `SignupComponent.java`** (verbatim from the demo, only the package line changed)

```java
package ${package};

import java.util.HashMap;
import java.util.Map;

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

/**
 * Form validation at {@code /signup}. Validation is plain Java in {@link #submit()}; errors live in
 * a {@code @State Map} and surface through {@code *if} + {{ }} (the evaluator resolves
 * {@code errors.name} as a map-key lookup). {@code submit()} refuses to commit invalid state. Inputs
 * stay uncontrolled, so marking a field invalid never clobbers what the user is typing.
 */
@MedleyRoute("/signup")
@MedleyComponent("signup")
public class SignupComponent extends Component {

    private static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

    @State String name = "";
    @State String email = "";
    @State int registered = 0;
    @State Map<String, String> errors = new HashMap<>();

    @Action void setName(String value) { this.name = value == null ? "" : value; }

    @Action void setEmail(String value) { this.email = value == null ? "" : value; }

    @Action void submit() {
        errors.clear();
        if (name.trim().length() < 2) {
            errors.put("name", "Enter a name (min. 2 characters).");
        }
        if (!email.matches(EMAIL)) {
            errors.put("email", "Invalid e-mail address.");
        }
        if (errors.isEmpty()) {   // valid -> commit and reset the form
            registered++;
            name = "";
            email = "";
        }
    }

    public int getRegistered() { return registered; }
}
```

- [ ] **Step 4: `ChartComponent.java`** (verbatim from the demo, only the package line changed)

```java
package ${package};

import app.besoft.medley.core.component.Annotations.Action;
import app.besoft.medley.core.component.Annotations.MedleyComponent;
import app.besoft.medley.core.component.Annotations.State;
import app.besoft.medley.core.component.Component;
import app.besoft.medley.spring.MedleyRoute;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * A component hosting a client island (see {@code chart.html} and {@code sparkline-island.js}).
 * The sparkline draws points and handles hover entirely client-side (zero round-trips); only a
 * coarse "select" commit reaches the server, which records it in {@code @State selected} and pushes
 * {@code data-selected} back down as a host-attribute patch.
 */
@MedleyRoute("/chart")
@MedleyComponent("chart")
public class ChartComponent extends Component {

    private static final int[] POINTS = {3, 7, 4, 9, 2, 6, 8};

    @State int selected = -1;

    @Action void clearSelection() { selected = -1; }

    /** Set by the sparkline island's commit (via {@link SparklineIsland}). */
    void selectPoint(int index) {
        if (index >= 0 && index < POINTS.length) {
            selected = index;
        }
    }

    public String getPointsCsv() {
        return Arrays.stream(POINTS).mapToObj(Integer::toString).collect(Collectors.joining(","));
    }

    public int getSelected() { return selected; }

    public boolean isHasSelection() { return selected >= 0; }

    public String getSelectedLabel() {
        return selected < 0 ? "(none — hover and click a point)"
                : "point #" + selected + " = " + POINTS[selected];
    }
}
```

- [ ] **Step 5: `SparklineIsland.java`** (verbatim from the demo, only the package line changed)

```java
package ${package};

import app.besoft.medley.spring.IslandAction;
import app.besoft.medley.spring.MedleyIsland;

/**
 * Server-side handler for the {@code sparkline} island. Stateless: it persists the coarse selection
 * by mutating the owning {@link ChartComponent}'s {@code @State}, whose re-render pushes the updated
 * {@code :data-selected} prop back down to the island.
 */
@MedleyIsland("sparkline")
public class SparklineIsland {

    @IslandAction
    void select(ChartComponent owner, int index) {
        owner.selectPoint(index);
    }
}
```

- [ ] **Step 6: `application.yml`** (minimal — NOT Velocity-filtered, so no `$`/`{{ }}` allowed)

Create `src/main/resources/application.yml`:

```yaml
server:
  port: 8080

# All Medley options (medley.*) are optional and documented in docs/configuration.md.
# For example, to enable the dev inspector while developing (NEVER in production — it exposes @State):
# medley:
#   devtools:
#     enabled: true
```

- [ ] **Step 7: `counter.html`** (verbatim from the demo)

Create `src/main/resources/templates/medley/counter.html`:

```html
<div class="counter">
  <button @click="decrement" *if="count > 0">−</button>
  <span>{{ label }}: {{ count }}</span>
  <button @click="increment">+</button>
  <button @click="reset" *if="count > 0">reset</button>
</div>
```

- [ ] **Step 8: `signup.html`** (verbatim from the demo — contains the `$value` tokens that require `filtered="false"`)

Create `src/main/resources/templates/medley/signup.html`:

```html
<div class="signup-demo">
  <h1>Medley — form validation</h1>
  <p><input type="text" @input="setName($value)" placeholder="name"><span *if="errors.name" class="error"> {{ errors.name }}</span></p>
  <p><input type="text" @input="setEmail($value)" placeholder="e-mail"><span *if="errors.email" class="error"> {{ errors.email }}</span></p>
  <button @click="submit">Register</button>
  <p>registered: {{ registered }}</p>
</div>
```

- [ ] **Step 9: `chart.html`** (verbatim from the demo)

Create `src/main/resources/templates/medley/chart.html`:

```html
<div class="chart">
  <medley-island name="sparkline" :data-points="pointsCsv" :data-selected="selected"></medley-island>
  <p>Selected: {{ selectedLabel }}</p>
  <button @click="clearSelection" *if="hasSelection">clear</button>
  <script src="/sparkline-island.js"></script>
</div>
```

- [ ] **Step 10: `sparkline-island.js`** (EXACT verbatim copy — no transformation)

Copy `examples/counter-demo/src/main/resources/static/sparkline-island.js` byte-for-byte to `tools/medley-archetype/src/main/resources/archetype-resources/src/main/resources/static/sparkline-island.js`. It contains no package/coordinate tokens, so it is an unmodified copy.

Run to confirm identity:
`diff examples/counter-demo/src/main/resources/static/sparkline-island.js tools/medley-archetype/src/main/resources/archetype-resources/src/main/resources/static/sparkline-island.js`
Expected: no output (files identical).

- [ ] **Step 11: Reinstall the archetype and commit**

Run: `cd tools/medley-archetype && mvn -q install && cd /c/Users/maw2b/IdeaProjects/medley`
Expected: `BUILD SUCCESS`.

```bash
git add tools/medley-archetype/src/main/resources/archetype-resources/src
git commit -m "feat(stage5.5): archetype app content — counter + form + island

Generated app: App, CounterComponent (route /), SignupComponent
(/signup), ChartComponent + SparklineIsland (/chart), templates,
sparkline-island.js, minimal application.yml. Lifted from counter-demo.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: End-to-end verification — generate and run the app

**Files:** none (verification only). Uses the scratchpad, never the repo tree.

**Interfaces:**
- Consumes: Maven-Local artifacts (Task 1) + installed archetype (Tasks 2–3).
- Produces: proof the loop works (this replaces a unit test — see the spec §7 rationale; note the absence of an automated test in the commit message).

Let `SCRATCH=C:/Users/maw2b/AppData/Local/Temp/claude/C--Users-maw2b-IdeaProjects-medley/f8bcf9be-4aad-4bc5-8c33-b190b83e852b/scratchpad`.

- [ ] **Step 1: Generate a project from the archetype (batch mode)**

```bash
cd "$SCRATCH" && rm -rf my-app 2>/dev/null; \
mvn -q -B archetype:generate \
  -DarchetypeGroupId=app.besoft.medley \
  -DarchetypeArtifactId=medley-archetype \
  -DarchetypeVersion=0.5.0 \
  -DgroupId=com.example -DartifactId=my-app -Dversion=0.1.0 -Dpackage=com.example.myapp
```

Expected: `BUILD SUCCESS`; `$SCRATCH/my-app` exists with `pom.xml`, `src/main/java/com/example/myapp/{App,CounterComponent,SignupComponent,ChartComponent,SparklineIsland}.java`, and `src/main/resources/{application.yml,templates/medley/*.html,static/sparkline-island.js}`.
(The `rm -rf my-app` here clears a previous run's scratchpad output only; it is not a repo deletion.)

- [ ] **Step 2: Prove templates were copied verbatim (the Velocity gotcha)**

```bash
diff "$SCRATCH/my-app/src/main/resources/templates/medley/signup.html" \
     tools/medley-archetype/src/main/resources/archetype-resources/src/main/resources/templates/medley/signup.html
grep -F 'setName($value)' "$SCRATCH/my-app/src/main/resources/templates/medley/signup.html"
```

Expected: `diff` prints nothing (identical — no filtering occurred); `grep` prints the `setName($value)` line (the `$value` token survived). If `grep` finds nothing, Velocity ate the token → the descriptor's `filtered="false"` is wrong; fix Task 2 Step 2 before continuing.

- [ ] **Step 3: Confirm `package` substitution in Java**

```bash
grep -F 'package com.example.myapp;' "$SCRATCH/my-app/src/main/java/com/example/myapp/CounterComponent.java"
```

Expected: prints the line (the `${package}` token was substituted because Java is `filtered="true"`).

- [ ] **Step 4: Package the generated app (resolves Medley from Maven Local)**

```bash
cd "$SCRATCH/my-app" && mvn -q -DskipTests package
```

Expected: `BUILD SUCCESS`; `target/my-app-0.1.0.jar` is produced. This proves the published starter POM resolves transitively (Task 1 §2 risk) and the components compile.

- [ ] **Step 5: Run it and hit all three routes (SSR proof)**

Start the app in the background, then curl each route:

```bash
cd "$SCRATCH/my-app" && mvn -q spring-boot:run &
# wait for startup, then:
sleep 25
curl -s http://localhost:8080/        | grep -F 'Clicks:'
curl -s http://localhost:8080/signup  | grep -F 'setName($value)'
curl -s http://localhost:8080/chart   | grep -F 'medley-island'
# stop the server:
# (find and kill the spring-boot:run process, or Ctrl-C if run in a foreground shell)
```

Expected: `/` shows `Clicks:` (counter SSR), `/signup` shows the `setName($value)` binding in the served HTML (proving the runtime uses the un-mangled template), `/chart` shows the `<medley-island>` host. If startup is slow, increase the sleep. After verifying, stop the server.

- [ ] **Step 6: No commit for this task**

Verification produces no repo changes. Record the result (routes served, template identity confirmed) in the Task 5 / final report. If any check failed, return to the relevant task before proceeding.

---

### Task 5: Documentation — getting-started + configuration + README

**Files:**
- Create: `docs/getting-started.md`
- Create: `docs/configuration.md`
- Modify: `README.md` (status-table row + a "Create a new project" pointer)

**Interfaces:**
- Consumes: the verified archetype flow from Task 4 (commands are copied from what actually ran).

- [ ] **Step 1: Create `docs/getting-started.md`**

```markdown
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

This installs `app.besoft.medley:medley-core:0.5.0` and
`app.besoft.medley:medley-spring-boot-starter:0.5.0` into `~/.m2`.

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
  -DarchetypeVersion=0.5.0 \
  -DgroupId=com.example -DartifactId=my-app -Dversion=0.1.0 -Dpackage=com.example.myapp
```

(The archetype version and the Medley starter version it wires in both track the framework
version — `0.5.0` here. Bump `-DarchetypeVersion` when the framework version bumps.)

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
```

- [ ] **Step 2: Create `docs/configuration.md`** (values verbatim from `MedleyProperties`)

```markdown
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
```

- [ ] **Step 3: Update `README.md` — status-table row**

In the "PoC implementation status" table, replace the final row:

```markdown
| Redis session backend, Maven archetype | ⏳ Stage 5 |
```

with:

```markdown
| Maven archetype — scaffold a new app (`docs/getting-started.md`) (**Stage 5**) | ✅ done |
| Redis session backend | ❌ rejected — sticky sessions are the model (MEDLEY_DESIGN §10.12) |
```

- [ ] **Step 4: Update `README.md` — add a "Create a new project" pointer**

Immediately after the `## Getting started` section's closing content (before `## Running the demo`), add:

```markdown
### Create a new project

To scaffold your own Medley app (not just run the demo), publish the framework to Maven Local and use
the Maven archetype — see **[docs/getting-started.md](docs/getting-started.md)**.
```

- [ ] **Step 5: Commit**

```bash
git add docs/getting-started.md docs/configuration.md README.md
git commit -m "docs(stage5.5): getting-started + configuration reference

Archetype quickstart, full medley.* property reference, README status
row (archetype done, Redis rejected) + create-a-project pointer.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: Status bookkeeping — CLAUDE.md, MEDLEY_DESIGN.md, MEMORY.md

**Files:**
- Modify: `CLAUDE.md` (Stage 5 section)
- Modify: `MEDLEY_DESIGN.md` (§11 stage plan)
- Modify: `C:\Users\maw2b\.claude\projects\C--Users-maw2b-IdeaProjects-medley\memory\MEMORY.md` and its `stage4-4b-progress.md`

**Interfaces:** none. Keeps the docs truthful per the working agreement.

- [ ] **Step 1: Add the 5.5 bullet to `CLAUDE.md`**

Under the Stage 5 list (after the `5.4` bullet), add a `5.5` bullet summarizing: Maven archetype under `tools/medley-archetype/` (counter+form+island), Medley published to Maven Local via Gradle `maven-publish`, `docs/getting-started.md` + `docs/configuration.md`; no core/API/wire change; `filtered="false"` Velocity gotcha; verified by real generate-and-run (no automated test — documented decision). Then mark **all Stage 5 increments (5.1–5.5) DONE / Stage 5 complete**.

- [ ] **Step 2: Add the 5.5 bullet to `MEDLEY_DESIGN.md` §11**

After the `5.4` bullet in the "Stage 5 — Performance and production" list, add a `5.5` bullet with the same substance, and flip the stage header from `🚧 in progress` to `✅ complete`.

- [ ] **Step 3: Update the memory index**

In `MEMORY.md`, update the Progress line: Stage 5 **complete** (5.1–5.5), archetype + docs shipped, Redis rejected. Update `stage4-4b-progress.md` body accordingly (convert any relative dates to absolute).

- [ ] **Step 4: Commit**

```bash
git add CLAUDE.md MEDLEY_DESIGN.md
git commit -m "docs(stage5.5): mark Stage 5 complete (5.1–5.5)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

(The memory files live outside the repo tree and are not part of this commit — update them with the Write tool.)

---

## Final: review + merge (per the workflow)

- Run `/code-review` (or `@reviewer`) on the branch; fix any Critical findings.
- Re-run `./gradlew build` (green) — the verification of record for the framework side.
- Merge `--no-ff` into `dev`, delete the feature branch.
- **Separate step (not this plan):** promote `dev`→`master` for the Stage 5 milestone and tag it.

## Self-review notes (author)

- **Spec coverage:** §1 scope → Tasks 1–6; §2 publishing → Task 1; §3 archetype module → Task 2; §4 app content → Task 3; §5 Velocity gotcha → Task 2 Step 2 + Task 4 Step 2; §6 docs → Task 5; §7 verification → Task 4; §8 bookkeeping → Task 6; §9 workflow → Final. All covered.
- **No placeholders:** every code/step shows full content; the one "copy verbatim" (island JS, Task 3 Step 10) points to an exact in-repo path + a `diff` check, so the content is fully determined.
- **Type/name consistency:** archetype coordinates `app.besoft.medley:medley-archetype:0.5.0`, required property `medleyVersion` (default `0.5.0`), and generated routes `/`, `/signup`, `/chart` are used identically across Tasks 2–5.
```
