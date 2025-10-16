module io.matthewnelson.kmp.log.compat.ktor {
    requires transitive io.ktor.utils;
    requires transitive io.matthewnelson.kmp.log;
    requires transitive io.matthewnelson.kmp.log.compat.slf4j;

    exports io.matthewnelson.kmp.log.compat.ktor;
}
