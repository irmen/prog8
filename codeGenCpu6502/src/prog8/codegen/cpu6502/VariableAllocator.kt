package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onSuccess
import prog8.ast.base.*
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.Subroutine
import prog8.ast.statements.ZeropageWish
import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.IErrorReporter
import prog8.compilerinterface.IVariablesAndConsts
import prog8.compilerinterface.ZeropageType


internal class VariableAllocator(private val vars: IVariablesAndConsts,
                                 private val options: CompilationOptions,
                                 private val errors: IErrorReporter) {

    private val zeropage = options.compTarget.machine.zeropage
    private val subroutineExtras = mutableMapOf<Subroutine, SubroutineExtraAsmInfo>()
    private val memorySlabsInternal = mutableMapOf<String, Pair<UInt, UInt>>()
    internal val memorySlabs: Map<String, Pair<UInt, UInt>> = memorySlabsInternal
    internal val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)

    internal fun getMemorySlab(name: String) = memorySlabsInternal[name]
    internal fun allocateMemorySlab(name: String, size: UInt, align: UInt) {
        memorySlabsInternal[name] = Pair(size, align)
    }

    /**
     * Allocate variables into the Zeropage.
     * The result should be retrieved from the current machine's zeropage object!
     */
    internal fun allocateZeropageVariables() {
        if(options.zeropage== ZeropageType.DONTUSE)
            return

        val allVariables = (
                        vars.blockVars.asSequence().flatMap { it.value } +
                        vars.subroutineVars.asSequence().flatMap { it.value }
                ).toList()

        val numberOfAllocatableVariables = allVariables.size
        val totalAllocatedMemorySize = allVariables.sumOf { memsize(it) }

        val varsRequiringZp = allVariables.filter { it.zp == ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp = allVariables.filter { it.zp == ZeropageWish.PREFER_ZEROPAGE }
        val varsDontCare = allVariables.filter { it.zp == ZeropageWish.DONTCARE }
        val numberOfExplicitNonZpVariables = allVariables.count { it.zp == ZeropageWish.NOT_IN_ZEROPAGE }
        require(varsDontCare.size + varsRequiringZp.size + varsPreferringZp.size + numberOfExplicitNonZpVariables == numberOfAllocatableVariables)

        var numVariablesAllocatedInZP: Int = 0
        var numberOfNonIntegerVariables: Int = 0

        varsRequiringZp.forEach { variable ->
            val numElements = numArrayElements(variable)
            val result = zeropage.allocate(
                variable.scopedname,
                variable.type,
                variable.scope,
                numElements,
                variable.initialValue,
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
                val numElements = numArrayElements(variable)
                val result = zeropage.allocate(
                    variable.scopedname,
                    variable.type,
                    variable.scope,
                    numElements,
                    variable.initialValue,
                    variable.position,
                    errors
                )
                result.onSuccess { numVariablesAllocatedInZP++ }
                //  no need to check for allocation error, if there is one, just allocate in normal system ram.
            }

            // try to allocate any other interger variables into the zeropage until it is full.
            // TODO some form of intelligent priorization? most often used variables first? loopcounter vars first? ...?
            if(errors.noErrors()) {
                for (variable in varsDontCare) {
                    if(variable.type in IntegerDatatypes) {
                        if(zeropage.free.isEmpty()) {
                            break
                        } else {
                            val numElements = numArrayElements(variable)
                            val result = zeropage.allocate(
                                variable.scopedname,
                                variable.type,
                                variable.scope,
                                numElements,
                                variable.initialValue,
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

        println("  number of allocated vars: $numberOfAllocatableVariables  total size: $totalAllocatedMemorySize")
        println("  put into zeropage: $numVariablesAllocatedInZP,  non-zp allocatable: ${numberOfNonIntegerVariables+numberOfExplicitNonZpVariables}")
        println("  zeropage free space: ${zeropage.free.size} bytes")
    }

    private fun memsize(variable: IVariablesAndConsts.StaticVariable): Int {
        return when(variable.type) {
            in NumericDatatypes ->
                options.compTarget.memorySize(variable.type)
            in ArrayDatatypes -> variable.arraysize!! * options.compTarget.memorySize(ArrayToElementTypes.getValue(variable.type))
            DataType.STR -> (variable.initialValue as StringLiteralValue).value.length + 1
            else -> 0
        }
    }

    internal fun isZpVar(scopedName: List<String>) = scopedName in zeropage.variables

    private fun numArrayElements(variable: IVariablesAndConsts.StaticVariable) =
        when(variable.type) {
            DataType.STR -> (variable.initialValue as StringLiteralValue).value.length
            in ArrayDatatypes -> variable.arraysize!!
            else -> null
        }

    fun subroutineExtra(sub: Subroutine): SubroutineExtraAsmInfo {
        var extra = subroutineExtras[sub]
        return if(extra==null) {
            extra = SubroutineExtraAsmInfo()
            subroutineExtras[sub] = extra
            extra
        }
        else
            extra
    }

    fun getFloatAsmConst(number: Double): String {
        val asmName = globalFloatConsts[number]
        if(asmName!=null)
            return asmName

        val newName = "prog8_float_const_${globalFloatConsts.size}"
        globalFloatConsts[number] = newName
        return newName
    }
}

/**
 * This class contains various attributes that influence the assembly code generator.
 * Conceptually it should be part of any INameScope.
 * But because the resulting code only creates "real" scopes on a subroutine level,
 * it's more consistent to only define these attributes on a Subroutine node.
 */
internal class SubroutineExtraAsmInfo {
    var usedRegsaveA = false
    var usedRegsaveX = false
    var usedRegsaveY = false
    var usedFloatEvalResultVar1 = false
    var usedFloatEvalResultVar2 = false

    val extraVars = mutableListOf<Triple<DataType, String, UInt?>>()
}
