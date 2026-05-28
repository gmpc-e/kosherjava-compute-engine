import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

group = "com.elad.halacha"
version = "0.1.2-SNAPSHOT"

kotlin { jvmToolchain(17) }

java {
    withSourcesJar()
    withJavadocJar()
}

// IMPORTANT: use the default resources root so files are packaged under "profiles/..."
// Place your JSON under: src/main/resources/profiles/*.json
// (Delete your previous sourceSets override)
dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")

    testImplementation(kotlin("test"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "profiles" // will publish as com.elad.halacha:profiles:0.1.2-SNAPSHOT
            // groupId & version inherited from project group/version above
        }
    }
}

tasks.test { useJUnitPlatform() }