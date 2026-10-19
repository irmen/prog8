package prog8.compiler.astprocessing

import prog8.ast.INameScope
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.Expression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.ForLoop
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.ISubType
import prog8.code.core.Position


internal fun checkLinkedForLoop(
    forLoop: ForLoop,
    nodeType: DataType,
    program: Program,
    errors: IErrorReporter
): Boolean {
    if (!nodeType.isPointer) {
        errors.err("linked-list for loop requires a pointer loop variable", forLoop.position)
        return false
    }

    val iterableType = forLoop.iterable.inferType(program).getOrUndef()
    if (!iterableType.isPointer) {
        errors.err("linked-list for loop start must be a pointer", forLoop.position)
        return false
    }
    if (iterableType != nodeType) {
        errors.err("linked-list for loop start must be a pointer to the same node type", forLoop.position)
        return false
    }

    val nodeStruct = nodeType.dereference().subType
    if (nodeStruct == null) {
        errors.err("linked-list for loop node type must be a struct", forLoop.position)
        return false
    }

    val linkField = forLoop.linkedVia!!
    val fieldType = nodeStruct.getFieldType(linkField)?.let {
        resolveDeferredType(it, nodeStruct, forLoop.definingScope, program, errors, forLoop.position)
    }
    if (fieldType == null || !fieldType.isPointer || fieldType != nodeType) {
        errors.err("linked-list for loop 'via' field must be a pointer to the same node type", forLoop.position)
        return false
    }

    if (forLoop.step != null) {
        errors.err("linked-list for loop cannot use 'step'", forLoop.position)
    }

    forLoop.linkedUntil?.let { untilExpr ->
        if (!isValidLinkedUntil(untilExpr, nodeType, program)) {
            errors.err("linked-list for loop 'until' must be a compatible node pointer or 0", forLoop.position)
        }
    }

    return errors.noErrors()
}


private fun isValidLinkedUntil(untilExpr: Expression, nodeType: DataType, program: Program): Boolean {
    if (untilExpr is NumericLiteral && untilExpr.number == 0.0)
        return true
    val untilType = untilExpr.inferType(program).getOrUndef()
    if (untilType == nodeType)
        return true
    if (untilExpr is AddressOf) {
        val addressOfTargetType = untilExpr.identifier?.inferType(program)?.getOrUndef()
        if (addressOfTargetType == nodeType)
            return true
        // Exec-style sentinel: address of an untyped pointer field.
        // The plain `pointer` base type is normalized to the target's pointer-sized integer (uword/long),
        // so accept that as well.
        if (addressOfTargetType?.base == program.target.pointerBaseType)
            return true
    }
    return false
}


internal fun resolveDeferredType(
    dt: DataType,
    nodeStruct: ISubType,
    scope: INameScope,
    program: Program,
    errors: IErrorReporter,
    position: Position
): DataType {
    val antlrName = dt.subTypeFromAntlr ?: return dt
    val structScope = nodeStruct.scopedNameString.split('.').dropLast(1)
    val name = if (antlrName.size == 1 && structScope.isNotEmpty()) structScope + antlrName else antlrName
    StructTypeResolver.resolve(scope, program, name, position, errors)?.let(dt::setActualSubType)
    return dt
}


