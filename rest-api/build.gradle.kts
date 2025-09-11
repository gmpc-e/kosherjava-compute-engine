import org.gradle.api.publish.maven.MavenPublication

plugins {
    kotlin("jvm")
    application            // ✅ apply the Application plugin
    `maven-publish`
}

kotlin { jvmToolchain(17) }

// Gradle 8+ style for mainClass
application {
    mainClass.set("com.elad.halacha.rest.MainKt")
}

val ktorVersion = "2.3.12"
val logbackVersion = "1.5.8"

dependencies {
    implementation(project(":core-engine"))
    implementation(project(":profiles"))

    // Ktor server (Netty)
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("org.slf4j:slf4j-api:2.0.16")

    // Time
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // kotlin.test on JUnit 5
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}