@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.maven.publish)
}

val publicApiCompilerOptions: KotlinCommonCompilerOptions.() -> Unit = {
    languageVersion = KotlinVersion.KOTLIN_2_2
    apiVersion = KotlinVersion.KOTLIN_2_2
    if (this is KotlinJvmCompilerOptions) {
        jvmTarget = JvmTarget.JVM_11
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions(publicApiCompilerOptions)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()
    compilerOptions(publicApiCompilerOptions)

    androidNativeArm64()
    androidNativeX64()
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    js().nodejs()
    jvm {
        compilerOptions(publicApiCompilerOptions)
    }
    linuxArm64()
    linuxX64()
    macosArm64()
    mingwX64()
    tvosArm64()
    tvosSimulatorArm64()
    wasmJs().nodejs()
    wasmWasi().nodejs()
    watchosArm64()
    watchosDeviceArm64()
    watchosSimulatorArm64()
}
