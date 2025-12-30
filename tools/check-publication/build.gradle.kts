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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("configuration")
}

repositories {
    google()

    if (version.toString().endsWith("-SNAPSHOT")) {
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    } else {
        maven("https://central.sonatype.com/api/v1/publisher/deployments/download/") {
            val p = rootProject.properties
            authentication.create("Authorization", HttpHeaderAuthentication::class.java)
            credentials(HttpHeaderCredentials::class.java) {
                val username = p["mavenCentralUsername"]?.toString() ?: throw NullPointerException()
                val password = p["mavenCentralPassword"]?.toString() ?: throw NullPointerException()
                name = "Authorization"
                @OptIn(ExperimentalEncodingApi::class)
                value = "Bearer " + Base64.Mime.encode("$username:$password".encodeToByteArray())
            }
        }
    }
}

kmpConfiguration {
    configureShared {
        androidLibrary(namespace = "tools.check.publication") {
            sourceSetMain {
                dependencies {
                    implementation("$group:sys-android:$version")
                }
            }
        }

        jvm {
            sourceSetMain {
                dependencies {
                    implementation("${group}:compat-slf4j:${version}")
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
                    implementation("$group:log:$version")
                    implementation("$group:sys:$version")
                }
            }
        }

        kotlin {
            with(sourceSets) {
                // All targets except wasmWasi
                arrayOf(
                    "jvmAndroid",
                    "jsWasmJs",
                    "native",
                ).forEach { name ->
                    findByName(name + "Main")?.dependencies {
                        implementation("$group:compat-ktor:$version")
                    }
                }
//                // Non-Js
//                arrayOf(
//                    "jvmAndroid",
//                    "native",
//                ).forEach { name ->
//                    findByName(name + "Main")?.dependencies {
//                        implementation("$group:file:$version")
//                    }
//                }
            }
        }
    }
}
