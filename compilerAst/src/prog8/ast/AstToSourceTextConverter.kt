package prog8.ast

import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.core.*


fun printProgram(program: Program) {
    println()
    val printer = AstToSourceTextConverter(::print, program, true)
    printer.visit(program)
    println()
}


/**
 * Produces Prog8 source text from a [Program] (AST node),
 * passing it as a String to the specified receiver function.
 */
class AstToSourceTextConverter(val output: (text: String) -> Unit, val program: Program, val skipLibraries: Boolean): IAstVisitor {
    private var scopelevel = 0

    private fun indent(s: String) = "    ".repeat(scopelevel) + s
    private fun outputln(text: String) = output(text + "\n")
    private fun outputlni(s: Any) = outputln(indent(s.toString()))
    private fun outputi(s: Any) = output(indent(s.toString()))

    override fun visit(program: Program) {
        outputln("; ============ PROGRAM ${program.name} (FROM AST) ==============")
        super.visit(program)
        outputln("; =========== END PROGRAM ${program.name} (FROM AST) ===========")
    }

    override fun visit(module: Module) {
        if(!module.isLibrary || !skipLibraries) {
            outputln("; ----------- module: ${module.name} -----------")
            super.visit(module)
        }
        else outputln("; library module skipped: ${module.name}")
    }

    override fun visit(block: Block) {
        if(block.isInLibrary && skipLibraries) {
            outputln("; library block skipped: ${block.name}")
            return
        }
        val addr = if(block.address!=null) block.address.toHex() else ""
        outputln("${block.name} $addr {")
        scopelevel++
        for(stmt in block.statements) {
            outputi("")
            stmt.accept(this)
            output("\n")
        }
        scopelevel--
        outputln("}\n")
    }

    override fun visit(ifExpr: IfExpression) {
        output("if ")
        ifExpr.condition.accept(this)
        output(" ")
        ifExpr.truevalue.accept(this)
        output(" else ")
        ifExpr.falsevalue.accept(this)
    }

    override fun visit(continueStmt: Continue) {
        output("continue")
    }

    override fun visit(unrollLoop: UnrollLoop) {
        output("unroll ")
        unrollLoop.iterations.accept(this)
        output(" ")
        unrollLoop.body.accept(this)
    }

    override fun visit(containment: ContainmentCheck) {
        containment.element.accept(this)
        output(" in ")
        containment.iterable.accept(this)
    }

    override fun visit(expr: PrefixExpression) {
        if(expr.operator.any { it.isLetter() })
            output(" ${expr.operator} ")
        else
            output(expr.operator)
        expr.expression.accept(this)
    }

    override fun visit(expr: BinaryExpression) {
        output("(")
        expr.left.accept(this)
        if(expr.operator.any { it.isLetter() })
            output(" ${expr.operator} ")
        else
            output(expr.operator)
        expr.right.accept(this)
        output(")")
    }

    override fun visit(directive: Directive) {
        output("${directive.directive} ")
        for(arg in directive.args) {
            when {
                arg.int!=null -> output(arg.int.toString())
                arg.name!=null -> output(arg.name)
                arg.str!=null -> output("\"${arg.str}\"")
            }
            if(arg!==directive.args.last())
                output(",")
        }
        output("\n")
    }

    override fun visit(decl: VarDecl) {
        if(decl.origin==VarDeclOrigin.SUBROUTINEPARAM)
            return

        when(decl.type) {
            VarDeclType.VAR -> {}
            VarDeclType.CONST -> output("const ")
            VarDeclType.MEMORY -> output("&")
        }

        output(decl.datatype.sourceString())
        if(decl.arraysize!=null) {
            decl.arraysize!!.indexExpr.accept(this)
        }
        if(decl.isArray)
            output("]")

        if(decl.zeropage == ZeropageWish.REQUIRE_ZEROPAGE)
            output(" @requirezp")
        else if(decl.zeropage == ZeropageWish.PREFER_ZEROPAGE)
            output(" @zp")
        if(decl.sharedWithAsm)
            output(" @shared")
        when(decl.alignment) {
            0u -> {}
            2u -> output(" @alignword")
            64u -> output(" @align64")
            256u -> output(" @alignpage")
            else -> throw IllegalArgumentException("invalid alignment size")
        }
        if(decl.names.size>1)
            output(decl.names.joinToString(prefix=" "))
        else
            output(" ${decl.name} ")
        if(decl.value!=null) {
            output("= ")
            decl.value?.accept(this)
        }
    }

