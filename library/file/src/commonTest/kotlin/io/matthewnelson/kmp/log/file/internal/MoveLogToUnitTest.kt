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

import io.matthewnelson.kmp.file.DirectoryNotEmptyException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.parentFile
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.readUtf8
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.file.writeUtf8
import io.matthewnelson.kmp.log.file.withTmpFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class MoveLogToUnitTest {

    @Test
    fun givenNonExistingFile_whenMoveTo_thenThrowsFileNotFoundException() = withTmpFile { source ->
        withTmpFile { dest ->
            try {
                source.moveLogTo(dest)
                fail("moveLogTo succeeded >> $source to $dest")
            } catch (e: FileNotFoundException) {
                assertEquals(true, e.message?.contains(source.path))
                assertEquals(false, e.message?.contains(dest.path))
            }
        }
    }

    @Test
    fun givenExistingFile_whenDestinationDoesNotExist_thenIsSuccessful() = withTmpFile { source ->
        withTmpFile { dest ->
            val text = "Test Source"
            source.writeUtf8(null, text)
            source.moveLogTo(dest)
            assertFalse(source.exists2(), "source.exists2")
            assertTrue(dest.exists2(), "dest.exists2")
            assertEquals(text, dest.readUtf8())
        }
    }

    @Test
    fun givenExistingDirectory_whenEmptyAndDestinationDoesNotExist_thenIsSuccessful() = withTmpFile { source ->
        withTmpFile { dest ->
            source.mkdirs2(mode = null).moveLogTo(dest)
            assertFalse(source.exists2(), "source.exists2")
            assertTrue(dest.exists2(), "dest.exists2")
        }
    }

    @Test
    fun givenExistingDirectory_whenNonEmptyAndDestinationDoesNotExist_thenIsSuccessful() = withTmpFile { source ->
        withTmpFile { dest ->
            val fileSource = source.resolve("file.txt")
            val fileDest = dest.resolve(fileSource.name)
            source.mkdirs2(mode = null)
            try {
                val text = "Test Source"
                fileSource.writeUtf8(null, text)
                source.moveLogTo(dest)
                assertFalse(source.exists2(), "source.exists2")
                assertTrue(dest.exists2(), "dest.exists2")
                assertEquals(text, fileDest.readUtf8())
            } finally {
                fileSource.delete2()
                fileDest.delete2()
            }
        }
    }

    @Test
    fun givenExistingFile_whenDestinationIsEmptyDirectory_thenIsSuccessful() = withTmpFile { source ->
        withTmpFile { dest ->
            val text = "Test Source"
            source.writeUtf8(null, text)
            dest.mkdirs2(mode = null)
            source.moveLogTo(dest)
            assertFalse(source.exists2(), "source.exists2")
            assertTrue(dest.exists2(), "dest.exists2")
            assertEquals(text, dest.readUtf8())
        }
    }

    @Test
    fun givenExistingFile_whenDestinationIsNonEmptyDirectory_thenThrowsDirectoryNotEmptyException() = withTmpFile { source ->
        withTmpFile { dest ->
            source.writeUtf8(null, "Hello World!")
            val subDirDest = dest.resolve("sub_directory").mkdirs2(mode = null)
            try {
                try {
                    source.moveLogTo(dest)
                    fail("moveLogTo succeeded >> $source to $dest")
                } catch (e: DirectoryNotEmptyException) {
                    assertEquals(dest, e.file)
                    assertNull(e.other, "other != null")
                }
            } finally {
                subDirDest.delete2()
            }
        }
    }

    @Test
    fun givenExistingDirectory_whenEmptyAndDestinationIsExistingFile_thenThrowsNotDirectoryException() = withTmpFile { source ->
        withTmpFile { dest ->
            source.mkdirs2(mode = null)
            dest.writeUtf8(null, "Hello World!")
            try {
                source.moveLogTo(dest)
                fail("moveLogTo succeeded >> $source to $dest")
            } catch (e: NotDirectoryException) {
                assertEquals(dest, e.file)
                assertNull(e.other, "other != null")
            }
        }
    }

    @Test
    fun givenExistingDirectory_whenEmptyAndDestinationIsEmptyDirectory_thenIsSuccessful() = withTmpFile { source ->
        withTmpFile { dest ->
            source.mkdirs2(mode = null)
            dest.mkdirs2(mode = null)
            source.moveLogTo(dest)
        }
    }

    @Test
    fun givenExistingDirectory_whenNonEmptyAndDestinationIsEmptyDirectory_thenIsSuccessful() = withTmpFile { source ->
        withTmpFile { dest ->
            val subDirSource = source.resolve("sub_directory").mkdirs2(mode = null)
            val subDirDest = dest.resolve(subDirSource.name)
            try {
                dest.mkdirs2(mode = null)
                source.moveLogTo(dest)
                assertFalse(source.exists2(), "source.exists2")
                assertTrue(subDirDest.exists2(), "destSubDir.exists2")
            } finally {
                subDirSource.delete2()
                subDirDest.delete2()
            }
        }
    }

    @Test
    fun givenExistingDirectory_whenNonEmptyAndDestinationIsNonEmptyDirectory_thenThrowsDirectoryNotEmptyException() = withTmpFile { source ->
        withTmpFile { dest ->
            val subDirSource = source.resolve("sub_directory").mkdirs2(mode = null)
            val subDirDest = dest.resolve(subDirSource.name)
            val subDirDest2 = (subDirDest.path + '2').toFile()

            try {
                subDirDest2.mkdirs2(mode = null)
                source.moveLogTo(dest)
                fail("moveLogTo succeeded >> $source to $dest")
            } catch (e: DirectoryNotEmptyException) {
                assertEquals(dest, e.file)
                assertNull(e.other, "other != null")
            } finally {
                subDirSource.delete2()
                subDirDest.delete2()
                subDirDest2.delete2()
            }
        }
    }

    @Test
    fun givenExistingDirectory_whenNonEmpty_thenDeleteOrMoveToRandomIsSuccessful() = withTmpFile { tmp ->
        val maxNewNameLen = 7
        val subDirTmp = tmp.resolve("sub_directory").mkdirs2(mode = null)
        var moved: File? = null
        try {
            moved = tmp.deleteOrMoveToRandomIfNonEmptyDirectory(buf = null, maxNewNameLen)
            assertNotNull(moved)
            assertFalse(tmp.exists2(), "tmp.exists2")
            assertTrue(moved.resolve(subDirTmp.name).exists2(), "moved(sub_directory).exists2")
            assertEquals(tmp.parentFile, moved.parentFile, "parent directories were not the same")
            assertNotNull(tmp.parentFile)
            assertTrue(moved.name.startsWith('.'), "does not start with '.'")
            assertEquals(maxNewNameLen, moved.name.length, "maxNewNameLen")
        } finally {
            subDirTmp.delete2()
            moved?.resolve(subDirTmp.name)?.delete2()
            moved?.delete2()
        }
    }
}
