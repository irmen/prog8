package prog8.ast.processing

import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.CompilerException
import prog8.functions.BuiltinFunctions
import prog8.functions.FSignature


internal class VarInitValueCreator(val program: Program): AstWalker() {
    // For VarDecls that declare an initialization value:
    // add an assignment to set this initial value explicitly.
    // This makes sure the variables get reset to the intended value on a next run of the program.
    // Also takes care to insert AddressOf (&) expression where required (string params to a UWORD function param etc).
    // TODO join this into the StatementReorderer?
    // TODO actually I think the code generator should take care of this entirely, and this step should be removed from the ast modifications...

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        if(decl.isArray && decl.value==null) {
            // array datatype without initialization value, add list of zeros
            val arraysize = decl.arraysize!!.size()!!
            val zero = decl.asDefaultValueDecl(decl).value!!
            return listOf(IAstModification.ReplaceExpr(
                    { newExpr -> decl.value = newExpr },
                    ArrayLiteralValue(InferredTypes.InferredType.known(decl.datatype),
                            Array(arraysize) { zero },
                            decl.position),
                    decl
            ))
        }

        if(decl.type == VarDeclType.VAR && decl.value != null && decl.datatype in NumericDatatypes) {
            val declvalue = decl.value!!
            val value =
                    if(declvalue is NumericLiteralValue)
                        declvalue.cast(decl.datatype)
                    else
                        declvalue
            val identifierName = listOf(decl.name)
            val initvalue = VariableInitializationAssignment(
                    AssignTarget(null,
                            IdentifierReference(identifierName, decl.position),
                            null, null, decl.position),
                    null, value, decl.position
            )
            val zero = decl.asDefaultValueDecl(decl).value!!
            return listOf(
                    IAstModification.Insert(decl, initvalue, parent),
                    IAstModification.ReplaceExpr(
                            { newExpr -> decl.value = newExpr },
                            zero,
                            decl
                    )
            )
        }

        return emptyList()
    }

    override fun after(functionCall: FunctionCall, parent: Node): Iterable<IAstModification> {
        // insert AddressOf (&) expression where required (string params to a UWORD function param etc).
        var parentStatement: Node = functionCall
        while(parentStatement !is Statement)
            parentStatement = parentStatement.parent
        val targetStatement = functionCall.target.targetSubroutine(program.namespace)
        if(targetStatement!=null) {
            return addAddressOfExprIfNeeded(targetStatement, functionCall.args, parentStatement)
        } else {
            val builtinFunc = BuiltinFunctions[functionCall.target.nameInSource.joinToString (".")]
            if(builtinFunc!=null)
                return addAddressOfExprIfNeededForBuiltinFuncs(builtinFunc, functionCall.args, parentStatement)
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

    private fun addAddressOfExprIfNeeded(subroutine: Subroutine, arglist: MutableList<Expression>, parent: Statement): Iterable<IAstModification> {
        // functions that accept UWORD and are given an array type, or string, will receive the AddressOf (memory location) of that value instead.
        val replacements = mutableListOf<IAstModification>()
        for(argparam in subroutine.parameters.withIndex().zip(arglist)) {
            if(argparam.first.value.type==DataType.UWORD || argparam.first.value.type == DataType.STR) {
                if(argparam.second is AddressOf)
                    continue
                val idref = argparam.second as? IdentifierReference
                if(idref!=null) {
                    val variable = idref.targetVarDecl(program.namespace)
                    if(variable!=null && variable.datatype in IterableDatatypes) {
                        replacements += IAstModification.ReplaceExpr(
                                { newExpr -> arglist[argparam.first.index] = newExpr },
                                AddressOf(idref, idref.position),
                                parent
                        )
                    }
                }
            }
        }
        return replacements
    }

    private fun addAddressOfExprIfNeededForBuiltinFuncs(signature: FSignature, args: MutableList<Expression>, parent: Statement): Iterable<IAstModification> {
        // val paramTypesForAddressOf = PassByReferenceDatatypes + DataType.UWORD
        for(arg in args.withIndex().zip(signature.parameters)) {
            val argvalue = arg.first.value
            val argDt = argvalue.inferType(program)
            if(argDt.typeOrElse(DataType.UBYTE) in PassByReferenceDatatypes && DataType.UWORD in arg.second.possibleDatatypes) {
                if(argvalue !is IdentifierReference)
                    throw CompilerException("pass-by-reference parameter isn't an identifier? $argvalue")
                val addrOf = AddressOf(argvalue, argvalue.position)
                args[arg.first.index] = addrOf
                addrOf.linkParents(parent)
            }
        }
        return emptyList()
    }
}
