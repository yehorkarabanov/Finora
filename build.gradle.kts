// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
        // AGP 9 has built-in Kotlin. Declaring KGP here overrides the bundled version.
        classpath(libs.kotlin.gradle.plugin)
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.spotless)
}
// Compose/Android conventions that ktlint's naming rules reject: PascalCase `val`
// tokens (Typography, FabSize), `_state` backing fields, Room migration objects,
// multi-declaration files.
val ktlintOverrides = mapOf(
    "ktlint_code_style" to "android_studio",
    "max_line_length" to "120",
    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
    "ktlint_standard_property-naming" to "disabled",
    "ktlint_standard_backing-property-naming" to "disabled",
    "ktlint_standard_class-naming" to "disabled",
    "ktlint_standard_filename" to "disabled",
    // The formatter still wraps to 120; what remains long is SQL/string literals.
    "ktlint_standard_max-line-length" to "disabled"
)

spotless {
    kotlin {
        target("app/src/**/*.kt")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintOverrides)
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts")
        ktlint(libs.versions.ktlint.get()).editorConfigOverride(ktlintOverrides)
    }
}
