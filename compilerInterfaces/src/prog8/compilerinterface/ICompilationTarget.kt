package prog8.compilerinterface

import prog8.ast.expressions.Expression
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine


interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition
    val supportedEncodings: Set<Encoding>

    override fun encodeString(str: String, encoding: Encoding): List<UByte>
    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String

    fun asmsubArgsEvalOrder(sub: Subroutine): List<Int>
    fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>,
                                          paramRegisters: List<RegisterOrStatusflag>): Boolean
}
