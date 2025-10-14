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
package io.matthewnelson.kmp.log.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class LockNativeUnitTest {

    private val lock = newLock()

    @Test
    fun givenLock_whenUsedFromMultipleThreads_thenGuardsAccess() = runTest(timeout = 3.minutes) {
        val size = 500_000
        // When list is resized, if it was not guarded by a lock then would
        // eventually end up throwing an IndexOutOfBoundsException.
        val list = ArrayList<Int>(50)
        Array(size) { i ->
            async(Dispatchers.IO) {
                lock.withLock {
                    if (i % 2 == 0) {
                        lock.withLock {
                            list.add(i)
                        }
                    } else {
                        list.add(i)
                    }
                }
            }
        }.toList().awaitAll()
    }
}
