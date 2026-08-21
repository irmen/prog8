package prog8.compiler.astprocessing

import prog8.ast.INameScope
import prog8.ast.Program
import prog8.ast.statements.Alias
import prog8.code.core.IErrorReporter
import prog8.code.core.ISubType
import prog8.code.core.Position

/**
 * Resolves deferred (antlr-parsed) subtype names to the actual ISubType declaration,
 * following alias chains (with cycle protection). Returns null and reports an error
 * if the name cannot be resolved to a struct type.
 */
object StructTypeResolver {

    private const val MAXHOPS = 100

    fun resolve(scope: INameScope, program: Program, name: List<String>, position: Position, errors: IErrorReporter): ISubType? {
        var symbol = scope.lookup(name)
        if(symbol==null) {
            errors.err("cannot find struct type ${name.joinToString(".")}", position)
            return null
        }
        if(name.size==1 && name[0] in program.builtinFunctions.names) {
            errors.err("builtin function can only be called, not used as a type name", position)
            return null
        }
        var hops = 0
        while(symbol is Alias) {
            if(++hops > MAXHOPS) {
                errors.err("alias loop while resolving struct type", position)
                return null
            }
            val targetName = symbol.target.nameInSource
            symbol = symbol.definingScope.lookup(targetName)
            if(symbol==null) {
                errors.err("cannot find struct type ${targetName.joinToString(".")}", position)
                return null
            }
        }
        return symbol as? ISubType
    }
}
