package prog8.compiler.astprocessing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.defaultZero
import prog8.ast.expressions.ArrayLiteral
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.StaticStructInitializer
import prog8.ast.statements.StructField
import prog8.ast.walk.AstModification
import prog8.ast.walk.AstWalker
import prog8.code.core.BaseDataType
import prog8.code.core.IErrorReporter
import prog8.code.core.Position

internal class NamedStructInitializerFlattener(private val errors: IErrorReporter) : AstWalker() {

    override fun after(initializer: StaticStructInitializer, parent: Node): Iterable<AstModification> {
        if(initializer.namedArgs.isEmpty())
            return noModifications

        val struct = initializer.structname.targetStructDecl()
        if(struct==null) {
            // Struct name not resolvable yet; leave named args for later error reporting.
            return noModifications
        }

        // Unions do not support named-field initialization; the checker rejects those.
        if(struct.isUnion)
            return noModifications

        val seen = mutableSetOf<String>()
        for((name, expr) in initializer.namedArgs) {
            if(name in seen)
                errors.err("duplicate field name '$name' in struct initializer", expr.position)
            seen.add(name)
        }

        val fieldsByName = struct.fields.associateBy { it.name }
        for((name, expr) in initializer.namedArgs) {
            if(name !in fieldsByName)
                errors.err("unknown field name '$name' in struct initializer", expr.position)
        }

        val positional = struct.fields.map { field ->
            initializer.namedArgs.firstOrNull { it.first == field.name }?.second
                ?: defaultValueForField(field, initializer.position)
        }.toMutableList()

        initializer.args.clear()
        initializer.args.addAll(positional)
        initializer.namedArgs.clear()
        positional.forEach { it.linkParents(initializer) }

        return noModifications
    }

    private fun defaultValueForField(field: StructField, position: Position): Expression {
        return when {
            field.type.isString -> defaultZero(BaseDataType.STR, position)
            field.isArray -> {
                val size = field.constSize() ?: 1
                val elementType = field.type.elementType()
                val elements = Array<Expression>(size) { defaultZero(elementType.base, position) }
                ArrayLiteral(InferredTypes.knownFor(field.type), elements, position)
            }
            else -> defaultZero(field.type.base, position)
        }
    }
}

internal fun Program.flattenNamedStructInitializers(errors: IErrorReporter) {
    NamedStructInitializerFlattener(errors).visit(this)
}
