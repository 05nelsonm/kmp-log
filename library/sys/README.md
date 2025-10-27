# Module sys

A `Log` implementation that formats, then prints logs to system locations.

e.g.

```kotlin
fun main() {
    Log.Root.install(SysLog.Debug)
    Log.Root.uninstall(log = SysLog.Debug)
    Log.Root.install(SysLog.of(min = Level.Warn))
    Log.Root.uninstall(uid = SysLog.UID)
}
```
