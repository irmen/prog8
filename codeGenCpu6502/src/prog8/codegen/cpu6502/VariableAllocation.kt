package prog8.codegen.cpu6502

import com.github.michaelbull.result.fold
import com.github.michaelbull.result.onSuccess
import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.VarDecl
import prog8.ast.statements.VarDeclOrigin
import prog8.ast.statements.ZeropageWish
import prog8.compilerinterface.*


internal class VariableAllocation(val vars: IVariablesAndConsts, val errors: IErrorReporter) {
    val varsInZeropage = mutableSetOf<VarDecl>()

    fun allocateAllZeropageVariables(options: CompilationOptions, callGraph: CallGraph) {
        if(options.zeropage== ZeropageType.DONTUSE)
            return

        val zeropage = options.compTarget.machine.zeropage
//        val allVariables = vars.blockVars.asSequence().flatMap { it.value }.map {it.origVar to it.origVar.scopedName} +
//                           vars.subroutineVars.asSequence().flatMap { it.value }.map {it.origVar to it.origVar.scopedName}
        val allVariables = callGraph.allIdentifiers.asSequence()
            .map { it.value }
            .filterIsInstance<VarDecl>()
            .filter { it.type== VarDeclType.VAR && it.origin!=VarDeclOrigin.SUBROUTINEPARAM }
            .toSet()
            .map { it to it.scopedName }
        val varsRequiringZp = allVariables.filter { it.first.zeropage== ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp = allVariables
            .filter { it.first.zeropage== ZeropageWish.PREFER_ZEROPAGE }
            .sortedBy { options.compTarget.memorySize(it.first.datatype) }      // allocate the smallest DT first


        val allVarsFromCallgraph = allVariables.map { it.first.name to it.first.position }.toSet()
        val altAllVars = (vars.blockVars.flatMap { it.value }.map {it.name to it.position} + vars.subroutineVars.flatMap { it.value }.map {it.name to it.position}).toSet()
        val extraVarsInCallgraph = allVarsFromCallgraph - altAllVars
        val extraVarsInNew = altAllVars - allVarsFromCallgraph

        if(extraVarsInCallgraph.any() || extraVarsInNew.any()) {
            println("EXTRA VARS IN CALLGRAPH: ${extraVarsInCallgraph.size}")
            extraVarsInCallgraph.forEach {
                println("  $it")
            }
            println("EXTRA VARS IN VARIABLESOBJ: ${extraVarsInNew.size}")
            extraVarsInNew.forEach {
                println("  $it")
            }
            // TODO("fix differences")
        }

        val altTotalNumVars = vars.blockVars.flatMap { it.value }.size + vars.subroutineVars.flatMap { it.value }.size
        val altVarsRequiringZpBlocks = vars.blockVars.flatMap { it.value }.filter { it.zp==ZeropageWish.REQUIRE_ZEROPAGE }
        val altVarsRequiringZpSubs = vars.subroutineVars.flatMap { it.value }.filter { it.zp==ZeropageWish.REQUIRE_ZEROPAGE }
        val altTotalZpVars = altVarsRequiringZpBlocks.size + altVarsRequiringZpSubs.size
        val altVarsPreferringZpBlocks = vars.blockVars.flatMap { it.value }.filter { it.zp==ZeropageWish.PREFER_ZEROPAGE }
        val altVarsPreferringZpSubs = vars.subroutineVars.flatMap { it.value }.filter { it.zp==ZeropageWish.PREFER_ZEROPAGE }
        val altTotalPrefZpVars = altVarsPreferringZpBlocks.size + altVarsPreferringZpSubs.size


        for ((vardecl, scopedname) in varsRequiringZp) {
            val numElements: Int? = when(vardecl.datatype) {
                DataType.STR -> {
                    (vardecl.value as StringLiteralValue).value.length
                }
                in ArrayDatatypes -> {
                    vardecl.arraysize!!.constIndex()
                }
                else -> null
            }
            val result = zeropage.allocate(scopedname, vardecl.datatype, numElements, vardecl.position, errors)
            result.fold(
                success = { varsInZeropage.add(vardecl) },
                failure = { errors.err(it.message!!, vardecl.position) }
            )
        }
        if(errors.noErrors()) {
            varsPreferringZp.forEach { (vardecl, scopedname) ->
                val arraySize: Int? = when (vardecl.datatype) {
                    DataType.STR -> {
                        (vardecl.value as StringLiteralValue).value.length
                    }
                    in ArrayDatatypes -> {
                        vardecl.arraysize!!.constIndex()
                    }
                    else -> null
                }
                val result = zeropage.allocate(scopedname, vardecl.datatype, arraySize, vardecl.position, errors)
                result.onSuccess { varsInZeropage.add(vardecl) }
                //  no need to check for error, if there is one, just allocate in normal system ram later.
            }
        }
    }
}