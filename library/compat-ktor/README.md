# Module compat-ktor

Convert a `Log.Logger` to a `io.ktor.util.logging.Logger` for compatibility purposes.

```kotlin
import io.matthewnelson.kmp.log.compat.ktor.asKtorLogger

fun main() {
    val ktorLogger = Log.Logger(tag = "MyLogger").asKtorLogger()
    val kmpLogLogger = ktorLogger.delegate
}
```
