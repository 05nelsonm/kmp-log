/*
 * Copyright (c) 2025 Matthew Nelson
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 **/
import com.android.build.gradle.tasks.MergeSourceSetFolders
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("configuration")
}

repositories { google() }

kmpConfiguration {

    val jniLibsDir = project.projectDir
        .resolve("src")
        .resolve("androidInstrumentedTest")
        .resolve("jniLibs")

    configureShared(java9ModuleName = "io.matthewnelson.kmp.log.sys", publish = true) {
        androidLibrary(namespace = "io.matthewnelson.kmp.log.sys") {
            android {
                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                sourceSets["androidTest"].apply {
                    jniLibs.srcDir(jniLibsDir)
                    manifest.srcFile(
                        project.projectDir
                            .resolve("src")
                            .resolve("androidInstrumentedTest")
                            .resolve("AndroidManifest.xml")
                    )
                }
            }

            sourceSetTestInstrumented {
                dependencies {
                    implementation(libs.androidx.test.core)
                    implementation(libs.androidx.test.runner)
                    implementation(libs.kmp.process)
                }
            }
        }

        @OptIn(ExperimentalWasmDsl::class)
        wasmWasi {
            target {
                nodejs()
            }
        }

        common {
            sourceSetMain {
                dependencies {
                    api(project(":library:log"))
                }
            }
        }

        kotlin {
            project.tasks.all {
                if (name != "clean") return@all
                doLast { jniLibsDir.deleteRecursively() }
            }
        }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "android",
                    "androidNative",
                ).mapNotNull { name -> findByName(name + "Main") }
                if (sets.isEmpty()) return@kotlin
                val main = maybeCreate("androidRuntimeMain").apply { dependsOn(getByName("commonMain")) }
                sets.forEach { m -> m.dependsOn(main) }
            }
        }

        kotlin {
            if (!project.plugins.hasPlugin("com.android.base")) return@kotlin

            project.afterEvaluate {
                val nativeTestBinaryTasks = arrayOf(
                    project to "libTestSys.so"
                ).flatMap { (project, libName) ->
                    val buildDir = project
                        .layout
                        .buildDirectory
                        .asFile.get()

                    arrayOf(
                        "Arm32" to "armeabi-v7a",
                        "Arm64" to "arm64-v8a",
                        "X64" to "x86_64",
                        "X86" to "x86",
                    ).mapNotNull { (arch, abi) ->
                        val nativeTestBinariesTask = project
                            .tasks
                            .findByName("androidNative${arch}TestBinaries")
                            ?: return@mapNotNull null

                        val abiDir = jniLibsDir.resolve(abi)
                        if (!abiDir.exists() && !abiDir.mkdirs()) throw RuntimeException("mkdirs[$abiDir]")

                        val testExecutable = buildDir
                            .resolve("bin")
                            .resolve("androidNative$arch")
                            .resolve("debugTest")
                            .resolve("test.kexe")

                        nativeTestBinariesTask.doLast {
                            testExecutable.copyTo(abiDir.resolve(libName), overwrite = true)
                        }

                        nativeTestBinariesTask
                    }
                }

                project.tasks.withType(MergeSourceSetFolders::class.java).all {
                    if (name != "mergeDebugAndroidTestJniLibFolders") return@all
                    nativeTestBinaryTasks.forEach { task -> dependsOn(task) }
                }
            }
        }

        configureKotlinVersion()
    }
}
