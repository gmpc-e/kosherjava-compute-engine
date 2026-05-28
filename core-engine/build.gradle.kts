import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

kotlin { jvmToolchain(17) }

// ✅ Put the coordinates on the project itself (clear + cache-friendly)
group = "com.elad.halacha"
version = "0.1.2-SNAPSHOT"

dependencies {
    implementation(project(":profiles"))
    implementation("com.kosherjava:zmanim:2.5.0")
    // junit should be test-only (you had it as implementation)
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test"))
    implementation("org.slf4j:slf4j-api:2.0.13")
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        // Keep the default name; only set artifactId.
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "core-engine"
            // ❌ DO NOT override groupId/version here (inherit from project)
        }
    }
    repositories {
        // Not strictly required for mavenLocal, but harmless:
        mavenLocal()
    }
}

// If you’re using JUnit 4, remove this or add vintage engine.
// tasks.test { useJUnitPlatform() }