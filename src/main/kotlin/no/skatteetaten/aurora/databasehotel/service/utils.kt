package no.skatteetaten.aurora.databasehotel.service

fun <T> measureTimeMillis(func: () -> T): Pair<Long, T> {
    val s = System.currentTimeMillis()
    val r = func()
    return System.currentTimeMillis() - s to r
}

