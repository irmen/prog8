package prog8.compiler.astprocessing

import prog8.ast.INameScope
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.RangeExpression
import prog8.ast.statements.ForLoop
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclType
import prog8.ast.walk.AstInsert
import prog8.ast.walk.AstModification
import prog8.code.core.DataType
import prog8.code.core.IErrorReporter
import prog8.code.core.Position


class ImplicitForIteratorDecls(
    private val program: Program,
    private val errors: IErrorReporter
) {

    private data class PendingDecl(
        val scope: INameScope,
        val name: String,
        val datatype: DataType
    )

    private val pending = mutableListOf<PendingDecl>()

    fun before(forLoop: ForLoop, parent: Node): Iterable<AstModification> {
        val names = forLoop.loopVar.nameInSource
        val inferredIterableType = forLoop.iterable.inferType(program)
        if (!inferredIterableType.isKnown)
            return emptyList()
        val iterableType = inferredIterableType.getOrUndef()

        val isLinked = forLoop.linkedVia != null || iterableType.isLinkedListType()
        if (!iterableType.isIterable && forLoop.iterable !is RangeExpression && !isLinked)
            return emptyList()

        val elementType = if (isLinked) {
            linkedNodeType(iterableType) ?: return emptyList()
        } else {
            iterableType.elementType()
        }

        if (isLinked) {
            validateLinkedForLoop(forLoop, iterableType, elementType)
            if (!errors.noErrors())
                return emptyList()
        }

        val scope = forLoop.definingScope
        val existing = scope.lookup(names)

        if (existing is VarDecl) {
            if (forLoop.loopVarType != null) {
                val wasImplicitlyCreated = pending.any { it.scope === scope && it.name == names.singleOrNull() }
                if (!wasImplicitlyCreated) {
                    errors.err(
                        "conflicting variable declaration: '${names.joinToString(".")}' is already declared",
                        forLoop.position
                    )
                    return emptyList()
                }
                val loopVarTypeResolved = resolveDt(forLoop.loopVarType!!, scope, forLoop.position)
                if (loopVarTypeResolved != existing.datatype) {
                    errors.err(
                        "for loop iterator type mismatch: '${names.joinToString(".")}' is already declared as ${existing.datatype} but the loop requests type ${forLoop.loopVarType}",
                        forLoop.position
                    )
                    return emptyList()
                }
            }
            if (existing.datatype.isPointer && (iterableType.isUnsignedWordArray || iterableType.isPointerArray ||
                (program.target.POINTER_MEM_SIZE > 2u && iterableType.isLongArray)))
                return emptyList()
            if (forLoop.iterable !is RangeExpression && !(elementType isAssignableTo existing.datatype)) {
                errors.err(
                    "for loop var '${names.joinToString(".")}' has type ${existing.datatype}, but the iterable elements have type $elementType",
                    forLoop.position
                )
            }
            return emptyList()
        }

        if (existing != null)
            return emptyList()

        val effectiveTypeRaw = forLoop.loopVarType ?: elementType
        val effectiveType = resolveDt(effectiveTypeRaw, scope, forLoop.position)
        val queued = pending.firstOrNull { it.scope === scope && it.name == names.singleOrNull() }
        if (queued != null) {
            if (!(effectiveType isAssignableTo queued.datatype)) {
                errors.err(
                    "for loop var '${names.joinToString(".")}' already has type ${queued.datatype} (from an earlier variable declaration or for loop), but the requested type is $effectiveType",
                    forLoop.position
                )
            }
            return emptyList()
        }

        if (names.size != 1) {
            if(names.size==2 && names[0]=="cx16" && names[1].startsWith("r")) {
                errors.err("cx16 virtual registers are not available on this compilation target", forLoop.loopVar.position)
            } else {
                errors.err("implicit for loop var must be an unqualified name", forLoop.loopVar.position)
            }
            return emptyList()
        }

        val declaration = VarDecl.builder(effectiveType, forLoop.position)
            .names(names.single())
            .type(VarDeclType.VAR)
            .build()
        pending += PendingDecl(scope, names.single(), effectiveType)

        val container = parent as? IStatementContainer
        if (container == null) {
            errors.err("cannot insert implicit for loop var declaration here", forLoop.position)
            return emptyList()
        }
        return listOf(AstInsert.before(forLoop, declaration, container))
    }


    private fun validateLinkedForLoop(forLoop: ForLoop, iterableType: DataType, nodeType: DataType) {
        val nodeStruct = nodeType.dereference().subType
        if (nodeStruct == null) {
            errors.err("linked-list for loop node type must be a struct", forLoop.position)
            return
        }

        val linkField: String = when {
            forLoop.linkedVia != null -> {
                if (iterableType != nodeType) {
                    errors.err("linked-list for loop start must be a pointer to the same node type", forLoop.position)
                    return
                }
                forLoop.linkedVia!!
            }
            else -> {
                val headerStruct = iterableType.dereference().subType
                val headerStart = listOf("head", "first").firstOrNull { headerStruct?.getFieldType(it) == nodeType }
                if (iterableType != nodeType && headerStart == null) {
                    errors.err("linked-list for loop start must be a pointer to the same node type or a list header with 'head'/'first' field", forLoop.position)
                    return
                }
                listOf("next", "succ").firstOrNull { nodeStruct.getFieldType(it)?.isPointer == true }
                    ?: run {
                        errors.err("linked-list for loop node type must have a 'next' or 'succ' field", forLoop.position)
                        return
                    }
            }
        }

        val fieldType = nodeStruct.getFieldType(linkField)
        if (fieldType == null || !fieldType.isPointer || fieldType != nodeType) {
            errors.err("linked-list for loop 'via' field must be a pointer to the same node type", forLoop.position)
            return
        }

        if (forLoop.step != null) {
            errors.err("linked-list for loop cannot use 'step'", forLoop.position)
        }

        forLoop.linkedUntil?.let { untilExpr ->
            val untilType = untilExpr.inferType(program).getOrUndef()
            val addressOfTargetType = (untilExpr as? AddressOf)?.identifier?.inferType(program)?.getOrUndef()
            val isValidUntil = (untilType.isPointer && untilType == nodeType) ||
                    (untilExpr is NumericLiteral && untilExpr.number == 0.0) ||
                    addressOfTargetType == nodeType
            if (!isValidUntil) {
                errors.err("linked-list for loop 'until' must be a compatible node pointer or 0", forLoop.position)
            }
        }
    }

    private fun linkedNodeType(iterableType: DataType): DataType? {
        if (iterableType.isPointer) {
            val subType = iterableType.dereference().subType
            val forwardLinks = listOf("next", "succ")
            if (subType != null && forwardLinks.any { subType.getFieldType(it)?.isPointer == true }) {
                return iterableType
            }
        }
        if (!iterableType.isPointer) return null
        val headerStruct = iterableType.dereference().subType
        val headName = listOf("head", "first").firstOrNull { headerStruct?.getFieldType(it)?.isPointer == true }
        if (headName != null) {
            return headerStruct?.getFieldType(headName)
        }
        return null
    }

    private fun resolveDt(dt: DataType, scope: INameScope, position: Position): DataType {
        val antlrName = dt.subTypeFromAntlr ?: return dt
        val resolved = StructTypeResolver.resolve(scope, program, antlrName, position, errors) ?: return dt
        return when {
            dt.isPointer -> DataType.pointer(resolved)
            dt.isStructInstance -> DataType.structInstance(resolved)
            else -> dt
        }
    }
}
