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
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    implementation(kotlin("stdlib"))
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