    override fun visit(subroutine: Subroutine) {
        output("\n")
        outputi("")
        if(subroutine.inline)
            output("inline ")
        if(subroutine.isAsmSubroutine) {
            if(subroutine.asmAddress!=null) {
                val bank = if(subroutine.asmAddress.constbank!=null) "@bank ${subroutine.asmAddress.constbank}"
                    else if(subroutine.asmAddress.varbank!=null) "@bank ${subroutine.asmAddress.varbank?.nameInSource?.joinToString(".")}"
                    else ""
                val address = subroutine.asmAddress.address
                val addrString = if(address is NumericLiteral) address.number.toHex() else "<non-const-address>"
                output("extsub $bank $addrString = ${subroutine.name} (")
            }
            else
                output("asmsub ${subroutine.name} (")
            for(param in subroutine.parameters.zip(subroutine.asmParameterRegisters)) {
                val reg =
                        when {
                            param.second.registerOrPair!=null -> param.second.registerOrPair.toString()
                            param.second.statusflag!=null -> param.second.statusflag.toString()
                            else -> "?????"
                        }
                output("${param.first.type.sourceString()} ${param.first.name} @$reg")
                if(param.first!==subroutine.parameters.last())
                    output(", ")
            }
        }
        else {
            output("sub ${subroutine.name} (")
            for(param in subroutine.parameters) {
                val reg = if(param.registerOrPair!=null) " @${param.registerOrPair}" else ""
                output("${param.type} ${param.name}$reg")
                if(param!==subroutine.parameters.last())
                    output(", ")
            }
        }
        output(") ")
        if(subroutine.asmClobbers.isNotEmpty()) {
            output("-> clobbers (")
            val regs = subroutine.asmClobbers.toList().sorted()
            for(r in regs) {
                output(r.toString())
                if(r!==regs.last())
                    output(",")
            }
            output(") ")
        }
        if(subroutine.returntypes.any()) {
            if(subroutine.asmReturnvaluesRegisters.isNotEmpty()) {
                val rts = subroutine.returntypes.zip(subroutine.asmReturnvaluesRegisters).joinToString(", ") {
                    val dtstr = it.first.sourceString()
                    if(it.second.registerOrPair!=null)
                        "$dtstr @${it.second.registerOrPair}"
                    else
                        "$dtstr @${it.second.statusflag}"
                }
                output("-> $rts ")
            } else {
                val rts = subroutine.returntypes.joinToString(", ") { it.sourceString() }
                output("-> $rts ")
            }
        }
        if (subroutine.asmAddress == null) {
            outputln("{ ")
            scopelevel++
            outputStatements(subroutine.statements.filter { it !is VarDecl || it.origin!=VarDeclOrigin.SUBROUTINEPARAM})
            scopelevel--
            outputi("}")
        }
    }

    private fun outputStatements(statements: List<Statement>) {
        for(stmt in statements) {
            outputi("")
            stmt.accept(this)
            output("\n")
        }
    }

    override fun visit(functionCallExpr: FunctionCallExpression) {
        printout(functionCallExpr as IFunctionCall)
    }

    override fun visit(functionCallStatement: FunctionCallStatement) {
        printout(functionCallStatement as IFunctionCall)
    }

    private fun printout(call: IFunctionCall) {
        call.target.accept(this)
        output("(")
        for(arg in call.args) {
            arg.accept(this)
            if(arg!==call.args.last())
                output(", ")
        }
        output(")")
    }

    override fun visit(identifier: IdentifierReference) {
        output(identifier.nameInSource.joinToString("."))
    }

    override fun visit(jump: Jump) {
        output("goto ")
        jump.target.accept(this)
    }

    override fun visit(ifElse: IfElse) {
        output("if ")
        ifElse.condition.accept(this)
        output(" ")
        ifElse.truepart.accept(this)
        if(ifElse.elsepart.statements.isNotEmpty()) {
            output(" else ")
            ifElse.elsepart.accept(this)
        }
    }

    override fun visit(branch: ConditionalBranch) {
        output("if_${branch.condition.toString().lowercase()} ")
        branch.truepart.accept(this)
        if(branch.elsepart.statements.isNotEmpty()) {
            output(" else ")
            branch.elsepart.accept(this)
        }
    }

    override fun visit(range: RangeExpression) {
        range.from.accept(this)
        output(" to ")
        range.to.accept(this)
        output(" step ")
        range.step.accept(this)
        output(" ")
    }

    override fun visit(label: Label) {
        output("\n")
        output("${label.name}:")
    }

    override fun visit(numLiteral: NumericLiteral) {
        when (numLiteral.type) {
            BaseDataType.BOOL -> output(if(numLiteral.number==0.0) "false" else "true")
            BaseDataType.FLOAT -> output(numLiteral.number.toString())
            else -> output(numLiteral.number.toInt().toString())
        }
    }

    override fun visit(char: CharLiteral) {
        if(char.encoding==Encoding.DEFAULT)
            output("'${char.value.escape()}'")
        else
            output("${char.encoding.prefix}:'${char.value.escape()}'")
    }

    override fun visit(string: StringLiteral) {
        if(string.encoding==Encoding.DEFAULT)
            output("\"${string.value.escape()}\"")
        else
            output("${string.encoding.prefix}:\"${string.value.escape()}\"")
    }

    override fun visit(array: ArrayLiteral) {
        outputListMembers(array.value)
    }

