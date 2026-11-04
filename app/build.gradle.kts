plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.yehor.finora"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yehor.finora"
        minSdk = 33
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Every normal build has allowBackup=false. Only a build started with
        // -PbackupVerification=true allows backup, for scripts/verify-backup-exclusion.ps1.
        manifestPlaceholders["allowBackup"] =
            if (project.hasProperty("backupVerification")) "true" else "false"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        // Production schema JSON files, needed by MigrationTestHelper. The scratch migration
        // test uses its own folder, schemas-test (design section 7).
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
    // androidTest variants write ScratchDatabase's schema to its own folder (design section 7).
    schemaDirectory("debugAndroidTest", "$projectDir/schemas-test")
}

// Room looks up schema JSON in a folder named after the database class. Copy the production
// v1 schema into the ScratchDatabase folder so the scratch migration starts from the real v1.
val copyScratchSchema by tasks.registering(Copy::class) {
    from("$projectDir/schemas/com.yehor.finora.data.db.FinoraDatabase/1.json")
    into("$projectDir/schemas-test/com.yehor.finora.scratch.ScratchDatabase")
}
tasks.matching { it.name.startsWith("copyRoomSchemasToAndroidTestAssets") || it.name.startsWith("kspDebugAndroidTest") }.configureEach {
    dependsOn(copyScratchSchema)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    // Pinned so the app APK and the androidTest APK resolve the same runtime. Room's schema bundle
    // (MigrationTestHelper) needs a recent kotlinx.serialization.
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.paging.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    kspAndroidTest(libs.androidx.room.compiler)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.turbine)
}
