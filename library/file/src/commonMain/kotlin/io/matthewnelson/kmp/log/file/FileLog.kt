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
@file:Suppress("DuplicatedCode", "LocalVariableName", "PrivatePropertyName", "NOTHING_TO_INLINE")

package io.matthewnelson.kmp.log.file

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Decoder.Companion.decodeBuffered
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.encoding.utf8.UTF8.CharPreProcessor.Companion.sizeUTF8
import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.openWrite
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.file.use
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.internal.CurrentThread
import io.matthewnelson.kmp.log.file.internal.FileLock
import io.matthewnelson.kmp.log.file.internal.InvalidFileLock
import io.matthewnelson.kmp.log.file.internal.LockFile
import io.matthewnelson.kmp.log.file.internal.LogBuffer
import io.matthewnelson.kmp.log.file.internal.LogBuffer.Companion.EXECUTE_ROTATE_LOGS
import io.matthewnelson.kmp.log.file.internal.LogBuffer.Companion.EXECUTE_ROTATE_LOGS_AND_RETRY
import io.matthewnelson.kmp.log.file.internal.LogBuffer.Companion.MAX_RETRIES
import io.matthewnelson.kmp.log.file.internal.LogAction
import io.matthewnelson.kmp.log.file.internal.ModeBuilder
import io.matthewnelson.kmp.log.file.internal.atomicLong
import io.matthewnelson.kmp.log.file.internal.consumeAndIgnore
import io.matthewnelson.kmp.log.file.internal.exists2Robustly
import io.matthewnelson.kmp.log.file.internal.format
import io.matthewnelson.kmp.log.file.internal.id
import io.matthewnelson.kmp.log.file.internal.isDesktop
import io.matthewnelson.kmp.log.file.internal.lockLog
import io.matthewnelson.kmp.log.file.internal.lockRotate
import io.matthewnelson.kmp.log.file.internal.moveTo
import io.matthewnelson.kmp.log.file.internal.now
import io.matthewnelson.kmp.log.file.internal.openLockFileRobustly
import io.matthewnelson.kmp.log.file.internal.openLogFileRobustly
import io.matthewnelson.kmp.log.file.internal.pid
import io.matthewnelson.kmp.log.file.internal.uninterrupted
import io.matthewnelson.kmp.log.file.internal.use
import io.matthewnelson.kmp.log.file.internal.valueDecrement
import io.matthewnelson.kmp.log.file.internal.valueGet
import io.matthewnelson.kmp.log.file.internal.valueIncrement
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.kotlincrypto.hash.blake2.BLAKE2s
import kotlin.concurrent.Volatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic
import kotlin.sequences.forEach
import kotlin.time.Duration.Companion.milliseconds

/**
 * TODO
 *
 * @see [Builder]
 * @see [DOMAIN]
 * */
public class FileLog: Log {

    public companion object {

        /**
         * [FileLog] itself utilizes a [Logger] instance to log [Level.Warn] and
         * [Level.Error] logs, if and when it is necessary. For obvious reasons
         * a [FileLog] instance can **never** log to itself, as that would create
         * a cyclical loop, but it *can* log to other installed [Log] instances
         * (including other [FileLog]). This reference is for the [Logger.domain]
         * used by all [FileLog] instance instantiated [Logger] to dispatch its
         * [Level.Warn] and [Level.Error] logs (as well as [Level.Debug] logs if
         * [FileLog.debug] is set to `true`).
         *
         * This reference is meant to be used with [Builder.blacklistDomain] and
         * [Builder.whitelistDomain] to configure a set of [FileLog] instances
         * for a cooperative logging experience. All logs generated by [FileLog]
         * instances can be centralized to a single [FileLog] instance (or other
         * [Log] implementation), while all *other* [FileLog] instances can be
         * configured to reject logs for the [DOMAIN].
         *
         * **NOTE:** This [Logger.domain] should **not** be utilized for your own
         * [Logger] instances; it is reserved for [FileLog] use only.
         *
         * e.g.
         *
         *     val fileLogErrors = FileLog.Builder(myLogDirectory)
         *         // Allow ONLY logs from other FileLog instances.
         *         .whitelistDomain(FileLog.DOMAIN)
         *         .whitelistDomainNull(allow = false)
         *
         *         .fileName("file_log")
         *         .fileExtension("err")
         *         .min(Log.Level.Warn)
         *         .maxLogBuffered(0) // Will default to the minimum
         *         .maxLogFileSize(0) // Will default to the minimum
         *         .maxLogFiles(0) // Will default to the minimum
         *         .build()
         *
         *     val fileLog1 = FileLog.Builder(myLogDirectory)
         *         // Allow all logs, EXCEPT those from FileLog instances.
         *         .blacklistDomain(FileLog.DOMAIN)
         *         // configure further...
         *         .build()
         *
         *     // val fileLog2 = ...
         *     // val fileLog3 = ...
         *
         *     Log.Root.install(fileLogErrors)
         *     Log.Root.install(fileLog1)
         *     // Log.Root.install(fileLog2)
         *     // Log.Root.install(fileLog3)
         * */
        public const val DOMAIN: String = "kmp-log:file"

        // See Log.Root.ROOT_DOMAIN
        private const val ROOT_DOMAIN: String = "kmp-log:log"
        private const val LOG_ROTATION: String = "LogRotation"
    }

    /**
     * TODO
     * */
    @JvmField
    public val logDirectory: String

    /**
     * TODO
     * */
    @JvmField
    public val logFiles: List<String>

    /**
     * TODO
     * */
    @JvmField
    public val logFiles0Hash: String

    /**
     * TODO
     * */
    @JvmField
    public val minWaitOn: Level

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
    public val maxLogBuffered: Int

    /**
     * TODO
     * */
    @JvmField
    public val maxLogFileSize: Long

    /**
     * TODO
     * */
    @JvmField
    public val maxLogYield: Byte

    /**
     * TODO
     * */
    @JvmField
    public val blacklistDomain: Set<String>

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
     * */
    @JvmField
    public var debug: Boolean

    /**
     * TODO
     * */
    @get:JvmName("isActive")
    public val isActive: Boolean get() = _logJob?.isActive ?: false

    /**
     * TODO
     * */
    @get:JvmName("bufferedLogCount")
    public val bufferedLogCount: Long get() = _bufferedLogCount.valueGet()

