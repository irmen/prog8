package prog8.code.core

val AssociativeOperators = arrayOf("+", "*", "&", "|", "^", "==", "!=", "xor")       // note: and,or are not associative because of Shortcircuit/McCarthy evaluation
val ComparisonOperators = arrayOf("==", "!=", "<", ">", "<=", ">=")
val LogicalOperators = arrayOf("and", "or", "xor", "not", "in")
val BitwiseOperators = arrayOf("&", "|", "^", "~")
val PrefixOperators = arrayOf("+", "-", "~", "not")

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
