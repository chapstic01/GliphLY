plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.gliphly"
    compileSdk = 35
    defaultConfig { applicationId = "dev.gliphly"; minSdk = 34; targetSdk = 35; versionCode = 1; versionName = "0.1" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(fileTree("libs") { include("*.aar") })   // Nothing Glyph SDK AAR — auto-fetched below
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
}

// Auto-fetch the Glyph SDK AAR straight from Nothing's public repo so nobody
// has to find and drop it in manually. Runs once per Gradle sync/build;
// skips the download if the file is already there. Delete app/libs/*.aar
// (or run `./gradlew fetchGlyphSdk --rerun`) to re-pull the latest copy.
val fetchGlyphSdk by tasks.registering {
    val outFile = file("libs/glyph-matrix-sdk-2.0.aar")
    val url = "https://raw.githubusercontent.com/Nothing-Developer-Programme/Glyph-Developer-Kit/main/sdk/glyph-matrix-sdk-2.0.aar"
    outputs.file(outFile)
    doLast {
        if (!outFile.exists()) {
            outFile.parentFile.mkdirs()
            println("Fetching Glyph SDK AAR from Nothing's GitHub repo...")
            uri(url).toURL().openStream().use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
            println("Saved to ${outFile.path} (${outFile.length()} bytes)")
        }
    }
}
tasks.named("preBuild") { dependsOn(fetchGlyphSdk) }
