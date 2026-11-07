plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smarted.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smarted.tv"
        minSdk = 23
        targetSdk = 35
        // O CI sobrescreve com -PversionCode=/-PversionName=; padrão para build local.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

        // Optional TMDB credentials (see docs/METADADOS.md). Ship your own key.
        buildConfigField("String", "TMDB_API_KEY", "\"${project.findProperty("TMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "TMDB_LANGUAGE", "\"${project.findProperty("TMDB_LANGUAGE") ?: "pt-BR"}\"")
    }

    signingConfigs {
        create("release") {
            // Set these in ~/.gradle/gradle.properties or via -P flags; never commit them.
            // Aceita -P (local) ou variáveis de ambiente (CI). Nunca commite segredos.
            fun secret(prop: String, env: String): String? =
                (project.findProperty(prop) as String?)?.takeIf { it.isNotBlank() }
                    ?: System.getenv(env)?.takeIf { it.isNotBlank() }

            val store = secret("RELEASE_STORE_FILE", "RELEASE_STORE_FILE")
            val storePasswordValue = secret("RELEASE_STORE_PASSWORD", "RELEASE_STORE_PASSWORD")
            val keyAliasValue = secret("RELEASE_KEY_ALIAS", "RELEASE_KEY_ALIAS")
            val keyPasswordValue = secret("RELEASE_KEY_PASSWORD", "RELEASE_KEY_PASSWORD")
            if (store != null && storePasswordValue != null && keyAliasValue != null && keyPasswordValue != null) {
                storeFile = file(store)
                storePassword = storePasswordValue
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            // `-Pminify=false` gera um release sem R8 (diagnóstico no CI).
            val minify = (project.findProperty("minify") as String?)?.toBoolean() ?: true
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf("-opt-in=kotlin.RequiresOptIn")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/LICENSE*",
                "/META-INF/NOTICE*",
                "META-INF/*.kotlin_module",
            )
        }
    }

    sourceSets["main"].assets.srcDirs("src/main/assets")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.datasource.okhttp)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.io.markwon.core)
    implementation(libs.io.markwon.html)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
}
