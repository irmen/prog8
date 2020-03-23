package prog8.ast.processing

import prog8.ast.IFunctionCall
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.IterableDatatypes
import prog8.ast.base.PassByReferenceDatatypes
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.Expression
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.IdentifierReference
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Statement
import prog8.ast.statements.Subroutine
import prog8.compiler.CompilerException
import prog8.functions.BuiltinFunctions
import prog8.functions.FSignature


internal class AddressOfInserter(val program: Program): AstWalker() {
    // Insert AddressOf (&) expression where required (string params to a UWORD function param etc).
    // TODO join this into the StatementReorderer?

    override fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        // insert AddressOf (&) expression where required (string params to a UWORD function param etc).
        var parentStatement: Node = functionCall
        while(parentStatement !is Statement)
            parentStatement = parentStatement.parent
        val targetStatement = functionCall.target.targetSubroutine(program.namespace)
        if(targetStatement!=null) {
            return addAddressOfExprIfNeeded(targetStatement, functionCall.args, functionCall)
        } else {
            val builtinFunc = BuiltinFunctions[functionCall.target.nameInSource.joinToString (".")]
            if(builtinFunc!=null)
                return addAddressOfExprIfNeededForBuiltinFuncs(builtinFunc, functionCall.args, functionCall)
        }
        return emptyList()
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<IAstModification> {
        // insert AddressOf (&) expression where required (string params to a UWORD function param etc).
        val targetStatement = functionCallStatement.target.targetSubroutine(program.namespace)
        if(targetStatement!=null) {
            return addAddressOfExprIfNeeded(targetStatement, functionCallStatement.args, functionCallStatement)
        } else {
            val builtinFunc = BuiltinFunctions[functionCallStatement.target.nameInSource.joinToString (".")]
            if(builtinFunc!=null)
                return addAddressOfExprIfNeededForBuiltinFuncs(builtinFunc, functionCallStatement.args, functionCallStatement)
        }
        return emptyList()
    }

    private fun addAddressOfExprIfNeeded(subroutine: Subroutine, args: MutableList<Expression>, parent: IFunctionCall): Iterable<IAstModification> {
        // functions that accept UWORD and are given an array type, or string, will receive the AddressOf (memory location) of that value instead.
        val replacements = mutableListOf<IAstModification>()
        for(argparam in subroutine.parameters.withIndex().zip(args)) {
            if(argparam.first.value.type==DataType.UWORD || argparam.first.value.type == DataType.STR) {
                if(argparam.second is AddressOf)
                    continue
                val idref = argparam.second as? IdentifierReference
                if(idref!=null) {
                    val variable = idref.targetVarDecl(program.namespace)
                    if(variable!=null && variable.datatype in IterableDatatypes) {
                        replacements += IAstModification.ReplaceNode(
                                args[argparam.first.index],
                                AddressOf(idref, idref.position),
                                parent as Node)
                    }
                }
            }
        }
        return replacements
    }

    private fun addAddressOfExprIfNeededForBuiltinFuncs(signature: FSignature, args: MutableList<Expression>, parent: IFunctionCall): Iterable<IAstModification> {
        // val paramTypesForAddressOf = PassByReferenceDatatypes + DataType.UWORD
        val replacements = mutableListOf<IAstModification>()
        for(arg in args.withIndex().zip(signature.parameters)) {
            val argvalue = arg.first.value
            val argDt = argvalue.inferType(program)
            if(argDt.typeOrElse(DataType.UBYTE) in PassByReferenceDatatypes && DataType.UWORD in arg.second.possibleDatatypes) {
                if(argvalue !is IdentifierReference)
                    throw CompilerException("pass-by-reference parameter isn't an identifier? $argvalue")
                replacements += IAstModification.ReplaceNode(
                        args[arg.first.index],
                        AddressOf(argvalue, argvalue.position),
                        parent as Node)
            }
        }
        return replacements
    }
}
