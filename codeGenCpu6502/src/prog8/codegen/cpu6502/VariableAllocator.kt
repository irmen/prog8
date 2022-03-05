package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onSuccess
import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.IntegerDatatypes
import prog8.ast.expressions.StringLiteral
import prog8.ast.statements.Subroutine
import prog8.ast.statements.ZeropageWish
import prog8.compilerinterface.*


internal class VariableAllocator(private val vars: IVariablesAndConsts,
                                 private val symboltable: SymbolTable,
                                 private val options: CompilationOptions,
                                 private val errors: IErrorReporter) {

    private val zeropage = options.compTarget.machine.zeropage
    private val subroutineExtras = mutableMapOf<Subroutine, SubroutineExtraAsmInfo>()
    private val memorySlabsInternal = mutableMapOf<String, Pair<UInt, UInt>>()
    internal val memorySlabs: Map<String, Pair<UInt, UInt>> = memorySlabsInternal
    internal val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)
    internal val zeropageVars: Map<List<String>, Zeropage.ZpAllocation> = zeropage.allocatedVariables

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

        val allVariablesOld = (
                        vars.blockVars.asSequence().flatMap { it.value } +
                        vars.subroutineVars.asSequence().flatMap { it.value }
                ).toList()

        val allVariables = collectAllVariables(symboltable)
        require(allVariables.size == allVariablesOld.size)
        require(allVariables.map{it.scopedName}.toSet()==allVariablesOld.map{it.scopedname}.toSet())

        val numberOfAllocatableVariables = allVariablesOld.size
        val varsRequiringZp = allVariablesOld.filter { it.zp == ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp = allVariablesOld.filter { it.zp == ZeropageWish.PREFER_ZEROPAGE }
        val varsDontCare = allVariablesOld.filter { it.zp == ZeropageWish.DONTCARE }
        val numberOfExplicitNonZpVariables = allVariablesOld.count { it.zp == ZeropageWish.NOT_IN_ZEROPAGE }
        require(varsDontCare.size + varsRequiringZp.size + varsPreferringZp.size + numberOfExplicitNonZpVariables == numberOfAllocatableVariables)

        val numberOfAllocatableVariables2 = allVariables.size
        val varsRequiringZp2 = allVariables.filter { it.zpw == ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp2 = allVariables.filter { it.zpw == ZeropageWish.PREFER_ZEROPAGE }
        val varsDontCare2 = allVariables.filter { it.zpw == ZeropageWish.DONTCARE }
        val numberOfExplicitNonZpVariables2 = allVariables.count { it.zpw == ZeropageWish.NOT_IN_ZEROPAGE }
        require(varsDontCare2.size + varsRequiringZp2.size + varsPreferringZp2.size + numberOfExplicitNonZpVariables2 == numberOfAllocatableVariables2)
        require(varsDontCare2.size==varsDontCare.size)
        require(varsRequiringZp2.size==varsRequiringZp.size)
        require(varsPreferringZp2.size==varsPreferringZp.size)
        require(numberOfExplicitNonZpVariables2==numberOfExplicitNonZpVariables)
        require(numberOfAllocatableVariables2==numberOfAllocatableVariables)

        val oldvarsByName = varsDontCare.associateBy { it.scopedname }
        val newvarsByName = varsDontCare2.associateBy { it.scopedName }
        require(oldvarsByName.keys==newvarsByName.keys)
        oldvarsByName.forEach { (name, oldvar) ->
            val newvar = newvarsByName.getValue(name)
            require(oldvar.scopedname==newvar.scopedName)
            require(oldvar.type==newvar.dt)
            require(oldvar.zp==newvar.zpw)
            require(oldvar.initialValue==newvar.initialvalue)
            require(oldvar.arraysize==newvar.arraysize)
            require(oldvar.position==newvar.position)
            require(numArrayElements(oldvar) == numArrayElements(newvar))
        }


        var numVariablesAllocatedInZP = 0
        var numberOfNonIntegerVariables = 0

        varsRequiringZp2.forEach { variable ->
            val numElements = numArrayElements(variable)
            val result = zeropage.allocate(
                variable.scopedName,
                variable.dt,
                numElements,
                variable.initialvalue,
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
            varsPreferringZp2.forEach { variable ->
                val numElements = numArrayElements(variable)
                val result = zeropage.allocate(
                    variable.scopedName,
                    variable.dt,
                    numElements,
                    variable.initialvalue,
                    variable.position,
                    errors
                )
                result.onSuccess { numVariablesAllocatedInZP++ }
                //  no need to check for allocation error, if there is one, just allocate in normal system ram.
            }

            // try to allocate any other interger variables into the zeropage until it is full.
            // TODO some form of intelligent priorization? most often used variables first? loopcounter vars first? ...?
            if(errors.noErrors()) {
                for (variable in varsDontCare2.sortedWith(compareBy({it.scopedName.size}, {it.name}))) {
                    if(variable.dt in IntegerDatatypes) {
                        if(zeropage.free.isEmpty()) {
                            break
                        } else {
                            val numElements = numArrayElements(variable)
                            val result = zeropage.allocate(
                                variable.scopedName,
                                variable.dt,
                                numElements,
                                variable.initialvalue,
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

        println("  number of allocated vars: $numberOfAllocatableVariables")
        println("  put into zeropage: $numVariablesAllocatedInZP,  non-zp allocatable: ${numberOfNonIntegerVariables+numberOfExplicitNonZpVariables}")
        println("  zeropage free space: ${zeropage.free.size} bytes")
    }

    private fun collectAllVariables(st: SymbolTable): Collection<StStaticVariable> {
        val vars = mutableListOf<StStaticVariable>()
        fun collect(node: StNode) {
            for(child in node.children) {
                if(child.value.type==StNodeType.STATICVAR)
                    vars.add(child.value as StStaticVariable)
                else
                    collect(child.value)
            }
        }
        collect(st)
        return vars
    }

    internal fun isZpVar(scopedName: List<String>) = scopedName in zeropage.allocatedVariables

    private fun numArrayElements(variable: IVariablesAndConsts.StaticVariable) =
        when(variable.type) {
            DataType.STR -> (variable.initialValue as StringLiteral).value.length
            in ArrayDatatypes -> variable.arraysize!!
            else -> null
        }

    private fun numArrayElements(variable: StStaticVariable) =
        when(variable.dt) {
            DataType.STR -> (variable.initialvalue as StringLiteral).value.length
            in ArrayDatatypes -> variable.arraysize!!
            else -> null
        }

    internal fun subroutineExtra(sub: Subroutine): SubroutineExtraAsmInfo {
        var extra = subroutineExtras[sub]
        return if(extra==null) {
            extra = SubroutineExtraAsmInfo()
            subroutineExtras[sub] = extra
            extra
        }
        else
            extra
    }

    internal fun getFloatAsmConst(number: Double): String {
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
