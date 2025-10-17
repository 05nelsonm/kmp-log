# kmp-log
[![badge-license]][url-license]
[![badge-latest]][url-latest]

[![badge-kotlin]][url-kotlin]
<!-- TODO: Uncomment when :library:file is re-enabled
[![badge-coroutines]][url-coroutines]
[![badge-kmp-file]][url-kmp-file]
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

A small, simple yet highly configurable/extensible, logging library for Kotlin Multiplatform. Inspired 
by [the OG -> Timber][url-timber], as well as the sad state of available logging libraries for Kotlin 
Multiplatform.

API docs available at [https://kmp-log.matthewnelson.io][url-docs]

### Usage

```kotlin
class MyClass {
    companion object {
        private val LOG = Log.Logger.of(tag = "MyClass")
    }

    fun doSomething(withThis: String): Int {
        var result = withThis.length
        result += LOG.v { "Something has been done with $withThis by $this" }
        result += LOG.d { "Something has been done with $withThis by $this" }
        result += LOG.i { "Something has been done with $withThis by $this" }
        result += LOG.w { "Something has been done with $withThis by $this" }
        result += LOG.e { "Something has been done with $withThis by $this" }

        // Don't do this. It is for example purposes only...
        if (!Log.AbortHandler.isInstalled) {

            // Log.Level.Fatal (i.e. 'wtf') logs will abort the process if the
            // Log.AbortHandler is installed at Log.Root (by default, it is
            // the only one).
            result += LOG.wtf {
                "This will be logged (if a Log instance is installed)," +
                "but not abort (no AbortHandler)."
            }
        }

        return result
    }
}

fun main() {
    Log.Root.install(SysLog.Default)
    val doSomethingResult = MyClass().doSomething("Hello!")

    // Ability to define a "domain", such as 'kmp-log:log', separate
    // from the tag (helpful for library developers to allow their
    // users the ability to filter by entire domains).
    val logger = Log.Logger.of(domain = "your.library:thing", tag = "Main")

    val numberOfLogInstancesThatLoggedThisThing = logger.log(
        level = Log.Level.Info,
        msg = "MyClass.doSomething returned $doSomethingResult",
        t = null,
    )
    assertEquals(1, numberOfLogInstancesThatLoggedThisThing)

    Log.Root.uninstall(SysLog.Default)
    assertEquals(0, logger.e { throw IllegalStateException("Will not happen") })

    Log.Root.install(object : Log(uid = "MyOwnLog", min = Level.Warn) {
        override fun log(
            level: Level,
            domain: String?,
            tag: String,
            msg: String?,
            t: Throwable?,
        ): Boolean {
            // ...
            return true
        }

        // Optional overrides...
        override fun isLoggable(level: Level, domain: String?, tag: String): Boolean {
            return domain == "your.library:thing"
        }
        override fun onInstall() { /* ... */ }
        override fun onUninstall() { /* ... */ }
    })

    assertEquals(2, Log.Root.installed().size)
    assertTrue(Log.AbortHandler.isInstalled)
    Log.Root.uninstallAll(evenAbortHandler = true)

    assertEquals(0, logger.wtf { StringBuilder("Nothing...") })

    Log.Root.install(Log.AbortHandler)
    Log.Root.installOrThrow(SysLog.of(min = Level.Fatal))

    assertEquals(0, logger.e(Throwable()) { "Nope.." })
    
    // Log.AbortHandler will be the final Log instance to log, which
    // will abort the process for the Log.Level.Fatal error. So, this
    // won't return, but for the example, the return value would be 2
    // as SysLog & Log.AbortHandler logged the log.
    assertEquals(2, logger.wtf { "ABORT!" })
}
```

### Get Started

<!-- TAG_VERSION -->

```kotlin
// build.gradle.kts
dependencies {
    val v = "0.1.0-alpha01"
    implementation("io.matthewnelson.kmp-log:log:$v")
    
    // If you need SysLog
    implementation("io.matthewnelson.kmp-log:sys:$v")

    // If you need to convert Log.Logger to org.slf4j.Logger
    implementation("io.matthewnelson.kmp-log:compat-slf4j:$v")

    // If you need to convert Log.Logger to io.ktor.util.logging.Logger
    implementation("io.matthewnelson.kmp-log:compat-ktor:$v")
}
```

<!-- TAG_VERSION -->
[badge-latest]: https://img.shields.io/badge/latest--release-0.1.0-alpha01-blue.svg?style=flat
[badge-license]: https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat

<!-- TAG_DEPENDENCIES -->
[badge-coroutines]: https://img.shields.io/badge/kotlinx.coroutines-1.10.2-blue.svg?logo=kotlin
[badge-kmp-file]: https://img.shields.io/badge/kmp--file-0.5.1--SNAPSHOT-blue.svg?style=flat
[badge-kotlin]: https://img.shields.io/badge/kotlin-2.2.20-blue.svg?logo=kotlin

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
[url-kmp-file]: https://github.com/05nelsonm/kmp-file
[url-kotlin]: https://kotlinlang.org
[url-latest]: https://github.com/05nelsonm/kmp-log/releases/latest
[url-license]: https://www.apache.org/licenses/LICENSE-2.0.txt
[url-timber]: https://github.com/JakeWharton/timber
