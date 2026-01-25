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
        var doAllocationInvocations: Int = 0
            private set
        @Volatile
        var doDeallocationInvocations: Int = 0
            private set

        override fun doAllocation(): Job {
            doAllocationInvocations++
            return Job(parent = scope.coroutineContext.job)
        }

        override fun Job.doDeallocation() {
            doDeallocationInvocations++
            cancel()
        }
    }

    @Test
    fun givenAllocator_whenGetOrAllocateAndDeRefCycled_thenOperatesAsExpected() = runTest {
        val allocator = TestAllocator(scope = this)
        val (job1, deRef1) = allocator.getOrAllocate()
        assertTrue(job1.isActive)
        assertEquals(1, allocator.doAllocationInvocations)

        // doDeallocation should be delayed after invoking DeRefHandle
        deRef1.invoke()
        assertTrue(job1.isActive)
        assertEquals(0, allocator.doDeallocationInvocations)

        // Would endlessly suspend if doDeallocation were not called
        job1.join()
        assertTrue(job1.isCancelled)
        assertEquals(1, allocator.doDeallocationInvocations)

        // Invoking DeRefHandle again should do nothing (would throw IllegalStateException otherwise)
        deRef1.invoke()

        // Should allocate another new resource
        val (job2, deRef2) = allocator.getOrAllocate()
        assertNotEquals(job1, job2)
        assertTrue(job2.isActive)
        assertEquals(2, allocator.doAllocationInvocations)

        // Should return same instance with different DeRefHandle, increasing the reference count
        val (_job2, _deRef2) = allocator.getOrAllocate()
        assertEquals(job2, _job2)
        assertNotEquals(deRef2, _deRef2)
        assertTrue(job2.isActive)
        assertEquals(2, allocator.doAllocationInvocations)

        // DeRefHandle invocation should only decrement count (no doDeallocation)
        _deRef2()
        withContext(allocator.testDispatcher) { delay(allocator.testDelay + 50.milliseconds) }

        assertTrue(job2.isActive)
        assertEquals(1, allocator.doDeallocationInvocations)

        // DeRefHandle invocation should decrement count and then call doDeallocation
        deRef2.invoke()

        // Would endlessly suspend if doDeallocation were not called
        job2.join()
        assertTrue(job2.isCancelled)
        assertEquals(2, allocator.doDeallocationInvocations)

        val (job3, deRef3) = allocator.getOrAllocate()
        assertNotEquals(job2, job3)

        // DeRefHandle invocation and then re-allocating before delayed
        // deallocation executes should cancel the deallocation Job
        deRef3.invoke()
        val (_job3, _deRef3) = allocator.getOrAllocate()
        assertEquals(job3, _job3)
        assertNotEquals(deRef3, _deRef3)

        withContext(allocator.testDispatcher) { delay(allocator.testDelay + 50.milliseconds) }
        assertTrue(job3.isActive)
        assertEquals(2, allocator.doDeallocationInvocations)
        withContext(allocator.testDispatcher) { delay(allocator.testDelay + 50.milliseconds) }
        assertTrue(job3.isActive)
        assertEquals(2, allocator.doDeallocationInvocations)

        // Lastly, deallocation should close up shop
        _deRef3.invoke()

        // Would endlessly suspend if doDeallocation were not called
        job3.join()
        assertTrue(job3.isCancelled)
        assertEquals(3, allocator.doDeallocationInvocations)
    }
}
