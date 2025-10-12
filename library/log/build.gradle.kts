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
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("configuration")
}

kmpConfiguration {
    configureShared(java9ModuleName = "io.matthewnelson.kmp.log", publish = true) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmWasi {
            target {
                nodejs()
            }
        }

        common {
            sourceSetTest {
                dependencies {
                    implementation(libs.kotlinx.coroutines.test)
                }
            }
        }

        kotlin {
            compilerOptions {
                optIn.add("kotlin.contracts.ExperimentalContracts")
            }
        }

        kotlin {
            with(sourceSets) {
                val sets = arrayOf(
                    "jvm",
                    "native",
                ).mapNotNull { name -> findByName(name + "Test") }
                if (sets.isEmpty()) return@kotlin
                val test = maybeCreate("jvmNativeTest").apply { dependsOn(getByName("commonTest")) }
                sets.forEach { t -> t.dependsOn(test) }
            }
        }

        configureKotlinVersion()
    }
}