    private fun outputListMembers(array: Array<Expression>) {
        var counter = 0
        output("[")
        scopelevel++
        for ((idx, v) in array.withIndex()) {
            v.accept(this)
            if (idx != array.size-1)
                output(", ")
            counter++
            if (counter > 16) {
                outputln("")
                outputi("")
                counter = 0
            }
        }
        scopelevel--
        output("]")
    }

    override fun visit(assignment: Assignment) {
        val binExpr = assignment.value as? BinaryExpression
        if(binExpr!=null && binExpr.left isSameAs assignment.target
                && binExpr.operator !in ComparisonOperators) {
            // we only support the inplace assignments of the form A = A <operator> <value>
            assignment.target.accept(this)
            output(" ${binExpr.operator}= ")
            binExpr.right.accept(this)
        } else {
            assignment.target.accept(this)
            output(" = ")
            assignment.value.accept(this)
        }
    }

    override fun visit(chainedAssignment: ChainedAssignment) {
        chainedAssignment.target.accept(this)
        output(" = ")
        chainedAssignment.nested.accept(this)
    }

    override fun visit(breakStmt: Break) {
        output("break")
    }

    override fun visit(forLoop: ForLoop) {
        output("for ")
        forLoop.loopVar.accept(this)
        output(" in ")
        forLoop.iterable.accept(this)
        output(" ")
        forLoop.body.accept(this)
    }

    override fun visit(whileLoop: WhileLoop) {
        output("while ")
        whileLoop.condition.accept(this)
        output(" ")
        whileLoop.body.accept(this)
    }

    override fun visit(repeatLoop: RepeatLoop) {
        output("repeat ")
        repeatLoop.iterations?.accept(this)
        output(" ")
        repeatLoop.body.accept(this)
    }

    override fun visit(untilLoop: UntilLoop) {
        output("do ")
        untilLoop.body.accept(this)
        output(" until ")
        untilLoop.condition.accept(this)
    }

    override fun visit(returnStmt: Return) {
        if(returnStmt.values.isEmpty())
            output("return")
        else
            output("return ${returnStmt.values.map{it.accept(this)}.joinToString(", ")}")
    }

    override fun visit(arrayIndexedExpression: ArrayIndexedExpression) {
        arrayIndexedExpression.arrayvar.accept(this)
        output("[")
        arrayIndexedExpression.indexer.indexExpr.accept(this)
        output("]")
    }

    override fun visit(assignTarget: AssignTarget) {
        if(assignTarget.void)
            output("void")
        else {
            assignTarget.memoryAddress?.accept(this)
            assignTarget.identifier?.accept(this)
            assignTarget.arrayindexed?.accept(this)
            val multi = assignTarget.multi
            if (multi != null) {
                multi.dropLast(1).forEach { target ->
                    target.accept(this)
                    output(", ")
                }
                multi.last().accept(this)
            }
        }
    }

    override fun visit(scope: AnonymousScope) {
        outputln("{")
        scopelevel++
        outputStatements(scope.statements)
        scopelevel--
        outputi("}")
    }

    override fun visit(defer: Defer) {
        outputln("defer {")
        scopelevel++
        outputStatements(defer.scope.statements)
        scopelevel--
        outputi("}")
    }

    override fun visit(typecast: TypecastExpression) {
        output("(")
        typecast.expression.accept(this)
        output(" as ${DataType.forDt(typecast.type).sourceString()}) ")
    }

    override fun visit(memread: DirectMemoryRead) {
        output("@(")
        memread.addressExpression.accept(this)
        output(")")
    }

    override fun visit(memwrite: DirectMemoryWrite) {
        output("@(")
        memwrite.addressExpression.accept(this)
        output(")")
    }

    override fun visit(addressOf: AddressOf) {
        output("&")
        if(addressOf.msb)
            output(">")
        addressOf.identifier.accept(this)
        if(addressOf.arrayIndex!=null) {
            output("[")
            addressOf.arrayIndex?.accept(this)
            output("]")
        }
    }

    override fun visit(inlineAssembly: InlineAssembly) {
        outputlni("%asm {{")
        outputln(inlineAssembly.assembly)
        outputlni("}}")
    }

    override fun visit(whenStmt: When) {
        output("when ")
        whenStmt.condition.accept(this)
        outputln(" {")
        scopelevel++
        whenStmt.choices.forEach { it.accept(this) }
        scopelevel--
        outputlni("}")
    }

    override fun visit(whenChoice: WhenChoice) {
        val choiceValues = whenChoice.values
        if(choiceValues==null)
            outputi("else -> ")
        else {
            outputi("")
            for(value in choiceValues) {
                value.accept(this)
                if(value !== choiceValues.last())
                    output(",")
            }
            output(" -> ")
        }
        if(whenChoice.statements.statements.size==1)
            whenChoice.statements.statements.single().accept(this)
        else
            whenChoice.statements.accept(this)
        outputln("")
    }

    override fun visit(alias: Alias) {
        output("alias ${alias.alias} = ${alias.target.nameInSource.joinToString(".")}")
    }
}
