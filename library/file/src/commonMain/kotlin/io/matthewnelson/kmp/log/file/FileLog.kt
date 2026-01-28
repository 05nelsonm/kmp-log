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
import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.encoding.utf8.UTF8.CharPreProcessor.Companion.sizeUTF8
import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.DirectoryNotEmptyException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.openRead
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
import io.matthewnelson.kmp.log.file.internal.LogAction
import io.matthewnelson.kmp.log.file.internal.LogAction.Companion.EXECUTE_ROTATE_LOGS
import io.matthewnelson.kmp.log.file.internal.LogAction.Companion.MAX_RETRIES
import io.matthewnelson.kmp.log.file.internal.LogAction.Companion.drop
import io.matthewnelson.kmp.log.file.internal.LogBuffer
import io.matthewnelson.kmp.log.file.internal.LogSend
import io.matthewnelson.kmp.log.file.internal.LogWait
import io.matthewnelson.kmp.log.file.internal.ModeBuilder
import io.matthewnelson.kmp.log.file.internal.RotateActionQueue
import io.matthewnelson.kmp.log.file.internal.ScopeFileLog
import io.matthewnelson.kmp.log.file.internal.ScopeLog
import io.matthewnelson.kmp.log.file.internal.ScopeLog.Companion.scopeLog
import io.matthewnelson.kmp.log.file.internal.ScopeLogHandle
import io.matthewnelson.kmp.log.file.internal.ScopeLogLoop
import io.matthewnelson.kmp.log.file.internal.ScopeLogLoop.Companion.scopeLogLoop
import io.matthewnelson.kmp.log.file.internal.SharedResourceAllocator
import io.matthewnelson.kmp.log.file.internal._atomic
import io.matthewnelson.kmp.log.file.internal._atomicRef
import io.matthewnelson.kmp.log.file.internal._compareAndSet
import io.matthewnelson.kmp.log.file.internal._decrement
import io.matthewnelson.kmp.log.file.internal._get
import io.matthewnelson.kmp.log.file.internal._getAndSet
import io.matthewnelson.kmp.log.file.internal._increment
import io.matthewnelson.kmp.log.file.internal._set
import io.matthewnelson.kmp.log.file.internal.async
import io.matthewnelson.kmp.log.file.internal.deleteOrMoveToRandomIfNonEmptyDirectory
import io.matthewnelson.kmp.log.file.internal.exists2Robustly
import io.matthewnelson.kmp.log.file.internal.format
import io.matthewnelson.kmp.log.file.internal.id
import io.matthewnelson.kmp.log.file.internal.isDesktop
import io.matthewnelson.kmp.log.file.internal.launch
import io.matthewnelson.kmp.log.file.internal.lockLog
import io.matthewnelson.kmp.log.file.internal.lockRotate
import io.matthewnelson.kmp.log.file.internal.moveLogTo
import io.matthewnelson.kmp.log.file.internal.now
import io.matthewnelson.kmp.log.file.internal.openLockFileRobustly
import io.matthewnelson.kmp.log.file.internal.openLogFileRobustly
import io.matthewnelson.kmp.log.file.internal.pid
import io.matthewnelson.kmp.log.file.internal.uninterrupted
import io.matthewnelson.kmp.log.file.internal.uninterruptedRunBlocking
import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
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
         * [FileLog] itself utilizes a [Logger] instance to log [Level.Error] and
         * [Level.Warn] logs, if and when it is necessary. For obvious reasons
         * a [FileLog] instance can **never** log to itself as that would create
         * a cyclical loop, but it *can* log to other installed [Log] instances
         * (including other [FileLog]). This reference is for the [Logger.domain]
         * used by all [FileLog] instance instantiated [Logger] for dispatching
         * their [Level.Error] and [Level.Warn] logs (as well as [Level.Debug]
         * logs if [FileLog.debug] is set to `true`).
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
         *         .maxLogFileSize(0) // Will default to the minimum
         *         .maxLogFiles(0) // Will default to the minimum
         *         .build()
         *
         *     val fileLog1 = FileLog.Builder(myLogDirectory)
         *         // Allow all logs, EXCEPT from other FileLog instances.
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

        private const val LOG_JOB: String = "LogJob"
        private const val LOG_LOOP: String = "LogLoop"
        private const val LOG_ROTATION: String = "LogRotation"

        private val DEFAULT_FORMATTER: Formatter = Formatter(::format)
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
    public val maxLogFileSize: Long

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
    public val bufferCapacity: Int

    /**
     * TODO
     * */
    public val bufferOverflowDropOldest: Boolean

    /**
     * TODO
     * */
    @JvmField
    public val minWaitOn: Level

    /**
     * TODO
     * */
    @JvmField
    public val yieldOn: Byte

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
    @Volatile
    public var debug: Boolean

    /**
     * TODO
     * */
    @JvmField
    @Volatile
    public var warn: Boolean

    /**
     * TODO
     * */
    @get:JvmName("isActive")
    public val isActive: Boolean get() = _logJob?.isActive ?: false

    /**
     * TODO
     * */
    @get:JvmName("pendingLogCount")
    public val pendingLogCount: Long get() = _pendingLogCount._get()

    /**
     * TODO
     *
     * @see [uninstallAndAwaitSync]
     *
     * @throws [ClassCastException]
     * */
    public suspend fun uninstallAndAwaitAsync(): Boolean {
        val instance = uninstallAndGet(uid) ?: return false
        (instance as FileLog)._logJob?.join() ?: return false
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
        val context = instance.scopeFileLog.handler + Dispatchers.IO
        while (job.isActive) {
            try {
                CurrentThread.uninterruptedRunBlocking(context) {
                    job.join()
                }
            } catch (_: Throwable) {
                // InterruptedException (Jvm/Android)
                // CancellationException (in which case job.isActive will be false)
            }
        }
        return true
    }

    /**
     * TODO
     * */
    public fun interface Formatter {

        /**
         * TODO
         * */
        public fun format(
            time: CharSequence,
            pid: Int,
            tid: Long,
            level: Level,
            domain: String?,
            tag: String,
            msg: String?,
            t: Throwable?,
        ): CharSequence?
    }

    /**
     * TODO
     * */
    public class Builder(

        /**
         * The directory where log files are to be kept.
         * */
        @JvmField
        public val logDirectory: String,
    ) {

        private var _min = Level.Info
        private var _max = Level.Fatal
        private var _modeDirectory = ModeBuilder.of(isDirectory = true)
        private var _modeFile = ModeBuilder.of(isDirectory = false)
        private var _fileName = "log"
        private var _fileExtension = ""
        private var _maxLogFileSize = (if (isDesktop()) 10L else 5L) * 1024L * 1024L // 10 Mb or 5 Mb
        private var _maxLogFiles: Byte = if (isDesktop()) 5 else 3
        private var _bufferCapacity = -1
        private var _bufferOverflowDropOldest = false
        private var _minWaitOn = Level.Verbose
        private var _yieldOn: Byte = 2
        private var _formatter = DEFAULT_FORMATTER
        private var _formatterOmitYear = true
        private val _blacklistDomain = mutableSetOf<String>()
        private val _whitelistDomain = mutableSetOf<String>()
        private var _whitelistDomainNull = true
        private val _whitelistTag = mutableSetOf<String>()
        private var _debug = false
        private var _warn = true

        /**
         * DEFAULT: [Level.Info]
         *
         * @param [level] The minimum [Log.Level] to allow.
         *
         * @return The [Builder]
         *
         * @see [Log.min]
         * */
        public fun min(level: Level): Builder = apply { _min = level }

        /**
         * DEFAULT: [Level.Fatal]
         *
         * @param [level] The maximum [Log.Level] to allow.
         *
         * @return The [Builder]
         *
         * @see [Log.max]
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
         * DEFAULT: `5 Mb` on `Android`/`AndroidNative`/`iOS`/`tvOS`/`watchOS`, otherwise `10 Mb`.
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogFileSize(nBytes: Long): Builder = apply { _maxLogFileSize = nBytes }

        /**
         * DEFAULT: `3` on `Android`/`AndroidNative`/`iOS`/`tvOS`/`watchOS`, otherwise `5`.
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogFiles(nFiles: Byte): Builder = apply { _maxLogFiles = nFiles }

        /**
         * DEFAULT: `-1` (i.e. TODO)
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun bufferCapacity(nLogs: Int): Builder = apply { _bufferCapacity = nLogs }

        /**
         * DEFAULT: `false` (i.e. TODO)
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun bufferOverflow(dropOldest: Boolean): Builder = apply { _bufferOverflowDropOldest = dropOldest }

        /**
         * DEFAULT: [Level.Verbose]
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun minWaitOn(level: Level): Builder = apply { _minWaitOn = level }

        /**
         * DEFAULT: `2`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun yieldOn(nLogs: Byte): Builder = apply { _yieldOn = nLogs }

        /**
         * DEFAULT: `null` (i.e. Use the default [Formatter])
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun format(formatter: Formatter?): Builder = apply { _formatter = formatter ?: DEFAULT_FORMATTER }

        /**
         * DEFAULT: `true` (i.e. Omit year from the time passed to [Formatter.format])
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun formatOmitYear(omit: Boolean): Builder = apply { _formatterOmitYear = omit }

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
         * DEFAULT: `true`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun warn(enable: Boolean): Builder = apply { _warn = enable }

        /**
         * TODO
         *
         * @return The [FileLog] to [Log.Root.install]
         *
         * @throws [IOException] If [File.canonicalFile2] fails to resolve [logDirectory].
         * */
        @Throws(Exception::class)
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

            val min = _min
            val minWaitOn = _minWaitOn.coerceAtLeast(min)
            val bufferOverflowDropOldest = if (minWaitOn == min) false else _bufferOverflowDropOldest
            val bufferCapacity = if (minWaitOn == min) {
                // If minWaitOn is configured such that FileLog.log blocks for all
                // Log.Level, then LogBuffer's Channel capacity has an inherent
                // constraint of n threads. To mitigate unnecessary work and object
                // creation, we want LogBuffer's Channel to always succeed on trySend
                // (if not closed).
                Channel.UNLIMITED
            } else {
                // If DROP_OLDEST is configured we cannot use Channel.RENDEZVOUS as
                // the minimum. Otherwise, with Channel.trySend always succeeding,
                // logs will almost always be dropped.
                val minimum = if (bufferOverflowDropOldest) 256 else Channel.RENDEZVOUS
                _bufferCapacity.coerceAtLeast(minimum)
            }

            return FileLog(
                min = min,
                max = _max,
                directory = directory,
                files = files.toImmutableList(),
                files0Hash = files0Hash,
                maxLogFileSize = _maxLogFileSize.coerceAtLeast(50L * 1024L), // 50kb
                modeDirectory = _modeDirectory.build(),
                modeFile = _modeFile.build(),
                bufferCapacity = bufferCapacity,
                bufferOverflowDropOldest = bufferOverflowDropOldest,
                minWaitOn = minWaitOn,
                yieldOn = _yieldOn.coerceIn(1, 10),
                formatter = _formatter,
                formatterOmitYear = _formatterOmitYear,
                blacklistDomain = blacklistDomain,
                whitelistDomain = whitelistDomain,
                whitelistDomainNull = if (whitelistDomain.isEmpty()) true else _whitelistDomainNull,
                whitelistTag = whitelistTag,
                debug = _debug,
                warn = _warn,
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

    private val formatter: Formatter
    private val formatterOmitYear: Boolean

    private val _blacklistDomain: Array<String?>
    private val _whitelistDomain: Array<String>
    private val _whitelistTag: Array<String>

    private val LOG: Logger

    private val scopeFileLog: ScopeFileLog
    private val allocator: DispatcherAllocator

    private val checkIfLogRotationIsNeeded: LogAction.Rotation

    @Volatile
    private var _onInstallInvocations: Long = 0L
    @Volatile
    private var _logJob: Job? = null

    private val _logHandle = _atomicRef<Pair<LogBuffer, ScopeLogHandle>?>(initial = null)
    private val _pendingLogCount = _atomic(initial = 0L)

    private constructor(
        min: Level,
        max: Level,
        directory: File,
        files: List<File>,
        files0Hash: String,
        maxLogFileSize: Long,
        modeDirectory: String,
        modeFile: String,
        bufferCapacity: Int,
        bufferOverflowDropOldest: Boolean,
        minWaitOn: Level,
        yieldOn: Byte,
        formatter: Formatter,
        formatterOmitYear: Boolean,
        blacklistDomain: Set<String>,
        whitelistDomain: Set<String>,
        whitelistDomainNull: Boolean,
        whitelistTag: Set<String>,
        debug: Boolean,
        warn: Boolean,
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
            // TODO: Should this be exposed publicly??? If a log rotation is
            //  interrupted whereby files[0] was truncated, one may need the
            //  reference to obtain the latest logs...
            this.dotRotateFile = directory.resolve("$dotName0.next")
            this.dotRotateTmpFile = directory.resolve("$dotName0.tmp")
        }

        this.formatter = formatter
        this.formatterOmitYear = formatterOmitYear

        this._blacklistDomain = blacklistDomain.toTypedArray()
        this._whitelistDomain = whitelistDomain.toTypedArray()

        this._whitelistTag = whitelistTag.toTypedArray()

        this.debug = debug
        this.warn = warn
        this.LOG = Logger.of(tag = uidSuffix, DOMAIN)

        this.scopeFileLog = ScopeFileLog(uidSuffix, handler = CoroutineExceptionHandler handler@ { context, t ->
            if (t is CancellationException) return@handler // Ignore...
            logE(t) { context }
        })
        this.allocator = object : DispatcherAllocator(LOG) {
            @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
            override fun doAllocation(): CloseableCoroutineDispatcher = newSingleThreadContext(name = LOG.tag)
            override fun debug(): Boolean = this@FileLog.debug
        }
        // For logLoop's RotateActionQueue
        this.checkIfLogRotationIsNeeded = LogAction.Rotation { _, _, sizeLog, processedWrites ->
            // We are being consumed and ignored.
            if (processedWrites < 0) return@Rotation 0L

            if (sizeLog >= maxLogFileSize) return@Rotation EXECUTE_ROTATE_LOGS

            // Moving dotRotateFile -> *.001 is the last move that gets
            // executed, so its existence indicates that a log rotation
            // was interrupted, either by process termination or error.
            if (dotRotateFile.exists2Robustly()) return@Rotation EXECUTE_ROTATE_LOGS

            logD { "$LOG_ROTATION not needed" }

            // Good to go; do nothing.
            0L
        }

        this.logDirectory = directory.path
        this.logFiles = files.map { it.path }.toImmutableList()
        this.logFiles0Hash = files0Hash
        this.maxLogFileSize = maxLogFileSize
        this.modeDirectory = modeDirectory
        this.modeFile = modeFile
        this.bufferCapacity = bufferCapacity
        this.bufferOverflowDropOldest = bufferOverflowDropOldest
        this.minWaitOn = minWaitOn
        this.yieldOn = yieldOn
        this.blacklistDomain = blacklistDomain
        this.whitelistDomain = whitelistDomain
        this.whitelistDomainNull = whitelistDomainNull
        this.whitelistTag = whitelistTag
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
        val (_, scope) = _logHandle._get() ?: return false
        return scope.supervisorJob.isActive
    }

    override fun log(level: Level, domain: String?, tag: String, msg: String?, t: Throwable?): Boolean {
        val (logBuffer, scope) = _logHandle._get() ?: return false
        if (!scope.supervisorJob.isActive) return false

        val tid = CurrentThread.id()

        // The formatted text to write, and its pre-calculated UTF-8 byte-size.
        val preprocessing: Deferred<Pair<CharSequence, Long>?> = run {
            val time = now(omitYear = formatterOmitYear)

            // LAZY start as to not do unnecessary work until we are
            // certain that the LogAction was committed to LogBuffer
            // successfully, which may be closed for sending.
            scope.async(start = CoroutineStart.LAZY) {
                val formatted = formatter.format(time, pid(), tid, level, domain, tag, msg, t)
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

        val logWait = if (level < minWaitOn) null else when (domain) {
            null -> LogWait(scope)
            // Do not block for FileLog or Log.Root logs. They should NEVER
            // be Level.Fatal, but it is checked for regardless, just in case
            // someone is attempting to bypass it.
            DOMAIN, ROOT_DOMAIN -> if (level == Level.Fatal) LogWait(scope) else null
            else -> LogWait(scope)
        }

        val logAction = object : LogAction.Write {

            @Volatile
            private var _retries = 0
            @Volatile
            private var _executed = 0

            override fun drop(undelivered: Boolean) {
                check(_executed++ == 0) { _executed--; "LogAction.Write has already been executed" }

                preprocessing.cancel()
                logWait?.fail()
                _pendingLogCount._decrement()
                if (undelivered) logW {
                    "A log awaiting processing was dropped. ${_pendingLogCount._get()} log(s) are currently pending."
                }
            }

            override suspend fun invoke(
                stream: FileStream.ReadWrite,
                buf: ByteArray,
                sizeLog: Long,
                processedWrites: Int,
            ): Long {
                check(_executed++ == 0) { _executed--; "LogAction.Write has already been executed" }

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

                    _pendingLogCount._decrement()
                    logWait?.fail()

                    // threw will only ever be non-null when result == null
                    threw?.let { throw it }

                    return 0L
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

                if (needsRotation && _retries++ < MAX_RETRIES) {
                    _executed--
                    return EXECUTE_ROTATE_LOGS
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

                _pendingLogCount._decrement()
                threw?.let { t ->
                    logWait?.fail()
                    throw t
                }

                logWait?.succeed()
                return written
            }
        }

        val trySendResult = logBuffer.channel.trySend(logAction)
        val logSend = if (trySendResult.isSuccess) null else {
            // Failure
            if (trySendResult.isClosed) {
                logWait?.fail()
                preprocessing.cancel()
                return false
            }
            if (logBuffer != _logHandle._get()?.first) {
                // Log.Root.uninstall was called between the time log was invoked and now
                logWait?.fail()
                preprocessing.cancel()
                return false
            }

            // This will only ever be the case when Builder.bufferCapacity was specified
            // and the LogBuffer.channel has reached capacity. The kotlinx.coroutines
            // Channel.trySendBlocking extension function cannot be used here because
            // it throws InterruptedException on Jvm/Android, and we must guarantee LogAction
            // is only ever enqueued ONCE; CurrentThread.uninterrupted gets us 95% of the
            // way, but there exists in that remaining 5% the possibility of another Thread
            // interrupting this one while the runBlocking lambda is executing.
            //
            // Additionally, there needs to exist a way to selectively block for the result
            // based on domain; this provides us that optionality.
            LogSend(scope, logBuffer, logAction)
        }

        _pendingLogCount._increment()
        preprocessing.start()

        // If logWait is non-null, we do not care about logSend result because it will either
        // succeed in sending LogAction, or fail due to channel closure in which case the
        // LogBuffer.channel.onUndeliveredElement callback will be invoked and LogAction
        // consumption ends up cancelling logWait for us.
        //
        // This skips an unnecessary runBlocking call and limits it to at MOST one, instead of
        // potentially two.
        if (logWait == null) return run {
            if (logSend == null) return@run true

            when (domain) {
                // Do not block for FileLog or Log.Root logs.
                DOMAIN, ROOT_DOMAIN -> {
                    logD { "Non-Blocking[domain=$domain] >> $logSend" }
                    return@run true
                }
            }

            logD { "Block[blocked=1, threadId=$tid] >> $logSend" }
            var result = LogSend.RESULT_UNKNOWN
            while (result == LogSend.RESULT_UNKNOWN) {
                try {
                    result = CurrentThread.uninterruptedRunBlocking(scope.dispatcher) {
                        // This will NOT throw CancellationException, as
                        // CoroutineStart.ATOMIC + withContext(NonCancellable)
                        // were used.
                        logSend.await()
                    }
                } catch (_: Throwable) {
                    // InterruptedException (Jvm/Android)
                }
            }
            logD { "Block[blocked=0, threadId=$tid] >> $logSend" }
            result == LogSend.RESULT_TRUE
        }

        while (logWait.isActive) {
            logD { "Block[blocked=1, threadId=$tid] >> $logWait" }
            try {
                CurrentThread.uninterruptedRunBlocking(scope.dispatcher) {
                    // If logSendResult was false (closed channel) then
                    // LogBuffer.channel's onUndeliveredElement will clean
                    // everything up for us and cancel logWait (eventually).
                    logWait.join()
                }
            } catch (_: Throwable) {
                // InterruptedException (Jvm/Android)
                // CancellationException (in which case logWait.isActive will be false)
            } finally {
                logD { "Block[blocked=0, threadId=$tid] >> $logWait" }
            }
        }

        return logWait.isSuccess()
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    override fun onInstall() {
        _onInstallInvocations++

        // Because runBlocking is being utilized by FileLog.log, we must always specify a
        // CoroutineDispatcher of our own. If we were to use Dispatchers.IO for everything,
        // then it could result in a deadlock if caller is also using Dispatchers.IO whereby
        // thread starvation could occur and LogLoop is unable to yield or launch LogRotation.
        val (dispatcher, dispatcherDeRef) = allocator.getOrAllocate()

        val logBuffer = LogBuffer(
            capacity = bufferCapacity,
            overflow = if (bufferOverflowDropOldest) BufferOverflow.DROP_OLDEST else BufferOverflow.SUSPEND,
        )
        val previousLogJob = _logJob

        scopeFileLog.launch(dispatcher, start = CoroutineStart.ATOMIC) {
            scopeLog {
                jobLog.invokeOnCompletion { logD { "$LOG_JOB Stopped >> $jobLog" } }
                logD { "$LOG_JOB Started >> $jobLog" }

                if (previousLogJob != null) {
                    logD {
                        if (!previousLogJob.isActive) null
                        else "Waiting for previous $LOG_JOB to complete >> $previousLogJob"
                    }
                    previousLogJob.join()
                }

                directory.mkdirs2(mode = modeDirectory, mustCreate = false)

                run {
                    val canonical = directory.canonicalFile2()
                    if (canonical != directory) {
                        // FAIL:
                        //   Between time of Builder.build and Log.Root.install, the specified
                        //   directory was modified to contain symbolic links such that the current
                        //   one points to a different location. This would invalidate the Log.uid,
                        //   allowing multiple FileLog instances to be installed simultaneously for
                        //   the same log file leading to data corruption.
                        throw IllegalStateException("Symbolic link hijacking detected >> [$canonical] != [$directory]")
                    }
                }

                try {
                    // Some systems, such as macOS, have highly unreliable fcntl byte-range file lock
                    // behavior when the target is a symbolic link. We want no part in that and require
                    // the lock file to be a regular file.
                    val canonical = dotLockFile.canonicalFile2()
                    if (canonical != dotLockFile) {
                        logW { "Symbolic link detected >> [$canonical] != [$dotLockFile]" }
                        dotLockFile.delete2(ignoreReadOnly = true)
                        delay(10.milliseconds)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    // ignore
                }

                jobLog.ensureActive()
                val lockFile = dotLockFile.openLockFileRobustly()
                val lockFileCompletion = jobLog.closeOnCompletion(lockFile)

                try {
                    val canonical = files[0].canonicalFile2()
                    if (canonical != files[0]) {
                        logW { "Symbolic link detected >> [$canonical] != [${files[0]}]" }
                        files[0].delete2(ignoreReadOnly = true)
                        delay(10.milliseconds)
                    }
                } catch (t: Throwable) {
                    if (t is CancellationException) throw t
                    // ignore
                }

                jobLog.ensureActive()
                val logStream = files[0].openLogFileRobustly(modeFile)
                val logStreamCompletion = jobLog.closeOnCompletion(logStream)

                logLoop(
                    logBuffer = logBuffer,
                    _lockFile = lockFile,
                    _lockFileCompletion = lockFileCompletion,
                    _logStream = logStream,
                    _logStreamCompletion = logStreamCompletion,
                )
            }
        }.let { logJob ->
            logJob.invokeOnCompletion { t ->
                logBuffer.channel.close()
                var count = 0L
                try {
                    while (true) {
                        val logAction = logBuffer.channel.tryReceive().getOrNull() ?: break
                        count++
                        logAction.drop(undelivered = false)
                    }
                } finally {
                    // Paranoia; if drop throws exception which it "shouldn't".
                    logBuffer.channel.cancel()
                }
                if (count > 0L) logW(t) { "$count log(s) awaiting processing were dropped." }
            }

            val logHandle = logBuffer to ScopeLogHandle(
                logJob,
                scopeFileLog,
                _onInstallInvocations,
                dispatcher,
                dispatcherDeRef,
            )

            _logJob = logJob
            _logHandle._set(new = logHandle)

            // In the event of an error experienced by logJob, if onUninstall has not been called
            // yet, dereferencing logHandle will inhibit all further logging even though Log.Root
            // still has this FileLog instance available.
            logJob.invokeOnCompletion { _logHandle._compareAndSet(expected = logHandle, new = null) }
        }

        log(Level.Info, LOG.domain, LOG.tag, "Log file opened at ${files[0].name}", t = null)
    }

    override fun onUninstall() {
        val (logBuffer, _) = _logHandle._getAndSet(new = null) ?: return
        // Close for send only. All remaining LogAction will be
        // processed by the LogLoop until exhaustion.
        logBuffer.channel.close(cause = null)
    }

    /*
    * Loops until LogBuffer is closed via onUninstall and is "drained" of all its
    * remaining LogAction.
    * */
    private suspend fun ScopeLog.logLoop(
        logBuffer: LogBuffer,
        _lockFile: LockFile,
        _lockFileCompletion: DisposableHandle,
        _logStream: FileStream.ReadWrite,
        _logStreamCompletion: DisposableHandle,
    ): Unit = scopeLogLoop {
        var lockFile = _lockFile
        var lockFileCompletion = _lockFileCompletion
        var logStream = _logStream
        var logStreamCompletion = _logStreamCompletion

        jobLogLoop.invokeOnCompletion { logD { "$LOG_LOOP Stopped >> $jobLogLoop" } }

        // Utilized for log rotation things. These LogAction contain no actual
        // write functionality, but are to be woven into the loop as "priority
        // 1" LogAction for scheduling retries and coordinating lockFile/logStream
        // closure/re-open behavior.
        //
        // Channel(Channel.UNLIMITED) is used under the hood instead of ArrayDequeue
        // to protect against potential ConcurrentModificationException. If a
        // LogAction needs to be enqueued from the completion handle of the child
        // job created by executeLogRotationMoves due to lockRotate.release failure,
        // there are no guarantees on the completion handler's execution context, so.
        val rotateActionQueue = RotateActionQueue(scope = this)

        // By queueing up an action for immediate execution, the loop will be able
        // to check for an interrupted log rotation before any writes to logStream
        // occur. If the program previously terminated in the midst of moving files,
        // that needs to be finished off firstly.
        rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)

        // If a write operation for a log entry would end up causing the log file to
        // exceed the configured maxLogFileSize, it is cached here and retried after
        // a log rotation is performed. This allows us to not drop lockLog and perform
        // an immediate retry of the LogAction after returning from rotateLogs.
        val retryAction = _atomicRef<LogAction.Write?>(initial = null)
        jobLogLoop.invokeOnCompletion {
            // This should never really be the case because the main loop would have
            // dequeued this for processing over ever calling logBuffer.channel.receive.
            //
            // Only in the event of an error within the loop (such as file re-open
            // failure) where a LogAction from rotateActionQueue was dequeued over one
            // from retryAction would there be unprocessed LogAction present.
            retryAction._getAndSet(new = null)?.drop(undelivered = true)
        }

        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        jobLogLoop.invokeOnCompletion { buf.fill(0) }

        // Migrate completion handles to ScopeLogLoop (take ownership over them)
        lockFileCompletion.dispose()
        lockFileCompletion = jobLogLoop.closeOnCompletion(lockFile, logOpen = false)
        logStreamCompletion.dispose()
        logStreamCompletion = jobLogLoop.closeOnCompletion(logStream, logOpen = false)

        logD { "$LOG_LOOP Started >> $jobLogLoop" }

        var lockLog: FileLock = InvalidFileLock

        // The main loop
        while (true) {
            yield()

            // Will only be set to null via the inner loop. Once available, it MUST be
            // processed or LogAction.drop called on it.
            var logAction: LogAction? = try {
                // Priority 1 LogAction to process immediately.
                rotateActionQueue.dequeueOrNull()

                    // Priority 2 LogAction that came from FileLog.log and was cached
                    // in order to perform a log rotation to make room for its write.
                    ?: retryAction._getAndSet(new = null)

                    // Lastly, LogAction sent from FileLog.log
                    ?: logBuffer.channel.receive()
            } catch (_: ClosedReceiveChannelException) {
                // FileLog was uninstalled via Log.Root.uninstall, and there are no
                // remaining LogAction within the LogBuffer to process.
                break
            }

            //
            // CRITICAL SECTION BELOW. Any exceptions that are to be thrown before
            // making it to the inner loop MUST consume logAction.
            //

            // Obtain a FileLock for writing to logStream
            //
            // Will be valid if and only if it was not released due to retryAction
            // containing a cached LogAction.
            lockLog = if (lockLog.isValid()) lockLog else CurrentThread.uninterrupted {
                try {
                    // TODO: Blocking timeout monitor
                    lockFile.lockLog()
                } catch (t: Throwable) {
                    // Could be an OverlappingFileLockException on Jvm/Android. Someone
                    // within this process may be holding a lock on our requested range
                    // (maliciously?). By closing and re-opening the file, this should
                    // invalidate all other locks held by this process (which we should
                    // be the ONLY ones acquiring).
                    //
                    // Alternatively, lockFile was previously closed due to a release
                    // failure requiring a re-open.

                    // If it was closed, the rotation job's lock is already invalid, so
                    // no reason to wait for it.
                    if (t !is ClosedException) {
                        try {
                            // If a log rotation is currently underway, we must wait for it
                            // so that we do not invalidate its lockRotate inadvertently.
                            awaitLogRotation()
                        } catch (t: CancellationException) {
                            logAction.drop(undelivered = true)
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
                    } catch (eee: IOException) {
                        t.addSuppressed(eee)
                        // Not a show-stopper. Ignore.
                    }

                    try {
                        logD(ee) { "Closed >> $lockFile" }
                        jobLogLoop.ensureActive()
                        lockFile = dotLockFile.openLockFileRobustly()
                        lockFileCompletion = jobLogLoop.closeOnCompletion(lockFile)
                        // TODO: Blocking timeout monitor
                        lockFile.lockLog()
                    } catch (tt: Throwable) {
                        // Total failure. Close up shop.
                        logAction.drop(undelivered = true)
                        if (tt is CancellationException) throw tt
                        t.addSuppressed(tt)
                        throw t
                    }
                }.also { lock ->
                    logD { "Acquired lock on ${dotLockFile.name} >> $lock" }
                }
            }

            // Obtain size and set logStream.position to it.
            var size: Long
            CurrentThread.uninterrupted {
                try {
                    size = logStream.size()
                    logStream.position(new = size)
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
                    } catch (eee: IOException) {
                        e.addSuppressed(eee)
                        // Not a show-stopper. Ignore.
                    }

                    try {
                        logD(ee) { "Closed >> $logStream" }
                        jobLogLoop.ensureActive()
                        logStream = files[0].openLogFileRobustly(modeFile)
                        logStreamCompletion = jobLogLoop.closeOnCompletion(logStream)
                        size = logStream.size()
                        logStream.position(new = size)
                    } catch (t: Throwable) {
                        // Total failure. Close up shop.
                        logAction.drop(undelivered = true)
                        if (t is CancellationException) throw t
                        e.addSuppressed(t)
                        throw e
                    }
                }
            }

            logD {
                val count = _pendingLogCount._get()
                "Current ${files[0].name} file byte size is $size, with $count log(s) pending"
            }

            var processedWrites = 0
            CurrentThread.uninterrupted {

                // The inner loop
                while (true) {
                    val action = logAction ?: break

                    val written = try {
                        action.invoke(logStream, buf, size, processedWrites)
                    } catch (t: Throwable) {
                        if (t is CancellationException) {
                            // preprocessing.await() threw. We're about to die. Nothing was written,
                            // so no need to do anything here. Alternatively, Formatter.format threw
                            // a CancellationException attempting to F with the loop. In either case,
                            // IGNORE; Will be handled in a moment when yield hits.
                        } else {
                            logE(t) { "Failed to write log entry to ${files[0].name}" }
                            // TODO:
                            //  - Check for InterruptedIOException.bytesTransferred???
                            //  - Truncate to size to wipe out any partially written logs???
                            //  - Increment processed to ensure a sync occurs???
                            //  - Handle these errors in FileLog.log created LogAction???
                        }
                        0L
                    }

                    if (written > 0L) {
                        processedWrites++
                        size += written
                        logD { "Wrote $written bytes to ${files[0].name}" }
                    } else {
                        // Check "special" negative return value
                        if (written == EXECUTE_ROTATE_LOGS) {
                            size = maxLogFileSize // Force a log rotation

                            if (action is LogAction.Write) {
                                val previous = retryAction._getAndSet(new = action)
                                if (previous != null) {
                                    previous.drop(undelivered = true)
                                    // HARD fail.... There should ONLY ever be 1 retryAction.
                                    throw IllegalStateException("retryAction's previous value was non-null")
                                }

                                logD { "Write would exceed maxLogFileSize[$maxLogFileSize]. Retrying after $LOG_ROTATION." }
                            }
                        }
                    }

                    // Rip through some more LogAction (if able) while we hold a lock.
                    logAction = when {
                        // We lost our lock
                        !lockLog.isValid() -> null
                        // We lost our logStream
                        !logStream.isOpen() -> null
                        // Log rotation is needed
                        size >= maxLogFileSize -> null
                        // Yield to another process (potentially)
                        processedWrites >= yieldOn -> null
                        // Job cancellation
                        !jobLogLoop.isActive -> null

                        // Dequeue next LogAction (if available)
                        else -> try {
                            // Share the thread.
                            yield()

                            rotateActionQueue.dequeueOrNull()
                                ?: retryAction._getAndSet(new = null)
                                ?: logBuffer.channel.tryReceive().getOrNull()
                        } catch (_: CancellationException) {
                            // Shouldn't happen b/c just checked isActive, but if so
                            // we want to ensure we pop out for logStream.sync. The
                            // main loop will re-throw the exception when it yields.
                            null
                        }
                    }
                }
            }

            if (processedWrites > 0) {
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
                        logW(e) { "Sync failure >> $logStream" }
                    }
                }

                logD { "Processed $processedWrites log(s)" }
            }

            if (jobLogLoop.isActive && size >= maxLogFileSize) {
                if (lockLog.isValid()) {
                    CurrentThread.uninterrupted {
                        rotateLogs(
                            rotateActionQueue = rotateActionQueue,
                            logStream = logStream,
                            lockFile = lockFile,
                            buf = buf,
                            retryActionIsNotNull = retryAction._get() != null,
                        )
                    }
                } else {
                    // We lost lockLog. Trigger an immediate retry.
                    rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)
                }
            }

            // Do not release lockLog if there is a retry LogAction. It will be
            // immediately dequeued and executed while still holding our lock
            // on writes to logStream (unless there are LogAction present in the
            // rotateActionQueue which come first).
            if (retryAction._get() == null && lockLog.isValid()) try {
                lockLog.release()
                logD { "Released lock on ${dotLockFile.name} >> $lockLog" }
            } catch (e: IOException) {
                // If a log rotation is currently underway, we must wait for it
                // so that we do not invalidate its lockRotate inadvertently.
                awaitLogRotation()

                try {
                    // Close the lock file (if not already). Next logLoop iteration
                    // will fail to obtain its lock due to a ClosedException and then
                    // re-open lockFile to retry acquisition.
                    lockFile.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }
                logW(e) { "Lock release failure >> $lockLog" }
            }
        }
    }

    /*
    * Performs a log file rotation. If dotRotateFile exists on the filesystem, then
    * the previously interrupted log rotation will be finished off. Otherwise, this
    * function atomically copies logStream to a tmp file (dotRotateFile), truncates
    * logStream, then performs a full log file rotation. The actual file moves that
    * happen are executed in a child coroutine of ScopeLogLoop, allowing for a prompt
    * return to processing LogAction while it completes.
    *
    * NOTE: lockLog is required to be held when calling this function.
    * */
    @Throws(CancellationException::class, DirectoryNotEmptyException::class)
    private suspend fun ScopeLogLoop.rotateLogs(
        rotateActionQueue: RotateActionQueue,
        logStream: FileStream.ReadWrite,
        lockFile: LockFile,
        buf: ByteArray,
        retryActionIsNotNull: Boolean,
    ) {
        // If a previous log rotation is currently underway, we must
        // wait for it to complete before doing another one.
        //
        // This "could" happen if, for example, a rotation pops
        // off and resumes log writing where the next writes to
        // logStream are so large that it triggers another rotation
        // before the initial one finishes moving files. In that
        // event, this will suspend (and in doing so inhibit further
        // writes to logStream because we currently hold lockLog).
        awaitLogRotation()

        // At this point, the rotateActionQueue:
        //  - Is empty.
        //  - Contains a lockFile.close action due to lockRotate.release
        //    failure in a previous rotation job's completion handle.
        //      - If that lockFile is the same as this function's
        //        lockFile (it should be at this point) and is closed
        //        here and now when the action is consumed, then we will
        //        fail to acquire lockRotate below and schedule a retry.
        //  - Contains a rotation retry action scheduled by a previous
        //    rotation job due to a log file move error (or something).
        //      - If that is the case, we're doing a rotation now.
        //
        // Either way, the queue should be exhausted here and contain
        // NO LogAction before continuing.
        while (true) {
            val action = rotateActionQueue.dequeueOrNull() ?: break
            action(logStream, buf, 0L, processedWrites = -1)
        }

        val lockRotate = try {
            // TODO: Blocking timeout monitor
            lockFile.lockRotate()
        } catch (t: Throwable) {
            // Could be an OverlappingFileLockException on Jvm/Android. Someone
            // within this process may be holding a lock on our requested range
            // (maliciously?). By closing and re-opening the file, this should
            // invalidate all other locks held by this process (which we should
            // be the ONLY ones acquiring).
            //
            // Alternatively, lockFile was previously closed due to a release
            // failure and requires a re-open.

            try {
                // Close the lock file (if not already). Next logLoop iteration
                // will fail to obtain its lock due to a ClosedException and then
                // re-open lockFile to retry acquisition.
                lockFile.close()
            } catch (e: IOException) {
                t.addSuppressed(e)
            }
            logW(t) { "Failed to acquire a rotation lock on ${dotLockFile.name}. Retrying $LOG_ROTATION." }

            // Trigger an immediate retry.
            rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)

            return
        }

        logD { "Acquired lock on ${dotLockFile.name} >> $lockRotate" }

        // There exists a potential here that another process was mid-rotation
        // when this process' LogJob was started, whereby the dotRotateFile
        // existed and had not been moved into place yet, triggering a
        // call to rotateLogs in this process.
        //
        // Now that we hold both lockLog and lockRotate, we need to determine
        // what action, if any, to take.

        // Builder.maxLogFiles accepts a Byte, so at most there will be 127
        // moves to execute (Byte.MAX_VALUE).
        val moves = ArrayDeque<Pair<File, File>>(files.size)

        if (dotRotateFile.exists2Robustly()) {
            // Picking up a previously interrupted log file rotation (moving
            // dotRotateFile -> *.001 is the last move that gets executed).
            prepareLogRotationInterrupted(logStream, buf, moves)

            // If was successful (moves is populated), logStream may not have
            // been truncated and may still need to have another full log rotation
            // done. The current state could also be that there is a LogAction
            // present still in the retryAction. In any event, the LogAction
            // produced by FileLog.log() will return EXECUTE_ROTATE_LOGS_AND_RETRY
            // again where we'll go through another log rotation. Next time,
            // however, dotRotateFile should have already been moved into place
            // and not exist on the filesystem anymore.
        } else {
            // Full log rotation. Check if it's actually necessary.

            // If a LogAction returned EXECUTE_ROTATE_LOGS_AND_RETRY, it wants a
            // log rotation so it can fit its log in there; fake it till we make it.
            val size = if (retryActionIsNotNull) maxLogFileSize else try {
                logStream.size()
            } catch (e: IOException) {
                // Since we're not picking up an interrupted rotation, closing
                // the stream on failure will trigger a re-open on next logLoop
                // iteration and, if size >= maxLogFileSize, will end up retrying
                // the log rotation.
                try {
                    logStream.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                logW(e) { "Failed to obtain size of ${files[0].name}. Retrying $LOG_ROTATION." }

                // The retryAction is currently null, so trigger an immediate
                // retry with a freshly opened logStream. If another process is also
                // logging, it may finish off the log rotation for us, so.
                rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)

                // Release lockRotate below and return early.
                0L
            }

            if (size < maxLogFileSize) {
                if (lockRotate.isValid()) try {
                    lockRotate.release()
                    logD { "Released lock on ${dotLockFile.name} >> $lockRotate" }
                } catch (e: IOException) {
                    try {
                        // Close the lock file (if not already). Next logLoop iteration
                        // will fail to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (ee: IOException) {
                        e.addSuppressed(ee)
                    }

                    logW(e) { "Lock release failure >> $lockRotate" }
                }

                // No further action is needed. Return early.
                return
            }

            prepareLogRotationFull(logStream, buf, moves)
        }

        if (moves.isEmpty()) {
            // An error occurred in prepareLogRotation{Full/Interrupted}

            if (lockRotate.isValid()) try {
                lockRotate.release()
                logD { "Released lock on ${dotLockFile.name} >> $lockRotate" }
            } catch (e: IOException) {
                try {
                    // Close the lock file (if not already). Next logLoop iteration
                    // will fail to obtain its lock due to a ClosedException and then
                    // re-open lockFile to retry acquisition.
                    lockFile.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                logW(e) { "Lock release failure >> $lockRotate" }
            }

            // Trigger an immediate retry. If another process is also
            // logging, it may finish off the log rotation for us, so.
            rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)

            return
        }

        executeLogRotationMoves(rotateActionQueue, moves).invokeOnCompletion {
            if (lockRotate.isValid()) try {
                lockRotate.release()
                logD { "Released lock on ${dotLockFile.name} >> $lockRotate" }
            } catch (e: IOException) {
                logW(e) { "Lock release failure >> $lockRotate" }

                // No other recovery mechanism but to close the lock file
                // and invalidate all locks currently held, otherwise the
                // next log rotation may deadlock when attempting to acquire
                // lockRotate.
                rotateActionQueue.enqueue { stream, _, _, processedWrites ->
                    // This is done lazily here as a priority LogAction in order
                    // to not inadvertently invalidate a lockLog in the midst
                    // of executing a LogAction that is writing to logStream. If
                    // the lockFile is already closed, it does nothing.

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
                    if (processedWrites > 0 && stream.isOpen()) {
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
                            logW(e) { "Sync failure >> $stream" }
                        }
                    }

                    try {
                        // Close the lock file (if not already). Next logLoop iteration
                        // will fail to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (_: Throwable) {}

                    0L
                }
            }
        }
    }

    /*
    * Does a full log rotation. Assumes dotRotateFile has been checked for
    * existence and DOES NOT exist on the filesystem. If it does, it will
    * be deleted or moved (if non-empty directory) to a randomly named file.
    *
    * NOTE: Both lockLog and lockRotate are required to be held when calling
    * this function.
    *
    * If moves is left unpopulated after returning, an error has occurred and
    * an immediate retry should be scheduled.
    * */
    @Throws(DirectoryNotEmptyException::class)
    private fun prepareLogRotationFull(
        logStream: FileStream.ReadWrite,
        buf: ByteArray,
        moves: ArrayDeque<Pair<File, File>>,
    ) {
        try {
            directory.mkdirs2(mode = modeDirectory, mustCreate = false)
        } catch (_: IOException) {
            // Not a show-stopper. Ignore.
        }

        // These files should NOT exist, but need to check and expunge to avoid complications going forward.
        arrayOf(dotRotateTmpFile, dotRotateFile).forEach { file ->

            // If ends up throwing a DirectoryNotEmptyException because it failed to move the thing, we want
            // to let it through and error out here.
            val moved = file.deleteOrMoveToRandomIfNonEmptyDirectory(
                buf = buf,

                // Will be at LEAST 7 characters in length ('.' prefix + ".lock" + minimum 1 character name).
                // This is to ensure new randomly generated name does not exceed it and risk an ENAMETOOLONG
                // error.
                maxNewNameLen = dotLockFile.name.length,
            ) ?: return@forEach

            logW { "Moved non-empty directory (which should NOT be there) ${file.name} >> ${moved.name}" }
        }

        try {
            // Shouldn't exist, but just in case using openWrite instead of
            // openLogFileRobustly to ensure it gets truncated if it does.
            dotRotateTmpFile.openWrite(excl = OpenExcl.MaybeCreate.of(modeFile)).use { tmpStream ->
                var position = 0L
                while (true) {
                    val read = logStream.read(buf, 0, buf.size, position)
                    if (read == -1) break
                    position += read
                    tmpStream.write(buf, 0, read)
                }
                // Ensure everything is synced to disk before closing.
                tmpStream.sync(meta = true)
            }

            // Move into its final location.
            dotRotateTmpFile.moveLogTo(dotRotateFile)

            // TODO: fsync directory???

            logD { "Atomically copied ${files[0].name} >> ${dotRotateFile.name}" }
        } catch (e: IOException) {
            // Something awful happened. Close up shop and retry.
            try {
                logStream.close()
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }

            // Delete it (if it exists) before releasing file locks.
            try {
                dotRotateTmpFile.delete2(ignoreReadOnly = true, mustExist = false)
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }

            logW(e) { "Failed to atomically copy ${files[0].name} >> ${dotRotateFile.name}. Retrying $LOG_ROTATION." }
            return
        }

        truncate0AndSync(logStream, file = files[0])

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
    }

    /*
    * Completes a previously interrupted log rotation. Assumes dotRotateFile
    * has been checked for existence and DOES exist on the filesystem.
    *
    * NOTE: Both lockLog and lockRotate are required to be held when calling
    * this function.
    *
    * If moves is left unpopulated after returning, an error has occurred and
    * an immediate retry should be scheduled.
    * */
    @Suppress("UnnecessaryVariable")
    private fun prepareLogRotationInterrupted(
        logStream: FileStream.ReadWrite,
        buf: ByteArray,
        moves: ArrayDeque<Pair<File, File>>,
    ) {
        logD { "Interrupted $LOG_ROTATION detected >> ${dotRotateFile.name} exists" }

        var iNoExist = -1
        var source = dotRotateFile
        for (i in 1 until files.size) {
            val dest = files[i]
            moves.addFirst(source to dest)
            source = dest

            if (!dest.exists2Robustly()) {
                iNoExist = i
                break
            }
        }

        if (iNoExist != -1) run {
            // Confirm there is a hole by checking the next element

            val next = files.elementAtOrNull(iNoExist + 1)
                // File at iNoExist was the final file in the rotation and
                // there is no next file to check existence of. There has
                // either NOT been a full log file rotation cycle yet, an
                // update included a change to Builder.maxLogFiles, or
                // someone deleted the last log file.
                //
                // Check if logStream needs truncation.
                ?: return@run

            if (!next.exists2Robustly()) {
                // Next file also does not exist. There has either NOT
                // been a full log file rotation cycle yet, an update
                // included a change to Builder.maxLogFiles, or someone
                // deleted things.
                //
                // Check if logStream needs truncation.
                return@run
            }

            logD { "Hole in archived logs confirmed at ${files[iNoExist].name}. Finishing up $LOG_ROTATION." }

            // Skip checking of logStream and return early (moves is already
            // populated). This assumes that we were previously interrupted
            // during the moving of files, AFTER logStream truncation had been
            // committed to disk.
            return
        }

        // We could not confirm above that we were previously interrupted in
        // the file moves step of the log rotation process which executes AFTER
        // the logStream truncation step. All we can confirm at this point is
        // that logStream was atomically copied to dotRotateFile. As such, we
        // need to check if it is also necessary to perform the logStream
        // truncation step of the log rotation process.
        logD { "Hole in archived logs was not found. Checking if ${files[0].name} needs to be truncated to 0." }

        val sizeLog = try {
            logStream.size()
        } catch (e: IOException) {
            try {
                logStream.close()
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }

            logW(e) { "Failed to obtain size of ${files[0].name}. Retrying $LOG_ROTATION." }

            // Will signal an error in calling function (i.e. rotateLogs) which will
            // release lockRotate and enqueue an immediate retry with a newly opened
            // logStream.
            moves.clear()
            return
        }

        if (sizeLog <= 0L) {
            logD { "Truncation of ${files[0].name} detected. Finishing up $LOG_ROTATION." }
            return
        }

        // Shouldn't be the case because of maxLogFileSize min, but just in case leave
        // it alone if it's too small.
        //
        // buf.size == DEFAULT_BUFFER_SIZE == (8 * 1024)
        (buf.size * 2).let { minimum ->
            if (sizeLog >= minimum) return@let

            logD {
                "Skipping truncation of ${files[0].name} to 0 >> " +
                "${files[0].name} size[$sizeLog] < minimum[$minimum]"
            }
            return
        }

        var dotStream: FileStream.Read? = null
        var threw: Throwable? = null
        try {
            dotStream = dotRotateFile.openRead()
            logD { "Opened >> $dotStream" }

            val sizeDot = dotStream.size()
            if (sizeLog != sizeDot) {
                // File sizes do not match. Leave it alone.
                logD {
                    "Skipping truncation of ${files[0].name} to 0 >> " +
                    "${files[0].name} size[$sizeLog] != ${dotRotateFile.name} size[$sizeDot]"
                }
                return
            }

            val bufLog = buf
            bufLog.fill(0)
            val bufDot = bufLog.copyOf()

            // Compare first and last 8192 bytes of each file. Because we're dealing with
            // a file that contains timestamp + pid + tid, we should know pretty early
            // on if they are the same or not.
            //
            // NOTE: If adding/removing positions to the array, update logD below!
            val readSizes = arrayOf(0L, sizeDot - bufLog.size).map { pos ->
                val readLog = logStream.read(bufLog, position = pos)
                val readDot = dotStream.read(bufDot, position = pos)

                if (readLog != readDot) {
                    logD {
                        "Skipping truncation of ${files[0].name} to 0 >> " +
                        "${files[0].name} read[$readLog] != ${dotRotateFile.name} " +
                        "read[$readDot] at position[$pos]"
                    }
                    return
                }

                // Negative, or not enough bytes read to make a comparison
                // valuable enough (that I would be safe with) to determine
                // the need for truncation.
                (bufLog.size / 4).let { minimum ->
                    if (readLog >= minimum) return@let

                    logD {
                        "Skipping truncation of ${files[0].name} to 0 >> " +
                        "read[$readLog] < minimum[$minimum] at position[$pos]"
                    }
                    return
                }

                // Byte for byte comparison
                for (i in 0 until readLog) {
                    if (bufLog[i] == bufDot[i]) continue

                    logD {
                        "Skipping truncation of ${files[0].name} to 0 >> " +
                        "${files[0].name} byte[${bufLog[i]}] != ${dotRotateFile.name} " +
                        "byte[${bufDot[i]}] at position[${pos + i}]"
                    }
                    return
                }

                bufLog.fill(0)
                bufDot.fill(0)

                readLog
            }

            logD {
                "Truncation of ${files[0].name} to 0 needed. " +
                "First ${readSizes[0]} and last ${readSizes[1]} bytes " +
                "of ${files[0].name} matched ${dotRotateFile.name}, and " +
                "both have a size of $sizeLog bytes."
            }

            truncate0AndSync(logStream, file = files[0])
        } catch (e: IOException) {
            threw = e
        } finally {
            try {
                // FileStream.close is called here instead of using
                // Closeable.use above to ensure errors related to
                // FileStream.close do not create false positives.
                dotStream?.close()
            } catch (e: IOException) {
                // Only add as suppressed. Ignore otherwise.
                threw?.addSuppressed(e)
            } finally {
                logD { dotStream?.let { "Closed >> $it" } }
            }
        }

        if (threw == null) return

        logW(threw) { "Failed to compare ${files[0].name} with ${dotRotateFile.name}. Retrying $LOG_ROTATION." }

        // Will signal an error in calling function (i.e. rotateLogs) which will
        // release lockRotate and enqueue an immediate retry with a newly opened
        // logStream.
        moves.clear()
    }

    /*
    * Truncates the stream to 0L and syncs it to disk. Failure results in closure
    * whereby file is then used to attempt openWrite (O_TRUNC) + sync + close. Failure
    * beyond that is ignored (currently).
    *
    * NOTE: lockLog is required to be held when calling this function (if the stream and
    * file are for files[0]).
    * */
    private fun truncate0AndSync(logStream: FileStream.ReadWrite, file: File) {
        try {
            logStream.size(new = 0L)

            logD { "Syncing ${file.name}" }
            try {
                logStream.sync(meta = true)
            } catch (e: IOException) {
                logW(e) { "Sync failure >> $this" }
                // Will be closed in catch block below which will
                // hopefully force it to the filesystem, in addition
                // to trying an alternative truncation method.
                throw e
            }

            logD { "Truncated ${file.name} to 0" }
        } catch (e: IOException) {
            try {
                logStream.close()
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }

            // Try a different way.
            var s: FileStream.Write? = null
            try {
                // O_TRUNC
                s = file.openWrite(excl = OpenExcl.MustExist)
                logD { "Opened >> $s" }

                logD { "Syncing ${file.name}" }
                try {
                    s.sync(meta = true)
                } catch (ee: IOException) {
                    // Truncation succeeded, but our sync did not. Just
                    // go with it at this point and hope for the best.
                    // The finally block will close this FileStream.Write,
                    // so hopefully that forces it to the filesystem.
                    logW(ee) { "Sync failure >> $s" }
                }

                logD { "Truncated ${file.name} to 0" }
            } catch (ee: IOException) {
                // openWrite failed
                e.addSuppressed(ee)
            } finally {
                try {
                    // FileStream.close is called here instead of using
                    // Closeable.use above to ensure errors related to
                    // FileStream.close do not create false positives.
                    s?.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                } finally {
                    logD { s?.let { "Closed >> $it" } }
                }
            }

            if (s != null) return
            logE(e) { "Failed to truncate ${file.name} to 0" }
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

    /*
    * Launches a child coroutine which executes the populated moves in the
    * background while LogLoop returns to processing LogAction.
    *
    * NOTE: lockRotate is required to be held when calling this function.
    * Additionally, lockRotate.release MUST be attached to the returned Job
    * via completion handler.
    * */
    private fun ScopeLogLoop.executeLogRotationMoves(
        rotateActionQueue: RotateActionQueue,
        moves: ArrayDeque<Pair<File, File>>,
    ): Job = scopeLogLoop.launch(context = CoroutineName(LOG.tag + "-$LOG_ROTATION")) {
        val thisJob = currentCoroutineContext().job
        thisJob.invokeOnCompletion { logD { "$LOG_ROTATION Stopped >> $thisJob" } }
        logD { "$LOG_ROTATION Started >> $thisJob" }

//        val startSize = moves.size
        while (moves.isNotEmpty()) {
            val (source, dest) = moves.removeFirst()

            try {
                source.moveLogTo(dest)
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

                logW(e) { "$LOG_ROTATION failure. TODO..." }

                // Trigger an immediate retry. If another process is also
                // logging, it may finish off the log rotation for us, so.
                rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)
                break
            }
        }

//        if (moves.size != startSize) {
//            // TODO: fsync directory???
//        }
    }

    @Throws(CancellationException::class)
    private suspend fun ScopeLogLoop.awaitLogRotation() {
        jobLogLoop.children.forEach { child ->
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
    private inline fun logW(lazyMsg: () -> Any?): Int {
        contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
        return logW(t = null, lazyMsg)
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun logW(t: Throwable?, lazyMsg: () -> Any?): Int {
        contract { callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE) }
        if (!warn) return 0
        return LOG.w(t, lazyMsg)
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

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    private abstract class DispatcherAllocator(
        LOG: Logger?,
    ): SharedResourceAllocator<CoroutineDispatcher>(
        LOG,
        deallocationDelay = 250.milliseconds,
        deallocationDispatcher = Dispatchers.IO,
    ) {

        abstract override fun doAllocation(): CloseableCoroutineDispatcher
        final override fun CoroutineDispatcher.doDeallocation() { (this as CloseableCoroutineDispatcher).close() }

//        companion object Global {
//
//            private const val TAG_BLOCKING_MONITOR = "BlockingMonitor"
//            private const val TAG_SHARED_POOL = "SharedPool"
//
//            private const val NAME_BLOCKING_MONITOR = "FileLog.$TAG_BLOCKING_MONITOR"
//            private const val NAME_SHARED_POOL = "FileLog.$TAG_SHARED_POOL"
//
//            // TODO: Use within logLoop of all FileLog to monitor FileLock acquisitions
//            val BlockingMonitor = object : DispatcherAllocator(Logger.of(tag = TAG_BLOCKING_MONITOR, domain = DOMAIN)) {
//                override fun doAllocation(): CloseableCoroutineDispatcher {
//                    return newSingleThreadContext(name = NAME_BLOCKING_MONITOR)
//                }
//            }
//
//            // TODO: Add ability to configure via FileLog.Builder to use instead of a dedicated allocator
//            //  Need to think about how best to do this, if at all.
//            val SharedPool = object : DispatcherAllocator(Logger.of(tag = TAG_SHARED_POOL, domain = DOMAIN)) {
//                override fun doAllocation(): CloseableCoroutineDispatcher {
//                    // TODO: Make configurable via environment variables/properties?
//                    return newFixedThreadPoolContext(nThreads = 3, name = NAME_SHARED_POOL)
//                }
//            }
//        }
    }
}
