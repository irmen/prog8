package prog8tests.helpers

import prog8.ast.IBuiltinFunctions
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Block
import prog8.ast.statements.RegisterOrStatusflag
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.compilerinterface.*

internal object DummyFunctions : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    override fun constValue(
        name: String,
        args: List<Expression>,
        position: Position,
    ): NumericLiteral? = null

    override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
}

internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType) = 0
    override fun memorySize(decl: VarDecl) = 0
}

internal object DummyStringEncoder : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return ""
    }
}

internal object AsciiStringEncoder : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> = str.map { it.code.toUByte() }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        return bytes.joinToString()
    }
}

internal object DummyCompilationTarget : ICompilationTarget {
    override val name: String = "dummy"
    override val machine: IMachineDefinition
        get() = throw NotImplementedError("dummy")
    override val supportedEncodings = setOf(Encoding.PETSCII, Encoding.SCREENCODES, Encoding.ISO)
    override val defaultEncoding = Encoding.PETSCII
    override val defaultLauncherType = LauncherType.NONE

    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        throw NotImplementedError("dummy")
    }

    override fun decodeString(bytes: List<UByte>, encoding: Encoding): String {
        throw NotImplementedError("dummy")
    }

    override fun asmsubArgsEvalOrder(sub: Subroutine): List<Int> {
        throw NotImplementedError("dummy")
    }

    override fun asmsubArgsHaveRegisterClobberRisk(args: List<Expression>,
                                                   paramRegisters: List<RegisterOrStatusflag>): Boolean {
        throw NotImplementedError("dummy")
    }

    override fun memorySize(dt: DataType): Int {
        throw NotImplementedError("dummy")
    }

    override fun memorySize(decl: VarDecl): Int {
        throw NotImplementedError("dummy")
    }
}

internal object DummyVarsAndConsts : IVariablesAndConsts {
    override val blockVars: Map<Block, Set<IVariablesAndConsts.StaticVariable>>
        get() = throw NotImplementedError("dummy")
    override val blockConsts: Map<Block, Set<IVariablesAndConsts.ConstantNumberSymbol>>
        get() = throw NotImplementedError("dummy")
    override val blockMemvars: Map<Block, Set<IVariablesAndConsts.MemoryMappedVariable>>
        get() = throw NotImplementedError("dummy")
    override val subroutineVars: Map<Subroutine, Set<IVariablesAndConsts.StaticVariable>>
        get() = throw NotImplementedError("dummy")
    override val subroutineConsts: Map<Subroutine, Set<IVariablesAndConsts.ConstantNumberSymbol>>
        get() = throw NotImplementedError("dummy")
    override val subroutineMemvars: Map<Subroutine, Set<IVariablesAndConsts.MemoryMappedVariable>>
        get() = throw NotImplementedError("dummy")

    override fun addIfUnknown(definingBlock: Block, variable: VarDecl) {
        throw NotImplementedError("dummy")
    }

}