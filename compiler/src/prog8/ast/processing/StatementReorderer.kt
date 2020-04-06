package prog8.ast.processing

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.CompilerException
import prog8.functions.BuiltinFunctions
import prog8.functions.FSignature


internal class StatementReorderer(val program: Program) : AstWalker() {
    // Reorders the statements in a way the compiler needs.
    // - 'main' block must be the very first statement UNLESS it has an address set.
    // - library blocks are put last.
    // - blocks are ordered by address, where blocks without address are placed last.
    // - in every scope, most directives and vardecls are moved to the top.
    // - the 'start' subroutine is moved to the top.
    // - (syntax desugaring) a vardecl with a non-const initializer value is split into a regular vardecl and an assignment statement.
    // - (syntax desugaring) augmented assignment is turned into regular assignment.
    // - (syntax desugaring) struct value assignment is expanded into several struct member assignments.
    // - sorts the choices in when statement.
    // - insert AddressOf (&) expression where required (string params to a UWORD function param etc).


    private val directivesToMove = setOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%option")

    override fun after(module: Module, parent: Node): Iterable<IAstModification> {
        val (blocks, other) = module.statements.partition { it is Block }
        module.statements = other.asSequence().plus(blocks.sortedBy { (it as Block).address ?: Int.MAX_VALUE }).toMutableList()

        val mainBlock = module.statements.filterIsInstance<Block>().firstOrNull { it.name=="main" }
        if(mainBlock!=null && mainBlock.address==null) {
            module.statements.remove(mainBlock)
            module.statements.add(0, mainBlock)
        }

        reorderVardeclsAndDirectives(module.statements)
        return emptyList()
    }

    private fun reorderVardeclsAndDirectives(statements: MutableList<Statement>) {
        val varDecls = statements.filterIsInstance<VarDecl>()
        statements.removeAll(varDecls)
        statements.addAll(0, varDecls)

        val directives = statements.filterIsInstance<Directive>().filter {it.directive in directivesToMove}
        statements.removeAll(directives)
        statements.addAll(0, directives)
    }

    override fun before(block: Block, parent: Node): Iterable<IAstModification> {
        parent as Module
        if(block.isInLibrary) {
            return listOf(
                    IAstModification.Remove(block, parent),
                    IAstModification.InsertLast(block, parent)
            )
        }

        reorderVardeclsAndDirectives(block.statements)
        return emptyList()
    }

    override fun before(subroutine: Subroutine, parent: Node): Iterable<IAstModification> {
        if(subroutine.name=="start" && parent is Block) {
            if(parent.statements.filterIsInstance<Subroutine>().first().name!="start") {
                return listOf(
                        IAstModification.Remove(subroutine, parent),
                        IAstModification.InsertFirst(subroutine, parent)
                )
            }
        }
        return emptyList()
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<IAstModification> {
        val declValue = decl.value
        if(declValue!=null && decl.type== VarDeclType.VAR && decl.datatype in NumericDatatypes) {
            val declConstValue = declValue.constValue(program)
            if(declConstValue==null) {
                // move the vardecl (without value) to the scope and replace this with a regular assignment
                decl.value = null
                val target = AssignTarget(null, IdentifierReference(listOf(decl.name), decl.position), null, null, decl.position)
                val assign = Assignment(target, null, declValue, decl.position)
                return listOf(
                        IAstModification.ReplaceNode(decl, assign, parent),
                        IAstModification.InsertFirst(decl, decl.definingScope() as Node)
                )
            }
        }
        return emptyList()
    }

    override fun after(whenStatement: WhenStatement, parent: Node): Iterable<IAstModification> {
        val choices = whenStatement.choiceValues(program).sortedBy {
            it.first?.first() ?: Int.MAX_VALUE
        }
        whenStatement.choices.clear()
        choices.mapTo(whenStatement.choices) { it.second }
        return emptyList()
    }

    override fun before(assignment: Assignment, parent: Node): Iterable<IAstModification> {
        if(assignment.aug_op!=null) {
            return listOf(IAstModification.ReplaceNode(assignment, assignment.asDesugaredNonaugmented(), parent))
        }

        val valueType = assignment.value.inferType(program)
        val targetType = assignment.target.inferType(program, assignment)
        if(valueType.istype(DataType.STRUCT) && targetType.istype(DataType.STRUCT)) {
            val assignments = if (assignment.value is StructLiteralValue) {
                flattenStructAssignmentFromStructLiteral(assignment, program)    //  'structvar = { ..... } '
            } else {
                flattenStructAssignmentFromIdentifier(assignment, program)    //   'structvar1 = structvar2'
            }
            if(assignments.isNotEmpty()) {
                val modifications = mutableListOf<IAstModification>()
                assignments.reversed().mapTo(modifications) { IAstModification.InsertAfter(assignment, it, parent) }
                modifications.add(IAstModification.Remove(assignment, parent))
                return modifications
            }
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

    private fun flattenStructAssignmentFromStructLiteral(structAssignment: Assignment, program: Program): List<Assignment> {
        val identifier = structAssignment.target.identifier!!
        val identifierName = identifier.nameInSource.single()
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val struct = targetVar.struct!!

        val slv = structAssignment.value as? StructLiteralValue
        if(slv==null || slv.values.size != struct.numberOfElements)
            throw FatalAstException("element count mismatch")

        return struct.statements.zip(slv.values).map { (targetDecl, sourceValue) ->
            targetDecl as VarDecl
            val mangled = mangledStructMemberName(identifierName, targetDecl.name)
            val idref = IdentifierReference(listOf(mangled), structAssignment.position)
            val assign = Assignment(AssignTarget(null, idref, null, null, structAssignment.position),
                    null, sourceValue, sourceValue.position)
            assign.linkParents(structAssignment)
            assign
        }
    }

    private fun flattenStructAssignmentFromIdentifier(structAssignment: Assignment, program: Program): List<Assignment> {
        val identifier = structAssignment.target.identifier!!
        val identifierName = identifier.nameInSource.single()
        val targetVar = identifier.targetVarDecl(program.namespace)!!
        val struct = targetVar.struct!!
        when (structAssignment.value) {
            is IdentifierReference -> {
                val sourceVar = (structAssignment.value as IdentifierReference).targetVarDecl(program.namespace)!!
                if (sourceVar.struct == null)
                    throw FatalAstException("can only assign arrays or structs to structs")
                // struct memberwise copy
                val sourceStruct = sourceVar.struct!!
                if(sourceStruct!==targetVar.struct) {
                    // structs are not the same in assignment
                    return listOf()     // error will be printed elsewhere
                }
                return struct.statements.zip(sourceStruct.statements).map { member ->
                    val targetDecl = member.first as VarDecl
                    val sourceDecl = member.second as VarDecl
                    if(targetDecl.name != sourceDecl.name)
                        throw FatalAstException("struct member mismatch")
                    val mangled = mangledStructMemberName(identifierName, targetDecl.name)
                    val idref = IdentifierReference(listOf(mangled), structAssignment.position)
                    val sourcemangled = mangledStructMemberName(sourceVar.name, sourceDecl.name)
                    val sourceIdref = IdentifierReference(listOf(sourcemangled), structAssignment.position)
                    val assign = Assignment(AssignTarget(null, idref, null, null, structAssignment.position),
                            null, sourceIdref, member.second.position)
                    assign.linkParents(structAssignment)
                    assign
                }
            }
            is StructLiteralValue -> {
                throw IllegalArgumentException("not going to flatten a structLv assignment here")
            }
            else -> throw FatalAstException("strange struct value")
        }
    }

}
