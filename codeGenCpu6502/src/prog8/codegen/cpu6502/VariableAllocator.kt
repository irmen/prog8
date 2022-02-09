package prog8.codegen.cpu6502

import com.github.michaelbull.result.onFailure
import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.IntegerDatatypes
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.ast.statements.ZeropageWish
import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.IErrorReporter
import prog8.compilerinterface.IVariablesAndConsts
import prog8.compilerinterface.ZeropageType


internal class VariableAllocator(private val vars: IVariablesAndConsts, private val errors: IErrorReporter) {

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
    fun allocateZeropageVariables(options: CompilationOptions) {
        if(options.zeropage== ZeropageType.DONTUSE)
            return

        val zeropage = options.compTarget.machine.zeropage
        val allVariables = (
                        vars.blockVars.asSequence().flatMap { it.value }.map {it.origVar to it.origVar.scopedName} +
                        vars.subroutineVars.asSequence().flatMap { it.value }.map {it.origVar to it.origVar.scopedName})
            .toList()

        val varsRequiringZp = allVariables.filter { it.first.zeropage == ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp = allVariables.filter { it.first.zeropage == ZeropageWish.PREFER_ZEROPAGE }
        val varsDontCare = allVariables.filter { it.first.zeropage == ZeropageWish.DONTCARE }

        varsRequiringZp.forEach { (vardecl, scopedname) ->
            val numElements = numArrayElements(vardecl)
            val result = zeropage.allocate(scopedname, vardecl.datatype, vardecl.definingScope, numElements, vardecl.value, vardecl.position, errors)
            result.onFailure { errors.err(it.message!!, vardecl.position) }
        }

        if(errors.noErrors()) {
            varsPreferringZp.forEach { (vardecl, scopedname) ->
                val numElements = numArrayElements(vardecl)
                zeropage.allocate(scopedname, vardecl.datatype, vardecl.definingScope, numElements, vardecl.value, vardecl.position, errors)
                //  no need to check for allocation error, if there is one, just allocate in normal system ram.
            }

            // try to allocate any other interger variables into the zeropage until it is full.
            // TODO some form of intelligent priorization? most often used variables first? loopcounter vars first? ...?
            if(errors.noErrors()) {
                for ((vardecl, scopedname) in varsDontCare) {
                    if(vardecl.datatype in IntegerDatatypes) {
                        val numElements = numArrayElements(vardecl)
                        zeropage.allocate(scopedname, vardecl.datatype, vardecl.definingScope, numElements, vardecl.value, vardecl.position, errors)
                        if(zeropage.free.isEmpty())
                            break
                    }
                }
            }
        }
    }

    private fun numArrayElements(vardecl: VarDecl): Int? = when(vardecl.datatype) {
        DataType.STR -> {
            (vardecl.value as StringLiteralValue).value.length
        }
        in ArrayDatatypes -> {
            vardecl.arraysize!!.constIndex()
        }
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
