// SPDX-License-Identifier: GPL-3.0
// Copyright (C) 2026 IntentX contributors

import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import java.util.Properties

plugins {
    alias(libs.plugins.intentx.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutLibraries)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val signingFile = keystoreProperties.getProperty("storeFile")?.takeIf(String::isNotBlank)?.let(rootProject::file)
val signingPassword = keystoreProperties.getProperty("storePassword")?.takeIf(String::isNotBlank)
val signingAlias = keystoreProperties.getProperty("keyAlias")?.takeIf(String::isNotBlank)
val signingKeyPassword = keystoreProperties.getProperty("keyPassword")?.takeIf(String::isNotBlank)
val hasLocalSigning = signingFile?.isFile == true && signingPassword != null && signingAlias != null && signingKeyPassword != null

android {
    namespace = "io.github.wxxsfxyzm.intentx"

    defaultConfig {
        applicationId = "io.github.wxxsfxyzm.intentx"
        versionCode = project.getGitCommitCount()
        versionName = "${project.getBaseVersionName()}.${project.getGitHash()}"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = false
    }

    packaging {
        jniLibs {
            excludes += setOf(
                "lib/*/libandroidx.graphics.path.so",
                "lib/*/libdatastore_shared_counter.so",
            )
        }
    }

    androidResources {
        generateLocaleConfig = true
    }

    signingConfigs {
        create("local") {
            if (hasLocalSigning) {
                storeFile = signingFile
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("local")
            optimization.enable = false
        }
        getByName("release") {
            if (hasLocalSigning) signingConfig = signingConfigs.getByName("local")
            vcsInfo.include = false
            optimization.enable = true
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

aboutLibraries {
    offlineMode = gradle.startParameter.isOffline
    library {
        duplicationMode = DuplicateMode.MERGE
        duplicationRule = DuplicateRule.SIMPLE
    }
}

dependencies {
    implementation(libs.aboutlibraries.core)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.materialKolor)
    implementation(libs.miuix.navigation)
    implementation(libs.miuix.core)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.shader)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(project(":intent-executor"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle)
    implementation(libs.androidx.splashscreen)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.materialIcons)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.ktx.serializationJson)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.rikka.shizuku.api)
    implementation(libs.timber)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
