package prog8.astvm

import prog8.ast.*
import prog8.compiler.HeapValues

class VariablesInitializer(private val runtimeVariables: RuntimeVariables, private val heap: HeapValues) : IAstProcessor {

    override fun process(decl: VarDecl): IStatement {
        if(decl.type==VarDeclType.VAR) {
            val value = when (decl.datatype) {
                in NumericDatatypes -> {
                    if(decl.value !is LiteralValue) {
                        TODO("evaluate vardecl expression $decl")
                        //RuntimeValue(decl.datatype, num = evaluate(decl.value!!, program, runtimeVariables, executeSubroutine).numericValue())
                    } else {
                        RuntimeValue.from(decl.value as LiteralValue, heap)
                    }
                }
                in StringDatatypes -> {
                    RuntimeValue.from(decl.value as LiteralValue, heap)
                }
                in ArrayDatatypes -> {
                    RuntimeValue.from(decl.value as LiteralValue, heap)
                }
                else -> throw VmExecutionException("weird type ${decl.datatype}")
            }
            runtimeVariables.define(decl.definingScope(), decl.name, value)
        }
        return super.process(decl)
    }

//    override fun process(assignment: Assignment): IStatement {
//        if(assignment is VariableInitializationAssignment) {
//            println("INIT VAR $assignment")
//        }
//        return super.process(assignment)
//    }

}
