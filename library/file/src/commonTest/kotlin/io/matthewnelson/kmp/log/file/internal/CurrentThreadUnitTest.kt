/*
 * Copyright (c) 2026 Matthew Nelson
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
package io.matthewnelson.kmp.log.file.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CurrentThreadUnitTest {

    @Test
    fun givenCurrentThread_whenId_thenIsGreaterThan0() = runTest {
        val tid1 = CurrentThread.id()
        assertTrue(tid1 > 0L)

        val tid2 = withContext(Dispatchers.IO) { CurrentThread.id() }
        assertTrue(tid2 > 0L)

        assertNotEquals(tid1, tid2)
        println("tid1=$tid1, tid2=$tid2")
    }
}
