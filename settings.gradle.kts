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
@file:Suppress("UnstableApiUsage")
rootProject.name = "kmp-log"

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }

    val vCatalogKC = rootDir
        .resolve("gradle")
        .resolve("libs.versions.toml")
        .readLines()
        .first { it.startsWith("kotlincrypto-catalog ") }
        .substringAfter('"')
        .substringBeforeLast('"')

    versionCatalogs {
        create("kotlincrypto") {
            // https://github.com/KotlinCrypto/version-catalog/blob/master/gradle/kotlincrypto.versions.toml
            from("org.kotlincrypto:version-catalog:$vCatalogKC")
        }
    }
}

includeBuild("build-logic")

@Suppress("PrivatePropertyName")
private val CHECK_PUBLICATION: String? by settings

if (CHECK_PUBLICATION != null) {
    include(":tools:check-publication")
} else {
    arrayOf(
        "compat-ktor",
        "compat-slf4j",
        "file",
        "log",
        "sys",
    ).forEach { name ->
        include(":library:$name")
    }
}
