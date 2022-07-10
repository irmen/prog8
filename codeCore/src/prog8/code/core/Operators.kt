package prog8.code.core

val AssociativeOperators = setOf("+", "*", "&", "|", "^", "==", "!=")
val ComparisonOperators = setOf("==", "!=", "<", ">", "<=", ">=")
val LogicalOperators = setOf("and", "or", "xor", "not")
val AugmentAssignmentOperators = setOf("+", "-", "/", "*", "&", "|", "^", "<<", ">>", "%")
val BitwiseOperators = setOf("&", "|", "^", "~")
val InvalidOperatorsForBoolean = setOf("-", "*", "/", "%", "<<", ">>")       // TODO what about +?  TODO add BitWiseOperators

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
