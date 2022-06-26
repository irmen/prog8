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

fun <A, B, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, combine2: (A, B) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                yield(combine2(a, b))
    }.toList()

fun <A, B, C, R> mapCombinations(
    dim1: Iterable<A>,
    dim2: Iterable<B>,
    dim3: Iterable<C>,
    combine3: (A, B, C) -> R
) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                for (c in dim3)
                    yield(combine3(a, b, c))
    }.toList()

fun <A, B, C, D, R> mapCombinations(
    dim1: Iterable<A>,
    dim2: Iterable<B>,
    dim3: Iterable<C>,
    dim4: Iterable<D>,
    combine4: (A, B, C, D) -> R
) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                for (c in dim3)
                    for (d in dim4)
                        yield(combine4(a, b, c, d))
    }.toList()
