package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val AugmentAssignmentOperators = setOf("+", "-", "/", "*", "&", "|", "^", "<<", ">>", "%", "and", "or", "xor")
val LogicalOperators = setOf("and", "or", "xor", "not")
val BitwiseOperators = setOf("&", "|", "^")

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
