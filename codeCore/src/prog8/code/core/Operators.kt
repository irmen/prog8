package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "==", "!=")
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val LogicalOperators = setOf("and", "or", "xor", "not")
val AugmentAssignmentOperators = setOf("+", "-", "/", "*", "&", "|", "^", "<<", ">>", "%", "and", "or", "xor")
val BitwiseOperators = setOf("&", "|", "^", "~")
val PrefixOperators = setOf("+", "-", "~", "not")
// val InvalidOperatorsForBoolean = setOf("+", "-", "*", "/", "%", "<<", ">>") + BitwiseOperators

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
