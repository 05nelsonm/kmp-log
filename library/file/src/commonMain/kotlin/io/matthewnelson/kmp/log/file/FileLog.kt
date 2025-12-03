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
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.SysFsInfo
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.internal.ModeBuilder
import io.matthewnelson.kmp.log.file.internal.isDesktop
import org.kotlincrypto.hash.blake2.BLAKE2s
import kotlin.jvm.JvmField

/**
 * TODO
 *
 * @see [Builder]
 * */
public class FileLog: Log {

    /**
     * TODO
     * */
    @JvmField
    public val logDirectory: String

    /**
     * TODO
     * */
    @JvmField
    public val logFiles: Set<String>

    /**
     * TODO
     * */
    @JvmField
    public val logFiles0Hash: String

    /**
     * TODO
     * */
    @JvmField
    public val maxLogSize: Long

    /**
     * TODO
     * */
    @JvmField
    public val whitelistDomains: Set<String>

    /**
     * TODO
     * */
    @JvmField
    public val whitelistTags: Set<String>

    /**
     * TODO
     * */
    public class Builder(

        /**
         * TODO
         * */
        @JvmField
        public val logDirectory: String,
    ) {
        private var _min = Level.Info
        private var _max = Level.Fatal
        private var _directoryMode = ModeBuilder.of(isDirectory = true)
        private var _fileMode = ModeBuilder.of(isDirectory = false)
        private var _fileName = "log"
        private var _fileExtension = ""
        private var _maxLogSize: Long = (if (isDesktop()) 10L else 5L) * 1024L * 1024L // 10 Mb or 5 Mb
        private var _maxLogs: Byte = if (isDesktop())  5  else 3
        private val _whitelistDomains = mutableSetOf<String>()
        private val _whitelistTags = mutableSetOf<String>()

        /**
         * DEFAULT: [Level.Info]
         *
         * TODO
         * */
        public fun min(level: Level): Builder {
            _min = level
            return this
        }

        /**
         * DEFAULT: [Level.Fatal]
         *
         * TODO
         * */
        public fun max(level: Level): Builder {
            _max = level
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun directoryGroupReadable(enable: Boolean): Builder {
            _directoryMode.groupRead = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun directoryGroupWritable(enable: Boolean): Builder {
            _directoryMode.groupWrite = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun directoryOtherReadable(enable: Boolean): Builder {
            _directoryMode.otherRead = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun directoryOtherWritable(enable: Boolean): Builder {
            _directoryMode.otherWrite = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun fileGroupReadable(enable: Boolean): Builder {
            _fileMode.groupRead = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun fileGroupWritable(enable: Boolean): Builder {
            _fileMode.groupWrite = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun fileOtherReadable(enable: Boolean): Builder {
            _fileMode.otherRead = enable
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         * */
        public fun fileOtherWritable(enable: Boolean): Builder {
            _fileMode.otherWrite = enable
            return this
        }

        /**
         * DEFAULT: `log`
         *
         * TODO
         * */
        public fun fileName(name: String): Builder {
            _fileName = name
            return this
        }

        /**
         * DEFAULT: no extension
         *
         * TODO
         * */
        public fun fileExtension(name: String): Builder {
            _fileExtension = name
            return this
        }

        /**
         * DEFAULT:
         *  - `5 Mb` on Android, AndroidNative, iOS, tvOS, watchOS
         *  - `10 Mb` otherwise
         *
         * TODO
         * */
        public fun maxLogSize(bytes: Long): Builder {
            _maxLogSize = bytes
            return this
        }

        /**
         * DEFAULT:
         *  - `3` on Android, AndroidNative, iOS, tvOS, watchOS
         *  - `5` otherwise
         *
         * TODO
         * */
        public fun maxLogs(max: Byte): Builder {
            _maxLogs = max
            return this
        }

        /**
         * DEFAULT: no domains (i.e. Allow all [Logger.domain])
         *
         * TODO
         * */
        public fun whitelistDomain(domain: String): Builder {
            _whitelistDomains.add(domain)
            return this
        }

        /**
         * DEFAULT: no domains (i.e. Allow all [Logger.domain])
         *
         * TODO
         * */
        public fun whitelistDomain(vararg domains: String): Builder {
            _whitelistDomains.addAll(domains)
            return this
        }

        /**
         * DEFAULT: no domains (i.e. Allow all [Logger.domain])
         *
         * TODO
         * */
        public fun whitelistDomain(domains: Collection<String>): Builder {
            _whitelistDomains.addAll(domains)
            return this
        }

        /**
         * DEFAULT: no tags (i.e. Allow all [Logger.tag])
         *
         * TODO
         * */
        public fun whitelistTag(tag: String): Builder {
            _whitelistTags.add(tag)
            return this
        }

        /**
         * DEFAULT: no tags (i.e. Allow all [Logger.tag])
         *
         * TODO
         * */
        public fun whitelistTag(vararg tags: String): Builder {
            _whitelistTags.addAll(tags)
            return this
        }

        /**
         * DEFAULT: no tags (i.e. Allow all [Logger.tag])
         *
         * TODO
         * */
        public fun whitelistTag(tags: Collection<String>): Builder {
            _whitelistTags.addAll(tags)
            return this
        }

        /**
         * TODO
         *
         * @throws [IllegalArgumentException] TODO: fileName/fileExtension & domains/tags
         * @throws [IOException] If [File.canonicalFile2] fails.
         * @throws [UnsupportedOperationException] If Js/WasmJs Browser.
         * */
        @Throws(Exception::class)
        public fun build(): FileLog {
            if (SysFsInfo.name == "FsJsBrowser") {
                throw UnsupportedOperationException("Logging to files is not supported on Js/WasmJs Browser.")
            }

            val fileName = _fileName
            // sanity checks
            require(fileName.isNotEmpty()) { "fileName cannot be empty" }
            require(fileName.length <= 64) { "fileName cannot exceed 64 characters" }
            require(!fileName.endsWith('.')) { "fileName cannot end with '.'" }
            require(fileName.indexOfFirst { it.isWhitespace() } == -1) { "fileName cannot contain whitespace" }

            val fileExtension = _fileExtension
            // sanity checks
            require(fileExtension.length <= 8) { "fileExtension cannot exceed 8 characters" }
            require(fileExtension.indexOfFirst { it.isWhitespace() } == -1) { "fileExtension cannot contain whitespace" }
            require(!fileExtension.contains('.')) { "fileExtension cannot contain '.'" }

            val whitelistDomains = _whitelistDomains.toImmutableSet()
            whitelistDomains.forEach { domain -> Logger.checkDomain(domain) }
            val whitelistTags = _whitelistTags.toImmutableSet()
            whitelistTags.forEach { tag -> Logger.checkTag(tag) }

            val directory = logDirectory.toFile().canonicalFile2()

            // Current and 1 previous.
            val maxLogs = _maxLogs.coerceAtLeast(MIN_MAX_LOGS)
            val files = LinkedHashSet<File>(maxLogs.toInt())
            for (i in 0 until maxLogs) {
                var name = fileName
                if (fileExtension.isNotEmpty()) {
                    name += '.'
                    name += fileExtension
                }
                if (i != 0) {
                    name += '.'
                    if (i <= 99) name += '0'
                    if (i <=  9) name += '0'
                    name += i
                }
                files.add(directory.resolve(name))
            }

            // Have to hash the file path b/c if there is any whitespace
            // in it, then it cannot be used in the Log.uid.
            val files0Hash = run {
                // Will produce a 12 byte digest which comes out to 24 characters when base16 encoded
                val blake2s = BLAKE2s(96)
                val digest = blake2s.digest(files.elementAt(0).path.decodeToByteArray(UTF8))
                blake2s.update(digest)
                blake2s.digestInto(digest, destOffset = 0)
                digest.encodeToString(Base16)
            }

            return FileLog(
                min = _min,
                max = _max,
                directory = directory,
                directoryMode = _directoryMode.build(),
                files = files.toImmutableSet(),
                files0Hash = files0Hash,
                fileMode = _fileMode.build(),
                maxLogSize = _maxLogSize.coerceAtLeast(MIN_MAX_LOG_SIZE),
                whitelistDomains = whitelistDomains,
                whitelistTags = whitelistTags,
            )
        }

        public companion object {

            /**
             * TODO
             * */
            public const val MIN_MAX_LOGS: Byte = 2

            /**
             * TODO
             * */
            public const val MIN_MAX_LOG_SIZE: Long = 50L * 1024L // 50 Kb
        }
    }

    private companion object {
        private const val UID_PREFIX = "io.matthewnelson.kmp.log.file."
        private const val DOMAIN = "kmp-log:file"
    }

    private val directory: File
    private val files: Set<File>

    private val directoryMode: String
    private val fileMode: String

    private val LOG: Logger by lazy { Logger.of(tag = uid.substringAfter(UID_PREFIX), DOMAIN) }

    private constructor(
        min: Level,
        max: Level,
        directory: File,
        directoryMode: String,
        files: Set<File>,
        files0Hash: String,
        fileMode: String,
        maxLogSize: Long,
        whitelistDomains: Set<String>,
        whitelistTags: Set<String>,
    ): super(uid = "${UID_PREFIX}FileLog-$files0Hash", min = min, max = max) {
        this.directory = directory
        this.files = files
        this.logDirectory = directory.path
        this.logFiles = files.mapTo(LinkedHashSet(files.size)) { it.path }.toImmutableSet()
        this.logFiles0Hash = files0Hash
        this.directoryMode = directoryMode
        this.fileMode = fileMode
        this.maxLogSize = maxLogSize
        this.whitelistDomains = whitelistDomains
        this.whitelistTags = whitelistTags
    }

    override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        // TODO
        return false
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        // Do not log to self, only to other Logs (if installed)
        if (domain == LOG.domain && tag == LOG.tag) return false
        if (whitelistDomains.isNotEmpty()) {
            if (domain == null) return false
            if (!whitelistDomains.contains(domain)) return false
        }
        if (whitelistTags.isNotEmpty()) {
            if (!whitelistTags.contains(tag)) return false
        }
        return true
    }

    override fun onInstall() {
        // TODO
    }

    override fun onUninstall() {
        // TODO
    }
}
