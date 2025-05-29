package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.expressions.ArrayIndexedExpression
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.PrefixExpression
import prog8.ast.statements.*
import prog8.code.core.*
import prog8.code.source.SourceCode
import prog8.code.target.C128Target
import prog8.code.target.C64Target
import prog8.code.target.PETTarget
import prog8.code.target.VMTarget
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder
import prog8tests.helpers.compileText


class TestMemory: FunSpec({

    val c64target = C64Target()
    val outputDir = tempdir().toPath()

    fun wrapWithProgram(statements: List<Statement>): Program {
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, statements.toMutableList(), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        program.addModule(module)
        return program
    }

    test("assignment target not in mapped IO space C64") {

        var memexpr = NumericLiteral.optimalInteger(0x0002, Position.DUMMY)
        var target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        var assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0x1000, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0x9fff, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0xa000, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0xc000, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0xcfff, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0xeeee, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = NumericLiteral.optimalInteger(0xffff, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false
    }

    test("assign target in mapped IO space C64") {

        var memexpr = NumericLiteral.optimalInteger(0x0000, Position.DUMMY)
        var target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        var assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe true

        memexpr = NumericLiteral.optimalInteger(0x0001, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe true

        memexpr = NumericLiteral.optimalInteger(0xd000, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe true

        memexpr = NumericLiteral.optimalInteger(0xdfff, Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe true
    }

    fun createTestProgramForMemoryRefViaVar(address: UInt, vartype: VarDeclType): AssignTarget {
        val decl = VarDecl(vartype, VarDeclOrigin.USERCODE, DataType.BYTE, ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), NumericLiteral.optimalInteger(address, Position.DUMMY), false, 0u, false, Position.DUMMY)
        val memexpr = IdentifierReference(listOf("address"), Position.DUMMY)
        val target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(decl, assignment))
        return target
    }

    test("identifier mapped to IO memory on C64") {
        var target = createTestProgramForMemoryRefViaVar(0x1000u, VarDeclType.VAR)
        target.isIOAddress(c64target) shouldBe false
        target = createTestProgramForMemoryRefViaVar(0xd020u, VarDeclType.VAR)
        target.isIOAddress(c64target) shouldBe false
        target = createTestProgramForMemoryRefViaVar(0x1000u, VarDeclType.CONST)
        target.isIOAddress(c64target) shouldBe false
        target = createTestProgramForMemoryRefViaVar(0xd020u, VarDeclType.CONST)
        target.isIOAddress(c64target) shouldBe true
        target = createTestProgramForMemoryRefViaVar(0x1000u, VarDeclType.MEMORY)
        target.isIOAddress(c64target) shouldBe false
        target = createTestProgramForMemoryRefViaVar(0xd020u, VarDeclType.MEMORY)
        target.isIOAddress(c64target) shouldBe true
    }

    test("memory expression mapped to IO memory on C64") {
        var memexpr = PrefixExpression("+", NumericLiteral.optimalInteger(0x1000, Position.DUMMY), Position.DUMMY)
        var target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        var assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe false

        memexpr = PrefixExpression("+", NumericLiteral.optimalInteger(0xd020, Position.DUMMY), Position.DUMMY)
        target = AssignTarget(
            null,
            null,
            DirectMemoryWrite(memexpr, Position.DUMMY),
            null,
            false,
            position = Position.DUMMY
        )
        assign = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        wrapWithProgram(listOf(assign))
        target.isIOAddress(c64target) shouldBe true
    }

    test("regular variable not in mapped IO ram on C64") {
        val decl = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.BYTE, ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), null, false, 0u, false, Position.DUMMY)
        val target = AssignTarget(
            IdentifierReference(listOf("address"), Position.DUMMY),
            null,
            null,
            null,
            false,
            position = Position.DUMMY
        )
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)
        target.isIOAddress(c64target) shouldBe false
    }

    test("memory mapped variable not in mapped IO ram on C64") {
        val address = 0x1000u
        val decl = VarDecl(VarDeclType.MEMORY, VarDeclOrigin.USERCODE, DataType.UBYTE, ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), NumericLiteral.optimalInteger(address, Position.DUMMY), false, 0u, false, Position.DUMMY)
        val target = AssignTarget(
            IdentifierReference(listOf("address"), Position.DUMMY),
            null,
            null,
            null,
            false,
            position = Position.DUMMY
        )
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)
        target.isIOAddress(c64target) shouldBe false
    }

    test("memory mapped variable in mapped IO ram on C64") {
        val address = 0xd020u
        val decl = VarDecl(VarDeclType.MEMORY, VarDeclOrigin.USERCODE, DataType.UBYTE, ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), NumericLiteral.optimalInteger(address, Position.DUMMY), false, 0u, false, Position.DUMMY)
        val target = AssignTarget(
            IdentifierReference(listOf("address"), Position.DUMMY),
            null,
            null,
            null,
            false,
            position = Position.DUMMY
        )
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)
        target.isIOAddress(c64target) shouldBe true
    }

    test("array not in mapped IO ram") {
        val decl = VarDecl(VarDeclType.VAR, VarDeclOrigin.USERCODE, DataType.arrayFor(BaseDataType.UBYTE), ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), null, false, 0u, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), null, ArrayIndex(NumericLiteral.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, null, false, position = Position.DUMMY)
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)
        target.isIOAddress(c64target) shouldBe false
    }

    test("memory mapped array not in mapped IO ram") {
        val address = 0x1000u
        val decl = VarDecl(VarDeclType.MEMORY, VarDeclOrigin.USERCODE, DataType.arrayFor(BaseDataType.UBYTE), ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), NumericLiteral.optimalInteger(address, Position.DUMMY), false, 0u, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), null, ArrayIndex(NumericLiteral.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, null, false, position = Position.DUMMY)
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)
        target.isIOAddress(c64target) shouldBe false
    }

    test("memory mapped array in mapped IO ram") {
        val address = 0xd800u
        val decl = VarDecl(VarDeclType.MEMORY, VarDeclOrigin.USERCODE, DataType.arrayFor(BaseDataType.UBYTE), ZeropageWish.DONTCARE,
            SplitWish.DONTCARE, null, "address", emptyList(), NumericLiteral.optimalInteger(address, Position.DUMMY), false, 0u, false, Position.DUMMY)
        val arrayindexed = ArrayIndexedExpression(IdentifierReference(listOf("address"), Position.DUMMY), null, ArrayIndex(NumericLiteral.optimalInteger(1, Position.DUMMY), Position.DUMMY), Position.DUMMY)
        val target = AssignTarget(null, arrayindexed, null, null, false, position = Position.DUMMY)
        val assignment = Assignment(target, NumericLiteral.optimalInteger(0, Position.DUMMY), AssignmentOrigin.USERCODE, Position.DUMMY)
        val subroutine = Subroutine("test", mutableListOf(), mutableListOf(), emptyList(), emptyList(), emptySet(), null, false, false, false, mutableListOf(decl, assignment), Position.DUMMY)
        val module = Module(mutableListOf(subroutine), Position.DUMMY, SourceCode.Generated("test"))
        Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
            .addModule(module)
        target.isIOAddress(c64target) shouldBe true
    }


    test("memory() with spaces in name works") {
        compileText(
            C64Target(), false, """
            main {
                sub start() {
                    uword @shared mem = memory("a b c", 100, $100)
                }
            }
        """, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("memory() with invalid argument") {
        compileText(
            C64Target(), false, """
            main {
                sub start() {
                    uword @shared mem1 = memory("abc", 100, -2)
                }
            }
        """, outputDir, writeAssembly = true) shouldBe null
    }

    context("memsizer") {
        withData(VMTarget(), C64Target(), PETTarget(), C128Target()) { target ->
            shouldThrow<IllegalArgumentException> {
                target.memorySize(BaseDataType.UNDEFINED)
            }
            shouldThrow<NoSuchElementException> {
                target.memorySize(DataType.arrayFor(BaseDataType.LONG), 10)
            }
            target.memorySize(BaseDataType.BOOL) shouldBe 1
            target.memorySize(BaseDataType.BYTE) shouldBe 1
            target.memorySize(BaseDataType.WORD) shouldBe 2
            target.memorySize(BaseDataType.LONG) shouldBe 4
            target.memorySize(BaseDataType.FLOAT) shouldBe target.FLOAT_MEM_SIZE

            target.memorySize(DataType.BOOL, null) shouldBe 1
            target.memorySize(DataType.WORD, null) shouldBe 2
            target.memorySize(DataType.FLOAT, null) shouldBe target.FLOAT_MEM_SIZE

            target.memorySize(DataType.STR, null) shouldBe 2
            target.memorySize(DataType.STR, 50) shouldBe 50
            target.memorySize(BaseDataType.STR) shouldBe 2
            target.memorySize(BaseDataType.ARRAY) shouldBe 2
            target.memorySize(BaseDataType.ARRAY_SPLITW) shouldBe 2

            target.memorySize(DataType.arrayFor(BaseDataType.BOOL), 10) shouldBe 10
            target.memorySize(DataType.arrayFor(BaseDataType.BYTE), 10) shouldBe 10
            target.memorySize(DataType.arrayFor(BaseDataType.WORD), 10) shouldBe 20
            target.memorySize(DataType.arrayFor(BaseDataType.UWORD), 10) shouldBe 20
            target.memorySize(DataType.arrayFor(BaseDataType.FLOAT), 10) shouldBe 10*target.FLOAT_MEM_SIZE
            target.memorySize(DataType.arrayFor(BaseDataType.WORD, true), 10) shouldBe 20
            target.memorySize(DataType.arrayFor(BaseDataType.UWORD, true), 10) shouldBe 20

            target.memorySize(DataType.BOOL, 10) shouldBe 10
            target.memorySize(DataType.UWORD, 10) shouldBe 20
            target.memorySize(DataType.LONG, 10) shouldBe 40
            target.memorySize(DataType.FLOAT, 10) shouldBe 10*target.FLOAT_MEM_SIZE
        }
    }
})
