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
package io.matthewnelson.kmp.log.file

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.SysTempDir
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.resolve
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.random.Random

@OptIn(ExperimentalContracts::class)
internal inline fun withTmpFile(block: (tmp: File) -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    var name = "kmp-log.file.test-"
    name += Random.nextBytes(12).encodeToString(Base16)
    val file = SysTempDir.resolve(name)
    try {
        block(file)
    } finally {
        file.delete2()
    }
}
