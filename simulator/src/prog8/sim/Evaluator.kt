package prog8.sim

import prog8.ast.base.DataType
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
            is PtArrayIndexer -> TODO()
            is PtArrayLiteral -> throw IllegalArgumentException("arrayliteral $expr")
            is PtBinaryExpression -> TODO()
            is PtBuiltinFunctionCall -> TODO()
            is PtConstant -> TODO()
            is PtContainmentCheck -> TODO()
            is PtFunctionCall -> evaluate(expr)
            is PtIdentifier -> evaluate(expr)
            is PtMemoryByte -> TODO()
            is PtNumber -> Pair(expr.number, expr.type)
            is PtPipe -> TODO()
            is PtPrefix -> TODO()
            is PtRange -> throw IllegalArgumentException("range $expr")
            is PtString -> throw IllegalArgumentException("string $expr")
            is PtTypeCast -> TODO()
            else -> TODO("missing evaluator for $expr")
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
            else -> {
                val sub = findPtNode(fcall.target.targetName, fcall)
                passCallArgs(sub, args)
                return simulator.executeSubroutine(sub)
            }
        }
    }

    private fun passCallArgs(sub: PtSub, args: List<Pair<Double, DataType>>) {
        require(sub.parameters.size==args.size)
        for ((param, arg) in sub.parameters.zip(args)) {
            require(param.type==arg.second)
            println("ARG ${param.name} = ${arg.first}") // TODO assign arg
        }
    }

    private fun findPtNode(scopedName: List<String>, scope: PtNode): PtSub {
        var root = scope
        while(root !is PtProgram) {
            root=root.parent
        }
        val block = root.allBlocks().first {
            it.name == scopedName.first()
        }
        var sub: PtNode = block
        scopedName.drop(1).forEach { namepart->
            val nodes = sub.children.filterIsInstance<PtNamedNode>()
            sub = nodes.first { it.name==namepart }
        }
        return sub as PtSub
    }

    private fun evaluate(ident: PtIdentifier): Pair<Double, DataType> {
        val target = symboltable.flat.getValue(ident.targetName)
        when(target.type) {
            StNodeType.STATICVAR -> {
                val variable = target as StStaticVariable
                return Pair(variables.getValue(variable), target.dt)
            }
            StNodeType.CONSTANT -> throw IllegalArgumentException("constants should have been const folded")
            else -> throw IllegalArgumentException("weird ref target")
        }
    }

    private fun evaluate(addressOf: PtAddressOf): Pair<Double, DataType> {
        val target = symboltable.flat.getValue(addressOf.identifier.targetName)
        val alloc = variables[target]
        return if(alloc==null) {
            // TODO throw IllegalArgumentException("can't get address of ${target.scopedName}")
            println("warning: returning dummy address for ${target.scopedName}")
            Pair(4096.0, DataType.UWORD)
        } else
            Pair(alloc.toDouble(), DataType.UWORD)
    }
}