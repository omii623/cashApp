plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.example.cashapp"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.cashapp"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"
    }

    androidResources {
        // A rendszer „Alkalmazás nyelve” beállításához (Android 13+) – a locales_config.xml-ből.
        generateLocaleConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        compose = true
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
}
