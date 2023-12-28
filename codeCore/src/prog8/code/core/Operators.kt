package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "==", "!=")
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val LogicalOperators = setOf("and", "or", "xor", "not")
val BitwiseOperators = setOf("&", "|", "^", "~")
val PrefixOperators = setOf("+", "-", "~", "not")

fun invertedComparisonOperator(operator: String) =
    when (operator) {
        "==" -> "!="
        "!=" -> "=="
        "<" -> ">="
        ">" -> "<="
        "<=" -> ">"
        ">=" -> "<"
        else -> null
    }