    /**
     * TODO
     *
     * @see [uninstallAndAwaitSync]
     *
     * @throws [ClassCastException]
     * */
    public suspend fun uninstallAndAwaitAsync(): Boolean {
        val instance = uninstallAndGet(uid) ?: return false
        (instance as FileLog)._logJob?.join()
        return true
    }

    /**
     * TODO
     *
     * @see [uninstallAndAwaitAsync]
     *
     * @throws [ClassCastException]
     * */
    public fun uninstallAndAwaitSync(): Boolean {
        val instance = uninstallAndGet(uid) ?: return false
        val job = (instance as FileLog)._logJob ?: return false
        while (job.isActive) {
            try {
                CurrentThread.uninterrupted {
                    runBlocking(Dispatchers.IO) { job.join() }
                }
            } catch (_: Throwable) {}
        }
        return true
    }

    // TODO: (Issue #60)
    //  @Throws(CancellationException::class, IOException:class)
    //  public suspend fun enqueueRead(buf: ByteArray, offset: Int, len: Int, position: Long): Int

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
        private var _maxLogBuffered: Int = Channel.UNLIMITED
        private var _maxLogFileSize: Long = (if (isDesktop()) 10L else 5L) * 1024L * 1024L // 10 Mb or 5 Mb
        private var _maxLogFiles: Byte = if (isDesktop()) 5 else 3
        private var _maxLogYield: Byte = 10
        private var _minWaitOn = Level.Fatal
        private val _blacklistDomain = mutableSetOf<String>()
        private val _whitelistDomain = mutableSetOf<String>()
        private var _whitelistDomainNull = true
        private val _whitelistTag = mutableSetOf<String>()
        private var _debug = false

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
         * TODO
         *
         * @return The [Builder]
         * */
        public fun minWaitOn(level: Level): Builder = apply { _minWaitOn = level }

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
         *  - [name] is `tmp`
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
            require(name != "tmp") { "fileExtension cannot be 'tmp'" }
            _fileExtension = name
            return this
        }

        /**
         * DEFAULT: [Channel.UNLIMITED] (i.e. [Int.MAX_VALUE])
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogBuffered(capacity: Int): Builder = apply { _maxLogBuffered = capacity }

        /**
         * DEFAULT:
         *  - `5 Mb` on `Android`, `AndroidNative`, `iOS`, `tvOS`, `watchOS`
         *  - `10 Mb` otherwise
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogFileSize(bytes: Long): Builder = apply { _maxLogFileSize = bytes }

        /**
         * DEFAULT:
         *  - `3` on `Android`, `AndroidNative`, `iOS`, `tvOS`, `watchOS`
         *  - `5` otherwise
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogFiles(max: Byte): Builder = apply { _maxLogFiles = max }

        /**
         * DEFAULT: `10`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogYield(num: Byte): Builder = apply { _maxLogYield = num }

        /**
         * DEFAULT: empty (i.e. Do not reject any [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun blacklistDomain(domain: String): Builder {
            Logger.checkDomain(domain)
            _blacklistDomain.add(domain)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Do not reject any [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun blacklistDomain(vararg domains: String): Builder {
            domains.forEach { domain -> Logger.checkDomain(domain) }
            _blacklistDomain.addAll(domains)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Do not reject any [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun blacklistDomain(domains: Collection<String>): Builder {
            domains.forEach { domain -> Logger.checkDomain(domain) }
            _blacklistDomain.addAll(domains)
            return this
        }

        /**
         * TODO
         * */
        public fun blacklistDomainReset(): Builder = apply { _blacklistDomain.clear() }

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomainNull]
         * @see [whitelistDomainReset]
         * @see [blacklistDomain]
         * @see [DOMAIN]
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
         * @see [blacklistDomain]
         * @see [DOMAIN]
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
         * @see [blacklistDomain]
         * @see [DOMAIN]
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
         * @see [DOMAIN]
         * */
        public fun whitelistDomainNull(allow: Boolean): Builder = apply { _whitelistDomainNull = allow }

        /**
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
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
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun debug(enable: Boolean): Builder = apply { _debug = enable }

        /**
         * TODO
         *
         * @return The [FileLog] to [Log.Root.install]
         *
         * @throws [IOException] If [File.canonicalFile2] fails.
         * */
        public fun build(): FileLog {
            val fileName = _fileName
            val fileExtension = _fileExtension
            val blacklistDomain = _blacklistDomain.toImmutableSet()
            val whitelistDomain = _whitelistDomain.toImmutableSet()
            val whitelistTag = _whitelistTag.toImmutableSet()
            val directory = logDirectory.toFile().canonicalFile2()

            // Current and 1 previous.
            val maxLogFiles = _maxLogFiles.coerceAtLeast(2)
            val files = ArrayList<File>(maxLogFiles.toInt())
            for (i in 0 until maxLogFiles) {
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
                val digest = blake2s.digest(files[0].path.decodeToByteArray(UTF8))
                blake2s.update(digest)
                blake2s.digestInto(digest, destOffset = 0)
                digest.encodeToString(Base16)
            }

            return FileLog(
                min = _min,
                minWaitOn = _minWaitOn,
                max = _max,
                directory = directory,
                files = files.toImmutableList(),
                files0Hash = files0Hash,
                modeDirectory = _modeDirectory.build(),
                modeFile = _modeFile.build(),
                maxLogBuffered = _maxLogBuffered.coerceAtLeast(1_000),
                maxLogFileSize = _maxLogFileSize.coerceAtLeast(50L * 1024L), // 50kb
                maxLogYield = _maxLogYield.coerceAtLeast(1),
                blacklistDomain = blacklistDomain,
                whitelistDomain = whitelistDomain,
                whitelistDomainNull = if (whitelistDomain.isEmpty()) true else _whitelistDomainNull,
                whitelistTag = whitelistTag,
                debug = _debug,
                uidSuffix = "FileLog-$files0Hash",
            )
        }
    }

    private val directory: File

    // Exposed for testing
    @get:JvmSynthetic
    internal val files: List<File>
    @get:JvmSynthetic
    internal val dotLockFile: File
    @get:JvmSynthetic
    internal val dotRotateFile: File
    @get:JvmSynthetic
    internal val dotRotateTmpFile: File

    private val _blacklistDomain: Array<String?>
    private val _whitelistDomain: Array<String>
    private val _whitelistTag: Array<String>

    private val LOG: Logger

    private val logScope: CoroutineScope

    @Volatile
    private var _logBuffer: LogBuffer? = null
    @Volatile
    private var _logJob: Job? = null
    private val _bufferedLogCount = atomicLong(0L)

    private constructor(
        min: Level,
        minWaitOn: Level,
        max: Level,
        directory: File,
        files: List<File>,
        files0Hash: String,
        modeDirectory: String,
        modeFile: String,
        maxLogBuffered: Int,
        maxLogFileSize: Long,
        maxLogYield: Byte,
        blacklistDomain: Set<String>,
        whitelistDomain: Set<String>,
        whitelistDomainNull: Boolean,
        whitelistTag: Set<String>,
        debug: Boolean,
        uidSuffix: String,
    ): super(uid = "io.matthewnelson.kmp.log.file.$uidSuffix", min = min, max = max) {
        this.directory = directory
        this.files = files

        // If the directory is a NFS mounted drive, then files0Hash may not be the
        // same on another machine that also has the drive mounted but with a
        // different canonical path. So, cannot use it in the name for these dot files.
        //
        // Even if files[0].name starts with a '.', we will prefix it with one. This
        // is because if we do so conditionally (only do it if it did not start with
        // one), another FileLog instance could have the same files[0].name as this
        // one, but without the '.' prefix, and we'd end up utilizing the same dot
        // files for 2 different logs (bad day).
        // TODO: Add tests that ensure these names do NOT change...
        ('.' + files[0].name).let { dotName0 ->

            // This will be the longest named file we are dealing with, as
            // Builder.maxLogFiles accepts a Byte whose maximum is 127. files[1]
            // and beyond will only ever be (files[0].path.length + 4) characters
            // in length (path + '.' + 3 digits); here for dotLockFile we are
            // adding a '.' prefix + ".lock", so will be (files[0].path.length + 6)
            // characters in length.
            //
            // As this is the first file opened, any failure attributed to path
            // name length (i.e. ENAMETOOLONG) will rear its head early and not
            // be something to worry about subsequently for any other log files.
            this.dotLockFile = directory.resolve("$dotName0.lock")

            // Neither extension lengths are greater than .lock, so.
            this.dotRotateFile = directory.resolve("$dotName0.next")
            this.dotRotateTmpFile = directory.resolve("$dotName0.tmp")
        }

        this._blacklistDomain = blacklistDomain.toTypedArray()
        this._whitelistDomain = whitelistDomain.toTypedArray()

        this._whitelistTag = whitelistTag.toTypedArray()
        this.LOG = Logger.of(tag = uidSuffix, DOMAIN)
        this.logScope = CoroutineScope(context =
            CoroutineName(uidSuffix)
            + Dispatchers.IO
            + SupervisorJob()
            + CoroutineExceptionHandler Handler@ { context, t ->
                if (t is CancellationException) return@Handler // Ignore...
                // TODO: Set global error variable which can be retrieved externally???
                logE(t) { context }
            }
        )

        this.logDirectory = directory.path
        this.logFiles = files.map { it.path }.toImmutableList()
        this.logFiles0Hash = files0Hash
        this.minWaitOn = minWaitOn
        this.modeDirectory = modeDirectory
        this.modeFile = modeFile
        this.maxLogBuffered = maxLogBuffered
        this.maxLogFileSize = maxLogFileSize
        this.maxLogYield = maxLogYield
        this.blacklistDomain = blacklistDomain
        this.whitelistDomain = whitelistDomain
        this.whitelistDomainNull = whitelistDomainNull
        this.whitelistTag = whitelistTag
        this.debug = debug
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        // Do not log to self, only to other Log instances (if installed).
        if (domain == LOG.domain && tag == LOG.tag) return false

        if (_blacklistDomain.contains(domain)) return false
        if (_whitelistDomain.isNotEmpty()) {
            if (domain == null) {
                if (!whitelistDomainNull) return false
            } else {
                if (!_whitelistDomain.contains(domain)) return false
            }
        }
        if (_whitelistTag.isNotEmpty()) {
            if (!_whitelistTag.contains(tag)) return false
        }
        return _logBuffer != null
    }

    override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val logBuffer = _logBuffer ?: return false

        // The formatted text to write, and its pre-calculated UTF-8 byte-size.
        val preprocessing: Deferred<Pair<CharSequence, Long>?> = run {
            val time = now()
            val tid = CurrentThread.id()

            // LAZY start as to not do unnecessary work until we are
            // certain that the LogAction was committed to LogBuffer
            // successfully, which may be closed for sending.
            logScope.async(start = CoroutineStart.LAZY) {
                val formatted = format(time, pid(), tid, level, domain, tag, msg, t)
                if (formatted.isNullOrEmpty()) return@async null

                // Highly unlikely, but if it is the case skip the work
                // which is only used to see if a log rotation is needed;
                // the value is not returned by the below LogAction, so.
                val sizeUTF8 = if (formatted.length >= maxLogFileSize) {
                    maxLogFileSize
                } else {
                    yield()
                    formatted.sizeUTF8(UTF8)
                }

                formatted to sizeUTF8
            }
        }

        val waitJob = if (level >= minWaitOn) {
            when (domain) {
                null -> Job()
                // Do not block for FileLog or Log.Root logs. They should NEVER
                // be Level.Fatal, but it is checked for regardless, just in case
                // someone is attempting to bypass it.
                DOMAIN, ROOT_DOMAIN -> if (level == Level.Fatal) Job() else null
                else -> Job()
            }
        } else {
            null
        }

        var retries = 0
        val trySendResult = logBuffer.channel.trySend logAction@ { stream, buf, sizeLog, _ ->
            // Can be null if there was an error with LogLoop which
            // is shutting down, or the LogAction is being dropped
            // due to buffer capacity being exceeded (if configured
            // via Builder.maxLogBuffered).
            if (stream == null) {
                preprocessing.cancel()
                waitJob?.cancel()
                _bufferedLogCount.valueDecrement()
                return@logAction 0L
            }

            var threw: Throwable? = null
            val preprocessingResult = try {
                preprocessing.await()
            } catch (t: Throwable) {
                threw = t
                null
            }

            if (preprocessingResult == null) {
                if (level == Level.Fatal) {
                    // fsync no matter what before the process is aborted.
                    try {
                        stream.sync(meta = true)
                    } catch (e: IOException) {
                        // Try to force it by closing.
                        try {
                            stream.close()
                        } catch (ee: IOException) {
                            e.addSuppressed(ee)
                        }
                        threw?.addSuppressed(e)
                    }
                }

                _bufferedLogCount.valueDecrement()
                waitJob?.cancel()

                // threw will only ever be non-null when result == null
                threw?.let { throw it }

                return@logAction 0L
            }

            val (formatted, sizeUTF8) = preprocessingResult

            val needsRotation = if (sizeUTF8 >= maxLogFileSize) {
                // Ideally this will NEVER be the case. But, if it is, a rotation
                // will be executed to truncate log file to 0 and commit the entire
                // log to a single log file. After return, another log rotation will
                // transpire. This "should" be ok b/c retries will not release the
                // file lock, so another process writing to it will not occur. Of
                // course, if the log rotation executes and there is a lock release
                // failure requiring closure of the lock file, then we may lose our
                // log lock, but that is unlikely and will simply retry again after
                // re-acquisition.
                sizeLog > 0L
            } else {
                (sizeLog + sizeUTF8) !in 0L..maxLogFileSize
            }

            if (needsRotation && retries++ < MAX_RETRIES) {
                return@logAction EXECUTE_ROTATE_LOGS_AND_RETRY
            }

            val written = try {
                formatted.decodeBuffered(
                    UTF8,
                    throwOnOverflow = false,
                    buf = buf,
                    action = stream::write,
                )
            } catch (t: Throwable) {
                threw = t
                0L
            } finally {
                if (level == Level.Fatal) {
                    // fsync no matter what before the process is aborted.
                    try {
                        stream.sync(meta = true)
                    } catch (e: IOException) {
                        // Try to force it by closing.
                        try {
                            stream.close()
                        } catch (ee: IOException) {
                            e.addSuppressed(ee)
                        }
                        threw?.addSuppressed(e)
                    }
                }
            }

            _bufferedLogCount.valueDecrement()
            threw?.let { t ->
                waitJob?.cancel()
                throw t
            }

            waitJob?.complete()
            written
        }

        if (trySendResult.isFailure) {
            // LogBuffer.channel is closed for sending
            waitJob?.cancel()
            preprocessing.cancel()
            return false
        }

        _bufferedLogCount.valueIncrement()
        preprocessing.start()
        if (waitJob == null) return true

        while (waitJob.isActive) {
            try {
                CurrentThread.uninterrupted {
                    runBlocking(Dispatchers.IO) { waitJob.join() }
                }
            } catch (_: Throwable) {}
        }

        return !waitJob.isCancelled
    }

    override fun onInstall() {
        val logBuffer = if (maxLogBuffered == Channel.UNLIMITED) {
            LogBuffer()
        } else {
            LogBuffer(capacity = maxLogBuffered, LOG, logScope)
        }

        val logJob = _logJob

        @OptIn(DelicateCoroutinesApi::class)
        logScope.launch(start = CoroutineStart.ATOMIC) {
            logBuffer.use(LOG) { buf ->
                val thisJob = currentCoroutineContext().job

                logD { "LogJob Started >> $thisJob" }

                if (logJob != null) {
                    logD {
                        if (!logJob.isActive) null
                        else "Waiting for previous LogJob to complete >> $logJob"
                    }
                    logJob.join()
                }

                directory.mkdirs2(mode = modeDirectory, mustCreate = false)

                run {
                    val canonical = directory.canonicalFile2()
                    if (canonical != directory) {
                        // FAIL:
                        //   Between time of Builder.build() and Log.Root.install(), the specified
                        //   directory was modified to contain symbolic links such that the current
                        //   one points to a different location. This would invalidate the Log.uid,
                        //   allowing multiple FileLog instances to be installed simultaneously for
                        //   the same log file leading to data corruption.
                        throw IOException("Symbolic link hijacking detected >> [$canonical] != [$directory]")
                    }
                }

                try {
                    // Some systems, such as macOS, have highly unreliable fcntl byte-range file lock
                    // behavior when the target is a symbolic link. We want no part in that and require
                    // the lock file to be a regular file.
                    val canonical = dotLockFile.canonicalFile2()
                    if (canonical != dotLockFile) {
                        LOG.w { "Symbolic link detected >> [$dotLockFile] != [$canonical]" }
                        canonical.delete2(ignoreReadOnly = true)
                        delay(10.milliseconds)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    // ignore
                }

                thisJob.ensureActive()
                val lockFile = dotLockFile.openLockFileRobustly()
                val lockFileCompletion = thisJob.closeOnCompletion(lockFile)

                try {
                    val canonical = files[0].canonicalFile2()
                    if (canonical != files[0]) {
                        LOG.w { "Symbolic link detected >> [${files[0]}] != [$canonical]" }
                        canonical.delete2(ignoreReadOnly = true)
                        delay(10.milliseconds)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    // ignore
                }

                thisJob.ensureActive()
                val logStream = files[0].openLogFileRobustly(modeFile)
                val logStreamCompletion = thisJob.closeOnCompletion(logStream)

                logLoop(
                    buf,
                    lockFile,
                    lockFileCompletion,
                    logStream,
                    logStreamCompletion,
                )
            }
        }.let { job ->
            job.invokeOnCompletion { logD { "LogJob Stopped >> $job" } }
            _logJob = job
        }
        _logBuffer = logBuffer
        log(Level.Info, LOG.domain, LOG.tag, "Log file opened at ${files[0].name}", t = null)
    }

    override fun onUninstall() {
        val logBuffer = _logBuffer
        _logBuffer = null
        logBuffer?.channel?.close(cause = null)
    }

    /*
    * Loops until LogBuffer is closed via onUninstall.
    * */
    private suspend fun LogBuffer.logLoop(
        buf: ByteArray,
        _lockFile: LockFile,
        _lockFileCompletion: DisposableHandle,
        _logStream: FileStream.ReadWrite,
        _logStreamCompletion: DisposableHandle,
    ): Unit = coroutineScope { // logLoop scope
        var lockFile = _lockFile
        var lockFileCompletion = _lockFileCompletion
        var logStream = _logStream
        var logStreamCompletion = _logStreamCompletion
        val thisJob = currentCoroutineContext().job

        thisJob.invokeOnCompletion { logD { "LogLoop Stopped >> $thisJob" } }

        // Utilized for log rotation things. These actions contain no actual
        // write functionality, but are to be woven into the loop as a "priority
        // queue" for scheduling retries and coordinating lockFile/logStream
        // re-open behavior.
        val rotateActionQueue = LogBuffer()
        thisJob.invokeOnCompletion { rotateActionQueue.channel.cancel() }

        // By queueing up an empty action for immediate execution, the loop
        // will be able to check for an interrupted log rotation. If the
        // program previously exited in the middle of doing one, that needs
        // to be finished off firstly.
        rotateActionQueue.channel.trySend(::checkLogRotation)

        // If a write operation for a log entry would end up causing the
        // log file to exceed the configured maxLogFileSize, it is cached
        // here and retried after a log rotation is performed. This allows
        // for us to not drop lockLog and perform an immediate retry.
        val retryActionQueue = ArrayDeque<LogAction>(1)
        thisJob.invokeOnCompletion {
            // This should NEVER be the case...
            var count = 0
            while (true) {
                val action = retryActionQueue.removeFirstOrNull() ?: break
                count++
                @OptIn(DelicateCoroutinesApi::class)
                logScope.launch(start = CoroutineStart.ATOMIC) { action.consumeAndIgnore(buf) }
            }
            if (count > 0) {
                logE(t = null) { "Retry LogAction were present in the queue. Skipped $count logs." }
            }
        }

        // Migrate completion handles to this scope (take ownership over them)
        lockFileCompletion.dispose()
        lockFileCompletion = thisJob.closeOnCompletion(lockFile, logOpen = false)
        logStreamCompletion.dispose()
        logStreamCompletion = thisJob.closeOnCompletion(logStream, logOpen = false)

        logD { "LogLoop Started >> $thisJob" }

        var lockLog: FileLock = InvalidFileLock

        // The loop
        while (true) {
            var logAction: LogAction? = try {
                rotateActionQueue.channel.tryReceive().getOrNull()
                    ?: retryActionQueue.removeFirstOrNull()
                    ?: channel.receive()
            } catch (_: ClosedReceiveChannelException) {
                // FileLog was uninstalled and there are
                // no remaining actions left to process.
                break
            }

            // Will be valid if and only if it was not released
            // due to retryActionQueue containing a cached LogAction
            lockLog = if (lockLog.isValid()) lockLog else CurrentThread.uninterrupted {
                try {
                    // TODO: Blocking timeout monitor
                    lockFile.lockLog()
                } catch (t: Throwable) {
                    // Could be an Overlapped exception on Jvm/Android, or
                    // on Native/Windows. Someone within this process may
                    // be holding a lock on our requested range (maliciously?).
                    // By closing and re-opening the file, this should invalidate
                    // all other locks held by this process (which we should be
                    // the ONLY ones acquiring).

                    // If it was closed, the rotation job's lock is already
                    // invalid, so no reason to wait for it.
                    if (t !is ClosedException) {
                        try {
                            // If a log rotation is currently underway, we must wait
                            // for it so that we do not invalidate its file lock
                            thisJob.awaitLogRotationChildJob()
                        } catch (t: CancellationException) {
                            logAction?.consumeAndIgnore(buf)
                            throw t
                        }
                    }

                    val ee: IOException? = try {
                        lockFile.close()
                        null
                    } catch (ee: IOException) {
                        t.addSuppressed(ee)
                        ee
                    } finally {
                        lockFileCompletion.dispose()
                    }

                    try {
                        directory.mkdirs2(mode = modeDirectory, mustCreate = false)
                    } catch (_: IOException) {
                        // Not a show-stopper. Ignore.
                    }

                    try {
                        logD(ee) { "Closed >> $lockFile" }
                        thisJob.ensureActive()
                        lockFile = dotLockFile.openLockFileRobustly()
                        lockFileCompletion = thisJob.closeOnCompletion(lockFile)
                        // TODO: Blocking timeout monitor
                        lockFile.lockLog()
                    } catch (tt: Throwable) {
                        // Total failure. Close up shop.
                        logAction?.consumeAndIgnore(buf)
                        if (tt is CancellationException) throw tt
                        t.addSuppressed(tt)
                        throw t
                    }
                }.also { lock ->
                    logD { "Acquired lock on ${dotLockFile.name} >> $lock" }
                }
            }

            var size: Long
            CurrentThread.uninterrupted {
                try {
                    size = logStream.size()
                    logStream.position(size)
                } catch (e: IOException) {
                    val ee: IOException? = try {
                        logStream.close()
                        null
                    } catch (ee: IOException) {
                        e.addSuppressed(ee)
                        ee
                    } finally {
                        logStreamCompletion.dispose()
                    }

                    try {
                        directory.mkdirs2(mode = modeDirectory, mustCreate = false)
                    } catch (_: IOException) {
                        // Not a show-stopper. Ignore.
                    }

                    try {
                        logD(ee) { "Closed >> $logStream" }
                        thisJob.ensureActive()
                        logStream = files[0].openLogFileRobustly(modeFile)
                        logStreamCompletion = thisJob.closeOnCompletion(logStream)
                        size = logStream.size()
                        logStream.position(size)
                    } catch (t: Throwable) {
                        // Total failure. Close up shop.
                        logAction?.consumeAndIgnore(buf)
                        if (t is CancellationException) throw t
                        e.addSuppressed(t)
                        throw e
                    }
                }
            }

            logD {
                val count = _bufferedLogCount.valueGet()
                val word = if (count == 1L) "log" else "logs"
                "Current ${files[0].name} file byte size is $size, with $count $word in the buffer"
            }

            var processed = 0
            CurrentThread.uninterrupted {

                // The inner loop
                while (true) {
                    val action = logAction ?: break

                    val written = try {
                        action.invoke(logStream, buf, size, processed)
                    } catch (t: Throwable) {
                        if (t is CancellationException) {
                            // Deferred.await() threw. We're about to die. Nothing
                            // was written, so no need to do anything here.
                        } else {
                            logE(t) { "Failed to write log entry to ${files[0].name}" }
                            // TODO:
                            //  - Check for InterruptedIOException.bytesTransferred???
                            //  - Truncate to size to wipe out any partially written logs???
                            //  - Increment processed to ensure a sync occurs???
                        }
                        0L
                    }

                    @Suppress("AssignedValueIsNeverRead")
                    if (written > 0L) {
                        processed++
                        size += written
                        logD { "Wrote $written bytes to ${files[0].name}" }
                    } else {
                        if (written == EXECUTE_ROTATE_LOGS) {
                            size = maxLogFileSize // To force a log rotation
                            logAction = null
                            break
                        }
                        if (written == EXECUTE_ROTATE_LOGS_AND_RETRY) {
                            size = maxLogFileSize // To force a log rotation
                            logAction = null
                            retryActionQueue.add(action)
                            logD { "Write would exceed maxLogFileSize[$maxLogFileSize]. Retrying after a $LOG_ROTATION." }
                            break
                        }
                    }

                    // Rip through some more buffered actions (if available) while we hold a lock.
                    logAction = when {
                        // We lost our lock, pop out.
                        !lockLog.isValid() -> null
                        // We lost our logStream, pop out.
                        !logStream.isOpen() -> null
                        // Log rotation is needed
                        size >= maxLogFileSize -> null
                        // Yield to another process (potentially)
                        processed >= maxLogYield -> null
                        // Job cancellation
                        !thisJob.isActive -> null
                        else -> rotateActionQueue.channel.tryReceive().getOrNull()
                            ?: retryActionQueue.removeFirstOrNull()
                            ?: channel.tryReceive().getOrNull()
                    }
                }
            }

            if (processed > 0) {
                if (logStream.isOpen()) {
                    // Ensure everything is synced to disk before going further,
                    // either to do a log rotation or release lockLog for another
                    // process to pickup.
                    logD { "Syncing ${files[0].name}" }
                    try {
                        CurrentThread.uninterrupted {
                            logStream.sync(meta = true)
                        }
                    } catch (e: IOException) {
                        // Try to force it by closing. logStream will be
                        // re-opened on the next logLoop iteration.
                        try {
                            logStream.close()
                        } catch (ee: IOException) {
                            e.addSuppressed(ee)
                        }
                        LOG.w(e) { "Sync failure >> $logStream" }
                    }
                }

                logD { "Processed $processed " + if (processed > 1) "logs" else "log" }
            }

            if (thisJob.isActive && size >= maxLogFileSize) {
                if (lockLog.isValid()) {
                    CurrentThread.uninterrupted {
                        rotateLogs(
                            rotateActionQueue = rotateActionQueue,
                            logStream = logStream,
                            lockFile = lockFile,
                            buf = buf,
                            retryActionQueueIsNotEmpty = retryActionQueue.isNotEmpty(),
                        )
                    }
                } else {
                    // We lost lockLog. Trigger an immediate retry.
                    rotateActionQueue.channel.trySend(::checkLogRotation)
                }
            }

            // Do not release lockLog if there is a retry scheduled. It will
            // be immediately dequeued and executed while still holding our
            // lock on log writes.
            if (retryActionQueue.isEmpty() && lockLog.isValid()) try {
                lockLog.release()
                logD { "Released lock on ${dotLockFile.name} >> $lockLog" }
            } catch (e: IOException) {
                // If a log rotation is currently underway, we must wait
                // for it so that we do not invalidate its file lock.
                thisJob.awaitLogRotationChildJob()

                try {
                    // Close the lock file. Next logLoop iteration will fail
                    // to obtain its lock due to a ClosedException and then
                    // re-open lockFile to retry acquisition.
                    lockFile.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }
                LOG.w(e) { "Lock release failure >> $lockLog" }
            }
        }
    }

    /*
    * Performs a log file rotation. Behaviorally, this function atomically copies
    * logStream to a tmp file (dotRotateFile), then executes in a separate coroutine
    * the renaming of files. This allows for a prompt return to processing logs
    * while the rotation executes.
    * */
    private suspend fun CoroutineScope.rotateLogs(
        rotateActionQueue: LogBuffer,
        logStream: FileStream.ReadWrite,
        lockFile: LockFile,
        buf: ByteArray,
        retryActionQueueIsNotEmpty: Boolean,
    ) {
        // If a previous log rotation is currently underway, we must
        // wait for it to complete before doing another one.
        //
        // This "could" happen if, for example, a rotation pops
        // off and resumes log writing where the next writes to
        // logStream are so large that it triggers another rotation
        // before the initial one finishes moving files. In that
        // event, this will suspend (and in doing so inhibit further
        // log writes because we currently hold lockLog).
        currentCoroutineContext().job.awaitLogRotationChildJob()

        run {
            // At this point, the rotateActionQueue:
            //  - Is empty.
            //  - Contains a lockFile.close action due to lockRotate
            //    release failure in a prior rotation job's completion
            //    handle.
            //      - If that lockFile is the same as this function's
            //        lockFile and is closed here and now, we will fail
            //        to acquire lockRotate below and schedule a retry
            //        with a newly-opened lockFile.
            //  - Contains a rotation retry action scheduled by the prior
            //    rotation job due to log file renaming error.
            //      - If that is the case, we're retrying a rotation now.
            //
            // Either way, the queue should be exhausted here and contain
            // NO actions before continuing.
            while (true) {
                val action = rotateActionQueue
                    .channel
                    .tryReceive()
                    .getOrNull()
                    ?: break
                action.consumeAndIgnore(buf)
            }
        }

        val lockRotate = try {
            // TODO: Blocking timeout monitor
            lockFile.lockRotate()
        } catch (t: Throwable) {
            // This can potentially happen if we had to wait on another
            // log rotation to finish up before running this one, and in
            // its completion handle there was a lockRotate.release failure
            // requiring it to close the lockFile.
            //
            // Alternatively, it's due to an Overlapped lock exception on
            // Jvm/Android or Native/Windows because someone within this
            // process is holding a lock on the requested range (maliciously?).

            try {
                // Close the lock file. Next logLoop iteration will fail
                // to obtain its lock due to a ClosedException and then
                // re-open lockFile to retry acquisition.
                lockFile.close()
            } catch (e: IOException) {
                t.addSuppressed(e)
            }
            LOG.w(t) { "Failed to acquire a rotation lock on ${dotLockFile.name}. Retrying $LOG_ROTATION." }

            // Trigger an immediate retry.
            rotateActionQueue.channel.trySend(::checkLogRotation)

            return
        }

        logD { "Acquired lock on ${dotLockFile.name} >> $lockRotate" }

        // At most there will be 127 moves to execute (Byte.MAX_VALUE).
        val moves = ArrayDeque<Pair<File, File>>(files.size)

        // There exists a potential here that another process was mid-rotation
        // when this process' LogJob was started, whereby the dotRotateFile
        // existed and had not been moved into place yet, triggering a
        // rotation in this process' LogJob.
        //
        // Now that we hold both lockLog and lockRotate, we need to determine
        // what action, if any, to take.

        if (!dotRotateFile.exists2Robustly()) {
            // Not picking up an interrupted log rotation to finish off.

            val size = if (retryActionQueueIsNotEmpty) maxLogFileSize else try {
                logStream.size()
            } catch (e: IOException) {
                // Since we're not picking up an interrupted
                // rotation, closing the stream on failure
                // will trigger a re-open on next logLoop iteration
                // and, if size >= maxLogFileSize, will end up
                // retrying the log rotation.
                try {
                    logStream.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                LOG.w(e) { "Failed to obtain size of ${files[0].name}. Retrying $LOG_ROTATION." }

                // No dotRotateFile, but we should trigger an immediate
                // retry to check logStream.size() without writing any
                // new logs to it. If size < maxLogFileSize, then it will
                // simply continue processing log actions.
                rotateActionQueue.channel.trySend(::checkLogRotation)

                // Release lockRotate and return early.
                0L
            }

            if (size < maxLogFileSize) {
                if (lockRotate.isValid()) try {
                    lockRotate.release()
                    logD { "Released lock on ${dotLockFile.name} >> $lockRotate" }
                } catch (e: IOException) {
                    try {
                        // Close the lock file. Next logLoop iteration will fail
                        // to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (ee: IOException) {
                        e.addSuppressed(ee)
                    }

                    LOG.w(e) { "Lock release failure >> $lockRotate" }
                }

                // No further action is needed. Return early.
                return
            }

            try {
                directory.mkdirs2(mode = modeDirectory, mustCreate = false)
            } catch (_: IOException) {
                // Not a show-stopper. Ignore.
            }

            // TODO: move and then delete dotRotateTmpFile. If it exists, we
            //  should attempt to expunge it from the filesystem entirely so
            //  there is no question about our atomic copy of the log hitting
            //  disk.

            try {
                // Shouldn't exist, but just in case using openWrite instead of
                // openLogFileRobustly() to ensure it gets truncated if it does.
                dotRotateTmpFile.openWrite(excl = OpenExcl.MaybeCreate.of(modeFile)).use { tmpStream ->
                    var position = 0L
                    while (true) {
                        val read = logStream.read(buf, 0, buf.size, position)
                        if (read == -1) break
                        position += read
                        tmpStream.write(buf, 0, read)
                    }
                    tmpStream.sync(meta = true)
                }

                // Move into its final location.
                dotRotateTmpFile.moveTo(dotRotateFile)

                // TODO: fsync directory???

                logD { "Atomically copied ${files[0].name} >> ${dotRotateFile.name}" }
            } catch (e: IOException) {
                // Something awful happened. Close up shop and retry.
                try {
                    logStream.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                // Delete it before releasing locks (if it exists).
                try {
                    dotRotateTmpFile.delete2(ignoreReadOnly = true, mustExist = false)
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                if (lockRotate.isValid()) try {
                    lockRotate.release()
                    logD { "Released lock on ${dotLockFile.name} >> $lockRotate" }
                } catch (ee: IOException) {
                    try {
                        // Close the lock file. Next logLoop iteration will fail
                        // to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (eee: IOException) {
                        ee.addSuppressed(eee)
                    }

                    e.addSuppressed(ee)
                    LOG.w(ee) { "Lock release failure >> $lockRotate" }
                }

                logE(e) { "Failed to atomically copy ${files[0].name} >> ${dotRotateFile.name}. Retrying $LOG_ROTATION." }

                // Trigger an immediate retry. If another process is also
                // logging, it may finish off the log rotation for us, so.
                rotateActionQueue.channel.trySend(::checkLogRotation)
                return
            }

            logStream.truncate0AndSync(file = files[0])

            // Checking for existence here is redundant, simply schedule all
            // moves and rip through them while ignoring FileNotFoundException.
            // Eventually we'll reach a full log rotation and never have any
            // failures attributed to non-existence, so.
            var source = dotRotateFile
            for (i in 1 until files.size) {
                val dest = files[i]
                moves.addFirst(source to dest)
                source = dest
            }
        } else {
            LOG.i { "Interrupted $LOG_ROTATION detected (${dotRotateFile.name} exists). Finishing it off." }

            // TODO
            //  - Find hole in log files to pick up where left off in renaming
            //  - If no hole in log files, we could have been interrupted after
            //    renaming dotRotateTmpFile to dotRotateFile, BEFORE truncating
            //    the logStream file. Need to verify by checking its size and/or
            //    peeking at both files and comparing.
            //  - If logStream needs truncation, truncate0AndSync.
            //  - If there WAS a hole in log files (indicating that logStream
            //    truncation took place), check logStream.size() < maxLogFileSize
            //    such that, if another rotation is needed we can enqueue
            //    an empty action for next logLoop iteration so no logs are
            //    written to it, increasing its size further. OR, maybe just
            //    always enqueue an empty action???
            //  - Move log files into final location(s) in separate coroutine
        }

        val child = launchLogRotation(rotateActionQueue, moves)

        child.invokeOnCompletion {
            if (lockRotate.isValid()) try {
                lockRotate.release()
                logD { "Released lock on ${dotLockFile.name} >> $lockRotate" }
            } catch (e: IOException) {
                LOG.w(e) { "Lock release failure >> $lockRotate" }

                // No other recovery mechanism but to close the lock file
                // and invalidate all locks currently held, otherwise the
                // next log rotation may deadlock when attempting to acquire
                // lockRotate.
                rotateActionQueue.channel.trySend { stream, _, _, processed ->
                    // This is done lazily here as a priority action in order
                    // to not inadvertently invalidate a lockLog in the midst
                    // of a write action. If the lockFile is already closed for
                    // some other reason, it does nothing.

                    // Ensure any writes are synced to disk before invalidating
                    // all locks. The inner loop within logLoop may have processed
                    // some actions before picking this one up. As such, invalidation
                    // of lockLog means another process will be able to acquire it.
                    //
                    // Additionally, if the post inner loop sync hits (processed > 0),
                    // then it will do so w/o lockLog (we're closing lockFile in this
                    // action). So, syncing here will make that essentially a no-op
                    // (all data has been pushed to disk already because the sync was
                    // performed here and then the inner loop popped out as a result
                    // of losing lockLog).
                    if (stream != null && processed > 0 && stream.isOpen()) {
                        logD { "Syncing ${files[0].name}" }
                        try {
                            stream.sync(meta = true)
                        } catch (e: IOException) {
                            // Try to force it by closing. logStream will be
                            // re-opened on the next logLoop iteration.
                            try {
                                stream.close()
                            } catch (ee: IOException) {
                                e.addSuppressed(ee)
                            }
                            LOG.w(e) { "Sync failure >> $stream" }
                        }
                    }

                    try {
                        lockFile.close()
                    } catch (_: Throwable) {}

                    0L
                }
            }
        }

        child.invokeOnCompletion { logD { "$LOG_ROTATION Stopped >> $child" } }
    }

    /*
    * Truncates the stream to 0L and syncs it to disk. Failure results in closure
    * whereby file is then used to attempt openWrite (O_TRUNC) + sync + close. Failure
    * beyond that is ignored (currently).
    * */
    private fun FileStream.ReadWrite.truncate0AndSync(file: File) {
        try {
            size(new = 0L)

            logD { "Syncing ${file.name}" }
            try {
                sync(meta = true)
            } catch (e: IOException) {
                LOG.w(e) { "Sync failure >> $this" }
                throw e
            }

            logD { "Truncated ${file.name} to 0" }
        } catch (e: IOException) {
            // Try a different way.
            try {
                close()
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }
            var s: FileStream.Write? = null
            try {
                // O_TRUNC
                s = file.openWrite(excl = OpenExcl.MustExist)

                logD { "Syncing ${file.name}" }
                try {
                    s.sync(meta = true)
                } catch (ee: IOException) {
                    // Truncation succeeded, but our sync did not. Just
                    // go with it at this point and hope for the best...
                    LOG.w(ee) { "Sync failure >> $s" }
                }

                logD { "Truncated ${file.name} to 0" }
            } catch (ee: IOException) {
                // openWrite failed
                e.addSuppressed(ee)
            } finally {
                try {
                    s?.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }
            }

            if (s != null) return
            logE(e) { "Failed to truncate ${file.name}" }
            // TODO: move and then delete files[0]? Need to see how that
            //  would work because if another process has it open and
            //  is logging to it, it wouldn't know that it needs to close
            //  and re-open its FileStream (unless there is an exists2() check
            //  right after obtaining lockLog to close its stream???)
            //
            // TODO: Maybe enqueue a truncation action whereby if the
            //  logStream.size() == size we truncate???
        }
    }

    private fun CoroutineScope.launchLogRotation(
        rotateActionQueue: LogBuffer,
        moves: ArrayDeque<Pair<File, File>>,
    ): Job = launch(context = CoroutineName("$LOG_ROTATION-$logFiles0Hash")) {
        logD { "$LOG_ROTATION Started >> ${currentCoroutineContext().job}" }

//        val startSize = moves.size
        while (moves.isNotEmpty()) {
            val (source, dest) = moves.removeFirst()

            try {
                source.moveTo(dest)
                logD { "Moved ${source.name} >> ${dest.name}" }

                // yield only after we have our first move such that
                // there exists a "hole" in the log files. This matters
                // when picking up an interrupted log rotation to cut
                // down on work needed to figure out where in the log
                // rotation we were interrupted.
                yield()
            } catch (e: IOException) {
                // Source file did not exist, ignore.
                if (e is FileNotFoundException) continue

                // TODO: Figure out what happened and attempt to recover.

                // TODO: Track rotation failures and abort if there have been
                //  too many. If someone swapped out a log file for a non-empty
                //  directory or something, we could end up retrying forever.

                logE(e) { "$LOG_ROTATION failure" }

                // Trigger an immediate retry. If another process is also
                // logging, it may finish off the log rotation for us, so.
                rotateActionQueue.channel.trySend(::checkLogRotation)
                break
            }
        }

//        if (moves.size != startSize) {
//            // TODO: fsync directory???
//        }
    }

    @Suppress("UNUSED", "UNUSED_PARAMETER")
    private suspend fun checkLogRotation(
        stream: FileStream.ReadWrite?,
        buf: ByteArray,
        sizeLog: Long,
        processed: Int,
    ): Long {
        if (sizeLog >= maxLogFileSize) return EXECUTE_ROTATE_LOGS
        if (dotRotateFile.exists2Robustly()) return EXECUTE_ROTATE_LOGS
        return 0L
    }

    private suspend fun Job.awaitLogRotationChildJob() {
        children.forEach { child ->
            logD {
                if (!child.isActive) null
                else "Waiting for $LOG_ROTATION to complete >> $child"
            }
            child.join()
        }
    }

    private fun Job.closeOnCompletion(closeable: Closeable, logOpen: Boolean = true): DisposableHandle {
        if (logOpen) logD { "Opened >> $closeable" }

        return invokeOnCompletion { t ->
            val e = try {
                closeable.close()
                null
            } catch (e: IOException) {
                t?.addSuppressed(e)
                e
            }
            logD(e) { "Closed >> $closeable" }
        }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun logD(lazyMsg: () -> Any?): Int {
        contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
        return logD(t = null, lazyMsg)
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun logD(t: Throwable?, lazyMsg: () -> Any?): Int {
        contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
        if (!debug) return 0
        return LOG.d(t, lazyMsg)
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun logE(t: Throwable?, lazyMsg: () -> Any?) {
        contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
        if (LOG.e(t, lazyMsg) == 0) {
            // No other Log instances installed, or none
            // accepted Level.Error for this Log.Logger.
            t?.printStackTrace()
        }
    }
}
