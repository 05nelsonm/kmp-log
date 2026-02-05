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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FileLockUnitTest {

    @Test
    fun givenFileLock_whenLockFileClosed_thenIsValidReturnsFalse() = withTmpFile { tmp ->
        tmp.openLockFile().use { lf ->
            val lock = lf.tryLock(position = 0, size = 1)
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
            val lockLog = lf.tryLock(position = FILE_LOCK_POS_LOG, size = FILE_LOCK_SIZE)
            assertNotNull(lockLog, "lock was null???")
            assertEquals(0L, lockLog.position())
            assertEquals(1L, lockLog.size())
        }
    }

    @Test
    fun givenLockFile_whenLockRotate_thenRangeIsOneToTwo() = withTmpFile { tmp ->
        // This should NEVER change...
        tmp.openLockFile().use { lf ->
            val lockRotate = lf.tryLock(position = FILE_LOCK_POS_ROTATE, size = FILE_LOCK_SIZE)
            assertNotNull(lockRotate, "lock was null???")
            assertEquals(1L, lockRotate.position())
            assertEquals(1L, lockRotate.size())
        }
    }

    @Test
    fun givenLockFile_whenLockRotateLockLog_thenRangesDoNotConflict() = withTmpFile { tmp ->
        tmp.openLockFile().use { lf ->
            val lockLog = lf.tryLock(position = FILE_LOCK_POS_LOG, size = FILE_LOCK_SIZE)
            val lockRotate = lf.tryLock(position = FILE_LOCK_POS_ROTATE, size = FILE_LOCK_SIZE)
            assertEquals(true, lockLog?.isValid(), "lockLog")
            assertEquals(true, lockRotate?.isValid(), "lockRotate")
            lockLog?.release()
            assertEquals(false, lockLog?.isValid(), "lockLog2")
            assertEquals(true, lockRotate?.isValid(), "lockRotate2")
            lockRotate?.release()
            assertEquals(false, lockRotate?.isValid(), "lockRotate3")
        }
    }

    @Test
    fun givenInvalidFileLock_whenIsValid_thenFileLockIsAlwaysInvalid() {
        assertFalse(InvalidFileLock.isValid())
    }

    @Test
    fun givenInvalidFileLock_whenRelease_thenDoesNotThrowException() {
        InvalidFileLock.release()
    }

    @Test
    fun givenStub_whenIsOpen_thenEqualsFalse() {
        assertFalse(StubLockFile.isOpen())
    }

    @Test
    fun givenStub_whenToString_thenReturnsStubLockFile() {
        assertEquals("StubLockFile", StubLockFile.toString())
    }

    @Test
    fun givenStub_whenLock_thenReturnsStubFileLock() {
        val lock = StubLockFile.lock(2, 3)
        assertNotNull(lock)
        assertIs<StubFileLock>(lock)
    }

    @Test
    fun givenStub_whenTryLock_thenReturnsStubFileLock() {
        val lock = StubLockFile.tryLock(2, 3)
        assertNotNull(lock)
        assertIs<StubFileLock>(lock)
    }

    @Test
    fun givenStub_whenLockPosLog_thenReturnsSameStubFileLockInstance() {
        val lock1 = StubLockFile.lock(FILE_LOCK_POS_LOG, FILE_LOCK_SIZE)
        val lock2 = StubLockFile.lock(FILE_LOCK_POS_LOG, FILE_LOCK_SIZE)
        assertEquals(lock1, lock2)
        assertEquals(FILE_LOCK_POS_LOG, lock1.position())
        assertEquals(FILE_LOCK_SIZE, lock1.size())
    }

    @Test
    fun givenStub_whenLockPosRotate_thenReturnsSameStubFileLockInstance() {
        val lock1 = StubLockFile.lock(FILE_LOCK_POS_ROTATE, FILE_LOCK_SIZE)
        val lock2 = StubLockFile.lock(FILE_LOCK_POS_ROTATE, FILE_LOCK_SIZE)
        assertEquals(lock1, lock2)
        assertEquals(FILE_LOCK_POS_ROTATE, lock1.position())
        assertEquals(FILE_LOCK_SIZE, lock1.size())
    }

    @Test
    fun givenStub_whenLock_thenStubFileLockIsAlwaysValid() {
        val lock = StubLockFile.lock(0, 1)
        assertTrue(lock.isValid(), "lock.isValid 1")
        lock.release()
        assertTrue(lock.isValid(), "lock.isValid 2")
    }
}
