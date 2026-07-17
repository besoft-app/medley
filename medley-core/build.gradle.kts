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
