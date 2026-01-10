# test-file-lock

For testing `:library:file` advisory lock behavior.

- Argument `1`: Path to the file to try locking
    - Must be absolute
    - Must exist
    - Must have read/write permissions
- Argument `2`: Timeout in milliseconds
    - Must be greater than or equal to `150`
    - Must be less than or equal to `2000`
- Argument `3`: FileLock start position to lock
    - Must be greater than or equal to `0`
- Argument `4`: FileLock size to lock
    - Must be greater than or equal to `0`
    - If `0`, byte-range `position` to `Long.MAX_VALUE` will be locked
    - `position` + `size` must not be negative (overflow)

```
./gradlew :test-file-lock:assembleFatJar
java -jar test-file-lock/build/libs/test-file-lock.jar "/path/to/file" "500" "0" "2"
```

Will attempt to lock the file for `timeout` milliseconds specified, from `position` to 
`size`. If successful, will sleep for remainder of `timeout` while holding the lock,
release it, sleep for an additional 100ms, then exit.

Success output
```
ACQUIRED[{position}, {size}]
RELEASING[{position}, {size}]
RELEASED[{position}, {size}]
```

Failure output
```
FAILURE[{position}, {size}]
```
