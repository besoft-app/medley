# Dev-server harness (verification only)

A dependency-free, JDK-only harness that runs the full Medley Stage-1 loop
**without Spring** — used to verify the engine + medley.js + WebSocket protocol
end to end in environments where Spring/Gradle dependencies aren't available.

It is NOT part of the framework. The real runtime is the Spring Boot starter.

## Run

```bash
# 1. compile the core engine first
cd ../../medley-core
javac -parameters -d build/classes $(find src/main/java -name '*.java')

# 2. compile + run the harness
cd ../tools/dev-server
CP="build:../../medley-core/build/classes"
javac -parameters -cp "../../medley-core/build/classes" -d build *.java

# start server (serves SSR + medley.js, handles WS)
java -cp "build:../../medley-core/build/classes" DevServer &
# open http://localhost:8081/counter in a browser, OR run the scripted client:
java -cp "build:../../medley-core/build/classes" WsClientTest
```

`DevServer` serves the counter at http://localhost:8081/counter and a hand-rolled
RFC6455 WebSocket server on :8082. `WsClientTest` drives increment/decrement/reset
and prints the patch batches returned for each event.
