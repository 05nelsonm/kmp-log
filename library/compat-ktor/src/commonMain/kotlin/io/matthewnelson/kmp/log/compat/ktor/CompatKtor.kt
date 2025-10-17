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
@file:JvmName("CompatKtor")

package io.matthewnelson.kmp.log.compat.ktor

import io.ktor.util.logging.Logger
import io.matthewnelson.kmp.log.Log
import kotlin.jvm.JvmName

/**
 * Helper for converting Ktor's [Logger] back to [KmpLogKtorLogger] to retrieve its
 * [KmpLogKtorLogger.delegate]. Only use if you are absolutely certain that the [Logger]
 * in some ktor API is actually a [KmpLogKtorLogger], such as with ktor server's
 * `Application.log` extension function whereby the `ApplicationEngineEnvironmentBuilder.log`
 * was set up with [KmpLogKtorLogger].
 *
 * @throws [ClassCastException] If [Logger] is not a [KmpLogKtorLogger].
 * */
public fun Logger.asKmpLogLoggerOrThrow(): Log.Logger = (this as? KmpLogKtorLogger)?.delegate
    ?: throw ClassCastException("Ktor Logger instance is not a KmpLogKtorLogger")
