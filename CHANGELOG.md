# CHANGELOG

## Version 0.1.2 (2026-02-03)
 - Fixes race condition in Kotlin/Native `Lock` implementation [[#114]][114] [[#116]][116]
 - Adds `Log.Root.uninstallAndGet` API [[#55]][55]
 - Documentation improvements [[#79]][79] [[#117]][117]

## Version 0.1.1 (2025-12-23)
 - Mitigate unnecessary `List` allocations when `Log.Root.installed` is called by persisting 
   and returning an immutable one [[#36]][36]
 - Implements buffered writing of `UTF-8` for `SysLog` `WasmWasi` [[#38]][38]
 - Adds `Log.Root.installedLevels` function for retrieving a more refined set than 
   `Log.Level.entries` to use with module `:compat-ktor` non-jvm implementations [[#40]][40]
 - Adds `KMP_LOG_LOCAL_DATE_TIME_SIZE` definition and documentation to `local_date_time` 
   cinterop [[#41]][41]
 - Adds Logger instance caching to modules `:compat-slf4j` and `:compat-ktor` [[#43]][43]

## Version 0.1.0 (2025-12-03)
 - Update dependencies [[#34]][34]:
     - Kotlin `2.2.20` -> `2.2.21`

## Version 0.1.0-alpha02 (2025-10-27)
 - Refactor `SysLog` public API to mitigate misuse/misunderstanding [[#33]][33]
     - `SysLog` modality was changed from `open class` to `final class`
     - `SysLog` companion object name `Default` was removed (now is just `Companion`)
     - `SysLog` companion object is no-longer an instance of `SysLog`
     - `SysLog.Companion.Debug` was added so that it is clear to all API consumers
       what the `Log.min` level is.

## Version 0.1.0-alpha01 (2025-10-19)
 - Initial Release

[33]: https://github.com/05nelsonm/kmp-log/pull/33
[34]: https://github.com/05nelsonm/kmp-log/pull/34
[36]: https://github.com/05nelsonm/kmp-log/pull/36
[38]: https://github.com/05nelsonm/kmp-log/pull/38
[40]: https://github.com/05nelsonm/kmp-log/pull/40
[41]: https://github.com/05nelsonm/kmp-log/pull/41
[43]: https://github.com/05nelsonm/kmp-log/pull/43
[55]: https://github.com/05nelsonm/kmp-log/pull/55
[79]: https://github.com/05nelsonm/kmp-log/pull/79
[114]: https://github.com/05nelsonm/kmp-log/pull/114
[116]: https://github.com/05nelsonm/kmp-log/pull/116
[117]: https://github.com/05nelsonm/kmp-log/pull/117
