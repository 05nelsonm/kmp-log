# kmp-log
[![badge-license]][url-license]
[![badge-latest]][url-latest]

[![badge-kotlin]][url-kotlin]
<!-- TODO: Uncomment when :library:file is re-enabled
[![badge-coroutines]][url-coroutines]
[![badge-encoding]][url-encoding]
[![badge-immutable]][url-immutable]
[![badge-kmp-file]][url-kmp-file]
[![badge-kotlincrypto-hash]][url-kotlincrypto-hash]
-->

![badge-platform-android]
![badge-platform-jvm]
![badge-platform-js]
![badge-platform-js-node]
![badge-platform-wasm]
![badge-platform-wasi]
![badge-platform-android-native]
![badge-platform-linux]
![badge-platform-macos]
![badge-platform-ios]
![badge-platform-tvos]
![badge-platform-watchos]
![badge-platform-windows]

A small, simple, highly extensible logging library for Kotlin Multiplatform. Inspired 
by [Timber][url-timber], and the unfortunate state of available logging libraries for 
Kotlin Multiplatform.

By default, the only `Log` instance available from `Log.Root` is the `Log.AbortHandler`, 
which only accepts `Log.Level.Fatal` logs and will abort the process in a system dependant
manner after the log is captured. Simply `Log.Root.uninstall` it to disable. Other than
that, no logging happens unless you choose to `Log.Root.install` a `Log`.

Logger compatibility dependencies are available for:
 - [Ktor](library/compat-ktor/README.md)
 - [SLF4J](library/compat-slf4j/README.md)

API docs are available at [https://kmp-log.matthewnelson.io][url-docs]

### Usage

1) `Log.Root.install` desired `Log` instance(s) at application startup.
2) Create `Log.Logger` instances throughout your codebase and log to them.

```kotlin
class MyClass {
    private companion object {
        // Setup Log.Logger instances.
        private val LOG = Log.Logger.of(tag = "MyClass")
    }

    fun doSomething(withThis: String): Int {
        var result = withThis.length

        // Lazy logging with inline functions to mitigate
        // unnecessary String creation. If no Log instances
        // are installed to accept logs from this Log.Logger
        // and at the specified level, then nothing happens.
        //
        // Jvm/Android extension functions are also available
        // for lazy logging via String.format
        result += LOG.v { "Log.Level.Verbose >> $withThis" }
        result += LOG.d { "Log.Level.Debug   >> $withThis" }
        result += LOG.i { "Log.Level.Info    >> $withThis" }
        result += LOG.w { "Log.Level.Warn    >> $withThis" }
        result += LOG.e { "Log.Level.Error   >> $withThis" }
//        result += LOG.wtf { "Log.Level.Fatal >> $withThis" }
        return result
    }
}

fun main() {
    // Install desired Log implementation(s) at startup
    Log.Root.install(SysLog.Debug)

    val doSomethingResult = MyClass().doSomething("Hello!")

    // Optionally, define a Log.Logger.domain, separate from its tag.
    // This is useful for library developers as users can receive
    // granular insight into the library's interworkings, if and only
    // if there is a Log instance installed.
    //
    // The domain can be used by Log.isLoggable implementations to either
    // blacklist or whitelist logging for the entire domain.
    val logger = Log.Logger.of(tag = "Main", domain = "my-library:submodule")

    // Return values of all Log.Logger functions indicate if logging happened.
    val numberOfLogInstancesThatLoggedThisThing = logger.log(
        level = Log.Level.Info,
        msg = "MyClass.doSomething returned $doSomethingResult",
    )
    assertEquals(1, numberOfLogInstancesThatLoggedThisThing)

    Log.Root.uninstall(SysLog.Debug)
    assertEquals(0, logger.e { throw IllegalStateException("Won't happen...") })

    // Create your own Log implementation(s) and install them.
    Log.Root.install(object : Log(uid = "MyOwnLog", min = Level.Warn) {
        // See docs.
        override fun log(
            level: Level,
            domain: String?,
            tag: String,
            msg: String?,
            t: Throwable?,
        ): Boolean {
            var wasLogged = true
            // Format & log data. If something happened and
            // no data could be logged, return false instead.
            return wasLogged
        }

        // Optional override... See docs.
        override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
            // e.g. Whitelist logging to this Log instance by specific domain
            return domain == "my-library:submodule"
        }
        // Optional override... See docs.
        override fun onInstall() { /* allocate resources */ }
        // Optional override... See docs.
        override fun onUninstall() { /* deallocate resources */ }
    })

    // Log.Level.Fatal logging that works how you want it to. By default,
    // Log.AbortHandler (if installed) is always the last Log instances
    // that gets logged to and only accepts Log.Level.Fatal logs. Simply
    // uninstall it to disable.
    assertEquals(2, Log.Root.installed().size)
    assertTrue(Log.AbortHandler.isInstalled)
    Log.Root.uninstallAll(evenAbortHandler = true)

    assertEquals(0, logger.wtf { StringBuilder("Nothing...") })

    Log.Root.install(Log.AbortHandler)
    Log.Root.installOrThrow(SysLog.of(min = Level.Fatal))

    assertEquals(0, logger.e(Throwable()) { "Still nothing.." })

    
    // This won't return because Log.AbortHandler will abort the process,
    // but for the sake of the example the return value would be 2, as
    // SysLog & Log.AbortHandler logged the log.
    assertEquals(2, logger.wtf { "ABORT!" })

    Log.Root.uninstallAll(evenAbortHandler = false)

    // If no other Log instances captured the Log.Level.Fatal log, then
    // Log.AbortHandler will create an exception and use Throwable.printStackTrace
    // before aborting.
    assertEquals(1, logger.wtf { "ABORT AGAIN!" })
}
```

