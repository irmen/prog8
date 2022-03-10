package prog8.compilerinterface

import prog8.ast.expressions.Expression
import prog8.ast.statements.Subroutine
import prog8.code.core.Encoding
import prog8.code.core.IMemSizer
import prog8.code.core.IStringEncoding
import prog8.code.core.RegisterOrStatusflag


interface ICompilationTarget: IStringEncoding, IMemSizer {
    val name: String
    val machine: IMachineDefinition
    val supportedEncodings: Set<Encoding>
    val defaultEncoding: Encoding

    override fun encodeString(str: String, encoding: Encoding): List<UByte>
    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String

    fun asmsubArgsEvalOrder(sub: Subroutine): List<Int>
    fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>,
                                          paramRegisters: List<RegisterOrStatusflag>): Boolean
}
