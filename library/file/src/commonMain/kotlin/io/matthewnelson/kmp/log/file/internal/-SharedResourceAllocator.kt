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
@file:Suppress("PrivatePropertyName")

package io.matthewnelson.kmp.log.file.internal

import io.matthewnelson.kmp.log.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile
import kotlin.time.Duration

internal abstract class SharedResourceAllocator<Resource: Any> protected constructor(
    private val LOG: Log.Logger?,
    private val deallocationDelay: Duration,
    private val deallocationDispatcher: CoroutineDispatcher,
) {

    @Volatile
    private var _resource: Resource? = null
    @Volatile
    private var _count = 0L
    @Volatile
    private var _deallocationJob: Job? = null

    private val lock = SynchronizedLock()

    /**
     * Increments the counter and, if necessary, allocates the resource via
     * [doAllocation]. If a deallocation of the resource is pending, it is
     * subsequently canceled.
     * */
    internal fun allocate(): Resource {
        var wasAllocated = false
        val resource = synchronized(lock) {
            var r = _resource
            if (r == null) {
                check(_count == 0L) { "count[$_count] != 0" }
                wasAllocated = true
                r = doAllocation()
                _resource = r
            }
            _count++
            _deallocationJob?.cancel()
            r
        }

        // Do not invoke Logger from within synchronized lambda
        if (wasAllocated && debug()) LOG?.d { "Allocated >> $resource" }

        return resource
    }

    /**
     * Decrements the counter and, if `0`, will [doDeallocation] after the specified [deallocationDelay] within the context of
     * the provided [deallocationDispatcher].
     *
     * @throws [IllegalStateException] If deallocation count is less than 1.
     * */
    internal fun deallocate() {
        synchronized(lock) {
            check(_count > 0L) { "count[$_count] <= 0" }
            if (--_count != 0L) return

            _deallocationJob?.cancel()

            @OptIn(DelicateCoroutinesApi::class)
            _deallocationJob = GlobalScope.launch(context = deallocationDispatcher, start = CoroutineStart.ATOMIC) {
                delay(deallocationDelay)

                val resource = synchronized(lock) {
                    // If we were canceled by allocate while waiting for the lock.
                    ensureActive()

                    // No going back beyond this point
                    val r = _resource
                    _resource = null
                    r
                } ?: return@launch

                var threw: Throwable? = null
                try {
                    // Do not invoke deallocate from within synchronized lambda.
                    resource.doDeallocation()
                } catch (t: Throwable) {
                    threw = t
                } finally {
                    // Do not invoke Logger from within synchronized lambda.
                    if (debug()) LOG?.d(threw) { "Deallocated >> $resource" }
                }
            }
        }
    }

    protected open fun debug(): Boolean = true

    protected abstract fun doAllocation(): Resource
    protected abstract fun Resource.doDeallocation()
}
