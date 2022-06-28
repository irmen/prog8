package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "or", "and", "xor", "==", "!=")
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val AugmentAssignmentOperators = setOf("+", "-", "/", "*", "&", "|", "^", "<<", ">>", "%", "and", "or", "xor")
val LogicalOperators = setOf("and", "or", "xor")        // not x is replaced with x==0
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
