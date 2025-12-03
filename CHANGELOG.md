# CHANGELOG

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
