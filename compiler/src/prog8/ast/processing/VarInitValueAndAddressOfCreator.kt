package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.HeapValues


internal class VarInitValueAndAddressOfCreator(private val namespace: INameScope, private val heap: HeapValues): IAstModifyingVisitor {
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

    override fun visit(decl: VarDecl): IStatement {
        super.visit(decl)

        if(decl.isArray && decl.value==null) {
            // array datatype without initialization value, add list of zeros
            val arraysize = decl.arraysize!!.size()!!
            val array = ReferenceLiteralValue(decl.datatype, null,
                    Array(arraysize) { NumericLiteralValue.optimalInteger(0, decl.position) },
                    null, decl.position)
            array.addToHeap(heap)
            decl.value = array
        }

        if(decl.type!= VarDeclType.VAR || decl.value==null)
            return decl

        if(decl.datatype in NumericDatatypes) {
            val scope = decl.definingScope()
            addVarDecl(scope, decl.asDefaultValueDecl(null))
            val declvalue = decl.value!!
            val value =
                    if(declvalue is NumericLiteralValue) {
                        val converted = declvalue.cast(decl.datatype)
                        converted ?: declvalue
                    }
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

    override fun visit(functionCall: FunctionCall): IExpression {
        val targetStatement = functionCall.target.targetSubroutine(namespace)
        if(targetStatement!=null) {
            var node: Node = functionCall
            while(node !is IStatement)
                node=node.parent
            addAddressOfExprIfNeeded(targetStatement, functionCall.arglist, node)
        }
        return functionCall
    }

    override fun visit(functionCallStatement: FunctionCallStatement): IStatement {
        val targetStatement = functionCallStatement.target.targetSubroutine(namespace)
        if(targetStatement!=null)
            addAddressOfExprIfNeeded(targetStatement, functionCallStatement.arglist, functionCallStatement)
        return functionCallStatement
    }

    private fun addAddressOfExprIfNeeded(subroutine: Subroutine, arglist: MutableList<IExpression>, parent: IStatement) {
        // functions that accept UWORD and are given an array type, or string, will receive the AddressOf (memory location) of that value instead.
        for(argparam in subroutine.parameters.withIndex().zip(arglist)) {
            if(argparam.first.value.type==DataType.UWORD || argparam.first.value.type in StringDatatypes) {
                if(argparam.second is AddressOf)
                    continue
                val idref = argparam.second as? IdentifierReference
                val strvalue = argparam.second as? ReferenceLiteralValue
                if(idref!=null) {
                    val variable = idref.targetVarDecl(namespace)
                    if(variable!=null && (variable.datatype in StringDatatypes || variable.datatype in ArrayDatatypes)) {
                        val pointerExpr = AddressOf(idref, idref.position)
                        pointerExpr.scopedname = parent.makeScopedName(idref.nameInSource.single())
                        pointerExpr.linkParents(arglist[argparam.first.index].parent)
                        arglist[argparam.first.index] = pointerExpr
                    }
                }
                else if(strvalue!=null) {
                    if(strvalue.isString) {
                        // add a vardecl so that the autovar can be resolved in later lookups
                        val variable = VarDecl.createAuto(strvalue, heap)
                        addVarDecl(strvalue.definingScope(), variable)
                        // replace the argument with &autovar
                        val autoHeapvarRef = IdentifierReference(listOf(variable.name), strvalue.position)
                        val pointerExpr = AddressOf(autoHeapvarRef, strvalue.position)
                        pointerExpr.scopedname = parent.makeScopedName(variable.name)
                        pointerExpr.linkParents(arglist[argparam.first.index].parent)
                        arglist[argparam.first.index] = pointerExpr
                    }
                }
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
