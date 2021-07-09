package prog8tests

import org.junit.jupiter.api.Test
import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.ParentSentinel
import prog8.ast.base.Position
import prog8.ast.base.VarDeclType
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.target.C64Target
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class TestMemory {
    private class DummyFunctions: IBuiltinFunctions {
        override val names: Set<String> = emptySet()
        override val purefunctionNames: Set<String> = emptySet()
        override fun constValue(name: String, args: List<Expression>, position: Position, memsizer: IMemSizer): NumericLiteralValue? = null
        override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
    }

    private class DummyMemsizer: IMemSizer {
        override fun memorySize(dt: DataType): Int = 0
    }

    @Test
    fun testInValidRamC64_memory_addresses() {

        var memexpr = NumericLiteralValue.optimalInteger(0x0000, Position.DUMMY)
        var target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val program = Program("test", mutableListOf(), DummyFunctions(), DummyMemsizer())
        assertTrue(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0x1000, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0x9fff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xc000, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xcfff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertTrue(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testNotInValidRamC64_memory_addresses() {

        var memexpr = NumericLiteralValue.optimalInteger(0xa000, Position.DUMMY)
        var target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val program = Program("test", mutableListOf(), DummyFunctions(), DummyMemsizer())
        assertFalse(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xafff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertFalse(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xd000, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertFalse(C64Target.isInRegularRAM(target, program))

        memexpr = NumericLiteralValue.optimalInteger(0xffff, Position.DUMMY)
        target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        assertFalse(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_memory_identifiers() {
        var target = createTestProgramForMemoryRefViaVar(0x1000, VarDeclType.VAR)
        val program = Program("test", mutableListOf(), DummyFunctions(), DummyMemsizer())

        assertTrue(C64Target.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0xd020, VarDeclType.VAR)
        assertFalse(C64Target.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0x1000, VarDeclType.CONST)
        assertTrue(C64Target.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0xd020, VarDeclType.CONST)
        assertFalse(C64Target.isInRegularRAM(target, program))
        target = createTestProgramForMemoryRefViaVar(0x1000, VarDeclType.MEMORY)
        assertFalse(C64Target.isInRegularRAM(target, program))
    }

    @Test
    private fun createTestProgramForMemoryRefViaVar(address: Int, vartype: VarDeclType): AssignTarget {
        val decl = VarDecl(vartype, DataType.BYTE, ZeropageWish.DONTCARE, null, "address", NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, false, Position.DUMMY)
        val memexpr = IdentifierReference(listOf("address"), Position.DUMMY)
        val target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        module.linkParents(ParentSentinel)
        return target
    }

    @Test
    fun testInValidRamC64_memory_expression() {
        val memexpr = PrefixExpression("+", NumericLiteralValue.optimalInteger(0x1000, Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, null, DirectMemoryWrite(memexpr, Position.DUMMY), Position.DUMMY)
        val program = Program("test", mutableListOf(), DummyFunctions(), DummyMemsizer())
        assertFalse(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_variable() {
        val decl = VarDecl(VarDeclType.VAR, DataType.BYTE, ZeropageWish.DONTCARE, null, "address", null, false, false, false, Position.DUMMY)
        val target = AssignTarget(IdentifierReference(listOf("address"), Position.DUMMY), null, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        module.linkParents(ParentSentinel)
        assertTrue(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_memmap_variable() {
        val address = 0x1000
        val decl = VarDecl(VarDeclType.MEMORY, DataType.UBYTE, ZeropageWish.DONTCARE, null, "address", NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, false, Position.DUMMY)
        val target = AssignTarget(IdentifierReference(listOf("address"), Position.DUMMY), null, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        module.linkParents(ParentSentinel)
        assertTrue(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testNotInValidRamC64_memmap_variable() {
        val address = 0xd020
        val decl = VarDecl(VarDeclType.MEMORY, DataType.UBYTE, ZeropageWish.DONTCARE, null, "address", NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, false, Position.DUMMY)
        val target = AssignTarget(IdentifierReference(listOf("address"), Position.DUMMY), null, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        module.linkParents(ParentSentinel)
        assertFalse(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_array() {
        val decl = VarDecl(VarDeclType.VAR, DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, "address", null, false, false, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), ArrayIndex(NumericLiteralValue.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        module.linkParents(ParentSentinel)
        assertTrue(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testInValidRamC64_array_memmapped() {
        val address = 0x1000
        val decl = VarDecl(VarDeclType.MEMORY, DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, "address", NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), ArrayIndex(NumericLiteralValue.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        module.linkParents(ParentSentinel)
        assertTrue(C64Target.isInRegularRAM(target, program))
    }

    @Test
    fun testNotValidRamC64_array_memmapped() {
        val address = 0xe000
        val decl = VarDecl(VarDeclType.MEMORY, DataType.ARRAY_UB, ZeropageWish.DONTCARE, null, "address", NumericLiteralValue.optimalInteger(address, Position.DUMMY), false, false, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), ArrayIndex(NumericLiteralValue.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, Position.DUMMY)
        val assignment = Assignment(target, NumericLiteralValue.optimalInteger(0, Position.DUMMY), Position.DUMMY)
        val subroutine = Subroutine("test", emptyList(), emptyList(), emptyList(), emptyList(), emptySet(), null, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module("test", mutableListOf(subroutine), Position.DUMMY, null)
        val program = Program("test", mutableListOf(module), DummyFunctions(), DummyMemsizer())
        module.linkParents(ParentSentinel)
        assertFalse(C64Target.isInRegularRAM(target, program))
    }
}
