package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onSuccess
import prog8.code.*
import prog8.code.core.*


internal class VariableAllocator(private val symboltable: SymbolTable,
                                 private val options: CompilationOptions,
                                 private val errors: IErrorReporter
) {

    private val zeropage = options.compTarget.zeropage
    internal val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)
    internal val zeropageVars: Map<String, MemoryAllocator.VarAllocation>

    init {
        allocateZeropageVariables()
        zeropageVars = zeropage.allocatedVariables
    }

    internal fun isZpVar(scopedName: String): Boolean {
        if(scopedName in zeropageVars)
            return true

        val v = symboltable.lookup(scopedName)
        return if(v is StMemVar) v.address <= 255u else false
    }

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
        val varsNotZp = allVariables.filter { it.zpwish == ZeropageWish.NOT_IN_ZEROPAGE }
        val (varsDontCareWithoutAlignment, varsDontCareWithAlignment) = allVariables.filter { it.zpwish == ZeropageWish.DONTCARE }.partition { it.align == 0u }
        require(varsDontCareWithAlignment.size + varsDontCareWithoutAlignment.size + varsRequiringZp.size + varsPreferringZp.size + varsNotZp.size == numberOfAllocatableVariables)

        var numVariablesAllocatedInZP = 0
        var numberOfNonIntegerVariables = 0

        varsRequiringZp.forEach { variable ->
            val result = zeropage.allocate(
                variable.scopedNameString,
                variable.dt,
                variable.length?.toInt(),
                variable.astNode?.position ?: Position.DUMMY,
                errors
            )
            result.fold(
                success = {
                    numVariablesAllocatedInZP++
                },
                failure = {
                    errors.err(it.message!!, variable.astNode?.position ?: Position.DUMMY)
                }
            )
        }

        if(errors.noErrors()) {
            varsPreferringZp.forEach { variable ->
                val result = zeropage.allocate(
                    variable.scopedNameString,
                    variable.dt,
                    variable.length?.toInt(),
                    variable.astNode?.position ?: Position.DUMMY,
                    errors
                )
                result.onSuccess { numVariablesAllocatedInZP++ }
                //  no need to check for allocation error, if there is one, just allocate in normal system ram.
            }

            // try to allocate the "don't care" interger variables into the zeropage until it is full.
            // TODO some form of intelligent priorization? most often used variables first? loopcounter vars first? ...?
            if(errors.noErrors()) {
                val sortedList = varsDontCareWithoutAlignment.sortedByDescending { it.scopedNameString }
                for (variable in sortedList) {
                    if(variable.dt.isIntegerOrBool) {
                        if(zeropage.free.isEmpty()) {
                            break
                        } else {
                            val result = zeropage.allocate(
                                variable.scopedNameString,
                                variable.dt,
                                variable.length?.toInt(),
                                variable.astNode?.position ?: Position.DUMMY,
                                errors
                            )
                            result.onSuccess { numVariablesAllocatedInZP++ }
                        }
                    } else
                        numberOfNonIntegerVariables++
                }
            }
        }

        // note: no zeropage allocation is done at all for the @nozp variables. This means they will always end up outside the zeropage.
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
        return vars.sortedBy { it.dt.base }
    }
}
