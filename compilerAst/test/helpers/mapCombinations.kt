package prog8tests.helpers

fun <A, B, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, combine2: (A, B) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                yield(combine2(a, b))
    }.toList()

fun <A, B, C, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, dim3: Iterable<C>, combine3: (A, B, C) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                for (c in dim3)
                    yield(combine3(a, b, c))
    }.toList()

fun <A, B, C, D, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, dim3: Iterable<C>, dim4: Iterable<D>, combine4: (A, B, C, D) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                for (c in dim3)
                    for (d in dim4)
                        yield(combine4(a, b, c, d))
    }.toList()