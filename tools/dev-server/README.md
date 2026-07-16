# Dev-server harness (verification only)

A dependency-free, JDK-only harness that runs the full Medley Stage-1 loop
**without Spring** — used to verify the engine + medley.js + WebSocket protocol
end to end in environments where Spring/Gradle dependencies aren't available.

It is NOT part of the framework. The real runtime is the Spring Boot starter.

## Run

Needs **Java 21** — the engine uses pattern matching in `switch`, which an older `javac`
rejects as a preview feature. If `javac -version` reports anything below 21, invoke the
JDK explicitly (e.g. `"$JAVA_HOME/bin/javac"`).

Compile the core engine first, then the harness:

```bash
# 1. core engine -> medley-core/build/devclasses
cd ../../medley-core
javac -parameters -d build/devclasses $(find src/main/java -name '*.java')

# 2. harness -> tools/dev-server/build
cd ../tools/dev-server
javac -parameters -cp "../../medley-core/build/devclasses" -d build *.java
```

PowerShell equivalents (note `;` — the classpath separator is OS-specific):

```powershell
cd ..\..\medley-core
javac -parameters -d build\devclasses (Get-ChildItem -Recurse src\main\java -Filter *.java).FullName

cd ..\tools\dev-server
javac -parameters -cp "..\..\medley-core\build\devclasses" -d build *.java
```

Then start the server and drive it:

```bash
# start server (serves SSR + medley.js, handles WS); use ';' instead of ':' on Windows
java -cp "build:../../medley-core/build/devclasses" DevServer &
# open http://localhost:8081/counter in a browser, OR run the scripted client:
java -cp "build:../../medley-core/build/devclasses" WsClientTest
```

`DevServer` serves the counter at http://localhost:8081/counter and a hand-rolled
RFC6455 WebSocket server on :8082. `WsClientTest` drives increment/decrement/reset
and prints the patch batches returned for each event.
