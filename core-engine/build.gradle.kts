import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

kotlin {
    jvmToolchain(17)
}

// IMPORTANT: no repositories{} here – use settings.gradle.kts

dependencies {
    implementation("com.kosherjava:zmanim:2.5.0")
    testImplementation(kotlin("test"))
    // ✅ add SLF4J API so LoggerFactory compiles
    implementation("org.slf4j:slf4j-api:2.0.13")
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
            artifactId = "core-engine"
            version = (findProperty("VERSION_NAME") as String?) ?: "0.1.0"
        }
    }
}

tasks.test {
    useJUnitPlatform()
}