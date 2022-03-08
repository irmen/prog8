package prog8.sim

import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.StStaticVariable
import prog8.compilerinterface.SymbolTable
import prog8.compilerinterface.intermediate.*
import java.util.Stack


sealed class FlowcontrolException : Exception()

class ReturnValue(val value: Pair<Double, DataType>?): FlowcontrolException()

class ExitProgram(val status: Int): FlowcontrolException()

class JumpTo(val targetName: List<String>): FlowcontrolException()


class Simulator(val program: PtProgram, val symboltable: SymbolTable) {
    val memory = Memory()
    private val variables = Variables(symboltable)
    private val eval = Evaluator(symboltable, memory, variables, this)

    fun run() {
        memory.clear()
        val start = program.entrypoint() ?: throw NoSuchElementException("no main.start() found")
        try {
            executeSubroutine(start)
        } catch(r: ReturnValue) {
            println("Program Exit.")
        } catch (exit: ExitProgram) {
            println("Program Exit! Status code: ${exit.status}")
        }
    }

    internal fun executeSubroutine(sub: PtSub): Pair<Double, DataType>? {
        return try {
            executeStatementList(sub.children)
            null
        } catch(r: ReturnValue) {
            r.value
        }
    }

    internal fun executeStatementList(nodes: List<PtNode>) {
        var instructionPtr = InstructionPointer(nodes)
        while(true) {
            try {
                if (executeStatement(instructionPtr.current))
                    instructionPtr.next()
            } catch(jump: JumpTo) {
                val target = findPtNode(jump.targetName, instructionPtr.current)
                val nodes = target.parent.children
                instructionPtr = InstructionPointer(nodes, nodes.indexOf(target))
            }
        }
    }

    internal fun executeStatement(node: PtNode): Boolean {
        return when(node) {
            is PtAsmSub -> throw NotImplementedError("can't run assembly subroutine in simulator at this time")
            is PtAssignment -> execute(node)
            is PtBuiltinFunctionCall -> {
                eval.evaluate(node)     // throw away any result
                true
            }
            is PtConditionalBranch -> TODO()
            is PtDirective -> execute(node)
            is PtForLoop -> TODO()
            is PtFunctionCall -> {
                eval.evaluate(node)     // throw away any result
                true
            }
            is PtGosub -> TODO()
            is PtIfElse -> execute(node)
            is PtInlineAssembly -> throw NotImplementedError("can't run inline assembly in simulator at this time")
            is PtJump -> execute(node)
            is PtLabel -> true
            is PtPipe -> execute(node)
            is PtPostIncrDecr -> execute(node)
            is PtRepeatLoop -> execute(node)
            is PtReturn -> execute(node)
            is PtSub -> true  // this simulator doesn't "fall through" into nested subroutines
            is PtVariable -> true
            is PtWhen -> TODO()
            else -> TODO("missing code for node $node")
        }
    }

    private fun execute(jump: PtJump): Boolean {
        if(jump.address!=null)
            throw NotImplementedError("simulator can't jump into memory machine code")
        else if(jump.generatedLabel!=null)
            throw NotImplementedError("simulator can't jump into generated label")
        else {
            throw JumpTo(jump.identifier!!.targetName)
        }
    }

    private fun execute(post: PtPostIncrDecr): Boolean {
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
        return true
    }

    private fun execute(ifelse: PtIfElse): Boolean {
        val condition = eval.evaluateExpression(ifelse.condition)
        if(condition.first!=0.0) {
            println("TODO: if part")
        } else {
            println("TODO: else part")
        }
        return true
    }

    private fun execute(repeat: PtRepeatLoop): Boolean {
        TODO("repeat $repeat. ${repeat.position}")
        return true
    }

    private fun execute(pipe: PtPipe): Boolean {
        TODO("pipe stmt $pipe")
    }

    private fun execute(ret: PtReturn): Boolean {
        if(ret.hasValue)
            throw ReturnValue(eval.evaluateExpression(ret.value!!))
        else
            throw ReturnValue(null)
    }

    private fun execute(directive: PtDirective): Boolean {
        // TODO handle directive
        return true
    }

    private fun execute(assign: PtAssignment): Boolean {
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
        return true
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
