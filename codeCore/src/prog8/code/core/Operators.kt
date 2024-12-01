package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "==", "!=", "xor")       // note: and,or are not associative because of Shortcircuit/McCarthy evaluation
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val LogicalOperators = setOf("and", "or", "xor", "not", "in")
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
