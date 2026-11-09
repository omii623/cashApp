plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

/** Az aktuális git commit rövid hash-e (ha nincs git, „dev”) – a verziószám mellé kerül. */
val gitHash: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim() }.getOrElse("").ifBlank { "dev" }

android {
    namespace = "com.example.cashapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cashapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0+$gitHash"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    androidResources {
        // A rendszer „Alkalmazás nyelve” beállításához (Android 13+) – a locales_config.xml-ből.
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            // R8: kódzsugorítás, obfuszkálás és erőforrás-zsugorítás (keepRules/rules.keep).
            optimization {
                enable = true
            }
            isShrinkResources = true
        }
    }
    lint {
        // Már meglévő lint hibák rögzítve; új hibák továbbra is elbuktatják a CI-t.
        baseline = file("lint-baseline.xml")
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // A migrációs tesztek (MigrationTestHelper) az exportált séma JSON-okból dolgoznak.
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    // A séma JSON-ok verziókövetve: ezekből készülnek majd a migrációs tesztek.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    // Csak az alkalmazásonkénti nyelvváltáshoz (AppCompatDelegate.setApplicationLocales).
    implementation(libs.androidx.appcompat)
    // Napi háttérfeladat: esedékes ismétlődő tételek + értesítés.
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    // valódi org.json a unit tesztekhez (az android.jar-ban csak üres csonk van)
    testImplementation("org.json:json:20240303")

    // Műszeres tesztek (migráció, DAO, mentés)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    constraints {
        // A room-testing (séma JSON beolvasás) legalább 1.8.1-et igényel; az app futási classpath-ja
        // 1.7.3-at hozna, és a teszt APK-nak ahhoz kell igazodnia → AbstractMethodError.
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    }
}
