// root build.gradle.kts

plugins {
    // keep empty unless you really need root plugins
}

// Read properties safely with fallbacks
val groupProp = (findProperty("GROUP") as String?) ?: "com.elad.halacha"
val versionProp = (findProperty("VERSION_NAME") as String?) ?: "0.1.1-SNAPSHOT"


allprojects {
    group = (findProperty("group") ?: "com.elad.halacha").toString()
    version = (findProperty("version") ?: "0.1.1-SNAPSHOT").toString()
}
// No repositories{} block here; repos live only in settings.gradle.kts