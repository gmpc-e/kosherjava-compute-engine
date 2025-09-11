import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

kotlin { jvmToolchain(17) }

// No repositories{} here

dependencies {
    testImplementation(kotlin("test"))
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = (findProperty("GROUP") as String?) ?: "com.elad.halacha"
            artifactId = "profiles"                    // <-- ensure this says "profiles"
            version = (findProperty("VERSION_NAME") as String?) ?: "0.1.0"
        }
    }
}

tasks.test { useJUnitPlatform() }