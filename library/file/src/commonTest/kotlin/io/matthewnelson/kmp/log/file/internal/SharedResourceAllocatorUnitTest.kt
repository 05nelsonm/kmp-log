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
@file:Suppress("LocalVariableName")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SharedResourceAllocatorUnitTest {

    private class TestAllocator(
        private val scope: TestScope,
        val testDelay: Duration = 10.milliseconds,
        LOG: Log.Logger? = null,
        val testDispatcher: TestDispatcher = StandardTestDispatcher(scope.testScheduler)
    ): SharedResourceAllocator<Job>(
        LOG = LOG,
        deallocationDelay = testDelay,
        deallocationDispatcher = testDispatcher,
    ) {

        @Volatile
        var doDeallocationInvocations: Int = 0
            private set

        override fun doAllocation(): Job = Job(parent = scope.coroutineContext.job)

        override fun Job.doDeallocation() {
            doDeallocationInvocations++
            cancel()
        }
    }

    @Test
    fun givenAllocator_whenAllocateAndDeallocateCycled_thenOperatesAsExpected() = runTest {
        val allocator = TestAllocator(scope = this)
        val job1 = allocator.allocate()
        assertTrue(job1.isActive)
        allocator.deallocate()

        // deallocate should be delayed
        assertTrue(job1.isActive)
        assertEquals(0, allocator.doDeallocationInvocations)

        // Would endlessly suspend if doDeallocation were not called
        job1.join()
        assertTrue(job1.isCancelled)
        assertEquals(1, allocator.doDeallocationInvocations)

        // Should create a new instance
        val job2 = allocator.allocate()
        assertNotEquals(job1, job2)
        assertTrue(job2.isActive)

        // allocate again (count == 2) should return the same instance
        assertEquals(job2, allocator.allocate())
        assertTrue(job2.isActive)

        // deallocate should decrement count, but not call doDeallocation (count == 1)
        allocator.deallocate()
        withContext(allocator.testDispatcher) { delay(allocator.testDelay + 50.milliseconds) }

        assertTrue(job2.isActive)
        assertEquals(1, allocator.doDeallocationInvocations)

        // deallocate should decrement count and then call doDeallocation (count == 0)
        allocator.deallocate()

        // Attempting to deallocate again should throw exception (count < 0)
        assertFailsWith<IllegalStateException> { allocator.deallocate() }

        // Would endlessly suspend if doDeallocation were not called
        job2.join()
        assertTrue(job2.isCancelled)
        assertEquals(2, allocator.doDeallocationInvocations)

        val job3 = allocator.allocate()
        assertNotEquals(job2, job3)

        // deallocating and then re-allocating should cancel the deallocation Job
        allocator.deallocate()
        assertEquals(job3, allocator.allocate())

        withContext(allocator.testDispatcher) { delay(allocator.testDelay + 50.milliseconds) }
        assertTrue(job3.isActive)
        assertEquals(2, allocator.doDeallocationInvocations)
        withContext(allocator.testDispatcher) { delay(allocator.testDelay + 50.milliseconds) }
        assertTrue(job3.isActive)
        assertEquals(2, allocator.doDeallocationInvocations)

        // Lastly, deallocation should close up shop
        allocator.deallocate()

        // Would endlessly suspend if doDeallocation were not called
        job3.join()
        assertTrue(job3.isCancelled)
        assertEquals(3, allocator.doDeallocationInvocations)
    }
}
