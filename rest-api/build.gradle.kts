plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }

application { mainClass = "com.elad.halacha.rest.MainKt" }

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

    // Tests
    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")

    // Time
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    // Make kotlin.test run on JUnit 5
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")

    // JUnit 5 engine (needed when useJUnitPlatform() is enabled)
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // If not present already, Ktor test host (usually already there)
    // testImplementation("io.ktor:ktor-server-tests-jvm:<your-ktor-version>")

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