### Get Started

<!-- TAG_VERSION -->

```kotlin
// build.gradle.kts
dependencies {
    val vKmpLog = "0.1.0-alpha02"
    implementation("io.matthewnelson.kmp-log:log:$vKmpLog")
    
    // If you need SysLog
    implementation("io.matthewnelson.kmp-log:sys:$vKmpLog")

    // If you need to convert Log.Logger to org.slf4j.Logger
    implementation("io.matthewnelson.kmp-log:compat-slf4j:$vKmpLog")

    // If you need to convert Log.Logger to io.ktor.util.logging.Logger
    implementation("io.matthewnelson.kmp-log:compat-ktor:$vKmpLog")
}
```

<!-- TAG_VERSION -->
[badge-latest]: https://img.shields.io/badge/latest--release-0.1.0--alpha02-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-coroutines]: https://img.shields.io/badge/kotlinx.coroutines-1.10.2-blue.svg?logo=kotlin
[badge-encoding]: https://img.shields.io/badge/encoding-2.5.0-blue.svg?style=flat
[badge-immutable]: https://img.shields.io/badge/immutable-0.3.0-blue.svg?style=flat
[badge-kmp-file]: https://img.shields.io/badge/kmp--file-0.5.1--SNAPSHOT-blue.svg?style=flat
[badge-kotlin]: https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin
[badge-kotlincrypto-hash]: https://img.shields.io/badge/KotlinCrypto.hash-0.8.0-blue.svg?style=flat

<!-- TAG_PLATFORMS -->
[badge-platform-android]: http://img.shields.io/badge/-android-6EDB8D.svg?style=flat
[badge-platform-jvm]: http://img.shields.io/badge/-jvm-DB413D.svg?style=flat
[badge-platform-js]: http://img.shields.io/badge/-js-F8DB5D.svg?style=flat
[badge-platform-js-node]: https://img.shields.io/badge/-nodejs-68a063.svg?style=flat
[badge-platform-linux]: http://img.shields.io/badge/-linux-2D3F6C.svg?style=flat
[badge-platform-macos]: http://img.shields.io/badge/-macos-111111.svg?style=flat
[badge-platform-ios]: http://img.shields.io/badge/-ios-CDCDCD.svg?style=flat
[badge-platform-tvos]: http://img.shields.io/badge/-tvos-808080.svg?style=flat
[badge-platform-watchos]: http://img.shields.io/badge/-watchos-C0C0C0.svg?style=flat
[badge-platform-wasm]: https://img.shields.io/badge/-wasm-624FE8.svg?style=flat
[badge-platform-wasi]: https://img.shields.io/badge/-wasi-18a033.svg?style=flat
[badge-platform-windows]: http://img.shields.io/badge/-windows-4D76CD.svg?style=flat
[badge-platform-android-native]: http://img.shields.io/badge/-android--native-6EDB8D.svg?style=flat

[url-docs]: https://kmp-log.matthewnelson.io
[url-coroutines]: https://github.com/Kotlin/kotlinx.coroutines
[url-encoding]: https://github.com/05nelsonm/encoding
[url-immutable]: https://github.com/05nelsonm/immutable
[url-kmp-file]: https://github.com/05nelsonm/kmp-file
[url-kotlin]: https://kotlinlang.org
[url-kotlincrypto-hash]: https://github.com/KotlinCrypto/hash
[url-latest]: https://github.com/05nelsonm/kmp-log/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0.txt
[url-timber]: https://github.com/JakeWharton/timber
