package prog8

/**
 * By convention, the right side of an `Either` is used to hold successful values.
 */
sealed class Either<out L, out R> {

    data class Left<out L>(val value: L) : Either<L, Nothing>()

    data class Right<out R>(val value: R) : Either<Nothing, R>()

    fun isRight() = this is Right<R>

    fun isLeft() = this is Left<L>

    inline fun <C> fold(ifLeft: (L) -> C, ifRight: (R) -> C): C = when (this) {
        is Right -> ifRight(value)
        is Left -> ifLeft(value)
    }

}

fun <L> left(a: L) = Either.Left(a)
fun <R> right(b: R) = Either.Right(b)
