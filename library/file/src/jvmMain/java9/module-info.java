module io.matthewnelson.kmp.log.file {
    requires io.matthewnelson.encoding.base16;
    requires io.matthewnelson.immutable.collections;
    requires transitive io.matthewnelson.kmp.log;
    requires io.matthewnelson.kmp.file.async;
    requires kotlinx.coroutines.core;
    requires org.kotlincrypto.hash.blake2;

    exports io.matthewnelson.kmp.log.file;
}
