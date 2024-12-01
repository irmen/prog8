package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "==", "!=", "xor")       // note: and,or are no longer associative because of Shortcircuit/McCarthy evaluation
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val LogicalOperators = setOf("and", "or", "xor", "not", "in")
val BitwiseOperators = setOf("&", "|", "^", "~")
val PrefixOperators = setOf("+", "-", "~", "^", "<<", "not")          // TODO ^ and << are experimental

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
