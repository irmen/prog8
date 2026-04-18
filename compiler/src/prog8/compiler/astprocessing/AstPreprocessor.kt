package prog8.compiler.astprocessing

import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.*
import prog8.code.core.*


class AstPreprocessor(val program: Program,
                      val errors: IErrorReporter,
                      val options: CompilationOptions) : AstWalker() {

    override fun before(program: Program): Iterable<AstModification> {
        if(options.zeropage==ZeropageType.KERNALSAFE || options.zeropage==ZeropageType.FULL) {
            // there may be enough space in the zero page to put the cx16 virtual registers there.
            // unfortunately, can't be the same address as CommanderX16.
            val reg0 = options.compTarget.zeropage.allocatedVariables["cx16.r0"]
            if(reg0!=null) {
                // cx16.r0 is in zeropage, relocate
                relocateCx16VirtualRegisters(program, reg0.address)
            }
        }
        return noModifications
    }

    private fun relocateCx16VirtualRegisters(program: Program, baseAddress: UInt) {
        val cx16block = program.allBlocks.single { it.name == "cx16" }
        val memVars = cx16block.statements
            .filterIsInstance<VarDecl>()
            .associateBy { it.name }

        fun getRegValueChecked(key: String): VarDecl  = memVars[key] ?: throw NoSuchElementException("no definition found for 'cx16.$key' in syslib.p8")

        for (regnum in 0u..15u) {
            val rX = getRegValueChecked("r$regnum")
            val rXL = getRegValueChecked("r${regnum}L")
            val rXH = getRegValueChecked("r${regnum}H")
            val rXs = getRegValueChecked("r${regnum}s")
            val rXsL = getRegValueChecked("r${regnum}sL")
            val rXsH = getRegValueChecked("r${regnum}sH")
            val rXbL = getRegValueChecked("r${regnum}bL")
            val rXbH = getRegValueChecked("r${regnum}bH")
            setAddress(rX, baseAddress + 2u * regnum)
            setAddress(rXL, baseAddress + 2u * regnum)
            setAddress(rXH, baseAddress + 2u * regnum + 1u)
            setAddress(rXs, baseAddress + 2u * regnum)
            setAddress(rXsL, baseAddress + 2u * regnum)
            setAddress(rXsH, baseAddress + 2u * regnum + 1u)
            setAddress(rXbL, baseAddress + 2u * regnum)
            setAddress(rXbH, baseAddress + 2u * regnum + 1u)
        }

        val r0r1sl = getRegValueChecked("r0r1sl")
        val r2r3sl = getRegValueChecked("r2r3sl")
        val r4r5sl = getRegValueChecked("r4r5sl")
        val r6r7sl = getRegValueChecked("r6r7sl")
        val r8r9sl = getRegValueChecked("r8r9sl")
        val r10r11sl = getRegValueChecked("r10r11sl")
        val r12r13sl = getRegValueChecked("r12r13sl")
        val r14r15sl = getRegValueChecked("r14r15sl")
        setAddress(r0r1sl, baseAddress + 0u)
        setAddress(r2r3sl, baseAddress + 4u)
        setAddress(r4r5sl, baseAddress + 8u)
        setAddress(r6r7sl, baseAddress + 12u)
        setAddress(r8r9sl, baseAddress + 16u)
        setAddress(r10r11sl, baseAddress + 20u)
        setAddress(r12r13sl, baseAddress + 24u)
        setAddress(r14r15sl, baseAddress + 28u)
    }

    private fun setAddress(vardecl: VarDecl, address: UInt) {
        val oldAddr = vardecl.value as NumericLiteral
        vardecl.value = NumericLiteral(oldAddr.type, address.toDouble(), oldAddr.position)
    }

    override fun before(directive: Directive, parent: Node): Iterable<AstModification> {
        if(directive.parent is Expression)
            errors.err("${directive.directive} is ambiguous here as an operand for the % operator and a directive. Add spaces around the operator % to distinguish it.", directive.position)
        return noModifications
    }

    override fun before(char: CharLiteral, parent: Node): Iterable<AstModification> {
        if(char.encoding== Encoding.DEFAULT)
            char.encoding = char.definingModule.textEncoding
        return noModifications
    }

    override fun before(string: StringLiteral, parent: Node): Iterable<AstModification> {
        if(string.encoding==Encoding.DEFAULT)
            string.encoding = string.definingModule.textEncoding
        return super.before(string, parent)
    }

    override fun before(expr: PrefixExpression, parent: Node): Iterable<AstModification> {
        if (parent is RangeExpression)
            return noModifications
        val constValue = expr.constValue(program) ?: return noModifications
        return listOf(AstReplaceNode(expr, constValue, parent))
    }

    override fun after(range: RangeExpression, parent: Node): Iterable<AstModification> {
        // has to be done before the constant folding, otherwise certain checks there will fail on invalid range sizes
        val modifications = mutableListOf<AstModification>()
        if(range.from !is NumericLiteral) {
            try {
                val constval = range.from.constValue(program)
                if (constval != null)
                    modifications += AstReplaceNode(range.from, constval, range)
            } catch (_: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.to !is NumericLiteral) {
            try {
                val constval = range.to.constValue(program)
                if(constval!=null)
                    modifications += AstReplaceNode(range.to, constval, range)
            } catch (_: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        if(range.step !is NumericLiteral) {
            try {
                val constval = range.step.constValue(program)
                if(constval!=null)
                    modifications += AstReplaceNode(range.step, constval, range)
            } catch (_: SyntaxError) {
                // syntax errors will be reported later
            }
        }
        return modifications
    }

    override fun after(scope: AnonymousScope, parent: Node): Iterable<AstModification> {

        // move vardecls in Anonymous scope up to the containing subroutine
        // and add initialization assignment in its place if needed
        val vars = scope.statements.asSequence().filterIsInstance<VarDecl>()
        val parentscope = scope.definingScope
        if(vars.any() && parentscope !== parent) {
            val movements = mutableListOf<AstModification>()
            val replacements = mutableListOf<AstModification>()

            for(decl in vars) {
                if(decl.type != VarDeclType.VAR) {
                    movements.add(AstInsert.first(parentscope, decl))
                    replacements.add(AstRemove(decl, scope))
                } else {
                    val declToInsert: VarDecl
                    if(decl.names.size>1) {
                        // we need to handle multi-decl here too, the desugarer maybe has not processed it here yet...
                        if(decl.value!=null) {
                            decl.names.forEach { name ->
                                val target = AssignTarget(
                                    IdentifierReference(listOf(name), decl.position),
                                    null,
                                    null,
                                    null,
                                    false,
                                    position = decl.position
                                )
                                val assign = Assignment(target.copy(), decl.value!!.copy(), AssignmentOrigin.VARINIT, decl.position)
                                replacements.add(AstInsert.after(decl, assign, scope))
                            }
                            replacements.add(AstRemove(decl, scope))
                            decl.value = null
                            decl.allowInitializeWithZero = false
                            declToInsert = decl
                        } else {
                            // just move it to the defining scope
                            replacements.add(AstRemove(decl, scope))
                            declToInsert = decl
                        }
                    } else {
                        // handle declaration of a single variable
                        if(decl.value!=null && !decl.datatype.isIterable) {
                            val target = AssignTarget(
                                IdentifierReference(listOf(decl.name), decl.position),
                                null,
                                null,
                                null,
                                false,
                                position = decl.position
                            )
                            val assign = Assignment(target, decl.value!!, AssignmentOrigin.VARINIT, decl.position)
                            replacements.add(AstReplaceNode(decl, assign, scope))
                            decl.value = null
                            decl.allowInitializeWithZero = false
                            declToInsert = decl.copy()
                        } else {
                            replacements.add(AstRemove(decl, scope))
                            declToInsert = decl
                        }
                    }
                    movements.add(AstInsert.first(parentscope, declToInsert))
                }
            }
            return movements + replacements
        }
        return noModifications
    }

    override fun after(expr: BinaryExpression, parent: Node): Iterable<AstModification> {
        if(expr.operator==".")
            return noModifications
        if(expr.operator=="in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            return listOf(AstReplaceNode(expr, containment, parent))
        }
        if(expr.operator=="not in") {
            val containment = ContainmentCheck(expr.left, expr.right, expr.position)
            val notContainment = PrefixExpression("not", containment, expr.position)
            return listOf(AstReplaceNode(expr, notContainment, parent))
        }
        return noModifications
    }

    override fun before(decl: VarDecl, parent: Node): Iterable<AstModification> {
        val tuple = decl.value as? ExpressionTuple
        if(tuple!=null) {
            if(decl.names.size != tuple.expressions.size) {
                errors.err("number of initialization values does not match number of variables", decl.position)
                decl.value = null
            } else {
                decl.value = null

                // check if all values in the tuple are a NumericLiteral and that they are all the same number
                // if so replace by vardecl with just this initialization value
                if (tuple.expressions.all { it is NumericLiteral }) {
                    val firstValue = (tuple.expressions.first() as NumericLiteral).number
                    if(tuple.expressions.all { (it as NumericLiteral).number == firstValue }) {
                        decl.value = tuple.expressions.first()
                        return noModifications
                    }
                }

                val vardecls = decl.names
                    .zip(tuple.expressions)
                    .reversed()
                    .map { (name, value) ->
                        val decl = VarDecl(decl.type, decl.origin, decl.datatype, decl.zeropage, decl.splitwordarray, decl.arraysize, decl.matrixNumCols?.copy(), name, emptyList(), value, decl.sharedWithAsm, decl.alignment, decl.dirty, decl.isPrivate, decl.position)
                        AstInsert.after(decl, decl, parent as IStatementContainer)
                    }
                return vardecls + AstRemove(decl, parent as IStatementContainer)
            }
        }
        return noModifications
    }

    override fun after(decl: VarDecl, parent: Node): Iterable<AstModification> {
        val nextAssignment = decl.nextSibling() as? Assignment
        if(nextAssignment!=null && nextAssignment.origin!=AssignmentOrigin.VARINIT) {
            // check if the following assignment initializes the variable
            if(decl.value==null && nextAssignment.target.identifier?.targetVarDecl()===decl) {
                if(!nextAssignment.value.referencesIdentifier(nextAssignment.target.identifier!!.nameInSource))
                    nextAssignment.origin = AssignmentOrigin.VARINIT
            }
        }

        // convert all antlr names to structs
        val antlrTypeName = decl.datatype.subTypeFromAntlr
        if(antlrTypeName!=null) {
            val node = decl.definingScope.lookup(antlrTypeName)
            if(node==null) {
                errors.err("cannot find struct type ${antlrTypeName.joinToString(".")}", decl.position)
            } else if(node is StructDecl) {
                decl.datatype.setActualSubType(node)
            } else if(antlrTypeName.size==1 && antlrTypeName[0] in program.builtinFunctions.names) {
                errors.err("builtin function can only be called, not used as a type name", decl.position)
            } else if(node is Alias) {
                val actual = decl.definingScope.lookup(node.target.nameInSource)
                if(actual is StructDecl)
                    decl.datatype.setActualSubType(actual)
                else if(actual==null)
                    errors.err("cannot find struct type ${node.target.nameInSource.joinToString(".")}", decl.position)
            }
        }

        // prefer to put pointer variables into zeropage if no other preference is given (to avoid having to copy the pointer var to zp every time)
        // NOT for library pointers and NOT for subroutine parameters (otherwise ZP is eaten up way too fast)
        if(decl.datatype.isPointer && decl.zeropage==ZeropageWish.DONTCARE) {
            if(decl.origin!= VarDeclOrigin.SUBROUTINEPARAM && !decl.definingModule.isLibrary) {
                decl.zeropage = ZeropageWish.PREFER_ZEROPAGE
            }
        }

        return noModifications
    }

    override fun after(subroutine: Subroutine, parent: Node): Iterable<AstModification> {
        // For non-kernal subroutines and non-asm parameters:
        // inject subroutine params as local variables (if they're not there yet).
        // If the param should be in a R0-R15 register, don't make a local variable but an alias instead.
        val symbolsInSub = subroutine.allDefinedSymbols
        val namesInSub = symbolsInSub.mapTo(mutableSetOf()) { it.first }
        if(subroutine.asmAddress==null) {
            if(!subroutine.isAsmSubroutine && subroutine.parameters.isNotEmpty()) {
                val mods = mutableListOf<AstModification>()
                val (normalParams, registerParams) = subroutine.parameters.partition { it.registerOrPair==null }
                if(normalParams.isNotEmpty()) {
                    val existingVars = subroutine.statements.filterIsInstance<VarDecl>().mapTo(mutableSetOf()) { it.name }
                    normalParams
                        .filter { it.name !in namesInSub && it.name !in existingVars }
                        .forEach {
                            val vardecl = VarDecl.fromParameter(it)
                            mods += AstInsert.first(subroutine, vardecl)
                        }
                }
                if(registerParams.isNotEmpty()) {
                    val existingAliases = subroutine.statements.filterIsInstance<Alias>().mapTo(mutableSetOf()) { it.alias }
                    registerParams
                        .filter { it.name !in namesInSub && it.name !in existingAliases }
                        .forEach {
                            if (it.registerOrPair in Cx16VirtualRegisters || it.registerOrPair in CombinedLongRegisters) {
                                if(it.type.isInteger || it.type.isBool || it.type.isPointer) {
                                    val mappedParamVar = VarDecl.fromParameter(it)
                                    mods += AstInsert.first(subroutine, mappedParamVar)
                                } else {
                                    errors.err("using R0-R15 as register param requires integer or boolean type", it.position)
                                }
                            } else
                                errors.err("can only use R0-R15 as register param for normal subroutines", it.position)
                        }
                }
                if(mods.isNotEmpty())
                    return mods
            }
        }

        subroutine.returntypes.forEach {
            if(it.subTypeFromAntlr!=null) {
                val struct = subroutine.lookup(it.subTypeFromAntlr!!) as? ISubType
                if(struct!=null)
                    it.setActualSubType(struct)
            }
        }
        subroutine.parameters.forEach {
            if(it.type.subTypeFromAntlr!=null) {
                val struct = subroutine.lookup(it.type.subTypeFromAntlr!!) as? ISubType
                if(struct!=null)
                    it.type.setActualSubType(struct)
            }
        }

        return noModifications
    }

    override fun after(functionCallExpr: FunctionCallExpression, parent: Node): Iterable<AstModification> {
        val stmtOfExpression = findParentNode<Statement>(functionCallExpr)
            ?: throw FatalAstException("cannot determine statement scope of function call expression at ${functionCallExpr.position}")

        checkStringParam(functionCallExpr as IFunctionCall, stmtOfExpression)

        if(functionCallExpr.target.nameInSource==listOf("sizeof")) {
            val arg = functionCallExpr.args.firstOrNull()
            if (arg is PtrDereference) {
                // replace sizeof(ptr^^)  with   sizeof(type that ptr points to)
                val dt = arg.inferType(program)
                if(dt.isKnown) {
                    val dtName = dt.getOrUndef().toString()
                    val newArg = IdentifierReference(dtName.split("."), arg.position)
                    return listOf(AstReplaceNode(arg, newArg, functionCallExpr))
                }
            }
        }
        return noModifications
    }

    override fun after(functionCallStatement: FunctionCallStatement, parent: Node): Iterable<AstModification> {
        checkStringParam(functionCallStatement as IFunctionCall, functionCallStatement)
        return noModifications
    }

    override fun before(alias: Alias, parent: Node): Iterable<AstModification> {
        // shortcut aliases that point to aliases (remove alias chains)
        val tgt = alias.target.targetStatement(program.builtinFunctions)

        if(tgt==null) {
            errors.undefined(alias.target.nameInSource, position = alias.target.position)
        } else {
            if(alias.alias == alias.target.nameInSource.first()) {
                errors.err("alias loop", alias.position)
            } else if(tgt is Alias) {
                var chainedAlias = alias
                var chainedTargetName = alias.target
                var maxhops = 100
                while(true) {
                    val tgt2 = chainedAlias.target.targetStatement(program.builtinFunctions)
                    if (tgt2 is Alias) {
                        chainedAlias = tgt2
                        chainedTargetName = tgt2.target
                    }
                    else {
                        val tgt2 = chainedTargetName.targetStatement(program.builtinFunctions) as? INamedStatement
                        val replacement = if(tgt2!=null) {
                            if(tgt2 is BuiltinFunctionPlaceholder) {
                                val unscopedTarget = IdentifierReference(listOf(tgt2.name), alias.position)
                                Alias(alias.alias, unscopedTarget, alias.position)
                            } else if(tgt2.scopedName != chainedTargetName.nameInSource) {
                                val scopedTarget = IdentifierReference(tgt2.scopedName, alias.position)
                                Alias(alias.alias, scopedTarget, alias.position)
                            } else {
                                Alias(alias.alias, chainedTargetName, alias.position)
                            }
                        } else {
                            Alias(alias.alias, chainedTargetName, alias.position)
                        }
                        return listOf(AstReplaceNode(alias, replacement, parent))
                    }
                    maxhops--
                    if(maxhops==0) {
                        errors.err("alias loop", alias.position)
                        break
                    }
                }
            }
        }

        return noModifications
    }

    override fun after(typecast: TypecastExpression, parent: Node): Iterable<AstModification> {
        // convert all antlr names to structs
        if(typecast.type.subTypeFromAntlr!=null) {
            val struct = typecast.definingScope.lookup(typecast.type.subTypeFromAntlr!!) as? ISubType
            if(struct!=null)
                typecast.type.setActualSubType(struct)
        }
        return noModifications
    }

    override fun after(field: StructFieldRef, parent: Node): Iterable<AstModification> {
        if(field.type.subTypeFromAntlr!=null) {
            val struct = field.definingScope.lookup(field.type.subTypeFromAntlr!!) as? ISubType
            if(struct!=null)
                field.type.setActualSubType(struct)
        }

        return noModifications
    }

    override fun after(struct: StructDecl, parent: Node): Iterable<AstModification> {
        // convert all antlr names to structs
        struct.fields.forEach {
            if(it.first.subTypeFromAntlr!=null) {
                val struct = struct.definingScope.lookup(it.first.subTypeFromAntlr!!) as? ISubType
                if(struct!=null)
                    it.first.setActualSubType(struct)
            }
        }

        // convert str fields to ^^ubyte
        val convertedFields = struct.fields.map {
            if(it.first.isString)
                DataType.pointer(BaseDataType.UBYTE) to it.second       // replace str field with ^^ubyte field
            else
                it.first to it.second
        }.toTypedArray()

        if(!convertedFields.contentEquals(struct.fields))
            convertedFields.copyInto(struct.fields)

        return noModifications
    }

    override fun after(enum: Enumeration, parent: Node): Iterable<AstModification> {
        // first check that there is no name conflict
        (parent as? IStatementContainer)?.let {
            val allNamed = it.statements.asSequence()
                .filterIsInstance<INamedStatement>()
                .filter { it.name==enum.name && it !== enum }
                .toList()
            if(allNamed.isNotEmpty()) {
                val existing = allNamed.first() as Node
                errors.err("name conflict '${enum.name}', also defined in ${existing.position.file} line ${existing.position.line}", enum.position)
                return noModifications
            }
        }

        if("::" in enum.name) {
            errors.err("only enum members can be accessed with '::' syntax", enum.position)
            return noModifications
        }
        for(member in enum.members) {
            if("::" in member.first) {
                errors.err("invalid enum member name '${member.first}'", enum.position)
                return noModifications
            }
        }

        // replace enum by a bunch of const vardecls inside a subroutine for scoping
        val dt = DataType.forDt(enum.type)
        var value = -1
        val constants = enum.members.map {
            val membername = "${enum.name}::${it.first}"
            if(it.second!=null) {
                if(it.second!!>value) {
                    value = it.second!!
                } else {
                    errors.err("invalid enum sequence member value ${it.first} = ${it.second}", enum.position)
                }
            } else value++
            val membervalue = NumericLiteral(enum.type, value.toDouble(), enum.position)
            VarDecl(VarDeclType.CONST, VarDeclOrigin.USERCODE, dt,
                ZeropageWish.DONTCARE, SplitWish.DONTCARE, null, null, membername, emptyList(), membervalue,
                false, 0u, false, false, enum.position)
        }

        /*
            val sub = Subroutine(enum.name, mutableListOf(), mutableListOf(), emptyList(), emptyList(),
                emptySet(), null, false, false, statements=constants.toMutableList(), position=enum.position)
         */

        val modifications =
            constants.map { AstInsert.before(enum, it, parent as IStatementContainer) } +
                AstRemove(enum, parent as IStatementContainer)

        return modifications
    }

    private fun checkStringParam(call: IFunctionCall, stmt: Statement) {
        val targetStatement = call.target.checkFunctionOrLabelExists(program, stmt, errors)
        if(targetStatement!=null) {
            if(targetStatement is Subroutine) {
                for(arg in call.args.zip(targetStatement.parameters)) {
                    if(arg.first.inferType(program).isBytes && arg.second.type.isString) {
                        if((arg.first as? NumericLiteral)?.number!=0.0)
                            errors.err("cannot use byte value for string parameter", arg.first.position)
                    }
                }
            }
        }
    }
}
