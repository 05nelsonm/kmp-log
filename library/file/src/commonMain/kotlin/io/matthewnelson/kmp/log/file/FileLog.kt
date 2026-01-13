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
@file:Suppress("LocalVariableName", "NOTHING_TO_INLINE", "PrivatePropertyName")

package io.matthewnelson.kmp.log.file

import io.matthewnelson.encoding.base16.Base16
import io.matthewnelson.encoding.core.Decoder.Companion.decodeBuffered
import io.matthewnelson.encoding.core.Decoder.Companion.decodeToByteArray
import io.matthewnelson.encoding.core.Encoder.Companion.encodeToString
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.ClosedException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.delete2
import io.matthewnelson.kmp.file.exists2
import io.matthewnelson.kmp.file.mkdirs2
import io.matthewnelson.kmp.file.name
import io.matthewnelson.kmp.file.path
import io.matthewnelson.kmp.file.resolve
import io.matthewnelson.kmp.file.toFile
import io.matthewnelson.kmp.log.Log
import io.matthewnelson.kmp.log.file.internal.CurrentThread
import io.matthewnelson.kmp.log.file.internal.LockFile
import io.matthewnelson.kmp.log.file.internal.LogBuffer
import io.matthewnelson.kmp.log.file.internal.LogWriteAction
import io.matthewnelson.kmp.log.file.internal.ModeBuilder
import io.matthewnelson.kmp.log.file.internal.isDesktop
import io.matthewnelson.kmp.log.file.internal.lockLog
import io.matthewnelson.kmp.log.file.internal.openLockFileRobustly
import io.matthewnelson.kmp.log.file.internal.openLogFileRobustly
import io.matthewnelson.kmp.log.file.internal.uninterrupted
import io.matthewnelson.kmp.log.file.internal.use
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kotlincrypto.hash.blake2.BLAKE2s
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration.Companion.milliseconds

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
         *  - [name] is `del` or `tmp`
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
            require(name != "del") { "fileExtension cannot be 'del'" }
            require(name != "tmp") { "fileExtension cannot be 'tmp'" }
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
         * */
        public fun build(): FileLog {
            val fileName = _fileName
            val fileExtension = _fileExtension
            val whitelistDomain = _whitelistDomain.toImmutableSet()
            val whitelistTag = _whitelistTag.toImmutableSet()
            val directory = logDirectory.toFile().canonicalFile2()

            // Current and 1 previous.
            val maxLogs = _maxLogs.coerceAtLeast(2)
            val files = ArrayList<File>(maxLogs.toInt())
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
                val digest = blake2s.digest(files[0].path.decodeToByteArray(UTF8))
                blake2s.update(digest)
                blake2s.digestInto(digest, destOffset = 0)
                digest.encodeToString(Base16)
            }

            return FileLog(
                min = _min,
                max = _max,
                directory = directory,
                files = files.toImmutableList(),
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

    // Exposed for testing
    @get:JvmSynthetic
    internal val files: List<File>
    @get:JvmSynthetic
    internal val dotLockFile: File
    @get:JvmSynthetic
    internal val dotRotateFile: File

    private val _whitelistDomain: Array<String>
    private val _whitelistTag: Array<String>

    private val LOG: Logger

    private val logScope: CoroutineScope

    @Volatile
    private var _logBuffer: LogBuffer? = null
    @Volatile
    private var _logJob: Job? = null

    private constructor(
        min: Level,
        max: Level,
        directory: File,
        files: List<File>,
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

        run {
            // If the directory is a NFS mounted drive, then files0Hash may not be the
            // same on another machine, so cannot use it in the name for these 2 files.
            // This is because the hash of the fully canonicalized path on this device
            // which may be different on another machine.
            var name0 = files[0].name
            if (!name0.startsWith('.')) name0 = ".$name0"
            name0
        }.let { dotName0 ->
            this.dotLockFile = directory.resolve("$dotName0.lock")
            this.dotRotateFile = directory.resolve("$dotName0.rotate")
        }

        this._whitelistDomain = whitelistDomain.toTypedArray()
        this._whitelistTag = whitelistTag.toTypedArray()
        this.LOG = Logger.of(tag = uidSuffix, DOMAIN)
        this.logScope = CoroutineScope(context =
            CoroutineName(uid)
            + Dispatchers.IO
            + SupervisorJob()
            + CoroutineExceptionHandler { context, t ->
                if (t is CancellationException) throw t
                if (LOG.e(t) { context } == 0) {
                    // No other Log are installed to log the error. Pipe to stderr.
                    t.printStackTrace()
                }
            }
        )

        this.logDirectory = directory.path
        this.logFiles = files.map { it.path }.toImmutableList()
        this.logFiles0Hash = files0Hash
        this.modeDirectory = modeDirectory
        this.modeFile = modeFile
        this.maxLogSize = maxLogSize
        this.whitelistDomain = whitelistDomain
        this.whitelistDomainNull = whitelistDomainNull
        this.whitelistTag = whitelistTag
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        // Do not log to self, only to other Logs (if installed)
        if (domain == LOG.domain && tag == LOG.tag) return false

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

        // TODO: time, thread id
        val preProcessing: Deferred<CharSequence?> = logScope.async {
            // TODO: Format
            if (msg.isNullOrEmpty()) return@async null
            msg + '\n'
        }

        val fatalJob = if (level == Level.Fatal) Job() else null

        val result = logBuffer.trySend { stream, buf ->
            try {
                // Can be null if there was an error to ensure this write
                // action still gets consumed and any completion job being
                // waited on is closed out properly.
                if (stream == null) {
                    preProcessing.cancel()
                    return@trySend 0L
                }

                val formatted = preProcessing.await()
                if (formatted.isNullOrEmpty()) return@trySend 0L

                val written = try {
                    formatted.decodeBuffered(
                        UTF8,
                        throwOnOverflow = false,
                        buf = buf,
                        action = stream::write,
                    )
                } finally {
                    if (fatalJob != null) {
                        // fsync no matter what before the process is aborted.
                        try {
                            stream.sync(meta = true)
                        } catch (_: Throwable) {}
                    }
                }

                fatalJob?.complete()
                written
            } finally {
                fatalJob?.cancel()
            }
        }

        if (result.isFailure) {
            // Channel is closed for sending
            fatalJob?.cancel()
            preProcessing.cancel()
            return false
        }

        preProcessing.start()
        if (fatalJob == null) return true

        // Block until the Level.Fatal log has been committed to disk
        try {
            CurrentThread.uninterrupted {
                runBlocking(Dispatchers.IO) { fatalJob.join() }
            }
        } catch (_: Throwable) {}

        return !fatalJob.isCancelled
    }

    override fun onInstall() {
        val logBuffer = LogBuffer()
        val logJob = _logJob
        logScope.launch {
            logBuffer.use(LOG) { buf ->
                val thisJob = currentCoroutineContext().job

                LOG.v { "LogJob Started >> $thisJob" }

                if (logJob != null) {
                    LOG.v {
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

                // TODO: deferred log statement (log opened)

                if (dotRotateFile.exists2()) {
                    // TODO: Complete potentially interrupted log rotation
                }

                // TODO: write deferred logs to the log file.

                loop(
                    buf,
                    lockFile,
                    lockFileCompletion,
                    logStream,
                    logStreamCompletion,
                )
            }
        }.let { job ->
            job.invokeOnCompletion { LOG.v { "LogJob Stopped >> $job" } }
            _logJob = job
        }
        _logBuffer = logBuffer
    }

    override fun onUninstall() {
        val logBuffer = _logBuffer
        _logBuffer = null
        logBuffer?.close(cause = null)
    }

    // Exposed for testing
    @JvmSynthetic
    internal suspend fun cancelAndJoinLogJob() { _logJob?.cancelAndJoin() }

    private suspend fun LogBuffer.loop(
        buf: ByteArray,
        lockFile: LockFile,
        lockFileCompletion: DisposableHandle,
        logStream: FileStream.ReadWrite,
        logStreamCompletion: DisposableHandle,
    ) {
        var _lockFile = lockFile
        var _lockFileCompletion = lockFileCompletion
        var _logStream = logStream
        var _logStreamCompletion = logStreamCompletion
        val thisJob = currentCoroutineContext().job

        while (true) {
            var writeAction: LogWriteAction? = try {
                receive()
            } catch (_: ClosedReceiveChannelException) {
                // FileLog was uninstalled and there
                // are no more actions buffered.
                break
            }

            CurrentThread.uninterrupted {

                val logLock = try {
                    _lockFile.lockLog()
                } catch (t: Throwable) {
                    try {
                        _lockFile.close()
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                    } finally {
                        _lockFileCompletion.dispose()
                    }

                    try {
                        thisJob.ensureActive()
                        _lockFile = dotLockFile.openLockFileRobustly()
                        _lockFileCompletion = thisJob.closeOnCompletion(_lockFile)
                        _lockFile.lockLog()
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                        // Total failure
                        writeAction?.invoke(null, buf)
                        throw t
                    }
                }

                var size: Long

                try {
                    size = _logStream.size()
                    _logStream.position(size)
                } catch (e: IOException) {
                    try {
                        _logStream.close()
                    } catch (t: Throwable) {
                        e.addSuppressed(t)
                    } finally {
                        _logStreamCompletion.dispose()
                    }
                    try {
                        thisJob.ensureActive()
                        _logStream = files[0].openLogFileRobustly(modeFile)
                        _logStreamCompletion = thisJob.closeOnCompletion(_logStream)
                        size = _logStream.size()
                        _logStream.position(size)
                    } catch (t: Throwable) {
                        e.addSuppressed(t)
                        // Total failure
                        writeAction?.invoke(null, buf)
                        throw e
                    }
                }

                var processed = 0
                while (true) {
                    val action = writeAction ?: break

                    size += try {
                        action(_logStream, buf)
                    } catch (t: Throwable) {
                        if (LOG.e(t) { "Failed to write log entry to File[${files[0]}]" } == 0) {
                            // No other Log are installed to log the error. Pipe to stderr.
                            t.printStackTrace()
                        }
                        0L
                    } finally {
                        processed++
                    }

                    thisJob.ensureActive()

                    if (!_logStream.isOpen()) {
                        // TODO: re-open logStream
                    }

                    // Rip through some more buffered actions while we hold a lock (if possible).
                    writeAction = when {
                        // Log rotation is needed
                        size >= maxLogSize -> null
                        // Yield to another process (potentially)
                        processed >= 10 -> null
                        else -> tryReceive().getOrNull()
                    }
                }

                if (size >= maxLogSize) {
                    // TODO: do rotation
                }

                try {
                    logLock.release()
                } catch (e: IOException) {
                    if (e !is ClosedException) {
                        LOG.e(e) { "FileLock.release() failed" }
                        // TODO: close and re-open lock file.
                        //  Need to be careful here though b/c
                        //  if a log rotation is underway we want
                        //  to wait for that to complete before
                        //  closing the lock file which would invalidate
                        //  the rotate log lock.
                    }
                }
            }
        }
    }
}

private inline fun Job.closeOnCompletion(closeable: Closeable): DisposableHandle {
    return invokeOnCompletion { t ->
        try {
            closeable.close()
        } catch (tt: Throwable) {
            t?.addSuppressed(tt)
        }
    }
}
