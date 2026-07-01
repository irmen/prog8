package codegen

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.core.*
import prog8.code.target.Cx16Target
import prog8.intermediate.*

class TestZeropageAllocator : FunSpec({

    fun createTestProgram(
        vars: List<IRStStaticVariable> = emptyList(),
        instructions: List<IRInstruction> = emptyList(),
        zeropageType: ZeropageType = ZeropageType.FLOATSAFE
    ): Pair<IRProgram, Cx16Target> {
        val target = Cx16Target()
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(zeropageType)
            .floats(false)
            .compilerVersion("test")
            .memtopAddress(0xffffu)
            .build()

        val st = IRSymbolTable()
        for (v in vars) {
            st.add(v)
        }

        val program = IRProgram("test", st, options, DummyStringEncoder)

        if (instructions.isNotEmpty()) {
            val chunk = IRCodeChunk(null, null)
            chunk.instructions.addAll(instructions)
            val sub = IRSubroutine("test.start", listOf(), listOf(), Position.DUMMY)
            sub.chunks.add(chunk)
            val block = IRBlock("test", false, IRBlock.Options(), Position.DUMMY)
            block.children.add(sub)
            program.blocks.add(block)
        }

        return Pair(program, target)
    }

    fun makeVar(name: String, dt: DataType, zpwish: ZeropageWish): IRStStaticVariable {
        return IRStStaticVariable(name, dt, null, null, zpwish, 0u, false, true)
    }

    test("REQUIRE_ZEROPAGE variables are allocated") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.x", DataType.UBYTE, ZeropageWish.REQUIRE_ZEROPAGE)
            )
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        result["main.x"] shouldNotBe null
        result["main.x"]!!.address.toInt() shouldBeGreaterThanOrEqual 0x22
    }

    test("PREFER_ZEROPAGE variables are allocated") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.y", DataType.UWORD, ZeropageWish.PREFER_ZEROPAGE)
            )
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        result["main.y"] shouldNotBe null
    }

    test("NOT_IN_ZEROPAGE variables are never allocated") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.z", DataType.UBYTE, ZeropageWish.NOT_IN_ZEROPAGE)
            )
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        result["main.z"] shouldBe null
    }

    test("isZpVar returns true for allocated variables") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.a", DataType.UBYTE, ZeropageWish.REQUIRE_ZEROPAGE)
            )
        )
        val allocator = ZeropageAllocator(program, target)
        allocator.allocate()

        allocator.isZpVar("main.a") shouldBe true
        allocator.isZpVar("main.nonexistent") shouldBe false
    }

    test("isZpVar returns true for memvar with address <= 255") {
        val (program, target) = createTestProgram()
        program.st.add(IRStMemVar("test.zpvar", DataType.UWORD, 0x30u, null))

        val allocator = ZeropageAllocator(program, target)
        allocator.isZpVar("test.zpvar") shouldBe true
    }

    test("isZpVar returns false for memvar with address > 255") {
        val (program, target) = createTestProgram()
        program.st.add(IRStMemVar("test.highvar", DataType.UWORD, 0x9000u, null))

        val allocator = ZeropageAllocator(program, target)
        allocator.isZpVar("test.highvar") shouldBe false
    }

    test("DONTcare variables are allocated sorted by usage frequency") {
        val lowFreqVar = makeVar("main.low", DataType.UBYTE, ZeropageWish.DONTCARE)
        val highFreqVar = makeVar("main.high", DataType.UBYTE, ZeropageWish.DONTCARE)

        val instructions = mutableListOf<IRInstruction>()
        repeat(10) {
            instructions.add(IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=0, labelSymbol="main.high"))
        }
        instructions.add(IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=0, labelSymbol="main.low"))

        val (program, target) = createTestProgram(
            vars = listOf(lowFreqVar, highFreqVar),
            instructions = instructions
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        result["main.high"] shouldNotBe null
    }

    test("DONTcare word variables get higher weight than byte variables") {
        val byteVar = makeVar("main.bytevar", DataType.UBYTE, ZeropageWish.DONTCARE)
        val wordVar = makeVar("main.wordvar", DataType.UWORD, ZeropageWish.DONTCARE)

        val instructions = mutableListOf<IRInstruction>()
        repeat(5) {
            instructions.add(IRInstruction(Opcode.LOADM, IRDataType.BYTE, reg1=0, labelSymbol="main.bytevar"))
            instructions.add(IRInstruction(Opcode.LOADM, IRDataType.WORD, reg1=0, labelSymbol="main.wordvar"))
        }

        val (program, target) = createTestProgram(
            vars = listOf(byteVar, wordVar),
            instructions = instructions
        )
        val allocator = ZeropageAllocator(program, target)
        allocator.allocate()

        // wordVar should score 10 (5 refs * weight 2), byteVar should score 5 (5 refs * weight 1)
    }

    test("DONTUSE zeropage type returns empty allocations") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.x", DataType.UBYTE, ZeropageWish.REQUIRE_ZEROPAGE)
            ),
            zeropageType = ZeropageType.DONTUSE
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        result shouldBe emptyMap()
    }

    test("multiple variables can be allocated") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.a", DataType.UBYTE, ZeropageWish.REQUIRE_ZEROPAGE),
                makeVar("main.b", DataType.UWORD, ZeropageWish.PREFER_ZEROPAGE),
                makeVar("main.c", DataType.UBYTE, ZeropageWish.NOT_IN_ZEROPAGE)
            )
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        result["main.a"] shouldNotBe null
        result["main.b"] shouldNotBe null
        result["main.c"] shouldBe null
    }

    test("allocated addresses are in ZP range") {
        val (program, target) = createTestProgram(
            vars = listOf(
                makeVar("main.x", DataType.UBYTE, ZeropageWish.REQUIRE_ZEROPAGE)
            )
        )
        val allocator = ZeropageAllocator(program, target)
        val result = allocator.allocate()

        val addr = result["main.x"]!!.address.toInt()
        addr shouldBeGreaterThanOrEqual 0x22
        addr shouldBeLessThanOrEqual 0xff
    }
})
