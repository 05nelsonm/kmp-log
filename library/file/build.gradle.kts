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
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    id("configuration")
}

kmpConfiguration {
    // TODO:
    //  - Set publish = true
    //  - Update module README.md
    //  - Update project README.
    //  - Uncomment :tools:check-publication build.gradle.kts
    configureShared(
        java9ModuleName = "io.matthewnelson.kmp.log.file",
        enableJs = false, // Node.js does not support file locks
        publish = false,
    ) {
        common {
            sourceSetMain {
                dependencies {
                    api(project(":library:log"))
                    implementation(libs.encoding.base16)
                    implementation(libs.encoding.utf8)
                    implementation(libs.immutable.collections)
                    implementation(libs.kmp.file)
                    implementation(libs.kotlinx.coroutines.core)
                    implementation(kotlincrypto.hash.blake2)
                }
            }
            sourceSetTest {
                dependencies {
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            val cinteropDir = project.projectDir
                .resolve("src")
                .resolve("nativeInterop")
                .resolve("cinterop")

            targets.filterIsInstance<KotlinNativeTarget>().forEach { target ->
                target.compilations["main"].cinterops.create("lock_file") {
                    definitionFile.set(cinteropDir.resolve("$name.def"))
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "linux",
                    "macos",
                ).mapNotNull { name -> findByName(name + "Test") }
                if (sets.isEmpty()) return@kotlin
                maybeCreate("unixLockFileTest").apply {
                    dependsOn(getByName("unixTest"))
                    sets.forEach { t -> t.dependsOn(this) }
                }
            }
        }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "jvm" to listOf("jvm"),
                    "unixLockFile" to listOf(
                        "linuxArm64",
                        "linuxX64",
                        "macosArm64",
                        "macosX64",
                    ),
                ).mapNotNull { (name, dependencies) ->
                    val test = findByName(name + "Test") ?: return@mapNotNull null
                    check(dependencies.isNotEmpty()) { "dependencies cannot be empty" }
                    test to dependencies.map { it + "Test" }
                }
                if (sets.isEmpty()) return@kotlin

                try {
                    evaluationDependsOn(":test-file-lock")
                } catch (_: Throwable) {}

                val kotlinSrcDir = layout
                    .buildDirectory
                    .get()
                    .asFile
                    .resolve("generated")
                    .resolve("sources")
                    .resolve("lockFileTest")
                    .resolve("kotlin")

                val packageName = "io.matthewnelson.kmp.log.file.internal"

                maybeCreate("lockFileTest").apply {
                    dependsOn(getByName("commonTest"))
                    sets.forEach { (t, _) -> t.dependsOn(this) }
                    dependencies {
                        implementation(libs.kmp.process)
                    }
                    kotlin.srcDir(kotlinSrcDir)
                }

                val configDir = kotlinSrcDir
                    .resolve(packageName.replace('.', File.separatorChar))
                configDir.mkdirs()

                project.afterEvaluate {
                    val jarTask = project(":test-file-lock")
                        .tasks
                        .findByName("assembleFatJar")

                    val jarPath = if (jarTask != null) {
                        sets.forEach { (_, testTasks) ->
                            testTasks.forEach { testTask ->
                                tasks
                                    .findByName(testTask)
                                    ?.dependsOn(jarTask)
                            }
                        }

                        jarTask.outputs.files.first().path.replace("\\", "\\\\")
                    } else {
                        ""
                    }

                    configDir.resolve("-LockFileTestConfig.kt").writeText("""
                        package $packageName

                        internal const val TEST_FILE_LOCK_JAR: String = "$jarPath"

                    """.trimIndent())
                }
            }
        }

        configureKotlinVersion()
    }
}
