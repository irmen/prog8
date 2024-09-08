package prog8tests.helpers

fun <T, U> cartesianProduct(c1: Collection<T>, c2: Collection<U>): Sequence<Pair<T, U>> {
    return c1.flatMap { lhsElem -> c2.map { rhsElem -> lhsElem to rhsElem } }.asSequence()
}

fun <T, U, V> cartesianProduct(c1: Collection<T>, c2: Collection<U>, c3: Collection<V>): Sequence<Triple<T, U, V>> {
    return sequence {
        for (a in c1)
            for (b in c2)
                for (c in c3)
                    yield(Triple(a, b, c))
    }
}

data class Product<out T, out U, out V, out W>(val first: T, val second: U, val third: V, val fourth: W)

fun <T, U, V, W> cartesianProduct(
    c1: Collection<T>,
    c2: Collection<U>,
    c3: Collection<V>,
    c4: Collection<W>
): Sequence<Product<T, U, V, W>> {
    return sequence {
        for (a in c1)
            for (b in c2)
                for (c in c3)
                    for (d in c4)
                        yield(Product(a, b, c, d))
    }
}
