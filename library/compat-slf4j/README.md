# Module compat-slf4j

Convert a `Log.Logger` to an `org.slf4j.Logger` for compatibility purposes.

```kotlin
import io.matthewnelson.kmp.log.compat.slf4j.KmpLogSLF4JLogger.Compat.asSLF4JLogger

fun main() {
    val slf4jLogger = Log.Logger(tag = "MyLogger").asSLF4JLogger()
    val kmpLogLogger = slf4jLogger.delegate
}
```
