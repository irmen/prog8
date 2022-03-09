package prog8.sim

import prog8.ast.base.DataType
import prog8.compilerinterface.StStaticVariable
import prog8.compilerinterface.SymbolTable
import prog8.compilerinterface.intermediate.*
import java.util.*


sealed class FlowcontrolException : Exception()

class ExitProgram(val status: Int): FlowcontrolException()


class Simulator(val program: PtProgram, val symboltable: SymbolTable) {
    val memory = Memory()
    private val variables = Variables(symboltable)
    private val eval = Evaluator(symboltable, memory, variables, this)
    internal val instructionPtrStack = Stack<InstructionPointer>()
    internal var ip = InstructionPointer(emptyList())

    fun run() {
        memory.clear()
        val start = program.entrypoint() ?: throw NoSuchElementException("no main.start() found")

        ip = InstructionPointer(start.children)
        try {
            while(true)
                executeStatement(ip.current)
        } catch (exit: ExitProgram) {
            println("Program Exit! Status code: ${exit.status}")
        }
    }


    internal fun executeStatement(node: PtNode) {
        return when(node) {
            is PtAsmSub -> throw NotImplementedError("can't run assembly subroutine in simulator at this time")
            is PtAssignment -> execute(node)
            is PtBuiltinFunctionCall -> {
                eval.evaluate(node)     // throw away any result
                ip.next()
            }
            is PtConditionalBranch -> TODO()
            is PtDirective -> execute(node)
            is PtForLoop -> TODO()
            is PtFunctionCall -> {
                eval.evaluate(node)     // throw away any result
                ip.next()
            }
            is PtGosub -> execute(node)
            is PtIfElse -> execute(node)
            is PtInlineAssembly -> throw NotImplementedError("can't run inline assembly in simulator at this time")
            is PtJump -> execute(node)
            is PtLabel -> ip.next()
            is PtPipe -> execute(node)
            is PtPostIncrDecr -> execute(node)
            is PtRepeatLoop -> execute(node)
            is PtReturn -> execute(node)
            is PtSub -> ip.next()  // this simulator doesn't "fall through" into nested subroutines
            is PtVariable -> ip.next()
            is PtWhen -> TODO()
            else -> TODO("missing code for node $node")
        }
    }

    private fun execute(jump: PtJump) {
        if(jump.address!=null)
            throw NotImplementedError("simulator can't jump into memory machine code")
        else if(jump.generatedLabel!=null)
            throw NotImplementedError("simulator can't jump into generated label")
        else {
            ip = findTargetNode(jump.identifier!!.targetName)
        }
    }

    internal fun findTargetNode(targetName: List<String>): InstructionPointer {
        val target = findPtNode(targetName, ip.current)
        val nodes = target.parent.children
        return InstructionPointer(nodes, nodes.indexOf(target))
    }

    private fun execute(gosub: PtGosub) {
        if(gosub.address!=null)
            throw NotImplementedError("simulator can't jump into memory machine code")
        else if(gosub.generatedLabel!=null)
            throw NotImplementedError("simulator can't jump into generated label")
        else {
            instructionPtrStack.push(ip)
            ip = findTargetNode(gosub.identifier!!.targetName)
        }
    }

    private fun execute(post: PtPostIncrDecr) {
        val identifier = post.target.identifier
        val memoryAddr = post.target.memory
        val array = post.target.array
        if(identifier!=null) {
            val variable = symboltable.lookup(identifier.targetName) as StStaticVariable
            var value = variables.getValue(variable).number!!
            if(post.operator=="++") value++ else value--
            variables.setValue(variable, Variables.Value(value, null, null))
        } else if(memoryAddr!=null) {
            val addr = eval.evaluateExpression(memoryAddr.address).first.toUInt()
            if(post.operator=="++")
                memory[addr] = (memory[addr]+1u).toUByte()
            else
                memory[addr] = (memory[addr]-1u).toUByte()
        } else if(array!=null) {
            println("TODO: ${array.variable.ref}[] ${post.operator}")
        }
        ip.next()
    }

    private fun execute(ifelse: PtIfElse) {
        val condition = eval.evaluateExpression(ifelse.condition)
        ip = if(condition.first!=0.0) {
            InstructionPointer(ifelse.ifScope.children)
        } else {
            InstructionPointer(ifelse.elseScope.children)
        }
        // TODO how to handle the exiting of the subscopes, and continue after the ifelse statement.
    }

    private fun execute(repeat: PtRepeatLoop) {
        val count = eval.evaluateExpression(repeat.count).first.toInt()
        // TODO how to handle the exiting of the subscopes, and continue after the repeat statement.
        TODO("repeat $count ${repeat.position}")
        ip.next()
    }

    private fun execute(pipe: PtPipe) {
        TODO("pipe stmt $pipe")
        ip.next()
    }

    private fun execute(ret: PtReturn) {
        if(ret.hasValue) {
            // TODO how to handle the actual return value
        }
        ip = instructionPtrStack.pop()
        ip.next()
    }

    private fun execute(directive: PtDirective) {
        // TODO handle directive
        ip.next()
    }

    private fun execute(assign: PtAssignment) {
        val value = eval.evaluateExpression(assign.value)
        val identifier = assign.target.identifier
        val memoryAddr = assign.target.memory
        val array = assign.target.array
        if(identifier!=null) {
            val targetvar = symboltable.flat.getValue(identifier.targetName) as StStaticVariable
            variables.setValue(targetvar, Variables.Value(value.first, null, null))
        }
        else if(memoryAddr!=null) {
            val address = eval.evaluateExpression(memoryAddr.address)
            require(address.second==DataType.UWORD)
            require(value.second==DataType.UBYTE)
            memory[address.first.toUInt()] = value.first.toInt().toUByte()
        }
        else if(array!=null)
            TODO("assign $value to array $array")
        else
            throw IllegalArgumentException("missing assign target")
        ip.next()
    }
}



internal fun findPtNode(scopedName: List<String>, scope: PtNode): PtNode {
    var root = scope
    while(root !is PtProgram) {
        root=root.parent
    }
    val block = root.allBlocks().first {
        it.name == scopedName.first()
    }
    var node: PtNode = block
    scopedName.drop(1).forEach { namepart->
        val nodes = node.children.filterIsInstance<PtNamedNode>()
        node = nodes.first { it.name==namepart }
    }
    return node
}
