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

import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.openRead
import io.matthewnelson.kmp.file.openReadWrite
import io.matthewnelson.kmp.file.openWrite
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.log.file.withTmpFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class OpenRobustlyUnitTest {

    @Test
    fun givenInvalidPermissions_whenOpenRobustly_thenRetriesOpenAfterChmod() = withTmpFile { tmp ->
        tmp.openWrite(excl = OpenExcl.MustCreate.of(mode = "400")).close()
        var invocationOpen = 0
        tmp.openRobustly(
            mode = OpenExcl.MaybeCreate.DEFAULT.mode,
            deleteOrMoveOnEISDIR = false,
            onEISDIR = { _, _ -> },
            open = { invocationOpen++; openReadWrite(excl = null) },
        ).close()
        assertEquals(2, invocationOpen)
    }

    @Test
    fun givenDirectory_whenOpenRobustly_thenRetryOpenPreservesFileNotFoundException() = withTmpFile { tmp ->
        var invocationOpen = 0
        var invocationMove = 0
        try {
            tmp.mkdirs2(mode = null, mustCreate = true).openRobustly(
                mode = OpenExcl.MaybeCreate.DEFAULT.mode,
                deleteOrMoveOnEISDIR = true,
                onEISDIR = { _, moved ->
                    invocationMove++
                    // delete2 should have succeeded b/c it was empty,
                    // and no move should have occurred.
                    assertNull(moved)
                },
                open = { invocationOpen++; openRead() }
            ).close()
            fail("openRobustly succeeded...")
        } catch (_: FileNotFoundException) {
            // pass
        }
        assertEquals(2, invocationOpen)
        assertEquals(1, invocationMove)
    }

    @Test
    fun givenEmptyDirectory_whenOpenRobustly_thenRetriesOpenAfterDeletion() = withTmpFile { tmp ->
        var invocationOpen = 0
        var invocationMove = 0
        tmp.mkdirs2(mode = null, mustCreate = true).openRobustly(
            mode = OpenExcl.MaybeCreate.DEFAULT.mode,
            deleteOrMoveOnEISDIR = true,
            onEISDIR = { _, moved ->
                invocationMove++
                // delete2 should have succeeded b/c it was empty,
                // and no move should have occurred.
                assertNull(moved)
            },
            open = { invocationOpen++; openReadWrite(excl = null) },
        ).close()
        assertEquals(2, invocationOpen)
        assertEquals(1, invocationMove)
    }

    @Test
    fun givenNonEmptyDirectory_whenOpenRobustly_thenRetriesOpenAfterMovingToRandomName() = withTmpFile { tmp ->
        var invocationOpen = 0
        var invocationMove = 0
        val subDirTmp = tmp.resolve("sub_directory").mkdirs2(mode = null, mustCreate = true)
        tmp.openRobustly(
            mode = OpenExcl.MaybeCreate.DEFAULT.mode,
            deleteOrMoveOnEISDIR = true,
            onEISDIR = { _, moved ->
                invocationMove++
                assertNotNull(moved)
                moved.resolve(subDirTmp.name).delete2(mustExist = true)
                moved.delete2(mustExist = true)
                assertNotEquals(tmp, moved)
            },
            open = { invocationOpen++; openReadWrite(excl = null) },
        ).close()
        assertEquals(2, invocationOpen)
        assertEquals(1, invocationMove)
    }
}
