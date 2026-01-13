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

import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.log.file.withTmpFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileLockUnitTest {

    @Test
    fun givenFileLock_whenLockFileClosed_thenIsValidReturnsFalse() = withTmpFile { tmp ->
        tmp.openLockFile().use { lf ->
            val lock = lf.tryLockLog()
            assertNotNull(lock, "lock was null???")
            assertTrue(lock.isValid())
            lf.close()
            assertFalse(lock.isValid())
        }
    }

    @Test
    fun givenLockFile_whenLockLog_thenRangeIsZeroToOne() = withTmpFile { tmp ->
        // This should NEVER change...
        tmp.openLockFile().use { lf ->
            val lockLog = lf.tryLockLog()
            assertNotNull(lockLog, "lock was null???")
            assertEquals(0L, lockLog.position())
            assertEquals(1L, lockLog.size())
        }
    }

    @Test
    fun givenLockFile_whenLockRotate_thenRangeIsOneToTwo() = withTmpFile { tmp ->
        // This should NEVER change...
        tmp.openLockFile().use { lf ->
            val lockRotate = lf.tryLockRotate()
            assertNotNull(lockRotate, "lock was null???")
            assertEquals(1L, lockRotate.position())
            assertEquals(1L, lockRotate.size())
        }
    }
}
