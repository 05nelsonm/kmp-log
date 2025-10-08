module io.matthewnelson.kmp.log.file {
    requires transitive io.matthewnelson.kmp.log;
    requires io.matthewnelson.kmp.file.async;
    requires kotlinx.coroutines.core;

    exports io.matthewnelson.kmp.log.file;
}
