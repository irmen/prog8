package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onSuccess
import prog8.code.StNode
import prog8.code.StNodeType
import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.core.*


internal class VariableAllocator(private val symboltable: SymbolTable,
                                 private val options: CompilationOptions,
                                 private val errors: IErrorReporter
) {

    private val zeropage = options.compTarget.machine.zeropage
    private val memorySlabsInternal = mutableMapOf<String, Pair<UInt, UInt>>()
    internal val memorySlabs: Map<String, Pair<UInt, UInt>> = memorySlabsInternal
    internal val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)
    internal val zeropageVars: Map<List<String>, Zeropage.ZpAllocation> = zeropage.allocatedVariables

    init {
        allocateZeropageVariables()
    }

    internal fun getMemorySlab(name: String) = memorySlabsInternal[name]

    internal fun allocateMemorySlab(name: String, size: UInt, align: UInt) {
        memorySlabsInternal[name] = Pair(size, align)
    }

    internal fun isZpVar(scopedName: List<String>) = scopedName in zeropage.allocatedVariables

    internal fun getFloatAsmConst(number: Double): String {
        val asmName = globalFloatConsts[number]
        if(asmName!=null)
            return asmName

        val newName = "prog8_float_const_${globalFloatConsts.size}"
        globalFloatConsts[number] = newName
        return newName
    }

    /**
     * Allocate variables into the Zeropage.
     * The result should be retrieved from the current machine's zeropage object!
     */
    private fun allocateZeropageVariables() {
        if(options.zeropage== ZeropageType.DONTUSE)
            return

        val allVariables = collectAllVariables(symboltable)

        val numberOfAllocatableVariables = allVariables.size
        val varsRequiringZp = allVariables.filter { it.zpwish == ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp = allVariables.filter { it.zpwish == ZeropageWish.PREFER_ZEROPAGE }
        val varsDontCare = allVariables.filter { it.zpwish == ZeropageWish.DONTCARE }
        val numberOfExplicitNonZpVariables = allVariables.count { it.zpwish == ZeropageWish.NOT_IN_ZEROPAGE }
        require(varsDontCare.size + varsRequiringZp.size + varsPreferringZp.size + numberOfExplicitNonZpVariables == numberOfAllocatableVariables)

        var numVariablesAllocatedInZP = 0
        var numberOfNonIntegerVariables = 0

        varsRequiringZp.forEach { variable ->
            val result = zeropage.allocate(
                variable.scopedName,
                variable.dt,
                variable.length,
                variable.position,
                errors
            )
            result.fold(
                success = {
                    numVariablesAllocatedInZP++
                },
                failure = {
                    errors.err(it.message!!, variable.position)
                }
            )
        }

        if(errors.noErrors()) {
            varsPreferringZp.forEach { variable ->
                val result = zeropage.allocate(
                    variable.scopedName,
                    variable.dt,
                    variable.length,
                    variable.position,
                    errors
                )
                result.onSuccess { numVariablesAllocatedInZP++ }
                //  no need to check for allocation error, if there is one, just allocate in normal system ram.
            }

            // try to allocate any other interger variables into the zeropage until it is full.
            // TODO some form of intelligent priorization? most often used variables first? loopcounter vars first? ...?
            if(errors.noErrors()) {
                for (variable in varsDontCare.sortedBy { it.scopedName.size }) {
                    if(variable.dt in IntegerDatatypes) {
                        if(zeropage.free.isEmpty()) {
                            break
                        } else {
                            val result = zeropage.allocate(
                                variable.scopedName,
                                variable.dt,
                                variable.length,
                                variable.position,
                                errors
                            )
                            result.onSuccess { numVariablesAllocatedInZP++ }
                        }
                    } else
                        numberOfNonIntegerVariables++
                }
            }
        }

//        println("  number of allocated vars: $numberOfAllocatableVariables")
//        println("  put into zeropage: $numVariablesAllocatedInZP,  non-zp allocatable: ${numberOfNonIntegerVariables+numberOfExplicitNonZpVariables}")
//        println("  zeropage free space: ${zeropage.free.size} bytes")
    }

    private fun collectAllVariables(st: SymbolTable): Collection<StStaticVariable> {
        val vars = mutableListOf<StStaticVariable>()
        fun collect(node: StNode) {
            for(child in node.children) {
                if(child.value.type == StNodeType.STATICVAR)
                    vars.add(child.value as StStaticVariable)
                else
                    collect(child.value)
            }
        }
        collect(st)
        return vars
    }
}
