package services

fun <T> List<T>.combinationsUpTo(): List<List<T>> {
    val result = mutableListOf<List<T>>()
    for (r in 1..size) {
        result += combinations(r)
    }
    return result
}

fun <T> List<T>.combinations(r: Int): List<List<T>> {
    val result = mutableListOf<List<T>>()
    fun combine(start: Int, current: MutableList<T>) {
        if (current.size == r) {
            result.add(current.toList())
            return
        }
        for (i in start until this.size) {
            current.add(this[i])
            combine(i + 1, current)
            current.removeAt(current.size - 1)
        }
    }
    combine(0, mutableListOf())
    return result
}