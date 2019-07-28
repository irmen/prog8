package prog8.compiler.target.c64.codegen2

import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.base.WordDatatypes
import prog8.ast.expressions.Expression
import prog8.ast.expressions.FunctionCall
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteralValue
import prog8.ast.statements.FunctionCallStatement
import prog8.compiler.CompilationOptions
import prog8.compiler.Zeropage
import prog8.compiler.target.c64.MachineDefinition.ESTACK_HI_PLUS1_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_HEX
import prog8.compiler.target.c64.MachineDefinition.ESTACK_LO_PLUS1_HEX
import prog8.functions.FunctionSignature

internal class BuiltinFunctionsAsmGen(private val program: Program,
                                      private val options: CompilationOptions,
                                      private val zeropage: Zeropage,
                                      private val asmgen: AsmGen2) {

    internal fun translateFunctioncallExpression(fcall: FunctionCall, func: FunctionSignature) {
        translateFunctioncall(fcall, func, false)
    }

    internal fun translateFunctioncallStatement(fcall: FunctionCallStatement, func: FunctionSignature) {
        translateFunctioncall(fcall, func, true)
    }

    private fun translateFunctioncall(fcall: IFunctionCall, func: FunctionSignature, discardResult: Boolean) {
        val functionName = fcall.target.nameInSource.last()
        if(discardResult) {
            if(func.pure)
                return  // can just ignore the whole function call altogether
            else if(func.returntype!=null)
                throw AssemblyError("discarding result of non-pure function $fcall")
        }

        when(functionName) {
            "msb" -> {
                val arg = fcall.arglist.single()
                if(arg.inferType(program) !in WordDatatypes)
                    throw AssemblyError("msb required word argument")
                if(arg is NumericLiteralValue)
                    throw AssemblyError("should have been const-folded")
                if(arg is IdentifierReference) {
                    val sourceName = arg.nameInSource.joinToString(".")
                    asmgen.out("  lda  $sourceName+1 |  sta  $ESTACK_LO_HEX,x |  dex")
                } else {
                    asmgen.translateExpression(arg)
                    asmgen.out("  lda  $ESTACK_HI_PLUS1_HEX,x |  sta  $ESTACK_LO_PLUS1_HEX,x")
                }
            }
            "mkword" -> {
                translateFunctionArguments(fcall.arglist)
                asmgen.out("  inx | lda  $ESTACK_LO_HEX,x  | sta  $ESTACK_HI_PLUS1_HEX,x")
            }
            "memset" -> {
                translateFunctionArguments(fcall.arglist)
                asmgen.out("  jsr  prog8lib.func_memset")
            }
            "memsetw" -> {
                translateFunctionArguments(fcall.arglist)
                asmgen.out("  jsr  prog8lib.func_memsetw")
            }
            else -> TODO("builtin function $functionName")
        }
    }

    private fun translateFunctionArguments(args: MutableList<Expression>) {
        args.forEach { asmgen.translateExpression(it) }
    }

}

