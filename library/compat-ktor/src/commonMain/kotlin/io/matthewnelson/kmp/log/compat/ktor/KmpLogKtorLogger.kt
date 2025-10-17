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
@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING", "UNUSED")

package io.matthewnelson.kmp.log.compat.ktor

import io.ktor.util.logging.Logger
import io.matthewnelson.kmp.log.Log

/**
 * Convert [Log.Logger] to [io.ktor.util.logging.Logger]
 *
 * @see [of]
 * @see [asKtorLogger]
 * @see [asKmpLogLoggerOrThrow]
 * */
public expect class KmpLogKtorLogger private constructor(
    delegate: Log.Logger,
): Logger {

    public val delegate: Log.Logger

    public companion object Compat {

        /**
         * An alias for [of].
         * */
        public inline fun Log.Logger.asKtorLogger(): KmpLogKtorLogger

        /**
         * Creates a new [KmpLogKtorLogger] instance.
         * */
        public fun of(
            logger: Log.Logger,
        ): KmpLogKtorLogger
    }

    public override fun trace(message: String)
    public override fun trace(message: String, cause: Throwable)

    public override fun debug(message: String)
    public override fun debug(message: String, cause: Throwable)

    public override fun info(message: String)
    public override fun info(message: String, cause: Throwable)

    public override fun warn(message: String)
    public override fun warn(message: String, cause: Throwable)

    public override fun error(message: String)
    public override fun error(message: String, cause: Throwable)

    /** @suppress */
    public override fun equals(other: Any?): Boolean
    /** @suppress */
    public override fun hashCode(): Int
    /** @suppress */
    public override fun toString(): String
}
