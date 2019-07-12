package prog8.vm.astvm

import prog8.ast.*
import prog8.ast.base.*
import prog8.ast.expressions.LiteralValue
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.StructDecl
import prog8.ast.statements.VarDecl
import prog8.compiler.HeapValues
import prog8.vm.RuntimeValue

class VariablesCreator(private val runtimeVariables: RuntimeVariables, private val heap: HeapValues) : IAstModifyingVisitor {

    override fun visit(program: Program) {
        // define the three registers as global variables
        runtimeVariables.define(program.namespace, Register.A.name, RuntimeValue(DataType.UBYTE, 0))
        runtimeVariables.define(program.namespace, Register.X.name, RuntimeValue(DataType.UBYTE, 255))
        runtimeVariables.define(program.namespace, Register.Y.name, RuntimeValue(DataType.UBYTE, 0))

        val globalpos = Position("<<global>>", 0, 0, 0)
        val vdA = VarDecl(VarDeclType.VAR, DataType.UBYTE, false, null, Register.A.name, null,
                LiteralValue.optimalInteger(0, globalpos), isArray = false, hiddenButDoNotRemove = true, position = globalpos)
        val vdX = VarDecl(VarDeclType.VAR, DataType.UBYTE, false, null, Register.X.name, null,
                LiteralValue.optimalInteger(255, globalpos), isArray = false, hiddenButDoNotRemove = true, position = globalpos)
        val vdY = VarDecl(VarDeclType.VAR, DataType.UBYTE, false, null, Register.Y.name, null,
                LiteralValue.optimalInteger(0, globalpos), isArray = false, hiddenButDoNotRemove = true, position = globalpos)
        vdA.linkParents(program.namespace)
        vdX.linkParents(program.namespace)
        vdY.linkParents(program.namespace)
        program.namespace.statements.add(vdA)
        program.namespace.statements.add(vdX)
        program.namespace.statements.add(vdY)

        super.visit(program)
    }

    override fun visit(decl: VarDecl): IStatement {
        // if the decl is part of a struct, just skip it
        if(decl.parent !is StructDecl) {
            when (decl.type) {
                // we can assume the value in the vardecl already has been converted into a constant LiteralValue here.
                VarDeclType.VAR -> {
                    println("$decl")
                    val value = RuntimeValue.from(decl.value as LiteralValue, heap)
                    runtimeVariables.define(decl.definingScope(), decl.name, value)
                }
                VarDeclType.MEMORY -> {
                    runtimeVariables.defineMemory(decl.definingScope(), decl.name, (decl.value as LiteralValue).asIntegerValue!!)
                }
                VarDeclType.CONST -> {
                    // consts should have been const-folded away
                }
                VarDeclType.STRUCT -> {
                    // struct vardecl can be skipped because its members have been flattened out
                }
            }
        }
        return super.visit(decl)
    }

//    override fun accept(assignment: Assignment): IStatement {
//        if(assignment is VariableInitializationAssignment) {
//            println("INIT VAR $assignment")
//        }
//        return super.accept(assignment)
//    }

}
