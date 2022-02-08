package prog8.codegen.cpu6502

import com.github.michaelbull.result.onFailure
import prog8.ast.base.ArrayDatatypes
import prog8.ast.base.DataType
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.statements.ZeropageWish
import prog8.compilerinterface.CompilationOptions
import prog8.compilerinterface.IErrorReporter
import prog8.compilerinterface.IVariablesAndConsts
import prog8.compilerinterface.ZeropageType


internal class VariableAllocation(val vars: IVariablesAndConsts, val errors: IErrorReporter) {

    fun allocateAllZeropageVariables(options: CompilationOptions) {
        if(options.zeropage== ZeropageType.DONTUSE)
            return

        val allVariables = (
                vars.blockVars.asSequence().flatMap { it.value }.map {it.origVar to it.origVar.scopedName} +
                vars.subroutineVars.asSequence().flatMap { it.value }.map {it.origVar to it.origVar.scopedName})
            .toList()
                // TODO now some HACKS to get rid of some unused vars in Petaxian - otherwise the executable gets larger than $45e8
            .filterNot { it.second.last() == "tbl" }   // TODO HACK -- NOT REALLY NECESSARY, BUT OLD CallGraph DIDN'T CONTAIN IT EITHER
            .filterNot { it.second.last() in setOf("retval_interm_w",  "retval_interm_b", "retval_interm_w2", "retval_interm_b2") }   // TODO HACK TO REMOVE THESE UNUSED VARS

        val varsRequiringZp = allVariables
            .filter { it.first.zeropage== ZeropageWish.REQUIRE_ZEROPAGE }
        val varsPreferringZp = allVariables
            .filter { it.first.zeropage== ZeropageWish.PREFER_ZEROPAGE }
            .sortedBy { options.compTarget.memorySize(it.first.datatype) }      // allocate the smallest DT first

/*
        // OLD CODE CHECKING:
        if(true) {
            val allVariablesFoundInCallgraph = callGraphForCheck.allIdentifiers.asSequence()
                .map { it.value }
                .filterIsInstance<VarDecl>()
                .filter { it.type == VarDeclType.VAR && it.origin != VarDeclOrigin.SUBROUTINEPARAM }
                .map { it.name to it.position }
                .toSet()
            val newAllVars = (vars.blockVars.flatMap { it.value }
                .map { it.name to it.position } + vars.subroutineVars.flatMap { it.value }
                .map { it.name to it.position }).toSet()
            val extraVarsInCallgraph = allVariablesFoundInCallgraph - newAllVars
            val extraVarsInNew = newAllVars - allVariablesFoundInCallgraph

            if (extraVarsInCallgraph.any() || extraVarsInNew.any()) {
                println("EXTRA VARS IN CALLGRAPH: ${extraVarsInCallgraph.size}")
                extraVarsInCallgraph.forEach {
                    println("  $it")
                }
                println("EXTRA VARS IN VARIABLESOBJ: ${extraVarsInNew.size}")
                extraVarsInNew.forEach {
                    println("  $it")
                }
                //TODO("fix differences")
            }
        }
*/

        val zeropage = options.compTarget.machine.zeropage

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
            val result = zeropage.allocate(scopedname, vardecl.datatype, numElements, vardecl.value, vardecl.position, errors)
            result.onFailure { errors.err(it.message!!, vardecl.position) }
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
                zeropage.allocate(scopedname, vardecl.datatype, arraySize, vardecl.value, vardecl.position, errors)
                //  no need to check for error, if there is one, just allocate in normal system ram later.
            }
        }
    }
}