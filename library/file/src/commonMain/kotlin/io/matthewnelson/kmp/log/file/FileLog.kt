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
@file:Suppress("PrivatePropertyName")

package io.matthewnelson.kmp.log.file

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.IOException
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
    public val modeDirectory: String

    /**
     * TODO
     * */
    @JvmField
    public val modeFile: String

    /**
     * TODO
     * */
    @JvmField
    public val maxLogSize: Long

    /**
     * TODO
     * */
    @JvmField
    public val whitelistDomain: Set<String>

    /**
     * TODO
     * */
    @JvmField
    public val whitelistDomainNull: Boolean

    /**
     * TODO
     * */
    @JvmField
    public val whitelistTag: Set<String>

    /**
     * TODO
     *
     * @throws [IllegalArgumentException] When:
     *  - [logDirectory] is empty
     *  - [logDirectory] contains null character `\u0000`
     * */
    public class Builder(

        /**
         * The directory where log files are to be kept.
         * */
        @JvmField
        public val logDirectory: String,
    ) {

        init {
            require(logDirectory.isNotEmpty()) { "logDirectory cannot be empty" }
            require(!logDirectory.contains('\u0000')) { "logDirectory cannot contain null character '\\u0000'" }
        }

        private var _min = Level.Info
        private var _max = Level.Fatal
        private var _modeDirectory = ModeBuilder.of(isDirectory = true)
        private var _modeFile = ModeBuilder.of(isDirectory = false)
        private var _fileName = "log"
        private var _fileExtension = ""
        private var _maxLogSize: Long = (if (isDesktop()) 10L else 5L) * 1024L * 1024L // 10 Mb or 5 Mb
        private var _maxLogs: Byte = if (isDesktop()) 5 else 3
        private val _whitelistDomain = mutableSetOf<String>()
        private var _whitelistDomainNull = true
        private val _whitelistTag = mutableSetOf<String>()

        /**
         * DEFAULT: [Level.Info]
         *
         * @param [level] The minimum [Log.Level] to allow.
         *
         * @return The [Builder]
         * */
        public fun min(level: Level): Builder = apply { _min = level }

        /**
         * DEFAULT: [Level.Fatal]
         *
         * @param [level] The maximum [Log.Level] to allow.
         *
         * @return The [Builder]
         * */
        public fun max(level: Level): Builder = apply { _max = level }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun directoryGroupReadable(allow: Boolean): Builder = apply { _modeDirectory.groupRead = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun directoryGroupWritable(allow: Boolean): Builder = apply { _modeDirectory.groupWrite = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun directoryOtherReadable(allow: Boolean): Builder = apply { _modeDirectory.otherRead = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun directoryOtherWritable(allow: Boolean): Builder = apply { _modeDirectory.otherWrite = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun fileGroupReadable(allow: Boolean): Builder = apply { _modeFile.groupRead = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun fileGroupWritable(allow: Boolean): Builder = apply { _modeFile.groupWrite = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun fileOtherReadable(allow: Boolean): Builder = apply { _modeFile.otherRead = allow }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun fileOtherWritable(allow: Boolean): Builder = apply { _modeFile.otherWrite = allow }

        /**
         * DEFAULT: `log`
         *
         * Configure the log file name.
         *
         * @param [name] The name to use for the log file.
         *
         * @return The [Builder]
         *
         * @throws [IllegalArgumentException] When:
         *  - [name] is empty
         *  - [name] is greater than `64` characters in length
         *  - [name] ends with character `.`
         *  - [name] contains whitespace
         *  - [name] contains character `/`
         *  - [name] contains character `\`
         *  - [name] contains null character `\u0000`
         * */
        public fun fileName(name: String): Builder {
            require(name.isNotEmpty()) { "fileName cannot be empty" }
            require(name.length <= 64) { "fileName cannot exceed 64 characters" }
            require(!name.endsWith('.')) { "fileName cannot end with '.'" }
            name.forEach { c ->
                require(!c.isWhitespace()) { "fileName cannot contain whitespace" }
                require(c != '/') { "fileName cannot contain '/'" }
                require(c != '\\') { "fileName cannot contain '\\'" }
                require(c != '\u0000') { "fileName cannot contain null character '\\u0000'" }
            }
            _fileName = name
            return this
        }

        /**
         * DEFAULT: empty (i.e. no extension)
         *
         * Configure the log file extension name.
         *
         * @param [name] The name to use for the log file extension, or empty for no extension.
         *
         * @return The [Builder]
         *
         * @throws [IllegalArgumentException] When:
         *  - [name] is greater than `8` characters in length
         *  - [name] contains whitespace
         *  - [name] contains character `.`
         *  - [name] contains character `/`
         *  - [name] contains character `\`
         *  - [name] contains null character `\u0000`
         * */
        public fun fileExtension(name: String): Builder {
            require(name.length <= 8) { "fileExtension cannot exceed 8 characters" }
            name.forEach { c ->
                require(!c.isWhitespace()) { "fileExtension cannot contain whitespace" }
                require(c != '.') { "fileExtension cannot contain '.'" }
                require(c != '/') { "fileExtension cannot contain '/'" }
                require(c != '\\') { "fileExtension cannot contain '\\'" }
                require(c != '\u0000') { "fileExtension cannot contain null character '\\u0000'" }
            }
            _fileExtension = name
            return this
        }

        /**
         * DEFAULT:
         *  - `5 Mb` on `Android`, `AndroidNative`, `iOS`, `tvOS`, `watchOS`
         *  - `10 Mb` otherwise
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogSize(bytes: Long): Builder = apply { _maxLogSize = bytes }

        /**
         * DEFAULT:
         *  - `3` on `Android`, `AndroidNative`, `iOS`, `tvOS`, `watchOS`
         *  - `5` otherwise
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogs(max: Byte): Builder = apply { _maxLogs = max }

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomainNull]
         * @see [whitelistDomainReset]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun whitelistDomain(domain: String): Builder {
            Logger.checkDomain(domain)
            _whitelistDomain.add(domain)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomainNull]
         * @see [whitelistDomainReset]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun whitelistDomain(vararg domains: String): Builder {
            domains.forEach { domain -> Logger.checkDomain(domain) }
            _whitelistDomain.addAll(domains)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomainNull]
         * @see [whitelistDomainReset]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun whitelistDomain(domains: Collection<String>): Builder {
            domains.forEach { domain -> Logger.checkDomain(domain) }
            _whitelistDomain.addAll(domains)
            return this
        }

        /**
         * DEFAULT: `true`
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomain]
         * @see [whitelistDomainReset]
         * */
        public fun whitelistDomainNull(allow: Boolean): Builder = apply { _whitelistDomainNull = allow }

        /**
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * */
        public fun whitelistDomainReset(): Builder = apply { _whitelistDomain.clear() }.whitelistDomainNull(true)

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistTagReset]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun whitelistTag(tag: String): Builder {
            Logger.checkTag(tag)
            _whitelistTag.add(tag)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistTagReset]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun whitelistTag(vararg tags: String): Builder {
            tags.forEach { tag -> Logger.checkTag(tag) }
            _whitelistTag.addAll(tags)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistTagReset]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun whitelistTag(tags: Collection<String>): Builder {
            tags.forEach { tag -> Logger.checkTag(tag) }
            _whitelistTag.addAll(tags)
            return this
        }

        /**
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistTag]
         * */
        public fun whitelistTagReset(): Builder = apply { _whitelistTag.clear() }

        /**
         * TODO
         *
         * @return The [FileLog] to [Log.Root.install]
         *
         * @throws [IOException] If [File.canonicalFile2] fails.
         * @throws [UnsupportedOperationException] If Js/WasmJs Browser.
         * */
        public fun build(): FileLog {
            val fileName = _fileName
            val fileExtension = _fileExtension
            val whitelistDomain = _whitelistDomain.toImmutableSet()
            val whitelistTag = _whitelistTag.toImmutableSet()
            val directory = logDirectory.toFile().canonicalFile2()

            // Current and 1 previous.
            val maxLogs = _maxLogs.coerceAtLeast(2)
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
                val blake2s = BLAKE2s(bitStrength = Byte.SIZE_BITS * 12)
                val digest = blake2s.digest(files.elementAt(0).path.decodeToByteArray(UTF8))
                blake2s.update(digest)
                blake2s.digestInto(digest, destOffset = 0)
                digest.encodeToString(Base16)
            }

            return FileLog(
                min = _min,
                max = _max,
                directory = directory,
                files = files.toImmutableSet(),
                files0Hash = files0Hash,
                modeDirectory = _modeDirectory.build(),
                modeFile = _modeFile.build(),
                maxLogSize = _maxLogSize.coerceAtLeast(50L * 1024L), // 50kb
                whitelistDomain = whitelistDomain,
                whitelistDomainNull = if (whitelistDomain.isEmpty()) true else _whitelistDomainNull,
                whitelistTag = whitelistTag,
                uidSuffix = "FileLog-$files0Hash",
            )
        }
    }

    private companion object {
        private const val DOMAIN = "kmp-log:file"
    }

    private val directory: File
    private val files: Set<File>

    private val LOG: Logger

    private constructor(
        min: Level,
        max: Level,
        directory: File,
        files: Set<File>,
        files0Hash: String,
        modeDirectory: String,
        modeFile: String,
        maxLogSize: Long,
        whitelistDomain: Set<String>,
        whitelistDomainNull: Boolean,
        whitelistTag: Set<String>,
        uidSuffix: String,
    ): super(uid = "io.matthewnelson.kmp.log.file.$uidSuffix", min = min, max = max) {
        this.directory = directory
        this.files = files
        this.LOG = Logger.of(tag = uidSuffix, DOMAIN)

        this.logDirectory = directory.path
        this.logFiles = files.mapTo(LinkedHashSet(files.size)) { it.path }.toImmutableSet()
        this.logFiles0Hash = files0Hash
        this.modeDirectory = modeDirectory
        this.modeFile = modeFile
        this.maxLogSize = maxLogSize
        this.whitelistDomain = whitelistDomain
        this.whitelistDomainNull = whitelistDomainNull
        this.whitelistTag = whitelistTag
    }

    override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        // TODO
        return false
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        // Do not log to self, only to other Logs (if installed)
        if (domain == LOG.domain && tag == LOG.tag) return false

        if (whitelistDomain.isNotEmpty()) {
            if (domain == null) {
                if (!whitelistDomainNull) return false
            } else {
                if (!whitelistDomain.contains(domain)) return false
            }
        }
        if (whitelistTag.isNotEmpty()) {
            if (!whitelistTag.contains(tag)) return false
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
