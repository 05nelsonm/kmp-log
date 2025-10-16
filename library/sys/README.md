# Module sys

A `Log` implementation that formats, then prints the logs.

e.g.

```kotlin
fun main() {
    Log.Root.install(SysLog.Default)
    Log.Root.uninstall(log = SysLog.Default)
    Log.Root.install(SysLog.of(min = Level.Warn))
    Log.Root.uninstall(uid = SysLog.UID)
}
```
