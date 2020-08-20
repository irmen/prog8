package prog8.compiler.target.c64.codegen

import prog8.ast.Program
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.PrefixExpression
import prog8.ast.expressions.TypecastExpression
import prog8.ast.statements.Assignment
import prog8.compiler.AssemblyError

internal class AugmentableAssignmentAsmGen(private val program: Program,
                                  private val assignmentAsmGen: AssignmentAsmGen,
                                  private val asmgen: AsmGen) {
    fun translate(assign: Assignment) {
        require(assign.isAugmentable)
        assignmentAsmGen.translateOtherAssignment(assign) // TODO get rid of this fallback

//        when (assign.value) {
//            is PrefixExpression -> {
//                TODO("aug prefix")
//            }
//            is TypecastExpression -> {
//                TODO("aug typecast")
//            }
//            is BinaryExpression -> {
//                TODO("aug binary")
//            }
//            else -> {
//                throw AssemblyError("invalid aug assign value type")
//            }
//        }

    }

}
