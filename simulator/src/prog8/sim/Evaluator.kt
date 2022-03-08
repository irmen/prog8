package prog8.sim

import prog8.ast.base.DataType
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.StNodeType
import prog8.compilerinterface.StStaticVariable
import prog8.compilerinterface.SymbolTable
import prog8.compilerinterface.intermediate.*

class Evaluator(
    val symboltable: SymbolTable,
    val memory: Memory,
    val variables: Variables,
    val simulator: Simulator
) {

    fun evaluateExpression(expr: PtNode): Pair<Double, DataType> {
        return when(expr) {
            is PtAddressOf -> evaluate(expr)
            is PtArrayIndexer -> evaluate(expr)
            is PtArrayLiteral -> throw IllegalArgumentException("arrayliteral $expr")
            is PtBinaryExpression -> evaluate(expr)
            is PtBuiltinFunctionCall -> evaluate(expr)
            is PtConstant -> Pair(expr.value, expr.type)
            is PtContainmentCheck -> TODO()
            is PtFunctionCall -> evaluate(expr)
            is PtIdentifier -> evaluate(expr)
            is PtMemoryByte -> evaluate(expr)
            is PtNumber -> Pair(expr.number, expr.type)
            is PtPipe -> TODO()
            is PtPrefix -> TODO()
            is PtRange -> throw IllegalArgumentException("range $expr")
            is PtString -> throw IllegalArgumentException("string $expr")
            is PtTypeCast -> TODO()
            else -> TODO("missing evaluator for $expr")
        }
    }

    private fun evaluate(memoryByte: PtMemoryByte): Pair<Double, DataType> {
        val address = evaluateExpression(memoryByte.address).first.toUInt()
        val value = memory[address]
        return Pair(value.toDouble(), DataType.UBYTE)
    }

    private fun evaluate(arrayIdx: PtArrayIndexer): Pair<Double, DataType> {
        val index = evaluateExpression(arrayIdx.index)
        println("TODO: get array value ${arrayIdx.variable.ref}[${index.first.toInt()}]")
        return Pair(0.0, DataType.UBYTE)
    }

    internal fun evaluate(expr: PtBinaryExpression): Pair<Double, DataType> {
        val left = evaluateExpression(expr.left)
        val right = evaluateExpression(expr.right)
        require(left.second==right.second)

        // TODO implement 8/16 bit maths

        return when(expr.operator) {
            "+" -> Pair(left.first+right.first, left.second)
            "-" -> Pair(left.first-right.first, left.second)
            "*" -> Pair(left.first*right.first, left.second)
            "/" -> Pair(left.first/right.first, left.second)
            "==" -> {
                val bool = if(left.first==right.first) 1 else 0
                Pair(bool.toDouble(), DataType.UBYTE)
            }
            "!=" -> {
                val bool = if(left.first!=right.first) 1 else 0
                Pair(bool.toDouble(), DataType.UBYTE)
            }
            "<" -> {
                val bool = if(left.first<right.first) 1 else 0
                Pair(bool.toDouble(), DataType.UBYTE)
            }
            ">" -> {
                val bool = if(left.first>right.first) 1 else 0
                Pair(bool.toDouble(), DataType.UBYTE)
            }
            "<=" -> {
                val bool = if(left.first<=right.first) 1 else 0
                Pair(bool.toDouble(), DataType.UBYTE)
            }
            ">=" -> {
                val bool = if(left.first>=right.first) 1 else 0
                Pair(bool.toDouble(), DataType.UBYTE)
            }

            else -> TODO("binexpr operator ${expr.operator}")
        }
    }

    internal fun evaluate(fcall: PtFunctionCall): Pair<Double, DataType> {
        val ref = fcall.target.ref
        val args = fcall.args.children.map { evaluateExpression(it) }
        return when(fcall.target.targetName) {
            listOf("sys", "memset") -> {
                memory.memset(args[0].first.toUInt(), args[1].first.toUInt(), args[2].first.toInt().toUByte())
                Pair(0.0, DataType.UBYTE)
            }
            listOf("txt", "print") -> {
                print(memory.getString(args.single().first.toUInt()))     // strings are passed as a memory address
                Pair(0.0, DataType.UBYTE)
            }
            listOf("txt", "print_uw") -> {
                print(args.single().first.toUInt())
                Pair(0.0, DataType.UBYTE)
            }
            else -> {
                val node = findPtNode(fcall.target.targetName, fcall)
                if(node is PtAsmSub)
                    throw NotImplementedError("simulator can't run asmsub ${node.name}")
                node as PtSub
                passCallArgs(node, args)
                return simulator.executeSubroutine(node)!!
            }
        }
    }

    internal fun evaluate(fcall: PtBuiltinFunctionCall): Pair<Double, DataType> {
        println("TODO: builtin function call ${fcall.name}")
        return Pair(0.0, DataType.UBYTE)
    }

    private fun passCallArgs(sub: PtSub, args: List<Pair<Double, DataType>>) {
        require(sub.parameters.size==args.size)
        for ((param, arg) in sub.parameters.zip(args)) {
            require(param.type==arg.second)
            println("ARG ${param.name} = ${arg.first}") // TODO assign arg
        }
    }

    private fun evaluate(ident: PtIdentifier): Pair<Double, DataType> {
        val target = symboltable.flat.getValue(ident.targetName)
        when(target.type) {
            StNodeType.STATICVAR -> {
                val variable = target as StStaticVariable
                val value = variables.getValue(variable)
                if(value.number==null)
                    TODO("${ident.ref} -> $value")
                return Pair(value.number!!, target.dt)
            }
            StNodeType.CONSTANT -> throw IllegalArgumentException("constants should have been const folded")
            else -> throw IllegalArgumentException("weird ref target")
        }
    }

    private fun evaluate(addressOf: PtAddressOf): Pair<Double, DataType> {
        val target = symboltable.flat.getValue(addressOf.identifier.targetName) as StStaticVariable
        return Pair(variables.getAddress(target).toDouble(), DataType.UWORD)
    }
}