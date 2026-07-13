plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":medley-spring-boot-starter"))
    // Actuator brings Micrometer + a MeterRegistry, which activates Medley's optional metrics
    // (medley.messages / medley.message.duration / medley.patches) at /actuator/metrics.
    implementation(libs.spring.boot.starter.actuator)
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("counter-demo.jar")
}
