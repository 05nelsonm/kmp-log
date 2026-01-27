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
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalContracts::class)
internal inline fun withTmpFile(block: (tmp: File) -> Unit) {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    var name = "kmp-log.file.test-"
    name += Random.nextBytes(12).encodeToString(Base16)

    val file = SysTempDir
        // Apple targets that utilize symbolic links for their temp
        // directory must be resolved when attempting to acquire a
        // lock on a file via fcntl. It must be the real path, otherwise
        // passing the file path containing a symbolic link segment in as
        // an argument to another process so IT can test lock acquisition
        // will result in a false positive.
        .canonicalFile2()
        .resolve(name)

    try {
        block(file)
    } finally {
        file.delete2()
    }
}

@OptIn(ExperimentalContracts::class)
internal suspend inline fun FileLog.installAndTest(
    deallocateDispatcherDelay: Duration = 375.milliseconds,
    testBody: () -> Unit,
) {
    contract { callsInPlace(testBody, InvocationKind.AT_MOST_ONCE) }

    var threw: Throwable? = null
    Log.installOrThrow(this)
    try {
        testBody()
    } catch (t: Throwable) {
        threw = t
    } finally {
        if (uninstallAndAwaitAsync() && deallocateDispatcherDelay.isPositive()) {
            withContext(Dispatchers.IO) { delay(deallocateDispatcherDelay) }
        }
        files.forEach { it.delete2() }
        dotLockFile.delete2()
        dotRotateFile.delete2()
        dotRotateTmpFile.delete2()
    }
    threw?.let { throw it }
    return
}
