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
import io.matthewnelson.encoding.core.EncoderDecoder.Companion.DEFAULT_BUFFER_SIZE
import io.matthewnelson.encoding.utf8.UTF8
import io.matthewnelson.encoding.utf8.UTF8.CharPreProcessor.Companion.sizeUTF8
import io.matthewnelson.immutable.collections.toImmutableList
import io.matthewnelson.immutable.collections.toImmutableSet
import io.matthewnelson.kmp.file.Closeable
import io.matthewnelson.kmp.file.DirectoryNotEmptyException
import io.matthewnelson.kmp.file.File
import io.matthewnelson.kmp.file.FileAlreadyExistsException
import io.matthewnelson.kmp.file.FileNotFoundException
import io.matthewnelson.kmp.file.FileStream
import io.matthewnelson.kmp.file.IOException
import io.matthewnelson.kmp.file.NotDirectoryException
import io.matthewnelson.kmp.file.OpenExcl
import io.matthewnelson.kmp.file.SysFsInfo
import io.matthewnelson.kmp.file.canonicalFile2
import io.matthewnelson.kmp.file.chmod2
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
import io.matthewnelson.kmp.log.annotation.ExperimentalLogApi
import io.matthewnelson.kmp.log.file.internal.CurrentThread
import io.matthewnelson.kmp.log.file.internal.Directory
import io.matthewnelson.kmp.log.file.internal.FILE_LOCK_POS_LOG
import io.matthewnelson.kmp.log.file.internal.FILE_LOCK_POS_ROTATE
import io.matthewnelson.kmp.log.file.internal.FILE_LOCK_SIZE
import io.matthewnelson.kmp.log.file.internal.FileLock
import io.matthewnelson.kmp.log.file.internal.InvalidFileLock
import io.matthewnelson.kmp.log.file.internal.LockFile
import io.matthewnelson.kmp.log.file.internal.LogAction
import io.matthewnelson.kmp.log.file.internal.LogAction.Companion.EXECUTE_ROTATE_LOGS
import io.matthewnelson.kmp.log.file.internal.LogAction.Companion.MAX_RETRIES
import io.matthewnelson.kmp.log.file.internal.LogAction.Companion.drop
import io.matthewnelson.kmp.log.file.internal.LogAction.Rotation.Companion.CONSUME_AND_IGNORE
import io.matthewnelson.kmp.log.file.internal.LogAction.Rotation.Companion.ROTATION_NOT_NEEDED
import io.matthewnelson.kmp.log.file.internal.LogBuffer
import io.matthewnelson.kmp.log.file.internal.LogDispatcher
import io.matthewnelson.kmp.log.file.internal.LogDispatcherAllocator
import io.matthewnelson.kmp.log.file.internal.LogSend
import io.matthewnelson.kmp.log.file.internal.LogWait
import io.matthewnelson.kmp.log.file.internal.ModeBuilder
import io.matthewnelson.kmp.log.file.internal.RealThreadPool
import io.matthewnelson.kmp.log.file.internal.RotateActionQueue
import io.matthewnelson.kmp.log.file.internal.ScopeFileLog
import io.matthewnelson.kmp.log.file.internal.ScopeLogHandle
import io.matthewnelson.kmp.log.file.internal.ScopeLogLoop
import io.matthewnelson.kmp.log.file.internal.ScopeLogLoop.Companion.scopeLogLoop
import io.matthewnelson.kmp.log.file.internal.StubFileLock
import io.matthewnelson.kmp.log.file.internal.StubLockFile
import io.matthewnelson.kmp.log.file.internal._atomic
import io.matthewnelson.kmp.log.file.internal._atomicRef
import io.matthewnelson.kmp.log.file.internal._compareAndSet
import io.matthewnelson.kmp.log.file.internal._decrementAndGet
import io.matthewnelson.kmp.log.file.internal._get
import io.matthewnelson.kmp.log.file.internal._getAndSet
import io.matthewnelson.kmp.log.file.internal._incrementAndGet
import io.matthewnelson.kmp.log.file.internal._set
import io.matthewnelson.kmp.log.file.internal.async
import io.matthewnelson.kmp.log.file.internal.awaitAndCancel
import io.matthewnelson.kmp.log.file.internal.deleteOrMoveToRandomIfNonEmptyDirectory
import io.matthewnelson.kmp.log.file.internal.exists2Robustly
import io.matthewnelson.kmp.log.file.internal.format
import io.matthewnelson.kmp.log.file.internal.id
import io.matthewnelson.kmp.log.file.internal.isDesktop
import io.matthewnelson.kmp.log.file.internal.launch
import io.matthewnelson.kmp.log.file.internal.lockNonBlock
import io.matthewnelson.kmp.log.file.internal.moveLogTo
import io.matthewnelson.kmp.log.file.internal.newLogDispatcher
import io.matthewnelson.kmp.log.file.internal.now
import io.matthewnelson.kmp.log.file.internal.openDirectory
import io.matthewnelson.kmp.log.file.internal.openLockFileRobustly
import io.matthewnelson.kmp.log.file.internal.openLogFileRobustly
import io.matthewnelson.kmp.log.file.internal.openRobustly
import io.matthewnelson.kmp.log.file.internal.pid
import io.matthewnelson.kmp.log.file.internal.uninterrupted
import io.matthewnelson.kmp.log.file.internal.uninterruptedRunBlocking
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.kotlincrypto.hash.blake2.BLAKE2s
import kotlin.concurrent.Volatile
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmField
import kotlin.jvm.JvmName
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.sequences.forEach
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * A highly robust, performant and configurable [Log] implementation for logging to a [File].
 *
 * The [FileLog] implementation is designed with out-of-the-box defaults tuned for data
 * integrity and durability. This, of course, may be modified via [Builder] to better suit
 * application specific needs.
 *
 * Multiple [FileLog] instances may be installed at [Log.Root] simultaneously, but only `1`
 * for a given [Builder.logDirectory] + [Builder.fileName] + [Builder.fileExtension]. This is
 * enforced by hashing the canonical path of the resultant log [File] and using it in the
 * [FileLog.uid].
 *
 * e.g.
 *
 *     println(myFileLog.logFiles0Hash)
 *     // 2D1D601236B22EB68CDB9E1D
 *     println(myFileLog.uid)
 *     // io.matthewnelson.kmp.log.file.FileLog-2D1D601236B22EB68CDB9E1D
 *
 * **WARNING:** Logging to a [File] can *significantly* reduce application performance if not
 * configured properly. It can become a bottleneck on throughput if, say a server experiences
 * an unexpected 1000x increase in traffic, and thus its log production. Additionally, it has
 * an inherent dependency on the underlying file system which, in some cases, can be one that
 * is prohibitively slow (such as logging to a [File] located in a NFS (Network File System)
 * mounted directory). As always, using an appropriate [Log.Level] and asking "does this data
 * really need to be logged?" are great ways to minimize logging overhead. Thoroughly reading
 * [Builder] documentation is **highly** recommended!
 *
 * ### Resources
 *
 * [FileLog] consumes as minimal resources as possible to get the job done. All resources are
 * allocated at time of [Log.Root.install], and deallocated at time of [Log.Root.uninstall]
 * (or upon experiencing an early shutdown due to an irrecoverable error).
 *
 * Each installed [FileLog] instance allocates a single `8192` byte array, `2` always-open [File]
 * (the log [File] and its associated lock [File] if not disabled via [Builder.fileLock]), a single
 * background [CoroutineDispatcher] (if not sharing a [ThreadPool] between instances), and a few
 * data structures for logging operations.
 *
 * [Builder.minWaitOn], [Builder.bufferCapacity], [Builder.bufferOverflow], [Builder.thread] and
 * [Builder.fileLock] are `5` configuration options which directly affect resources used for
 * logging operations.
 *
 * ### Log Rotation
 *
 * Once a log [File] reaches its specified maximum size, a log rotation is triggered. The log
 * rotation implementation is designed with filesystem atomicity in mind such that **any**
 * previously interrupted log rotation, either by process termination or error, can be detected
 * and brought to completion. Checking for a previously interrupted log rotation is always the
 * first thing [FileLog] does at time of [Log.Root.install].
 *
 * As soon as the log [File] reaches capacity, it is atomically copied to its reproducibly
 * derived pre-archival location within the [logDirectory], then truncated to `0`. Execution of
 * rotating log archives is then performed by a separate [Job], allowing for a prompt return to
 * writing logs to the log [File]. This ensures, as much as the underlying file system permits,
 * `0` log loss and an ability to detect previously interrupted log rotations (if the pre-archival
 * [File] has not been moved to its final `{fileName}{.fileExtension}.001` location).
 *
 * [Builder.maxLogFileSize] and [Builder.maxLogFiles] are `2` configuration options which
 * directly affect log rotations.
 *
 * ### Multi-Process Logging
 *
 * Using [FileLog] from multiple processes to write to the *same* log [File], without fear of
 * data corruption, is built into the core of [FileLog]. It utilizes byte-range [File] locks to
 * cooperatively interact with other processes when modifying the file system. `2` different
 * byte-ranges are used when locking the lock [File] for coordinating work; a byte-range for
 * writing logs to the log [File], and another byte-range for performing log rotations (as
 * described above). Prior to releasing either of the aforementioned [File] lock byte-ranges,
 * [FileLog] syncs all file system modifications it made pertaining to that byte-range, to disk.
 *
 * [Builder.yieldOn], [Builder.fileLock] and [Builder.fileLockTimeout] are `3` configuration
 * options which directly affect how [FileLog] performs in multi-process application environments.
 *
 * ### File System Permissions (POSIX)
 *
 * [FileLog] supports the ability to define both directory and log [File] permissions. By default,
 * directory permissions `700` and log [File] permissions `600` are used; permissions for `group`
 * and `other` of each being configurable via [Builder].
 *
 * **NOTE:** Applications supporting multi-user installations **must** keep this in mind if
 * logging to a shared directory or shared log [File] (or are planning to do so in the future).
 *
 * ### Log Filtering
 *
 * In addition to the general filtering provided by [Log.min] and [Log.max], [FileLog] provides
 * further filtering configuration by way of blacklists and whitelists for both [Logger.domain]
 * and/or [Logger.tag].
 *
 * [Builder.min], [Builder.max], [Builder.blacklistDomain], [Builder.blacklistDomainNull],
 * [Builder.blacklistTag], [Builder.whitelistDomain], [Builder.whitelistDomainNull] and
 * [Builder.whitelistTag] are `8` configuration options which directly affect how [FileLog]
 * filters incoming logs.
 *
 * ### Log Formatting
 *
 * By default, logs are formatted with a space-delimited prefix for all lines of a log entry. The
 * prefix includes:
 * 1) The local date (year omitted)
 * 2) The local time
 * 3) The first character of the [Log.Level] name
 * 4) The `0` prefixed 7-digit process-id
 * 5) The `0` prefixed 7-digit thread-id
 * 6) The concatenated [Logger.domain] (if non-null) and [Logger.tag]
 *
 * ```
 * 01-01 01:59:01.850 D 0452849 0452849 [some.domain]SomeTag: {log line 1 of 3}
 * 01-01 01:59:01.850 D 0452849 0452849 [some.domain]SomeTag: {log line 2 of 3}
 * 01-01 01:59:01.850 D 0452849 0452849 [some.domain]SomeTag: {log line 3 of 3}
 * 01-01 01:59:01.852 D 0452849 0452855 [some.domain]SomeTag: {log line 1 of 1}
 * 01-01 01:59:01.853 D 0452849 0452855 [some.domain]SomeTag: {log line 1 of 2}
 * 01-01 01:59:01.853 D 0452849 0452855 [some.domain]SomeTag: {log line 2 of 2}
 * 01-01 01:59:01.853 D 0452849 0452855 SomeOtherTagNoDomain: {log line 1 of 1}
 * 01-01 01:59:01.854 D 0452849 0452855 [some.domain]SomeTag: {log line 1 of 1}
 * ```
 *
 * The thread-id is retrieved in the following platform specific manner:
 *  - Android:
 *      + API 36+: [Thread.threadId](https://developer.android.com/reference/java/lang/Thread#threadId())
 *      + API 35-: [Thread.getId](https://developer.android.com/reference/java/lang/Thread#getId())
 *  - Jvm/AndroidUnitTest:
 *      + Java 19+: [Thread.threadId](https://docs.oracle.com/en/java/javase/19/docs/api/java.base/java/lang/Thread.html#threadId())
 *      + Java 18-: [Thread.getId](https://docs.oracle.com/javase/8/docs/api/java/lang/Thread.html#getId--)
 *  - Native:
 *      + Android: [gettid](https://www.man7.org/linux/man-pages/man2/gettid.2.html)
 *      + Darwin: [pthread_mach_thread_np](https://github.com/apple-oss-distributions/libpthread/blob/42d026df5b07825070f60134b980a1ec2552dfee/include/pthread/pthread.h#L543)
 *      + Linux: [SYS_gettid](https://www.man7.org/linux/man-pages/man2/gettid.2.html)
 *      + MinGW: [GetCurrentThreadId](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-getcurrentthreadid)
 *
 * [Builder.format] and [Builder.formatOmitYear] are `2` configuration options which directly
 * affect how logs are formatted before being written to the log [File].
 *
 * ### Blocking & Non-Blocking Logging
 *
 * [FileLog] supports configurable modes of operation for blocking, non-blocking, or a mixture
 * of both. A limiting factor of [FileLog] data processing is how quickly the file system allows
 * it to write to the log [File]. Because of this, the rate at which logs are produced could
 * rapidly outpace the rate at which [FileLog] can process them. This can result in OOM (Out
 * Of Memory) exceptions if no system(s) of control are in place.
 *
 * The default system of control [FileLog] employs is thread parking (i.e. blocking), restricting
 * log production to the number of threads available. Once a log has been written to the log [File],
 * the thread is unparked and [FileLog.log] returns its result. This, however, may be changed via
 * [Builder] to better suit application needs.
 *
 * **NOTE:** One caveat to non-blocking configurations has to do with [Level.Fatal] log handling.
 * There exists no way to achieve *fully* non-blocking behavior, while also accepting [Level.Fatal]
 * logs. [FileLog.log] **must** block the thread until the [Level.Fatal] log is written to the log
 * [File], **before** the process is aborted by [Log.AbortHandler].
 *
 * [Builder.minWaitOn], [Builder.bufferCapacity] and [Builder.bufferOverflow] are `3` configuration
 * options which directly affect blocking and non-blocking modes of operation.
 *
 * ### Irrecoverable Errors
 *
 * The [FileLog] implementation is one that is **highly** redundant, but there are *some* scenarios
 * in which it has no other choice but to shut itself down. Most irrecoverable errors are security
 * related, attributed to potential malicious actors on the device doing things they should not.
 *
 * 1) Symbolic link hijacking: If between the time [Builder.build] is called and the resulting [FileLog]
 *    instance is installed at [Log.Root], a symbolic link is created to alter the destination of
 *    [FileLog.logDirectory], [FileLog] will error out and shut itself down. This is to prevent data
 *    corruption due to the potential of multiple [FileLog] instances being installed, modifying the
 *    *same* log [File].
 * 2) Failure to open the log [File] or its associated lock [File]: [FileLog] tries its best with robust
 *    [File] open logic, but if it is unable to open a [File], it will error out and shut itself down.
 * 3) Failure to obtain a [File] lock: [FileLog] tries its best with robust lock acquisition logic (such
 *    as closing and re-opening the lock [File] on certain failures). But, if unable to obtain a [File]
 *    lock for the requested byte-range, or within the specified [fileLockTimeout], [FileLog] will error
 *    out and shut itself down.
 * 4) Failure to rotate logs: [FileLog] has many redundancies built into its log rotation implementation
 *    to handle edge-cases, and will almost always attempt retries upon failure. But, to prevent itself
 *    from looping infinitely on retries, [FileLog] will error out and shut itself down.
 *
 * Errors that [FileLog] experiences are always dispatched as [Level.Error] logs via its internally
 * instantiated [Logger]. If the [Level.Error] log was not logged by another installed [Log] instance
 * (i.e. [Logger.e] returned `0`), then [Throwable.printStackTrace] is used.
 *
 * @see [Builder]
 * @see [DOMAIN]
 * */
public class FileLog: Log {

    public companion object {

        /**
         * [FileLog] itself utilizes a [Logger] instance to log [Level.Error] and [Level.Warn] logs,
         * if and when it is necessary. For obvious reasons a [FileLog] instance will **never** log
         * to itself as that would create a cyclical loop, but it *can* log to other installed [Log]
         * instances (including other [FileLog]). This reference is for the [Logger.domain] used by
         * all [FileLog] instance instantiated [Logger] for dispatching their [Level.Error] and
         * [Level.Warn] logs (as well as [Level.Debug] logs if [FileLog.debug] is set to `true`).
         *
         * This reference is meant to be used with [Builder.blacklistDomain] and [Builder.whitelistDomain]
         * to configure a set of [FileLog] instances for a cooperative logging experience. All logs
         * generated by [FileLog] instances can be centralized to a single [FileLog] instance (or other
         * [Log] implementation), while all *other* [FileLog] instances can be configured to reject
         * logs for the [DOMAIN].
         *
         * **NOTE:** This [Logger.domain] should not be utilized for your own [Logger] instances; it
         * is reserved for [FileLog] use **only**.
         *
         * e.g.
         *
         *     // Use the same thread since fileLogErrors is not going
         *     // to be doing much work and that'd be wasteful.
         *     //
         *     // NOTE: Adjust nThreads to accommodate additional FileLog.
         *     @OptIn(ExperimentalLogApi::class)
         *     val pool = FileLog.ThreadPool.of(nThreads = 1)
         *
         *     val fileLogErrors = FileLog.Builder(myLogDirectory)
         *         .fileName("file_log")
         *         .fileExtension("err")
         *         .min(Log.Level.Warn)
         *         .thread(pool)
         *
         *         // Allow ONLY logs from other FileLog instances.
         *         .whitelistDomain(FileLog.DOMAIN)
         *         .whitelistDomainNull(allow = false)
         *
         *         // Configure a rolling buffer with maximum capacity.
         *         .minWaitOn(Level.Fatal)
         *         .bufferCapacity(nLogs = 32)
         *         .bufferOverflow(dropOldest = true)
         *
         *         .maxLogFileSize(0) // Will default to the minimum
         *         .maxLogFiles(0) // Will default to the minimum
         *         .build()
         *
         *     val fileLog1 = FileLog.Builder(myLogDirectory)
         *         .thread(pool)
         *
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

        // Used for fileLockTimeout to indicate that Builder.fileLock was set
        // to false (disable use of file locking).
        private const val FILE_LOCK_DISABLED = -1L

        private val DEFAULT_FORMATTER: Formatter = Formatter(::format)
    }

    /**
     * The canonical path of the directory for which this [FileLog] instance operates in.
     *
     * @see [Builder.logDirectory]
     * */
    @JvmField
    public val logDirectory: String

    /**
     * A list of all log [File]; element `0` being the active log, and all other elements
     * being archive logs. Will **always** contain *at least* `2` elements.
     *
     * e.g.
     *
     *     myFileLog.logFiles.forEach(::println)
     *     // /path/to/some/directory/logs/my_log
     *     // /path/to/some/directory/logs/my_log.001
     *     // /path/to/some/directory/logs/my_log.002
     *     // /path/to/some/directory/logs/my_log.003
     *
     * @see [Builder.fileName]
     * @see [Builder.fileExtension]
     * @see [Builder.maxLogFiles]
     * */
    @JvmField
    public val logFiles: List<String>

    /**
     * A `24` character, base 16 (hex) encoded, double hash of the canonical path of the active
     * log [File] (i.e. element `0` of [logFiles]).
     *
     * **NOTE:** This value is device specific and should not be utilized as a stable reference
     * externally of said device.
     * */
    @JvmField
    public val logFiles0Hash: String

    /**
     * The maximum byte size the active log [File] can reach before a log rotation is triggered.
     *
     * @see [Builder.maxLogFileSize]
     * */
    @JvmField
    public val maxLogFileSize: Long

    /**
     * The permissions to use for [File.mkdirs2] when creating [logDirectory], and any necessary
     * subdirectories. If [logDirectory] already exists, then permissions are re-applied to the
     * [logDirectory] via [File.chmod2] at time of [Log.Root.install].
     *
     * @see [File.chmod2]
     * @see [Builder.directoryGroupReadable]
     * @see [Builder.directoryGroupWritable]
     * @see [Builder.directoryOtherReadable]
     * @see [Builder.directoryOtherWritable]
     * */
    @JvmField
    public val modeDirectory: String

    /**
     * The permissions to use for [OpenExcl.MaybeCreate] when opening log files.
     *
     * @see [File.chmod2]
     * @see [Builder.fileGroupReadable]
     * @see [Builder.fileGroupWritable]
     * @see [Builder.fileOtherReadable]
     * @see [Builder.fileOtherWritable]
     * */
    @JvmField
    public val modeFile: String

    /**
     * The capacity of the [Channel] backing this [FileLog] instance.
     *
     * **NOTE:** If [minWaitOn] is equal to [min], this will **always** be [Channel.UNLIMITED].
     *
     * @see [Builder.bufferCapacity]
     * */
    @JvmField
    public val bufferCapacity: Int

    /**
     * The overflow behavior of the [Channel] backing this [FileLog] instance. If `true`,
     * [BufferOverflow.DROP_OLDEST], otherwise [BufferOverflow.SUSPEND].
     *
     * **NOTE:** If [minWaitOn] is equal to [min], this wil **always** be `false`.
     *
     * @see [Builder.bufferOverflow]
     * */
    @JvmField
    public val bufferOverflowDropOldest: Boolean

    /**
     * The minimum [Level] for which [FileLog.log] will block on when processing incoming
     * logs. Will **always** be greater than or equal to [min].
     *
     * @see [Builder.minWaitOn]
     * */
    @JvmField
    public val minWaitOn: Level

    /**
     * The number of logs to batch process before calling [FileStream.sync] and yielding
     * the [File] lock to another process.
     *
     * @see [Builder.yieldOn]
     * */
    @JvmField
    public val yieldOn: Byte

    /**
     * If `true`, [FileStream.sync] will be called after every write to the log file. Otherwise,
     * [FileStream.sync] behavior will defer to [yieldOn].
     *
     * @see [Builder.syncEachWrite]
     * */
    @JvmField
    public val syncEachWrite: Boolean

    /**
     * The timeout, in milliseconds, to use when acquiring a [File] lock.
     *
     * **NOTE:** If `-1`, [File] lock use was disabled via [Builder.fileLock]. Otherwise,
     * will **always** be between `375` and [Duration.INFINITE] (inclusive).
     *
     * @see [Builder.fileLock]
     * @see [Builder.fileLockTimeout]
     * */
    @JvmField
    public val fileLockTimeout: Long

    /**
     * The [Logger.domain] to deny logs from. If empty, no [Logger.domain] are denied.
     *
     * @see [Builder.blacklistDomain]
     * */
    @JvmField
    public val blacklistDomain: Set<String>

    /**
     * If [Logger.domain] `null` (i.e. no domain) will be denied.
     *
     * @see [Builder.blacklistDomainNull]
     * */
    @JvmField
    public val blacklistDomainNull: Boolean

    /**
     * The [Logger.tag] to deny logs from. If empty, no [Logger.tag] are denied.
     *
     * @see [Builder.blacklistTag]
     * */
    @JvmField
    public val blacklistTag: Set<String>

    /**
     * The [Logger.domain] to allow logs from. If empty, all [Logger.domain] are allowed.
     *
     * @see [Builder.whitelistDomain]
     * */
    @JvmField
    public val whitelistDomain: Set<String>

    /**
     * If [Logger.domain] `null` (i.e. no domain) is allowed.
     *
     * **NOTE:** If [whitelistDomain] is empty, this will **always** be `true`.
     *
     * @see [Builder.whitelistDomainNull]
     * */
    @JvmField
    public val whitelistDomainNull: Boolean

    /**
     * The [Logger.tag] to allow logs from. If empty, all [Logger.tag] are allowed.
     *
     * @see [Builder.whitelistTag]
     * */
    @JvmField
    public val whitelistTag: Set<String>

    /**
     * Enable/Disable [Level.Debug] logs this instance generates pertaining to its internal operations.
     *
     * **NOTE:** To capture the logs, a non-[FileLog] implementation of [Log] is required. All [FileLog]
     * instances deny [Level.Debug] logs from [DOMAIN].
     *
     * @see [DOMAIN]
     * @see [Builder.debug]
     * */
    @JvmField
    @Volatile
    public var debug: Boolean

    /**
     * Enable/Disable [Level.Warn] logs this instance generates pertaining to its internal operations.
     *
     * @see [DOMAIN]
     * @see [Builder.warn]
     * */
    @JvmField
    @Volatile
    public var warn: Boolean

    /**
     * Checks [Job.isActive] of the underlying log job launched by [FileLog.onInstall].
     * */
    @get:JvmName("isActive")
    public val isActive: Boolean get() = _logJob?.isActive ?: false

    /**
     * The number of logs that are pending processing.
     * */
    @get:JvmName("pendingLogCount")
    public val pendingLogCount: Long get() = _pendingLogCount._get()

    /**
     * Uninstalls the [FileLog] instance from [Log.Root], then waits for the underlying log [Job]
     * to finish any of its remaining work.
     *
     * **NOTE:** If the calling coroutine's [Job] is in a canceled state, or is canceled while waiting,
     * this function will re-throw the [CancellationException] *after* first cancelling the underlying
     * log [Job]. The result of this function, either by return or by cancellation, is that the [FileLog]
     * instance is in an "off" state.
     *
     * @return `false` if the instance was not installed at [Log.Root]. Otherwise, `true` (i.e. the
     *   instance, or an instance with the same [uid], was uninstalled from [Log.Root] and it is now
     *   in an "off" state).
     *
     * @see [Log.Root.uninstallAndGet]
     * @see [uninstallAndAwaitSync]
     *
     * @throws [CancellationException]
     * @throws [ClassCastException] If the [Log] returned by [Log.Root.uninstallAndGet] using [uid]
     *   was non-`null`, but not an instance of [FileLog] (highly unlikely, but possible).
     * */
    public suspend inline fun uninstallAndAwaitAsync(): Boolean {
        return uninstallAndAwaitAsync(timeout = Duration.INFINITE)
    }

    /**
     * Uninstalls the [FileLog] instance from [Log.Root], then waits for the underlying log [Job]
     * to finish any of its remaining work for the specified [timeout]. If [timeout] is exceeded,
     * then the underlying log [Job] is canceled with any unfinished work being dropped. If the
     * specified [timeout] is less than or equal to [Duration.ZERO], the underlying log [Job] is
     * canceled with immediate effect.
     *
     * **NOTE:** If the calling coroutine's [Job] is in a canceled state, or is canceled while waiting
     * for the specified [timeout], this function will re-throw the [CancellationException] *after*
     * first cancelling the underlying log [Job]. The result of this function, either by return or by
     * cancellation, is that the [FileLog] instance is in an "off" state.
     *
     * @param [timeout] A maximum [Duration] to wait for the underlying log [Job] to finish any of its
     *   remaining work. If less than or equal to [Duration.ZERO], the underlying log [Job] is canceled
     *   immediately.
     *
     * @return `false` if the instance was not installed at [Log.Root]. Otherwise, `true` (i.e. the
     *   instance, or an instance with the same [uid], was uninstalled from [Log.Root] and it is now
     *   in an "off" state).
     *
     * @see [Log.Root.uninstallAndGet]
     * @see [uninstallAndAwaitSync]
     *
     * @throws [CancellationException]
     * @throws [ClassCastException] If the [Log] returned by [Log.Root.uninstallAndGet] using [uid]
     *   was non-`null`, but not an instance of [FileLog] (highly unlikely, but possible).
     * */
    public suspend fun uninstallAndAwaitAsync(timeout: Duration): Boolean {
        val instance = uninstallAndGet(uid) ?: return false
        val logJob = (instance as FileLog)._logJob ?: return false

        run {
            if (!timeout.isPositive()) return@run
            val dispatcher = currentCoroutineContext()[ContinuationInterceptor] ?: return@run
            val qn = dispatcher::class.qualifiedName ?: return@run
            if (!qn.startsWith("kotlinx.coroutines.test")) return@run

            // Dispatchers.Unconfined because test dispatcher may fast-forward which we do NOT want.
            try {
                withContext(Dispatchers.Unconfined) {
                    awaitAndCancel(logJob, timeout, canceledBy = { "uninstallAndAwaitAsync" })
                }
            } finally {
                // withContext can throw CancellationException, so ensure logJob is ALWAYS complete.
                if (logJob.isActive) logJob.cancel("Canceled by uninstallAndAwaitAsync")
            }
            return true
        }

        awaitAndCancel(logJob, timeout, canceledBy = { "uninstallAndAwaitAsync" })
        return true
    }

    /**
     * Uninstalls the [FileLog] instance from [Log.Root], then waits for the underlying log [Job]
     * to finish any of its remaining work.
     *
     * @return `false` if the instance was not installed at [Log.Root]. Otherwise, `true` (i.e. the
     *   instance, or an instance with the same [uid], was uninstalled from [Log.Root] and it is now
     *   in an "off" state).
     *
     * @see [Log.Root.uninstallAndGet]
     * @see [uninstallAndAwaitAsync]
     *
     * @throws [ClassCastException] If the [Log] returned by [Log.Root.uninstallAndGet] using [uid]
     *   was non-`null`, but not an instance of [FileLog] (highly unlikely, but possible).
     * */
    public inline fun uninstallAndAwaitSync(): Boolean {
        return uninstallAndAwaitSync(timeoutMillis = Duration.INFINITE.inWholeMilliseconds)
    }

    /**
     * Uninstalls the [FileLog] instance from [Log.Root], then waits for the underlying log [Job]
     * to finish any of its remaining work for the specified [timeoutMillis]. If [timeoutMillis]
     * is exceeded, then the underlying log [Job] is canceled with any unfinished work being dropped.
     * If the specified [timeoutMillis] is less than or equal to `0`, the underlying log [Job] is
     * canceled with immediate effect.
     *
     * @param [timeoutMillis] A maximum duration, in milliseconds, to wait for the underlying log
     *   [Job] to finish any of its remaining work. If less than or equal to `0`, the underlying
     *   log [Job] is canceled immediately.
     *
     * @return `false` if the instance was not installed at [Log.Root]. Otherwise, `true` (i.e. the
     *   instance, or an instance with the same [uid], was uninstalled from [Log.Root] and it is now
     *   in an "off" state).
     *
     * @see [Log.Root.uninstallAndGet]
     * @see [uninstallAndAwaitAsync]
     *
     * @throws [ClassCastException] If the [Log] returned by [Log.Root.uninstallAndGet] using [uid]
     *   was non-`null`, but not an instance of [FileLog] (highly unlikely, but possible).
     * */
    public fun uninstallAndAwaitSync(timeoutMillis: Long): Boolean {
        val instance = uninstallAndGet(uid) ?: return false
        val logJob = (instance as FileLog)._logJob ?: return false
        val context = instance.scopeFileLog.handler + Dispatchers.IO
        while (logJob.isActive) {
            try {
                CurrentThread.uninterruptedRunBlocking(context) {
                    awaitAndCancel(logJob, timeoutMillis.milliseconds, canceledBy = { "uninstallAndAwaitSync" })
                }
            } catch (_: Throwable) {
                // InterruptedException (Jvm/Android)
                // CancellationException (in which case job.isActive will be false)
            }
        }
        return true
    }

    /**
     * A callback for formatting log entries.
     *
     * @see [Builder.format]
     * @see [Builder.formatOmitYear]
     * */
    public fun interface Formatter {

        /**
         * Format the log entry. Must be thread-safe, fast, non-blocking, and not throw
         * exception.
         *
         * **NOTE:** This will **always** be called from the background thread [FileLog]
         * is operating on.
         *
         * @param [time] The local date/time recorded when [FileLog.log] was invoked. If
         *   [Builder.formatOmitYear] was `true`, will be in the format of `MM-dd HH:mm:ss.SSS`.
         *   Otherwise, will be in the format of `yyyy-MM-dd HH:mm:ss.SSS`.
         * @param [pid] The process id. Can be `-1` on Jvm if was unable to obtain it.
         * @param [tid] The thread id recorded when [FileLog.log] was invoked.
         * @param [level] The [Log.Level] of the log.
         * @param [domain] The [Logger.domain] or `null`.
         * @param [tag] The [Logger.tag]
         * @param [msg] The log message. Will be `null` or non-empty, never empty.
         * @param [t] The exception or `null`.
         *
         * @return The formatted log to write, or `null` if no log needs to be written.
         *
         * @see [Log.log]
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
     * A reference meant for instantiating multiple [FileLog] with such that they are able to share a
     * single [CoroutineDispatcher] of [nThreads].
     *
     * **NOTE:** This class does not immediately allocate anything. [CoroutineDispatcher] allocation
     * occurs upon [Log.Root.install] of the **first** [FileLog] instance using the [ThreadPool], and
     * will be de-allocated upon [Log.Root.uninstall] of the **last** [FileLog] instance using the
     * [ThreadPool].
     *
     * @see [of]
     * @see [Builder.thread]
     * */
    public abstract class ThreadPool internal constructor(

        /**
         * The number of threads to allocate. Will be between `1` and `8` (inclusive).
         * */
        @JvmField
        public val nThreads: Int,
        init: Any,
    ) {

        public companion object {

            /**
             * Creates a new [ThreadPool] instance.
             *
             * **NOTE:** [nThreads] must be appropriate for the number of [FileLog] that will be
             * utilizing the [ThreadPool], and total amount of work all are expected to be doing.
             * It is ill-advised to put, say `10` [FileLog], on a [ThreadPool] instantiated with
             * an [nThreads] of `1`.
             *
             * @param [nThreads] The number of threads to allocate.
             *
             * @return The new [ThreadPool]
             *
             * @throws [IllegalArgumentException] If [nThreads] is less than `1`, or greater than `8`.
             * */
            @JvmStatic
            @ExperimentalLogApi
            public fun of(nThreads: Int): ThreadPool = RealThreadPool.of(nThreads, INIT)

            private val INIT = Any()
        }

        init {
            // Why someone would need or dedicate 8 threads for logging is beyond me...
            require(nThreads in 1..8) { "nThreads[$nThreads] !in 1..8" }
            check(init == INIT) { "FileLog.ThreadPool cannot be extended. Use FileLog.ThreadPool.of" }
        }

        /** @suppress */
        public override fun toString(): String = "ThreadPool[nThreads=$nThreads]@${hashCode()}"
    }

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

        // NOTE: If adding anything, update copy function.
        private var _min = Level.Info
        private var _max = Level.Fatal
        private val _modeDirectory = ModeBuilder.of(isDirectory = true)
        private val _modeFile = ModeBuilder.of(isDirectory = false)
        private var _fileName = "log"
        private var _fileExtension = ""
        private var _maxLogFileSize = (if (isDesktop()) 10L else 5L) * 1024L * 1024L // 10 Mb or 5 Mb
        private var _maxLogFiles: Byte = if (isDesktop()) 5 else 3
        private var _bufferCapacity = -1
        private var _bufferOverflowDropOldest = false
        private var _minWaitOn = Level.Verbose
        private var _yieldOn: Byte = 2
        private var _syncEachWrite = false
        private var _fileLockEnable = true
        private var _fileLockTimeout = -1L
        private var _threadPool: ThreadPool? = null
        private var _formatter = DEFAULT_FORMATTER
        private var _formatterOmitYear = true
        private val _blacklistDomain = mutableSetOf<String>()
        private var _blacklistDomainNull = false
        private val _blacklistTag = mutableSetOf<String>()
        private val _whitelistDomain = mutableSetOf<String>()
        private var _whitelistDomainNull = true
        private val _whitelistTag = mutableSetOf<String>()
        private var _debug = false
        private var _warn = true

        /**
         * Copies this [Builder] and all of its current settings to a new one.
         *
         * @return A new [Builder]
         * */
        public inline fun copy(): Builder = copy(logDirectory = null)

        /**
         * Copies this [Builder] and all of its current settings to a new one.
         *
         * @param [logDirectory] The new [Builder.logDirectory] to use, or `null` to use
         * the current [Builder.logDirectory].
         *
         * @return A new [Builder]
         * */
        public fun copy(logDirectory: String?): Builder {
            val new = Builder(logDirectory ?: this.logDirectory)
            new._min = _min
            new._max = _max
            new._modeDirectory.groupRead = _modeDirectory.groupRead
            new._modeDirectory.groupWrite = _modeDirectory.groupWrite
            new._modeDirectory.otherRead = _modeDirectory.otherRead
            new._modeDirectory.otherWrite = _modeDirectory.otherWrite
            new._modeFile.groupRead = _modeFile.groupRead
            new._modeFile.groupWrite = _modeFile.groupWrite
            new._modeFile.otherRead = _modeFile.otherRead
            new._modeFile.otherWrite = _modeFile.otherWrite
            new._fileName = _fileName
            new._fileExtension = _fileExtension
            new._maxLogFileSize = _maxLogFileSize
            new._maxLogFiles = _maxLogFiles
            new._bufferCapacity = _bufferCapacity
            new._bufferOverflowDropOldest = _bufferOverflowDropOldest
            new._minWaitOn = _minWaitOn
            new._yieldOn = _yieldOn
            new._syncEachWrite = _syncEachWrite
            new._fileLockEnable = _fileLockEnable
            new._fileLockTimeout = _fileLockTimeout
            new._threadPool = _threadPool
            new._formatter = _formatter
            new._formatterOmitYear = _formatterOmitYear
            new._blacklistDomain.addAll(_blacklistDomain)
            new._blacklistDomainNull = _blacklistDomainNull
            new._blacklistTag.addAll(_blacklistTag)
            new._whitelistDomain.addAll(_whitelistDomain)
            new._whitelistDomainNull = _whitelistDomainNull
            new._whitelistTag.addAll(_whitelistTag)
            new._debug = _debug
            new._warn = _warn
            return new
        }

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
         * @param [value] The name to use for the log file.
         *
         * @return The [Builder]
         *
         * @throws [IllegalArgumentException] When:
         *  - [value] is empty
         *  - [value] is greater than `64` characters in length
         *  - [value] ends with character `.`
         *  - [value] contains whitespace
         *  - [value] contains character `/`
         *  - [value] contains character `\`
         *  - [value] contains null character `\u0000`
         * */
        public fun fileName(value: String): Builder {
            require(value.isNotEmpty()) { "fileName cannot be empty" }
            require(value.length <= 64) { "fileName cannot exceed 64 characters" }
            require(!value.endsWith('.')) { "fileName cannot end with '.'" }
            value.forEach { c ->
                require(!c.isWhitespace()) { "fileName cannot contain whitespace" }
                require(c != '/') { "fileName cannot contain '/'" }
                require(c != '\\') { "fileName cannot contain '\\'" }
                require(c != '\u0000') { "fileName cannot contain null character '\\u0000'" }
            }
            _fileName = value
            return this
        }

        /**
         * DEFAULT: empty (i.e. no extension)
         *
         * Configure the log file extension name.
         *
         * @param [value] The name to use for the log file extension, or empty for no extension.
         *
         * @return The [Builder]
         *
         * @throws [IllegalArgumentException] When:
         *  - [value] is greater than `8` characters in length
         *  - [value] contains whitespace
         *  - [value] contains character `.`
         *  - [value] contains character `/`
         *  - [value] contains character `\`
         *  - [value] contains null character `\u0000`
         * */
        public fun fileExtension(value: String): Builder {
            require(value.length <= 8) { "fileExtension cannot exceed 8 characters" }
            value.forEach { c ->
                require(!c.isWhitespace()) { "fileExtension cannot contain whitespace" }
                require(c != '.') { "fileExtension cannot contain '.'" }
                require(c != '/') { "fileExtension cannot contain '/'" }
                require(c != '\\') { "fileExtension cannot contain '\\'" }
                require(c != '\u0000') { "fileExtension cannot contain null character '\\u0000'" }
            }
            _fileExtension = value
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
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun syncEachWrite(enable: Boolean): Builder = apply { _syncEachWrite = enable }

        /**
         * DEFAULT: `true` (i.e. Use [File] locks)
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun fileLock(enable: Boolean): Builder = apply { _fileLockEnable = enable }

        /**
         * DEFAULT: `-1` (i.e. Calculate a generous timeout based on other settings)
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun fileLockTimeout(millis: Long): Builder = apply { _fileLockTimeout = millis }

        /**
         * DEFAULT: `null` (i.e. Use a single, dedicated thread)
         *
         * TODO
         *
         * @return The [Builder]
         * */
        public fun thread(pool: ThreadPool?): Builder = apply { _threadPool = pool }

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
         * DEFAULT: empty (i.e. Do not deny any [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomainNull]
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun blacklistDomain(deny: String): Builder {
            Logger.checkDomain(deny)
            _blacklistDomain.add(deny)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Do not deny any [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomainNull]
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun blacklistDomain(vararg deny: String): Builder {
            deny.forEach { domain -> Logger.checkDomain(domain) }
            _blacklistDomain.addAll(deny)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Do not deny any [Logger.domain])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomainNull]
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun blacklistDomain(deny: Collection<String>): Builder {
            deny.forEach { domain -> Logger.checkDomain(domain) }
            _blacklistDomain.addAll(deny)
            return this
        }

        /**
         * DEFAULT: `false`
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomain]
         * @see [blacklistDomainReset]
         * @see [whitelistDomain]
         * @see [whitelistDomainNull]
         * @see [DOMAIN]
         * */
        public fun blacklistDomainNull(deny: Boolean): Builder = apply { _blacklistDomainNull = deny }

        /**
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistDomain]
         * @see [blacklistDomainNull]
         * @see [DOMAIN]
         * */
        public fun blacklistDomainReset(): Builder = apply { _blacklistDomain.clear() }.blacklistDomainNull(deny = false)

        /**
         * DEFAULT: empty (i.e. Do not deny any [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistTagReset]
         * @see [whitelistTag]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun blacklistTag(deny: String): Builder {
            Logger.checkTag(deny)
            _blacklistTag.add(deny)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Do not deny any [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistTagReset]
         * @see [whitelistTag]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun blacklistTag(vararg deny: String): Builder {
            deny.forEach { tag -> Logger.checkTag(tag) }
            _blacklistTag.addAll(deny)
            return this
        }

        /**
         * DEFAULT: empty (i.e. Do not deny any [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistTagReset]
         * @see [whitelistTag]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun blacklistTag(deny: Collection<String>): Builder {
            deny.forEach { tag -> Logger.checkTag(tag) }
            _blacklistTag.addAll(deny)
            return this
        }

        /**
         * TODO
         *
         * @return The [Builder]
         *
         * @see [blacklistTag]
         * */
        public fun blacklistTagReset(): Builder = apply { _blacklistTag.clear() }

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
         * @see [blacklistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun whitelistDomain(allow: String): Builder {
            Logger.checkDomain(allow)
            _whitelistDomain.add(allow)
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
         * @see [blacklistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun whitelistDomain(vararg allow: String): Builder {
            allow.forEach { domain -> Logger.checkDomain(domain) }
            _whitelistDomain.addAll(allow)
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
         * @see [blacklistDomainNull]
         * @see [DOMAIN]
         *
         * @throws [IllegalArgumentException] If [Logger.checkDomain] fails.
         * */
        public fun whitelistDomain(allow: Collection<String>): Builder {
            allow.forEach { domain -> Logger.checkDomain(domain) }
            _whitelistDomain.addAll(allow)
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
         * @see [blacklistDomain]
         * @see [blacklistDomainNull]
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
        public fun whitelistDomainReset(): Builder = apply { _whitelistDomain.clear() }.whitelistDomainNull(allow = true)

        /**
         * DEFAULT: empty (i.e. Allow all [Logger.tag])
         *
         * TODO
         *
         * @return The [Builder]
         *
         * @see [whitelistTagReset]
         * @see [blacklistTag]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun whitelistTag(allow: String): Builder {
            Logger.checkTag(allow)
            _whitelistTag.add(allow)
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
         * @see [blacklistTag]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun whitelistTag(vararg allow: String): Builder {
            allow.forEach { tag -> Logger.checkTag(tag) }
            _whitelistTag.addAll(allow)
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
         * @see [blacklistTag]
         *
         * @throws [IllegalArgumentException] If [Logger.checkTag] fails.
         * */
        public fun whitelistTag(allow: Collection<String>): Builder {
            allow.forEach { tag -> Logger.checkTag(tag) }
            _whitelistTag.addAll(allow)
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
            val blacklistTag = _blacklistTag.toImmutableSet()
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
                val minimum = if (bufferOverflowDropOldest) 32 else Channel.RENDEZVOUS
                _bufferCapacity.coerceAtLeast(minimum)
            }

            val yieldOn = _yieldOn.coerceIn(1, 10)
            val maxLogFileSize = _maxLogFileSize.coerceAtLeast(50L * 1024L) // 50kb

            var fileLockTimeout = _fileLockTimeout
            if (fileLockTimeout <= 0L) fileLockTimeout = run {
                // Values below are total guesstimates, complete fiction.
                //
                // With all Builder defaults, comes out to ~2500ms
                var process = 2L                // Millis to process 1 LogAction.Write
                process *= yieldOn              // Number of LogAction.Write other Process may do before yielding
                process += 10L                  // Millis post-processing FileStream.sync might take

                var rotate = maxLogFileSize
                rotate /= DEFAULT_BUFFER_SIZE   // Number of FileStream.{read/write} to atomically copy log file
                rotate *= 2L                    // Millis to complete each FileStream.{read/write}
                rotate += 10L                   // Millis to FileStream.sync .tmp (before closing)
                rotate += 2L                    // Millis to move from .tmp -> .next
                rotate += 5L                    // Millis to FileStream.sync log file truncation
                rotate += 10L                   // Rotation logic overhead

                var move = files.size.toLong()
                move *= 2L                      // Millis to move 1 archived log
                move += 10L                     // Move logic overhead

                process + rotate + move
            }

            fileLockTimeout = if (!_fileLockEnable) FILE_LOCK_DISABLED else {
                fileLockTimeout.coerceIn(375L, Duration.INFINITE.inWholeMilliseconds)
            }

            return FileLog(
                min = min,
                max = _max,
                directory = directory,
                files = files.toImmutableList(),
                files0Hash = files0Hash,
                maxLogFileSize = maxLogFileSize,
                modeDirectory = _modeDirectory.build(),
                modeFile = _modeFile.build(),
                bufferCapacity = bufferCapacity,
                bufferOverflowDropOldest = bufferOverflowDropOldest,
                minWaitOn = minWaitOn,
                yieldOn = yieldOn,
                syncEachWrite = _syncEachWrite,
                fileLockTimeout = fileLockTimeout,
                threadPool = _threadPool as? RealThreadPool,
                formatter = _formatter,
                formatterOmitYear = _formatterOmitYear,
                blacklistDomain = blacklistDomain,
                blacklistDomainNull = _blacklistDomainNull,
                blacklistTag = blacklistTag,
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

    private val _blacklistDomain: Array<String>
    private val _whitelistDomain: Array<String>
    private val _blacklistTag: Array<String>
    private val _whitelistTag: Array<String>

    private val LOG: Logger

    private val scopeFileLog: ScopeFileLog
    // Lazy, so nothing is initialized until time of onInstall
    private val allocator: Lazy<LogDispatcherAllocator>

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
        syncEachWrite: Boolean,
        fileLockTimeout: Long,
        threadPool: RealThreadPool?,
        formatter: Formatter,
        formatterOmitYear: Boolean,
        blacklistDomain: Set<String>,
        blacklistDomainNull: Boolean,
        blacklistTag: Set<String>,
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

        this._blacklistTag = blacklistTag.toTypedArray()
        this._whitelistTag = whitelistTag.toTypedArray()

        this.debug = debug
        this.warn = warn
        this.LOG = Logger.of(tag = uidSuffix, DOMAIN)

        this.scopeFileLog = ScopeFileLog(uidSuffix, handler = CoroutineExceptionHandler handler@ { context, t ->
            if (t is CancellationException) return@handler // Ignore...
            logE(t) { context }
        })
        this.allocator = threadPool?.allocator ?: lazy {
            object : LogDispatcherAllocator(LOG) {
                override fun doAllocation(): LogDispatcher = newLogDispatcher(nThreads = 1, name = LOG.tag)
                override fun debug(): Boolean = this@FileLog.debug
            }
        }
        // For logLoop's RotateActionQueue
        this.checkIfLogRotationIsNeeded = LogAction.Rotation.newCheckAction(maxLogFileSize, dotRotateFile)

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
        this.syncEachWrite = syncEachWrite
        this.fileLockTimeout = fileLockTimeout
        this.blacklistDomain = blacklistDomain
        this.blacklistDomainNull = blacklistDomainNull
        this.blacklistTag = blacklistTag
        this.whitelistDomain = whitelistDomain
        this.whitelistDomainNull = whitelistDomainNull
        this.whitelistTag = whitelistTag
    }

    override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
        // domain
        if (domain == null) {
            if (blacklistDomainNull) return false
            if (!whitelistDomainNull) return false
        } else {
            if (domain == DOMAIN) {
                // Do NOT log to self, only to other Log instances (if installed).
                if (tag == LOG.tag) return false
                // Do NOT allow debug logs from other FileLog instances. This would
                // be severely problematic as FileLog.log exempts DOMAIN from blocking.
                if (level <= Level.Debug) return false
            }

            if (_blacklistDomain.contains(domain)) return false
            if (_whitelistDomain.isNotEmpty() && !_whitelistDomain.contains(domain)) return false
        }

        // tag
        if (_blacklistTag.contains(tag)) return false
        if (_whitelistTag.isNotEmpty() && !_whitelistTag.contains(tag)) return false

        return _logHandle._get()?.second?.supervisorJob?.isActive ?: false
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
            private var _guard = 0

            override fun drop(warn: Boolean) {
                check(_guard++ == 0) { _guard--; "LogAction.Write has already been executed" }

                preprocessing.cancel()
                logWait?.failure()
                _pendingLogCount._decrementAndGet()
                if (warn) logW {
                    "Dropped 1 log(s) >> ${_pendingLogCount._get()} log(s) are currently pending processing."
                }
            }

            override suspend fun invoke(
                stream: FileStream.ReadWrite,
                buf: ByteArray,
                sizeLog: Long,
                processedWrites: Int,
            ): Long {
                check(_guard++ == 0) { _guard--; "LogAction.Write has already been executed" }

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
                        stream.doSync(log = false, threw = threw)
                    }

                    _pendingLogCount._decrementAndGet()
                    logWait?.failure()

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
                    _guard--
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
                }

                if (syncEachWrite || level == Level.Fatal) {
                    stream.doSync(log = level != Level.Fatal, threw = threw)
                }

                _pendingLogCount._decrementAndGet()
                threw?.let { t ->
                    logWait?.failure()
                    throw t
                }

                logWait?.success()
                return written
            }
        }

        val trySendResult = logBuffer.channel.trySend(logAction)
        val logSend = if (trySendResult.isSuccess) null else {
            // Failure
            if (trySendResult.isClosed) {
                logWait?.failure()
                preprocessing.cancel()
                return false
            }
            if (logBuffer != _logHandle._get()?.first) {
                // Log.Root.uninstall was called between the time log was invoked and now
                logWait?.failure()
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

        _pendingLogCount._incrementAndGet()
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

    override fun onInstall() {
        _onInstallInvocations++

        // Because runBlocking is being utilized by FileLog.log, we must always specify a
        // CoroutineDispatcher of our own. If we were to use Dispatchers.IO for everything,
        // then it could result in a deadlock if caller is also using Dispatchers.IO whereby
        // thread starvation could occur and LogLoop is unable to yield or launch LogRotation.
        val (dispatcher, dispatcherDeRef) = allocator.value.getOrAllocate()

        val logBuffer = LogBuffer(
            capacity = bufferCapacity,
            overflow = if (bufferOverflowDropOldest) BufferOverflow.DROP_OLDEST else BufferOverflow.SUSPEND,
        )
        val previousLogJob = _logJob

        @OptIn(DelicateCoroutinesApi::class)
        scopeFileLog.launch(dispatcher, start = CoroutineStart.ATOMIC) {
            val logJob = currentCoroutineContext().job
            logJob.invokeOnCompletion { logD { "$LOG_JOB Stopped >> $logJob" } }
            logD { "$LOG_JOB Started >> $logJob" }

            if (previousLogJob != null) {
                logD {
                    if (!previousLogJob.isActive) null
                    else "Cancelling and waiting for previous $LOG_JOB to complete >> $previousLogJob"
                }
                awaitAndCancel(previousLogJob, timeout = 25.milliseconds, canceledBy = { "new $LOG_JOB >> $logJob" })
            }

            val wasDirectoryCreated = try {
                directory.mkdirs2(mode = modeDirectory, mustCreate = true)
                logD { "Created directory (and any required subdirectories) ${directory.name}" }
                true
            } catch (_: FileAlreadyExistsException) {
                false
            }

            if (!wasDirectoryCreated) {
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

            // Directory permissions are not a thing on Windows.
            if (!wasDirectoryCreated && SysFsInfo.isPosix) try {
                directory.chmod2(mode = modeDirectory)
                logD { "Applied permissions $modeDirectory to directory ${directory.name}" }
            } catch (e: IOException) {
                // Try continuing such that failure occurs at lock/log file open.
                logW(e) { "Failed to apply permissions $modeDirectory to directory ${directory.name}" }
            }

            if (!wasDirectoryCreated) try {
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

            logJob.ensureActive()

            val lockFile = if (fileLockTimeout == FILE_LOCK_DISABLED) StubLockFile
            else dotLockFile.openLockFileRobustly(::logNonEmptyDirectoryMoved)

            val lockFileCompletion = logJob.closeOnCompletion(lockFile)

            if (!wasDirectoryCreated) try {
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

            logJob.ensureActive()
            val logStream = files[0].openLogFileRobustly(modeFile)
            val logStreamCompletion = logJob.closeOnCompletion(logStream)

            logBuffer.logLoop(
                lockFile,
                lockFileCompletion,
                logStream,
                logStreamCompletion,
            )
        }.let { logJob ->
            logJob.invokeOnCompletion { t ->
                logBuffer.channel.close()
                var count = 0L
                try {
                    while (true) {
                        val logAction = logBuffer.channel.tryReceive().getOrNull() ?: break
                        count++
                        logAction.drop(warn = false)
                    }
                } finally {
                    logBuffer.channel.cancel()
                }
                if (count > 0L) logW(t) { "Dropped $count log(s)" }
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
    * remaining LogAction.Write.
    * */
    private suspend fun LogBuffer.logLoop(
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

        // For tracking failures across successive rotations such that errors can be
        // raised to shut down exceptionally after a certain number, instead of just
        // infinitely looping.
        val rotationState = RotationState()

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
            retryAction._getAndSet(new = null)?.drop(warn = true)
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
                    ?: channel.receive()
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
            // Will be valid if it was not released due to retryAction containing a
            // cached LogAction.Write, or if it's a StubFileLock because file locking
            // has been disabled (StubFileLock is always valid).
            lockLog = if (lockLog.isValid()) lockLog else CurrentThread.uninterrupted {
                try {
                    lockFile.lockNonBlock(
                        position = FILE_LOCK_POS_LOG,
                        size = FILE_LOCK_SIZE,
                        timeout = fileLockTimeout.milliseconds,
                    )
                } catch (t: Throwable) {
                    // Could be an OverlappingFileLockException on Jvm/Android. Someone
                    // within this process may be holding a lock on our requested range
                    // (maliciously?). By closing and re-opening the file, this should
                    // invalidate all other locks held by this process (which we should
                    // be the ONLY ones acquiring).
                    //
                    // Alternatively, lockFile was previously closed due to a release
                    // failure requiring a re-open.

                    // If we timed out, that's a showstopper.
                    if (t.cause is TimeoutCancellationException) {
                        logAction.drop(warn = true)
                        jobLogLoop.ensureActive()
                        throw t
                    }

                    if (lockFile.isOpen()) {
                        try {
                            // If a log rotation is currently underway, we must wait for it
                            // so that we do not invalidate its lockRotate inadvertently.
                            awaitLogRotation()
                        } catch (t: CancellationException) {
                            logAction.drop(warn = true)
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
                        // Not a showstopper. Ignore.
                    }

                    try {
                        // Should NEVER be a StubLockFile, but just in case...
                        logD(ee) {
                            if (lockFile == StubLockFile) null
                            else "Closed >> $lockFile"
                        }
                        jobLogLoop.ensureActive()

                        lockFile = if (lockFile == StubLockFile) lockFile
                        else dotLockFile.openLockFileRobustly(::logNonEmptyDirectoryMoved)

                        lockFileCompletion = jobLogLoop.closeOnCompletion(lockFile)
                        lockFile.lockNonBlock(
                            position = FILE_LOCK_POS_LOG,
                            size = FILE_LOCK_SIZE,
                            timeout = fileLockTimeout.milliseconds,
                        )
                    } catch (tt: Throwable) {
                        // Total failure. Close up shop.
                        logAction.drop(warn = true)
                        if (tt is CancellationException) throw tt
                        if (tt.cause is TimeoutCancellationException) jobLogLoop.ensureActive()
                        t.addSuppressed(tt)
                        throw t
                    }
                }.also { lock ->
                    logD {
                        if (lock is StubFileLock) null
                        else "Acquired lock on ${dotLockFile.name} >> $lock"
                    }
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
                        // Not a showstopper. Ignore.
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
                        logAction.drop(warn = true)
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

            // The inner loop
            while (true) {
                val action = logAction ?: break

                val written = try {
                    CurrentThread.uninterrupted {
                        action.invoke(logStream, buf, size, processedWrites)
                    }
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
                } else when (written) { // Check special negative return values.
                    EXECUTE_ROTATE_LOGS -> run {
                        size = maxLogFileSize // Force a log rotation
                        if (action !is LogAction.Write) return@run

                        val previous = retryAction._getAndSet(new = action)
                        if (previous != null) {
                            previous.drop(warn = true)
                            // HARD fail.... There should ONLY ever be 1 retryAction.
                            throw IllegalStateException("retryAction's previous value was non-null")
                        }
                        logD { "Write would exceed maxLogFileSize[$maxLogFileSize]. Retrying after $LOG_ROTATION." }
                    }
                    ROTATION_NOT_NEEDED -> logD { "$LOG_ROTATION not needed" }
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
                            ?: channel.tryReceive().getOrNull()
                    } catch (_: CancellationException) {
                        // Shouldn't happen b/c just checked isActive, but if so
                        // we want to ensure we pop out for logStream.sync. The
                        // main loop will re-throw the exception when it yields.
                        null
                    }
                }
            }

            if (processedWrites > 0) {
                if (!syncEachWrite) logStream.doSync(sync = {
                    CurrentThread.uninterrupted { sync(meta = true) }
                })

                logD { "Processed $processedWrites log(s)" }
            }

            if (jobLogLoop.isActive && size >= maxLogFileSize) {
                if (lockLog.isValid()) {
                    rotateLogs(
                        state = rotationState,
                        rotateActionQueue = rotateActionQueue,
                        logStream = logStream,
                        lockFile = lockFile,
                        buf = buf,
                        retryActionIsNotNull = retryAction._get() != null,
                    )
                } else {
                    // We lost lockLog. Trigger an immediate retry after re-acquiring lockLog.
                    rotateActionQueue.enqueue(
                        if (retryAction._get() != null) LogAction.Rotation.ExecuteAction else checkIfLogRotationIsNeeded
                    )
                }
            }

            // Do not release lockLog if there is a retry LogAction. It will be
            // immediately dequeued and executed while still holding our lock
            // on writes to logStream (unless there are LogAction present in the
            // rotateActionQueue which come first).
            if (retryAction._get() == null) lockLog.doRelease(onFailure = { e ->
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
            })
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
    @Throws(CancellationException::class, IOException::class)
    private suspend fun ScopeLogLoop.rotateLogs(
        state: RotationState,
        rotateActionQueue: RotateActionQueue,
        logStream: FileStream.ReadWrite,
        lockFile: LockFile,
        buf: ByteArray,
        retryActionIsNotNull: Boolean,
    ): Unit = CurrentThread.uninterrupted {
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
            action(logStream, buf, 0L, processedWrites = CONSUME_AND_IGNORE)
        }

        val lockRotate = try {
            lockFile.lockNonBlock(
                position = FILE_LOCK_POS_ROTATE,
                size = FILE_LOCK_SIZE,
                timeout = fileLockTimeout.milliseconds,
            )
        } catch (t: Throwable) {
            // Could be an OverlappingFileLockException on Jvm/Android. Someone
            // within this process may be holding a lock on our requested range
            // (maliciously?). By closing and re-opening the file, this should
            // invalidate all other locks held by this process (which we should
            // be the ONLY ones acquiring).
            //
            // Alternatively, lockFile was previously closed due to a release
            // failure and requires a re-open.

            // If we timed out, that's a showstopper.
            if (t.cause is TimeoutCancellationException) {
                jobLogLoop.ensureActive()
                throw t
            }

            try {
                // Close the lock file (if not already). Next logLoop iteration
                // will fail to obtain its lock due to a ClosedException and then
                // re-open lockFile to retry acquisition.
                lockFile.close()
            } catch (e: IOException) {
                t.addSuppressed(e)
            }

            if (state.lockAcquisitionFailures++ > (MAX_RETRIES - 1)) {
                throw state.failureIOException(
                    attempts = state.lockAcquisitionFailures,
                    reason = "acquire a rotation lock on ${dotLockFile.name}",
                    cause = t,
                )
            }

            logW(t) { "Failed to acquire a rotation lock on ${dotLockFile.name}. Retrying $LOG_ROTATION." }

            // Trigger an immediate retry.
            rotateActionQueue.enqueue(
                if (retryActionIsNotNull) LogAction.Rotation.ExecuteAction else checkIfLogRotationIsNeeded
            )

            return
        }

        state.lockAcquisitionFailures = 0

        logD {
            if (lockRotate is StubFileLock) null
            else "Acquired lock on ${dotLockFile.name} >> $lockRotate"
        }

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

        // Storage. prepareLogRotationFull will open the directory to sync it
        // after atomically copying logStream. Will populate this with either
        // the Pair (success), or null (failure to open). If prepareLogRotationFull
        // is NOT called below, this will be empty and, as such, the directory
        // will be opened before executing any moves in order to pass to that
        // Job.
        val openDir = ArrayList<Pair<Directory, DisposableHandle>?>(1)

        if (dotRotateFile.exists2Robustly()) {
            // Picking up a previously interrupted log file rotation (moving
            // dotRotateFile -> *.001 is the last move that gets executed).
            prepareLogRotationInterrupted(state, logStream, buf, moves)

            // If was successful (moves is populated), logStream may not have
            // been truncated and may still need to have another full log rotation
            // done. The current state could also be that there is a LogAction
            // present still in the retryAction. In any event, the LogAction.Write
            // produced by FileLog.log will return EXECUTE_ROTATE_LOGS again where
            // we'll go through another log rotation. Next time, however, dotRotateFile
            // should have already been moved into place and not exist on the filesystem
            // anymore.
        } else {
            // Full log rotation. Check if it's actually necessary.

            // If a LogAction.Write returned EXECUTE_ROTATE_LOGS, it wants a log
            // rotation so it can fit its log in there; fake it till we make it.
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
                -1L
            }

            if (size < maxLogFileSize) {
                if (size != -1L) {
                    // Was no error from above when obtaining FileStream.size.
                    // size really is less than maxLogFileSize and there is
                    // nothing to do. Reset state.
                    state.atomicCopyFailures = 0
                    state.comparisonFailures = 0
                    state.moveFailures = 0
                }

                lockRotate.doRelease(onFailure = { e ->
                    try {
                        // Close the lock file (if not already). Next logLoop iteration
                        // will fail to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (ee: IOException) {
                        e.addSuppressed(ee)
                    }
                })

                // No further action is needed. Return early.
                return
            }

            prepareLogRotationFull(state, openDir, logStream, buf, moves)
        }

        if (moves.isEmpty()) {
            // An error occurred in prepareLogRotation{Full/Interrupted}

            lockRotate.doRelease(onFailure = { e ->
                try {
                    // Close the lock file (if not already). Next logLoop iteration
                    // will fail to obtain its lock due to a ClosedException and then
                    // re-open lockFile to retry acquisition.
                    lockFile.close()
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }
            })

            // Could be unpopulated, or element 0 is null (failed to open directory).
            openDir.removeFirstOrNull()?.let { (dir, handle) ->
                val e: IOException? = try {
                    dir.close()
                    null
                } catch (e: IOException) {
                    e
                } finally {
                    handle.dispose()
                }

                logD(e) {
                    if (dir == Directory.NoOp) null
                    else "Closed >> $dir"
                }
            }

            // Trigger an immediate retry.
            rotateActionQueue.enqueue(
                if (retryActionIsNotNull) LogAction.Rotation.ExecuteAction else checkIfLogRotationIsNeeded
            )

            return
        }

        // Success. Reset.
        state.atomicCopyFailures = 0
        state.comparisonFailures = 0

        // Try opening (if not already populated)
        tryOpenLogDirectory(storage = openDir)

        val childJob = executeLogRotationMoves(state, openDir.firstOrNull()?.first, rotateActionQueue, moves)

        // Migrate completion handle to ChildJob
        openDir.removeFirstOrNull()?.let { (dir, handle) ->
            handle.dispose()
            childJob.closeOnCompletion(dir, logOpen = false)
        }

        childJob.invokeOnCompletion {
            lockRotate.doRelease(onFailure = { _ ->
                // No other recovery mechanism but to close the lock file
                // and invalidate all locks currently held, otherwise the
                // next log rotation may deadlock when attempting to acquire
                // lockRotate.
                //
                // This is done lazily here as a priority LogAction in order
                // to not inadvertently invalidate a lockLog in the midst
                // of executing a LogAction that is writing to logStream. If
                // the lockFile is already closed, it does nothing.
                rotateActionQueue.enqueue { stream, _, _, processedWrites ->
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
                    if (processedWrites > 0 && !syncEachWrite) stream.doSync()

                    try {
                        // Close the lock file (if not already). Next logLoop iteration
                        // will fail to obtain its lock due to a ClosedException and then
                        // re-open lockFile to retry acquisition.
                        lockFile.close()
                    } catch (_: Throwable) {}

                    0L
                }
            })
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
    @Throws(IOException::class)
    private fun ScopeLogLoop.prepareLogRotationFull(
        state: RotationState,
        openDir: ArrayList<Pair<Directory, DisposableHandle>?>,
        logStream: FileStream.ReadWrite,
        buf: ByteArray,
        moves: ArrayDeque<Pair<File, File>>,
    ) {
        try {
            directory.mkdirs2(mode = modeDirectory, mustCreate = false)
        } catch (_: IOException) {
            // Not a showstopper. Ignore.
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

            logNonEmptyDirectoryMoved(file, moved)
        }

        tryOpenLogDirectory(storage = openDir)

        try {
            // Shouldn't exist, but just in case using openWrite instead of openLogFileRobustly to ensure it
            // gets truncated if it does.
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

            if (state.atomicCopyFailures++ > (MAX_RETRIES - 1)) {
                throw state.failureIOException(
                    attempts = state.atomicCopyFailures,
                    reason = "atomically copy ${files[0].name} >> ${dotRotateFile.name}",
                    cause = e,
                )
            }

            logW(e) { "Failed to atomically copy ${files[0].name} >> ${dotRotateFile.name}. Retrying $LOG_ROTATION." }
            return
        }

        openDir.firstOrNull()?.let { (dir, handle) ->
            if (!dir.isOpen()) return@let // Directory.NoOp

            try {
                dir.sync()
                logD { "Synced directory ${directory.name}" }
            } catch (e: IOException) {
                val ee: IOException? = try {
                    dir.close()
                    null
                } catch (ee: IOException) {
                    ee
                } finally {
                    handle.dispose()
                }

                logW(e) { "Sync failure >> $dir" }
                logD(ee) { "Closed >> $dir" }

                // Attempt a re-open before moves get executed.
                openDir.clear()
            }
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
    @Throws(IOException::class)
    private fun prepareLogRotationInterrupted(
        state: RotationState,
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

        // Shouldn't be the case because of maxLogFileSize minimum, but just in case
        // leave it alone if it's too small.
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
        var threw: IOException? = null
        try {
            dotStream = dotRotateFile.openRobustly(
                modeFile,
                deleteOrMoveOnEISDIR = true,
                onEISDIR = { previous, moved -> if (moved != null) logNonEmptyDirectoryMoved(previous, moved) },
                open = File::openRead,
            )
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
                // valuable enough, or one that I would be comfortable with,
                // to determine the need for truncation.
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

        if (dotStream == null && threw is FileNotFoundException) {
            // dotRotateFile was a directory, was deleted or moved, and
            // the openRead retry failed b/c it no-longer exists. This
            // is a "successful" failure.
            state.comparisonFailures = 0
            moves.clear()
            return
        }

        if (state.comparisonFailures++ > (MAX_RETRIES - 1)) {
            throw state.failureIOException(
                attempts = state.comparisonFailures,
                reason = "compare ${files[0].name} with ${dotRotateFile.name}",
                cause = threw,
            )
        }

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
            logD { "Truncated ${file.name} to 0" }
            logStream.doSync(file = file, throwOnFailure = true)
        } catch (e: IOException) {
            try {
                // FileStream.Write.size may have failed and it is still open.
                // Make certain that it is closed before going forward.
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
                logD { "Truncated ${file.name} to 0" }

                s.doSync(
                    file = file,
                    // If we fail, truncation succeeded via openWrite, but our sync did
                    // not. Just go with it at this point and hope for the best. The
                    // finally block will close this FileStream.Write, so hopefully
                    // that forces it to the filesystem.
                    throwOnFailure = false,
                )
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
        state: RotationState,
        logDir: Directory?,
        rotateActionQueue: RotateActionQueue,
        moves: ArrayDeque<Pair<File, File>>,
    ): Job = scopeLogLoop.launch(context = CoroutineName(LOG.tag + "-$LOG_ROTATION")) {
        val thisJob = currentCoroutineContext().job
        thisJob.invokeOnCompletion { logD { "$LOG_ROTATION Stopped >> $thisJob" } }
        logD { "$LOG_ROTATION Started >> $thisJob" }

        var threw: IOException? = null
        var needsFinalDirectorySync = false

        while (moves.isNotEmpty()) {
            val move = moves.removeFirst()
            val (source, dest) = move

            try {
                source.moveLogTo(dest)
                logD { "Moved ${source.name} >> ${dest.name}" }

                if (logDir != null && logDir.isOpen()) {
                    try {
                        logDir.sync()
                        logD { "Synced directory ${directory.name}" }
                    } catch (e: IOException) {
                        // If we failed and there are still moves left to execute,
                        // ensure a final attempt to sync the directory is made after
                        // remaining moves execute.
                        if (moves.isNotEmpty()) needsFinalDirectorySync = true

                        try {
                            logDir.close()
                        } catch (ee: IOException) {
                            e.addSuppressed(ee)
                        }
                        logW(e) { "Sync failure >> $logDir" }
                    }
                }

                // yield only after we have our first move such that
                // there exists a "hole" in the log files. This matters
                // when picking up an interrupted log rotation to cut
                // down on work needed to figure out where in the log
                // rotation we were interrupted.
                yield()
            } catch (e: IOException) {
                // Source file did not exist. Ignore.
                if (e is FileNotFoundException) continue

                // Dest is an existing non-empty directory.
                if (e is DirectoryNotEmptyException) try {
                    // Dest is something like {logDirectory}/{fileName}{.fileExtension}.001
                    // and should NOT be a directory... Deletion should fail and end up
                    // moving it to a randomly named directory within logDirectory.
                    val moved = dest.deleteOrMoveToRandomIfNonEmptyDirectory(
                        buf = null,
                        maxNewNameLen = dotLockFile.name.length,
                    )
                    if (moved != null) logNonEmptyDirectoryMoved(e, dest, moved)
                    if (moved != null || !dest.exists2Robustly()) {
                        // Successfully cleared the obstacle. Retry moving the log archive.
                        moves.addFirst(move)
                        continue
                    }/* else {
                        // Fall through and cache the exception via threw
                    }*/
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                // Source is an existing directory, dest exists and is NOT a directory.
                if (e is NotDirectoryException) try {
                    // Not the final move. Skip and let the next iteration deal with it.
                    if (moves.isNotEmpty()) continue

                    // Final move. dotRotateFile is a directory and should NOT be one...
                    val moved = source.deleteOrMoveToRandomIfNonEmptyDirectory(
                        buf = null,
                        maxNewNameLen = dotLockFile.name.length,
                    )
                    if (moved != null) logNonEmptyDirectoryMoved(e, source, moved)
                    if (moved != null || !source.exists2Robustly()) {
                        continue
                    }/* else {
                        // Fall through and cache the exception via threw
                    }*/
                } catch (ee: IOException) {
                    e.addSuppressed(ee)
                }

                threw?.addSuppressed(e) ?: run { threw = e }
            }
        }

        if (needsFinalDirectorySync) run {
            // Will only be the case if logDir was closed due to sync failure, meaning
            // that logDir was NOT Directory.NoOp (i.e. not Windows).
            val newLogDir = try {
                directory.openDirectory()
            } catch (e: IOException) {
                logW(e) { "Failed to re-open directory >> ${directory.name}" }
                return@run
            }

            thisJob.closeOnCompletion(newLogDir)

            try {
                newLogDir.sync()
                logD { "Synced directory ${directory.name}" }
            } catch (e: IOException) {
                logW(e) { "Sync failure >> $newLogDir" }
            }

            // Will be closed by job completion.
        }

        if (threw == null) {
            state.moveFailures = 0
            return@launch
        }

        if (!dotRotateFile.exists2Robustly()) {
            state.moveFailures = 0
            logW(threw) {
                "$LOG_ROTATION experienced failure(s), but successfully moved ${dotRotateFile.name} >> ${files[1].name}"
            }
            return@launch
        }

        if (state.moveFailures++ > (MAX_RETRIES - 1)) {
            throw state.failureIOException(
                attempts = state.moveFailures,
                reason = "move ${dotRotateFile.name} >> ${files[1].name}",
                cause = threw,
            )
        }

        logW(threw) { "Failed to move ${dotRotateFile.name} >> ${files[1].name}. Retrying $LOG_ROTATION." }
        rotateActionQueue.enqueue(checkIfLogRotationIsNeeded)
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

    private class RotationState {

        @Volatile
        var lockAcquisitionFailures: Int = 0
        @Volatile
        var atomicCopyFailures: Int = 0
        @Volatile
        var comparisonFailures: Int = 0
        @Volatile
        var moveFailures: Int = 0

        fun failureIOException(attempts: Int, reason: String, cause: Throwable?): IOException {
            return IOException("$LOG_ROTATION failure. Failed $attempts times to $reason", cause)
        }
    }

    @Throws(IOException::class) // Only if throwOnFailure == true
    @OptIn(ExperimentalContracts::class)
    private inline fun FileStream.Write.doSync(
        log: Boolean = true,
        threw: Throwable? = null,
        file: File = files[0],
        throwOnFailure: Boolean = false,
        sync: FileStream.Write.() -> Unit = { sync(meta = true) },
    ) {
        contract { callsInPlace(sync, InvocationKind.AT_MOST_ONCE) }
        if (!isOpen()) return

        try {
            sync()
        } catch (e: IOException) {
            // Try to force it by closing. logStream will be
            // re-opened on the next logLoop iteration.
            try {
                close()
            } catch (ee: IOException) {
                e.addSuppressed(ee)
            }
            if (log) logW(e) { "Sync failure >> $this" }
            threw?.addSuppressed(e)
            if (throwOnFailure) throw e
            return
        }

        if (log) logD { "Synced ${file.name}" }
    }

    @OptIn(ExperimentalContracts::class)
    private inline fun FileLock.doRelease(onFailure: (e: IOException) -> Unit) {
        contract { callsInPlace(onFailure, InvocationKind.AT_MOST_ONCE) }
        if (!isValid()) return

        try {
            release()
        } catch (e: IOException) {
            onFailure(e)
            logW(e) { "Lock release failure >> $this" }
            return
        }

        logD {
            if (this is StubFileLock) null
            // Assume lock belongs to active lock file...
            else "Released lock on ${dotLockFile.name} >> $this"
        }
    }

    private fun ScopeLogLoop.tryOpenLogDirectory(storage: ArrayList<Pair<Directory, DisposableHandle>?>) = try {
        if (storage.isEmpty()) {
            val dir = directory.openDirectory()
            val handle = jobLogLoop.closeOnCompletion(dir)
            storage.add(dir to handle)
        }
        Unit
    } catch (e: IOException) {
        // Indicates that an attempt to open was made, but resulted in failure.
        // Subsequent invocations of tryOpenLogDirectory for the same storage will
        // simply return and not try again.
        storage.add(null)
        logW(e) { "Failed to open directory >> ${directory.name}" }
    }

    private object NoOpDisposableHandle: DisposableHandle { override fun dispose() {} }

    private fun Job.closeOnCompletion(closeable: Closeable, logOpen: Boolean = true): DisposableHandle {
        if (closeable == StubLockFile) return NoOpDisposableHandle
        if (closeable == Directory.NoOp) return NoOpDisposableHandle

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

    private fun logNonEmptyDirectoryMoved(previous: File, moved: File): Int {
        return logNonEmptyDirectoryMoved(t = null, previous, moved)
    }

    private fun logNonEmptyDirectoryMoved(t: Throwable?, previous: File, moved: File): Int = logW(t) {
        "Moved non-empty directory (which should NOT be there) ${previous.name} >> ${moved.name}"
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
}
