plugins {
    java
}

allprojects {
    group = "app.besoft.medley"
    version = "0.4.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters") // keep @Param/@Action parameter names at runtime
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
