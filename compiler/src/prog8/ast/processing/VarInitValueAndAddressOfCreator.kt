package prog8.ast.processing

import prog8.ast.INameScope
import prog8.ast.Module
import prog8.ast.Node
import prog8.ast.Program
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.CompilerException
import prog8.functions.BuiltinFunctions
import prog8.functions.FunctionSignature


internal class VarInitValueAndAddressOfCreator(private val program: Program): IAstModifyingVisitor {
    // For VarDecls that declare an initialization value:
    // Replace the vardecl with an assignment (to set the initial value),
    // and add a new vardecl with the default constant value of that type (usually zero) to the scope.
    // This makes sure the variables get reset to the intended value on a next run of the program.
    // Variable decls without a value don't get this treatment, which means they retain the last
    // value they had when restarting the program.
    // This is done in a separate step because it interferes with the namespace lookup of symbols
    // in other ast processors.

    // Also takes care to insert AddressOf (&) expression where required (string params to a UWORD function param etc).

    private val vardeclsToAdd = mutableMapOf<INameScope, MutableList<VarDecl>>()

    override fun visit(module: Module) {
        vardeclsToAdd.clear()
        super.visit(module)
        // add any new vardecls to the various scopes
        for((where, decls) in vardeclsToAdd) {
            where.statements.addAll(0, decls)
            decls.forEach { it.linkParents(where as Node) }
        }
    }

    override fun visit(decl: VarDecl): Statement {
        super.visit(decl)

        if(decl.isArray && decl.value==null) {
            // array datatype without initialization value, add list of zeros
            val arraysize = decl.arraysize!!.size()!!
            val array = ArrayLiteralValue(decl.datatype,
                    Array(arraysize) { NumericLiteralValue.optimalInteger(0, decl.position) },
                    null, decl.position)
            array.addToHeap()
            decl.value = array
        }

        if(decl.type!= VarDeclType.VAR || decl.value==null)
            return decl

        if(decl.datatype in NumericDatatypes) {
            val scope = decl.definingScope()
            addVarDecl(scope, decl.asDefaultValueDecl(null))
            val declvalue = decl.value!!
            val value =
                    if(declvalue is NumericLiteralValue)
                        declvalue.cast(decl.datatype)
                    else
                        declvalue
            val identifierName = listOf(decl.name)    // this was: (scoped name) decl.scopedname.split(".")
            return VariableInitializationAssignment(
                    AssignTarget(null, IdentifierReference(identifierName, decl.position), null, null, decl.position),
                    null,
                    value,
                    decl.position
            )
        }

        return decl
    }

    override fun visit(functionCall: FunctionCall): Expression {
        var parentStatement: Node = functionCall
        while(parentStatement !is Statement)
            parentStatement = parentStatement.parent
        val targetStatement = functionCall.target.targetSubroutine(program.namespace)
        if(targetStatement!=null) {
            addAddressOfExprIfNeeded(targetStatement, functionCall.args, parentStatement)
        } else {
            val builtinFunc = BuiltinFunctions[functionCall.target.nameInSource.joinToString (".")]
            if(builtinFunc!=null)
                addAddressOfExprIfNeededForBuiltinFuncs(builtinFunc, functionCall.args, parentStatement)
        }
        return functionCall
    }

    override fun visit(functionCallStatement: FunctionCallStatement): Statement {
        val targetStatement = functionCallStatement.target.targetSubroutine(program.namespace)
        if(targetStatement!=null) {
            addAddressOfExprIfNeeded(targetStatement, functionCallStatement.args, functionCallStatement)
        } else {
            val builtinFunc = BuiltinFunctions[functionCallStatement.target.nameInSource.joinToString (".")]
            if(builtinFunc!=null)
                addAddressOfExprIfNeededForBuiltinFuncs(builtinFunc, functionCallStatement.args, functionCallStatement)
        }
        return functionCallStatement
    }

    private fun addAddressOfExprIfNeeded(subroutine: Subroutine, arglist: MutableList<Expression>, parent: Statement) {
        // functions that accept UWORD and are given an array type, or string, will receive the AddressOf (memory location) of that value instead.
        for(argparam in subroutine.parameters.withIndex().zip(arglist)) {
            if(argparam.first.value.type==DataType.UWORD || argparam.first.value.type == DataType.STR) {
                if(argparam.second is AddressOf)
                    continue
                val idref = argparam.second as? IdentifierReference
                val strvalue = argparam.second as? StringLiteralValue
                if(idref!=null) {
                    val variable = idref.targetVarDecl(program.namespace)
                    if(variable!=null && variable.datatype in IterableDatatypes) {
                        val pointerExpr = AddressOf(idref, idref.position)
                        pointerExpr.linkParents(arglist[argparam.first.index].parent)
                        arglist[argparam.first.index] = pointerExpr
                    }
                }
                else if(strvalue!=null) {
                    // add a vardecl so that the autovar can be resolved in later lookups
                    val variable = VarDecl.createAuto(strvalue)
                    addVarDecl(strvalue.definingScope(), variable)
                    // replace the argument with &autovar
                    val autoHeapvarRef = IdentifierReference(listOf(variable.name), strvalue.position)
                    val pointerExpr = AddressOf(autoHeapvarRef, strvalue.position)
                    pointerExpr.linkParents(arglist[argparam.first.index].parent)
                    arglist[argparam.first.index] = pointerExpr
                }
            }
        }
    }

    private fun addAddressOfExprIfNeededForBuiltinFuncs(signature: FunctionSignature, args: MutableList<Expression>, parent: Statement) {
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
    }


    private fun addVarDecl(scope: INameScope, variable: VarDecl) {
        if(scope !in vardeclsToAdd)
            vardeclsToAdd[scope] = mutableListOf()
        val declList = vardeclsToAdd.getValue(scope)
        if(declList.all{it.name!=variable.name})
            declList.add(variable)
    }

}
