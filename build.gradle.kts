import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.buildconfig) apply false
    alias(libs.plugins.maven.publish) apply false
}

allprojects {
    group = "li.songe"
    version = "0.2.0"
}

subprojects {
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
            compilerOptions {
                jvmTarget.set(JvmTarget.JVM_21)
                languageVersion.set(KotlinVersion.KOTLIN_2_2)
                apiVersion.set(KotlinVersion.KOTLIN_2_2)
            }
        }
    }

    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
            coordinates(project.group.toString(), project.name, project.version.toString())
            if (System.getenv("ORG_GRADLE_PROJECT_signing.keyId") != null) {
                publishToMavenCentral()
                signAllPublications()
            }
            val repositoryUrl = "https://github.com/lisonge/kotlin-codeorigin"
            pom {
                name.set("CodeOrigin for Kotlin")
                description.set("Compile-time source introspection for Kotlin")
                url.set(repositoryUrl)
                licenses {
                    license {
                        name.set("The Apache Software License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("lisonge")
                        email.set("i@songe.li")
                        url.set("https://github.com/lisonge")
                    }
                }
                scm {
                    url.set(repositoryUrl)
                }
            }
        }
    }

    pluginManager.withPlugin("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "functionalTest"
                    url = rootProject.layout.buildDirectory.dir("functional-test-repository").get().asFile.toURI()
                }
            }
        }
    }
}
