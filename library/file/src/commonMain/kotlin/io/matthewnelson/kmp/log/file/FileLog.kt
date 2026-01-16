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
@file:Suppress("DuplicatedCode", "LocalVariableName", "PrivatePropertyName")

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
import io.matthewnelson.kmp.log.file.internal.consumeAndIgnore
import io.matthewnelson.kmp.log.file.internal.exists2Robustly
import io.matthewnelson.kmp.log.file.internal.format
import io.matthewnelson.kmp.log.file.internal.id
import io.matthewnelson.kmp.log.file.internal.isDesktop
import io.matthewnelson.kmp.log.file.internal.lockLog
import io.matthewnelson.kmp.log.file.internal.lockRotate
import io.matthewnelson.kmp.log.file.internal.now
import io.matthewnelson.kmp.log.file.internal.openLockFileRobustly
import io.matthewnelson.kmp.log.file.internal.openLogFileRobustly
import io.matthewnelson.kmp.log.file.internal.pid
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
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
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
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic
import kotlin.sequences.forEach
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
    public val maxLogYield: Byte

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
    @get:JvmName("isActive")
    public val isActive: Boolean get() = _logJob?.isActive ?: false

    /**
     * TODO
     *
     * TODO: See uninstallAndBlock
     *
     * @throws [ClassCastException]
     * */
    public suspend fun uninstallAndJoin() {
        val instance = uninstallAndGet(uid) ?: return
        (instance as FileLog)._logJob?.join()
    }

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
        private var _maxLogYield: Byte = 10
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
         * DEFAULT: `10`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun maxLogYield(num: Byte): Builder = apply { _maxLogYield = num }

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
                maxLogYield = _maxLogYield.coerceAtLeast(1),
                whitelistDomain = whitelistDomain,
                whitelistDomainNull = if (whitelistDomain.isEmpty()) true else _whitelistDomainNull,
                whitelistTag = whitelistTag,
                uidSuffix = "FileLog-$files0Hash",
            )
        }
    }

    private companion object {
        private const val DOMAIN = "kmp-log:file"

        // Special return value of a LogWriteAction to trigger rotateLogs
        private const val EXECUTE_ROTATE_LOGS = -42L
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
        maxLogYield: Byte,
        whitelistDomain: Set<String>,
        whitelistDomainNull: Boolean,
        whitelistTag: Set<String>,
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
        // files for 2 different logs.
        ('.' + files[0].name).let { dotName0 ->

            // This will be the longest named file we are dealing with, as
            // Builder.maxLogs() accepts a Byte whose maximum is 127. files[1]
            // and beyond will only ever be (files[0].path.length + 4) characters
            // in length (path + '.' + 3 digits); here for dotLockFile we are
            // adding a '.' prefix + ".lock", so will be (files[0].path.length + 6)
            // characters in length.
            //
            // As this is the first file opened, any failure attributed to path
            // name length (i.e. ENAMETOOLONG) will rear its head early and not
            // be something to worry about subsequently for any other log files.
            this.dotLockFile = directory.resolve("$dotName0.lock")

            // .rot and .tmp extension lengths are less than .lock, so.
            this.dotRotateFile = directory.resolve("$dotName0.rot")
            this.dotRotateTmpFile = directory.resolve("$dotName0.tmp")
        }

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
        this.maxLogYield = maxLogYield
        this.whitelistDomain = whitelistDomain
        this.whitelistDomainNull = whitelistDomainNull
        this.whitelistTag = whitelistTag
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        // Do not log to self, only to other Log instances (if installed).
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

        val preProcessing: Deferred<CharSequence?> = run {
            val time = now()
            val tid = CurrentThread.id()
            logScope.async { format(time, pid(), tid, level, domain, tag, msg, t) }
        }

        val fatalJob = if (level == Level.Fatal) Job() else null

        val result = logBuffer.channel.trySend { stream, buf ->
            try {
                // Can be null if there was an error, ensuring that this
                // action still gets consumed and any fatalJob that may
                // be present is properly canceled.
                if (stream == null) {
                    preProcessing.cancel()
                    return@trySend 0L
                }

                val written = try {
                    val formatted = preProcessing.await()
                    if (formatted.isNullOrEmpty()) return@trySend 0L

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

                logLoop(
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
        log(Level.Info, LOG.domain, LOG.tag, "Log file opened.", t = null)
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

        thisJob.invokeOnCompletion { LOG.v { "LogLoop Stopped >> $thisJob" } }

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
        rotateActionQueue.channel.trySend { _, _ ->
            if (dotRotateFile.exists2Robustly()) EXECUTE_ROTATE_LOGS else 0L
        }

        // Migrate completion handles to this scope (take ownership over them)
        lockFileCompletion.dispose()
        lockFileCompletion = thisJob.closeOnCompletion(lockFile, logOpen = false)
        logStreamCompletion.dispose()
        logStreamCompletion = thisJob.closeOnCompletion(logStream, logOpen = false)

        LOG.v { "LogLoop Started >> $thisJob" }

        while (true) {
            var writeAction: LogWriteAction? = try {
                rotateActionQueue.channel.tryReceive().getOrNull() ?: channel.receive()
            } catch (_: ClosedReceiveChannelException) {
                // FileLog was uninstalled and there are
                // no remaining actions left to process.
                break
            }

            val lockLog = CurrentThread.uninterrupted {
                try {
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
                            thisJob.children.forEach { child -> child.join() }
                        } catch (t: CancellationException) {
                            writeAction?.consumeAndIgnore(buf)
                            throw t
                        }
                    }

                    val tt: Throwable? = try {
                        lockFile.close()
                        null
                    } catch (tt: Throwable) {
                        t.addSuppressed(tt)
                        tt
                    } finally {
                        lockFileCompletion.dispose()
                    }

                    try {
                        LOG.v(tt) { "Closed >> $lockFile" }
                        thisJob.ensureActive()
                        directory.mkdirs2(mode = modeDirectory, mustCreate = false)
                        lockFile = dotLockFile.openLockFileRobustly()
                        lockFileCompletion = thisJob.closeOnCompletion(lockFile)
                        lockFile.lockLog()
                    } catch (ttt: Throwable) {
                        // Total failure. Close up shop.
                        writeAction?.consumeAndIgnore(buf)
                        if (ttt is CancellationException) throw ttt
                        t.addSuppressed(ttt)
                        throw t
                    }
                }
            }

            var size: Long
            CurrentThread.uninterrupted {
                try {
                    size = logStream.size()
                    logStream.position(size)
                } catch (e: IOException) {
                    val t: Throwable? = try {
                        logStream.close()
                        null
                    } catch (t: Throwable) {
                        e.addSuppressed(t)
                        t
                    } finally {
                        logStreamCompletion.dispose()
                    }

                    try {
                        LOG.v(t) { "Closed >> $logStream" }
                        thisJob.ensureActive()
                        directory.mkdirs2(mode = modeDirectory, mustCreate = false)
                        logStream = files[0].openLogFileRobustly(modeFile)
                        logStreamCompletion = thisJob.closeOnCompletion(logStream)
                        size = logStream.size()
                        logStream.position(size)
                    } catch (tt: Throwable) {
                        // Total failure. Close up shop.
                        writeAction?.consumeAndIgnore(buf)
                        if (tt is CancellationException) throw tt
                        e.addSuppressed(tt)
                        throw e
                    }
                }
            }

            var processed = 0
            CurrentThread.uninterrupted {
                while (writeAction != null) {
                    val written = try {
                        @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
                        writeAction!!.invoke(logStream, buf)
                    } catch (t: Throwable) {
                        if (t !is CancellationException) {
                            if (LOG.e(t) { "Failed to write log entry to File[${files[0]}]" } == 0) {
                                // No other Log are installed to log the error. Pipe to stderr.
                                t.printStackTrace()
                            }
                            // TODO:
                            //  - Check for InterruptedIOException.bytesTransferred???
                            //  - Truncate to size to wipe out any partially written logs???
                        }
                        0L
                    }

                    if (written > 0L) {
                        processed++
                        size += written
                        LOG.v { "Wrote $written bytes to log" }
                    } else {
                        if (written == EXECUTE_ROTATE_LOGS) {
                            size = maxLogSize
                            break
                        }
                    }

                    if (!logStream.isOpen()) {
                        // This should never hopefully be the case? If we fail here,
                        // fail hard; no recovery...
                        logStreamCompletion.dispose()
                        LOG.v { "Closed >> $logStream" }
                        directory.mkdirs2(mode = modeDirectory, mustCreate = false)
                        logStream = files[0].openLogFileRobustly(modeFile)
                        logStreamCompletion = thisJob.closeOnCompletion(logStream)
                        size = logStream.size()
                        logStream.position(size)
                    }

                    // Rip through some more buffered actions (if available) while we hold a lock.
                    writeAction = when {
                        // We lost our lock, pop out.
                        !lockLog.isValid() -> null
                        // Log rotation is needed
                        size >= maxLogSize -> null
                        // Yield to another process (potentially)
                        processed >= maxLogYield -> null
                        // Job cancellation
                        !thisJob.isActive -> null
                        else -> rotateActionQueue.channel.tryReceive().getOrNull()
                            ?: channel.tryReceive().getOrNull()
                    }
                }
            }

            if (processed > 0) {
                // Ensure everything is synced to disk before going further,
                // either to do a log rotation or release the log lock.
                try {
                    CurrentThread.uninterrupted {
                        logStream.sync(meta = true)
                    }
                } catch (e: IOException) {
                    if (LOG.e(e) { "sync failure" } == 0) {
                        e.printStackTrace()
                    }
                }

                LOG.v { "Processed $processed " + if (processed > 1) "logs" else "log" }
            }

            if (thisJob.isActive && size >= maxLogSize) {
                if (lockLog.isValid()) {
                    CurrentThread.uninterrupted {
                        rotateLogs(rotateActionQueue, logStream, lockFile, buf)
                    }
                } else {
                    // We lost lockLog. Trigger an immediate retry without writing
                    // any new logs to logStream.
                    rotateActionQueue.channel.trySend { _, _ ->
                        if (dotRotateFile.exists2Robustly()) EXECUTE_ROTATE_LOGS else 0L
                    }
                }
            }

            if (lockLog.isValid()) try {
                lockLog.release()
            } catch (e: IOException) {
                // Non-ClosedException. The only way to recover is
                // to close and re-open the lock file.
                LOG.e(e) { "lockLog.release() failure >> $lockLog" }

                // If a log rotation is currently underway, we must wait
                // for it so that we do not invalidate its file lock.
                thisJob.children.forEach { child -> child.join() }

                try {
                    // Close the lock file. Next logLoop iteration will fail
                    // to obtain its lock due to a ClosedException and then
                    // re-open lockFile to retry acquisition.
                    lockFile.close()
                } catch (_: Throwable) {}
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
    ) {
        // If a previous log rotation is currently underway, we must
        // wait for it to complete before doing another one.
        //
        // This "could" happen if, for example, a rotation pops
        // off and resumes log writing where the next writes to
        // logStream are so large that it triggers another rotation
        // before the initial one finishes moving files. In that
        // event, this will suspend (and in doing so inhibiting further
        // log writes because we currently hold lockLog).
        currentCoroutineContext().job.children.forEach { child -> child.join() }

        run {
            // At this point, the rotateActionQueue:
            //  - Is empty.
            //  - Contains a lockFile.close() action due to lockRotate
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
            lockFile.lockRotate()
        } catch (_: Throwable) {
            // This can potentially happen if we had to wait on another
            // log rotation to finish up before running this one, and in
            // its completion handle there was a lockRotate.release() failure
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
            } catch (_: Throwable) {}

            // Trigger an immediate retry without writing any new logs to logStream.
            rotateActionQueue.channel.trySend { _, _ ->
                if (dotRotateFile.exists2Robustly()) EXECUTE_ROTATE_LOGS else 0L
            }

            return
        }

        // There exists a potential here that another process was mid-rotation
        // when this process' LogJob was started, whereby the dotRotateFile
        // existed and had not been moved into place yet, triggering a
        // rotation in this process' LogJob. Now that we hold both lockLog
        // and lockRotate, we need to determine what action, if any, to take.
        if (!dotRotateFile.exists2Robustly()) {
            // Not picking up an interrupted log rotation to finish off.

            val size = try {
                logStream.size()
            } catch (_: IOException) {
                // Since we're not picking up an interrupted
                // rotation, closing the stream on failure
                // will trigger a re-open on next logLoop iteration
                // and, if size >= maxLogSize, will end up
                // retrying the log rotation.
                try {
                    logStream.close()
                } catch (_: Throwable) {}

                // No dotRotateFile, but we should trigger an immediate
                // retry to check logStream.size() without writing any
                // new logs to it. If size < maxLogSize, then it will
                // simply continue processing actions.
                rotateActionQueue.channel.trySend { _, _ -> 0L }

                // Release lockRotate and return early.
                0L
            }

            if (size < maxLogSize) {
                if (lockRotate.isValid()) try {
                    lockRotate.release()
                } catch (e: IOException) {
                    // Non-ClosedException
                    LOG.e(e) { "lockRotate.release() failure >> $lockRotate" }
                    try {
                        // Close the lock file. Next logLoop iteration will fail
                        // to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (_: Throwable) {}
                }

                // No further action is needed. Return early.
                return
            }

            // TODO
            //  - Copy logStream to dotRotateTmpFile
            //  - Rename dotRotateTmpFile file to dotRotateFile
            //      - How to recover from failure here???
            //  - logStream.size(new = 0L)
            //      - If failure:
            //          - logStream.close()
            //              - Next LogLoop iteration will fail to retrieve size and re-open it.
            //          - files[0].openWrite(excl = OpenExcl.MaybeCreate.of(modeFile)).close()
            //  - Move log files into final location(s) in separate coroutine
        } else {
            LOG.v { "Interrupted log rotation detected. Finishing it off." }

            // TODO
            //  - Find hole in log files to pick up where left off in renaming
            //  - If no hole in log files, we could have been interrupted after
            //    renaming dotRotateTmpFile to dotRotateFile, BEFORE truncating
            //    the logStream file. Need to verify by checking its size and/or
            //    peeking at both files and comparing.
            //  - If logStream needs truncation
            //      - logStream.size(new = 0L)
            //          - If failure:
            //              - logStream.close()
            //                  - Next LogLoop iteration will fail to retrieve size and re-open it.
            //              - files[0].openWrite(excl = OpenExcl.MaybeCreate.of(modeFile)).close()
            //  - If there WAS a hole in log files (indicating that logStream
            //    truncation took place), check logStream.size() < maxLogSize
            //    such that, if another rotation is needed we can enqueue
            //    an empty action for next logLoop iteration so no logs are
            //    written to it, increasing its size further. OR, maybe just
            //    always enqueue an empty action???
            //  - Move log files into final location(s) in separate coroutine
        }

        val child = launch(context = CoroutineName("LogRotation-$logFiles0Hash")) {
            LOG.v { "LogRotation Started >> ${currentCoroutineContext().job}" }
            // TODO
            //  - Rename files
            //  - fsync directory???
            //  - On any failure, rotateActionQueue.channel.trySend { _, _ -> EXECUTE_ROTATE_LOGS }
        }

        child.invokeOnCompletion {
            if (lockRotate.isValid()) try {
                lockRotate.release()
            } catch (e: IOException) {
                // Non-ClosedException
                LOG.e(e) { "lockRotate.release() failure >> $lockRotate" }

                rotateActionQueue.channel.trySend { _, _ ->
                    // No other recovery mechanism but to close the lock file
                    // and invalidate all locks currently held, otherwise the
                    // next log rotation may deadlock when attempting to acquire
                    // rotateLock.
                    //
                    // This is done lazily here as a priority action in order
                    // to not inadvertently invalidate a lockLog in the midst
                    // of a write action. If the lockFile is already closed for
                    // some other reason, it does nothing.
                    try {
                        lockFile.close()
                    } catch (_: Throwable) {}
                    0L
                }
            }
        }
        child.invokeOnCompletion { LOG.v { "LogRotation Stopped >> $child" } }
    }

    private fun Job.closeOnCompletion(closeable: Closeable, logOpen: Boolean = true): DisposableHandle {
        if (logOpen) LOG.v { "Opened >> $closeable" }

        return invokeOnCompletion { t ->
            val tt = try {
                closeable.close()
                null
            } catch (tt: Throwable) {
                t?.addSuppressed(tt)
                tt
            }
            LOG.v(tt) { "Closed >> $closeable" }
        }
    }
}
