package prog8.vm.astvm

import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.base.Register
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.ArrayLiteralValue
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.expressions.StringLiteralValue
import prog8.ast.processing.IAstModifyingVisitor
import prog8.ast.statements.Statement
import prog8.ast.statements.StructDecl
import prog8.ast.statements.VarDecl
import prog8.ast.statements.ZeropageWish
import prog8.compiler.HeapValues
import prog8.vm.RuntimeValue

class VariablesCreator(private val runtimeVariables: RuntimeVariables, private val heap: HeapValues) : IAstModifyingVisitor {

    override fun visit(program: Program) {
        // define the three registers as global variables
        runtimeVariables.define(program.namespace, Register.A.name, RuntimeValue(DataType.UBYTE, 0))
        runtimeVariables.define(program.namespace, Register.X.name, RuntimeValue(DataType.UBYTE, 255))
        runtimeVariables.define(program.namespace, Register.Y.name, RuntimeValue(DataType.UBYTE, 0))

        val globalpos = Position("<<global>>", 0, 0, 0)
        val vdA = VarDecl(VarDeclType.VAR, DataType.UBYTE, ZeropageWish.DONTCARE, null, Register.A.name, null,
                NumericLiteralValue.optimalInteger(0, globalpos), isArray = false, autogeneratedDontRemove = true, position = globalpos)
        val vdX = VarDecl(VarDeclType.VAR, DataType.UBYTE, ZeropageWish.DONTCARE, null, Register.X.name, null,
                NumericLiteralValue.optimalInteger(255, globalpos), isArray = false, autogeneratedDontRemove = true, position = globalpos)
        val vdY = VarDecl(VarDeclType.VAR, DataType.UBYTE, ZeropageWish.DONTCARE, null, Register.Y.name, null,
                NumericLiteralValue.optimalInteger(0, globalpos), isArray = false, autogeneratedDontRemove = true, position = globalpos)
        vdA.linkParents(program.namespace)
        vdX.linkParents(program.namespace)
        vdY.linkParents(program.namespace)
        program.namespace.statements.add(vdA)
        program.namespace.statements.add(vdX)
        program.namespace.statements.add(vdY)

        super.visit(program)
    }

    override fun visit(decl: VarDecl): Statement {
        // if the decl is part of a struct, just skip it
        if(decl.parent !is StructDecl) {
            when (decl.type) {
                VarDeclType.VAR -> {
                    if(decl.datatype!=DataType.STRUCT) {
                        val numericLv = decl.value as? NumericLiteralValue
                        val value = if(numericLv!=null) {
                            RuntimeValue.fromLv(numericLv)
                        } else {
                            if(decl.value is StringLiteralValue)
                                RuntimeValue.fromLv(decl.value as StringLiteralValue)
                            else
                                RuntimeValue.fromLv(decl.value as ArrayLiteralValue)
                        }
                        runtimeVariables.define(decl.definingScope(), decl.name, value)
                    }
                }
                VarDeclType.MEMORY -> {
                    runtimeVariables.defineMemory(decl.definingScope(), decl.name, (decl.value as NumericLiteralValue).number.toInt())
                }
                VarDeclType.CONST -> {
                    // consts should have been const-folded away
                }
            }
        }
        return super.visit(decl)
    }
}
