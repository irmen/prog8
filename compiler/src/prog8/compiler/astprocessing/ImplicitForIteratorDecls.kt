package prog8.compiler.astprocessing

import prog8.ast.INameScope
import prog8.ast.IStatementContainer
import prog8.ast.Node
import prog8.ast.Program
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
        val isList = isListIterable(iterableType)
        if (!iterableType.isIterable && forLoop.iterable !is RangeExpression && !isList)
            return emptyList()

        val elementType = if(isList) {
            val listDecl = structDeclFor(iterableType) ?: return emptyList()
            val nodeType = listDecl.fields[0].type
            // Head field is ^^Node, use that as element type
            // Resolve it in the list declaration's own scope: the field type may not
            // have been processed yet if its module hasn't been visited (and the name
            // may not even be visible from the scope using the list).
            resolveDt(nodeType, listDecl.definingScope, forLoop.position)
        } else iterableType.elementType()
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
            errors.err("implicit for loop var must be an unqualified name", forLoop.position)
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

    private fun isListIterable(dt: DataType): Boolean = ListIterationHelper.isListIterable(dt, program)
    private fun structDeclFor(dt: DataType): prog8.ast.statements.StructDecl? = ListIterationHelper.structDeclFor(dt, program)
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
