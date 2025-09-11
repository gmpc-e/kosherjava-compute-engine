plugins { kotlin("jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    implementation("com.kosherjava:zmanim:2.5.0")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }