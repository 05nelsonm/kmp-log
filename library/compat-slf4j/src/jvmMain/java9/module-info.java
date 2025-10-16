module io.matthewnelson.kmp.log.compat.slf4j {
    requires transitive io.matthewnelson.kmp.log;
    requires transitive org.slf4j;

    exports io.matthewnelson.kmp.log.compat.slf4j;
}
