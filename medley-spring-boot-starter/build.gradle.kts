plugins {
    `java-library`
    `maven-publish`
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
    }
}

dependencies {
    api(project(":medley-core"))

    // Pinned to the Spring Boot version explicitly (not the libs.* catalog alias) on purpose:
    // io.spring.dependency-management customizes only the generated POM's <dependencyManagement>,
    // NOT the Gradle Module Metadata (.module) that maven-publish also emits and Gradle consumers
    // prefer — an unpinned alias lands there with no version, so a Gradle consumer can't resolve it.
    // Do not simplify back to libs.spring.boot.starter.web etc. without restoring versions in .module.
    api("org.springframework.boot:spring-boot-starter-web:${libs.versions.spring.boot.get()}")
    api("org.springframework.boot:spring-boot-starter-websocket:${libs.versions.spring.boot.get()}")
    implementation("org.springframework.boot:spring-boot-autoconfigure:${libs.versions.spring.boot.get()}")
    annotationProcessor(libs.spring.boot.configuration.processor)

    // Optional observability: metrics activate only when the app provides Micrometer + a MeterRegistry
    // (e.g. by adding spring-boot-starter-actuator). Not forced on consumers.
    compileOnly(libs.micrometer.core)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.micrometer.core)
    // Actuator on the test classpath so a test can prove the metrics auto-config orders correctly
    // AFTER Actuator creates the MeterRegistry (the afterName ordering).
    testImplementation(libs.spring.boot.starter.actuator)
    // Spring Session on the test classpath ONLY, so a test can prove the interop guard fires when it
    // is present. Medley never depends on Spring Session — the guard warns about it, it does not use it.
    // Safe to have here: spring-session-core alone is inert. It self-registers nothing (no
    // AutoConfiguration.imports), and while Boot's SessionAutoConfiguration does match, it wires no
    // SessionRepository without a store module (redis/jdbc/hazelcast/mongo), hence no
    // SessionRepositoryFilter — so the container's own HttpSession, and with it MedleySession storage and
    // the 6a valueUnbound eviction path, stay untouched. Adding a store module here would change that.
    testImplementation(libs.spring.session.core)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
