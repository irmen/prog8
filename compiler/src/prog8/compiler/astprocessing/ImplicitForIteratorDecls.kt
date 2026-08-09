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
        if (!iterableType.isIterable && forLoop.iterable !is RangeExpression)
            return emptyList()

        val elementType = iterableType.elementType()
        val scope = forLoop.definingScope
        val existing = scope.lookup(names)

        if (existing is VarDecl) {
            if (existing.datatype.isPointer && (iterableType.isUnsignedWordArray || iterableType.isPointerArray))
                return emptyList()
            if (forLoop.iterable !is RangeExpression && !(elementType isAssignableTo existing.datatype)) {
                errors.err(
                    "for loop iterator '${names.joinToString(".")}' has type ${existing.datatype}, but the iterable elements have type $elementType",
                    forLoop.position
                )
            }
            return emptyList()
        }

        if (existing != null)
            return emptyList()

        val queued = pending.firstOrNull { it.scope === scope && it.name == names.singleOrNull() }
        if (queued != null) {
            if (!(elementType isAssignableTo queued.datatype)) {
                errors.err(
                    "for loop iterator '${names.joinToString(".")}' has type ${queued.datatype}, but the iterable elements have type $elementType",
                    forLoop.position
                )
            }
            return emptyList()
        }

        if (names.size != 1) {
            errors.err("implicit for loop iterator must be an unqualified name", forLoop.position)
            return emptyList()
        }

        val declaration = VarDecl.builder(elementType, forLoop.position)
            .names(names.single())
            .type(VarDeclType.VAR)
            .build()
        pending += PendingDecl(scope, names.single(), elementType)

        val container = parent as? IStatementContainer
        if (container == null) {
            errors.err("cannot insert implicit for loop iterator declaration here", forLoop.position)
            return emptyList()
        }
        return listOf(AstInsert.before(forLoop, declaration, container))
    }
}
