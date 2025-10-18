/*
 * Copyright (c) 2025 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
import io.matthewnelson.kmp.configuration.extension.KmpConfigurationExtension
import io.matthewnelson.kmp.configuration.extension.container.target.KmpConfigurationContainerDsl
import io.matthewnelson.kmp.configuration.extension.container.target.TargetAndroidContainer
import org.gradle.api.Action
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.konan.target.HostManager

fun KmpConfigurationExtension.configureShared(
    java9ModuleName: String? = null,
    publish: Boolean = false,
    jvmOnly: Boolean = false,
    action: Action<KmpConfigurationContainerDsl>,
) {
    if (publish) {
        require(!java9ModuleName.isNullOrBlank()) { "publications must specify a module-info name" }
    }

    configure {
        options {
            useUniqueModuleNames = true
        }

        jvm {
            // CI complains on Windows b/c Java 11 is not found.
            // Publications never happen on Windows, so just disable it.
            if (!HostManager.hostIsMingw) {
                java9ModuleInfoName = java9ModuleName
            }
        }

        if (!jvmOnly) {
            js {
                target {
                    browser()
                    nodejs {
                        testTask {
                            useMocha { timeout = "30s" }
                        }
                    }
                }
            }
            @OptIn(ExperimentalWasmDsl::class)
            wasmJs {
                target {
                    browser()
                    nodejs()
                }
            }

            androidNativeAll()

            iosAll()
            macosAll()
            tvosAll()
            watchosAll()

            linuxAll()
            mingwAll()
        }

        common {
            if (publish) pluginIds("publication", "dokka")

            sourceSetTest {
                dependencies {
                    implementation(kotlin("test"))
                }
            }
        }

        kotlin { explicitApi() }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "js",
                    "wasmJs",
                ).mapNotNull { name ->
                    val main = findByName(name + "Main") ?: return@mapNotNull null
                    main to getByName(name + "Test")
                }
                if (sets.isEmpty()) return@kotlin
                val main = maybeCreate("jsWasmJsMain").apply { dependsOn(getByName("nonJvmMain")) }
                val test = maybeCreate("jsWasmJsTest").apply { dependsOn(getByName("nonJvmTest")) }
                sets.forEach { (m, t) -> m.dependsOn(main); t.dependsOn(test) }
            }
        }

        action.execute(this)
    }
}

fun KmpConfigurationContainerDsl.androidLibrary(
    namespace: String,
    buildTools: String? = "36.0.0",
    compileSdk: Int = 36,
    minSdk: Int = 15,
    action: (Action<TargetAndroidContainer.Library>)? = null,
) {
    androidLibrary {
        target {
            if (project.plugins.hasPlugin("publication")) {
                publishLibraryVariants("release")
            }
        }

        android {
            buildTools?.let { buildToolsVersion = it }
            this.compileSdk = compileSdk
            this.namespace = namespace

            defaultConfig {
                this.minSdk = minSdk

                testInstrumentationRunnerArguments["disableAnalytics"] = true.toString()
            }
        }

        action?.execute(this)
    }
}

@Suppress("DEPRECATION")
fun KmpConfigurationContainerDsl.configureKotlinVersion(
    common: KotlinVersion = KotlinVersion.KOTLIN_1_9,
    jsWasmJs: KotlinVersion? = null,
    wasi: KotlinVersion? = null,
) {
    kotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xsuppress-version-warnings")
            apiVersion.set(common)
            languageVersion.set(common)
        }
    }

    kotlin {
        if (jsWasmJs == null) return@kotlin
        sourceSets.configureEach {
            if (name.startsWith("js") || name.startsWith("wasmJs")) {
                languageSettings {
                    apiVersion = jsWasmJs.version
                    languageVersion = jsWasmJs.version
                }
            }
        }
    }

    kotlin {
        if (wasi == null) return@kotlin
        sourceSets.configureEach {
            if (name.startsWith("wasmWasi")) {
                languageSettings {
                    apiVersion = wasi.version
                    languageVersion = wasi.version
                }
            }
        }
    }
}
