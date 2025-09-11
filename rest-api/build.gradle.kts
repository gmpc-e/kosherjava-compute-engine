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
}
