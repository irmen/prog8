package prog8.sim

import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.StStaticVariable
import prog8.compilerinterface.SymbolTable
import prog8.compilerinterface.intermediate.*
import java.util.Stack


class ExitProgram(val status: Int): Exception()


class Simulator(val program: PtProgram, val symboltable: SymbolTable) {
    val memory = Memory()
    private val variables = Variables(symboltable, program.memsizer)
    private val eval = Evaluator(symboltable, memory, variables, this)
    private val callStack = Stack<InstructionPointer>()
    private var instructionPtr = InstructionPointer(listOf(PtReturn(Position.DUMMY)))

    fun run() {
        memory.clear()
        val start = program.entrypoint() ?: throw NoSuchElementException("no main.start() found")
        try {
            executeSubroutine(start)
        } catch (exit: ExitProgram) {
            println("Program Exit! Status code: ${exit.status}")
        }
    }

    internal fun executeSubroutine(sub: PtSub): Pair<Double, DataType> {
        instructionPtr = InstructionPointer(sub.children)
        callStack.push(instructionPtr)
        while(true) {
            val incr = executeStatement(instructionPtr.current)
            if(callStack.empty())
                throw ExitProgram(0)
            if(incr)
                instructionPtr++
        }
    }

    internal fun executeStatement(node: PtNode): Boolean {
        return when(node) {
            is PtAsmSub -> true
            is PtAssignment -> execute(node)
            is PtBuiltinFunctionCall -> execute(node)
            is PtConditionalBranch -> TODO()
            is PtDirective -> execute(node)
            is PtForLoop -> TODO()
            is PtFunctionCall -> {
                eval.evaluate(node)     // throw away any result
                true
            }
            is PtGosub -> TODO()
            is PtIfElse -> TODO()
            is PtInlineAssembly -> throw NotImplementedError("can't run inline assembly in simulator at this time")
            is PtJump -> TODO()
            is PtLabel -> true
            is PtPipe -> execute(node)
            is PtPostIncrDecr -> TODO()
            is PtRepeatLoop -> execute(node)
            is PtReturn -> execute(node)
            is PtSub -> true  // this simulator doesn't "fall through" into nested subroutines
            is PtVariable -> true
            is PtWhen -> TODO()
            else -> TODO("missing code for node $node")
        }
    }

    private fun execute(repeat: PtRepeatLoop): Boolean {
        TODO("repeat $repeat. ${repeat.position}")
        return true
    }

    private fun execute(pipe: PtPipe): Boolean {
        TODO("pipe stmt $pipe")
    }

    private fun execute(ret: PtReturn): Boolean {
        instructionPtr = callStack.pop()
        return true
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
            variables.setValue(targetvar, value.first)
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

    private fun execute(fcall: PtBuiltinFunctionCall): Boolean {
        when(fcall.name) {
            "print" -> {
                val string = fcall.children.single() as PtString
                require(string.encoding==Encoding.DEFAULT)
                print(string.value)
            }
            else -> TODO("missing builtin function ${fcall.name}")
        }
        return true
    }
}