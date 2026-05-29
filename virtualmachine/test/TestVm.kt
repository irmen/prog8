import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.intermediate.*
import prog8.vm.VirtualMachine
import prog8.vm.VmRunner

class TestVm: FunSpec( {

    fun getTestOptions(): CompilationOptions {
        val target = VMTarget()
        return CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .floats(true)
            .compilerVersion("99.99")
            .loadAddress(target.PROGRAM_LOAD_ADDRESS)
            .memtopAddress(0xffffu)
            .build()
    }

    test("vm execution: empty program") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val vm = VirtualMachine(program)
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe 0
        vm.stepCount shouldBe 0
        vm.run(false)
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe 0
        vm.stepCount shouldBe 0
    }

    test("vm execution: modify memory") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("testmain", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("testmain.testsub", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)
        code += IRInstruction(Opcode.NOP)
        code += IRInstruction(Opcode.LOAD, IRDataType.WORD, reg1=1, immediate=12345)
        code += IRInstruction(Opcode.STOREM, IRDataType.WORD, reg1=1, address=1000u.toAddress())
        code += IRInstruction(Opcode.RETURN)
        startSub += code
        block += startSub
        program.addBlock(block)
        val vm = VirtualMachine(program)
        vm.memory.setUW(1000u, 0u)

        vm.memory.getUW(1000u) shouldBe 0u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe 0
        vm.stepCount shouldBe 0
        vm.run(false)
        vm.stepCount shouldBe 4
        vm.memory.getUW(1000u) shouldBe 12345u
        vm.callStack.shouldBeEmpty()
        vm.valueStack.shouldBeEmpty()
        vm.pcIndex shouldBe code.instructions.size-1
        vm.stepCount shouldBe code.instructions.size
    }

    test("asmsub not supported in vm even with IR") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRAsmSubroutine(
            "main.asmstart",
            0x2000u,
            emptySet(),
            emptyList(),
            emptyList(),
            IRInlineAsmChunk("main.asmstart", "return", false, null),
            Position.DUMMY
        )
        block += startSub
        program.addBlock(block)
        shouldThrowWithMessage<IRParseException>("vm does not support asmsubs (use normal sub): main.asmstart") {
            VirtualMachine(program)
        }
    }

    test("vmrunner") {
        val runner = VmRunner()
        val irSource="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test" COMPILERVERSION="99.99">
<OPTIONS>
</OPTIONS>

<ASMSYMBOLS>
</ASMSYMBOLS>

<VARS>

<NOINITCLEAN>
</NOINITCLEAN>
<NOINITDIRTY>
</NOINITDIRTY>
<INIT>
</INIT>

<STRUCTINSTANCESNOINIT>
</STRUCTINSTANCESNOINIT>
<STRUCTINSTANCES>
</STRUCTINSTANCES>

<CONSTANTS>
</CONSTANTS>

<MEMORYMAPPED>
</MEMORYMAPPED>

<MEMORYSLABS>
</MEMORYSLABS>

</VARS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" FORCEOUTPUT="false" ALIGN="NONE" POS="[unittest: line 42 col 1-9]">
</BLOCK>
</PROGRAM>
"""
        runner.runProgram(irSource, false)
    }

    test("vm machine float bits") {
        val cx16machine = Cx16Target()
        cx16machine.getFloatAsmBytes(Math.PI) shouldBe "\$82, \$49, \$0f, \$da, \$a2"
        val c64machine = C64Target()
        c64machine.getFloatAsmBytes(Math.PI) shouldBe "\$82, \$49, \$0f, \$da, \$a2"

        val vm = VMTarget()
        vm.getFloatAsmBytes(Math.PI) shouldBe "\$40, \$09, \$21, \$fb, \$54, \$44, \$2d, \$18"
    }

    test("vm signed long division") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        code += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=1, reg2=3)
        code += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=2, reg2=4)
        code += IRInstruction(Opcode.DIVSR, IRDataType.LONG, reg1=1, reg2=2)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setSL(3, 100)
        vm.registers.setSL(4, 7)
        vm.run(false)
        vm.registers.getSL(1) shouldBe 14
    }

    test("vm signed long modulo") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        code += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=1, reg2=2)
        code += IRInstruction(Opcode.MODS, IRDataType.LONG, reg1=1, immediate=7)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setSL(2, 100)
        vm.run(false)
        vm.registers.getSL(1) shouldBe 2
    }

    test("vm signed long division constant divisor") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        code += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=1, reg2=2)
        code += IRInstruction(Opcode.DIVS, IRDataType.LONG, reg1=1, immediate=8)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setSL(2, 50)
        vm.run(false)
        vm.registers.getSL(1) shouldBe 6
    }

    test("vm signed long division by zero") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        code += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=1, reg2=3)
        code += IRInstruction(Opcode.LOADR, IRDataType.LONG, reg1=2, reg2=4)
        code += IRInstruction(Opcode.DIVSR, IRDataType.LONG, reg1=1, reg2=2)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setSL(3, 100)
        vm.registers.setSL(4, 0)
        vm.run(false)
        vm.registers.getSL(1) shouldBe Int.MAX_VALUE
    }

    test("vm register usage: LOADI") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        // BWL: memory[registers[reg2] + offset] -> registers[reg1]
        code += IRInstruction(Opcode.LOADI, IRDataType.WORD, reg1=1, reg2=2, immediate=10)
        // FLOAT: memory[registers[reg1] + offset] -> registers[fpReg1]
        code += IRInstruction(Opcode.LOADI, IRDataType.FLOAT, fpReg1=RegisterNum(3), reg1=2, immediate=20)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setUW(2, 1000u)
        vm.memory.setUW(1010u, 1234u)
        vm.memory.setFloat(1020u, 3.14)
        vm.run(false)

        vm.registers.getUW(1) shouldBe 1234u
        vm.registers.getFloat(RegisterNum(3)) shouldBe 3.14
    }

    test("vm register usage: LOADX") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        // BWL: memory[address + registers[reg2]] -> registers[reg1]
        code += IRInstruction(Opcode.LOADX, IRDataType.WORD, reg1=1, reg2=2, address=1000u.toAddress())
        // FLOAT: memory[address + registers[reg1]] -> registers[fpReg1]
        code += IRInstruction(Opcode.LOADX, IRDataType.FLOAT, fpReg1=RegisterNum(3), reg1=2, address=2000u.toAddress())
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setUB(2, 10u)
        vm.memory.setUW(1010u, 5678u)
        vm.memory.setFloat(2010u, 2.718)
        vm.run(false)

        vm.registers.getUW(1) shouldBe 5678u
        vm.registers.getFloat(RegisterNum(3)) shouldBe 2.718
    }

    test("vm register usage: STOREI") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        // BWL: registers[reg1] -> memory[registers[reg2] + offset]
        code += IRInstruction(Opcode.STOREI, IRDataType.WORD, reg1=1, reg2=2, immediate=10)
        // FLOAT: registers[fpReg1] -> memory[registers[reg1] + offset]
        code += IRInstruction(Opcode.STOREI, IRDataType.FLOAT, fpReg1=RegisterNum(3), reg1=2, immediate=20)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setUW(1, 9999u)
        vm.registers.setFloat(RegisterNum(3), 1.23)
        vm.registers.setUW(2, 1000u)
        vm.run(false)

        vm.memory.getUW(1010u) shouldBe 9999u
        vm.memory.getFloat(1020u) shouldBe 1.23
    }

    test("vm register usage: STOREX") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        // BWL: registers[reg1] -> memory[address + registers[reg2]]
        code += IRInstruction(Opcode.STOREX, IRDataType.WORD, reg1=1, reg2=2, address=1000u.toAddress())
        // FLOAT: registers[fpReg1] -> memory[address + registers[reg1]]
        code += IRInstruction(Opcode.STOREX, IRDataType.FLOAT, fpReg1=RegisterNum(3), reg1=2, address=2000u.toAddress())
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setUW(1, 8888u)
        vm.registers.setFloat(RegisterNum(3), 4.56)
        vm.registers.setUB(2, 10u)
        vm.run(false)

        vm.memory.getUW(1010u) shouldBe 8888u
        vm.memory.getFloat(2010u) shouldBe 4.56
    }

    test("vm register usage: LSR.l") {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val startSub = IRSubroutine("main.test", emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(startSub.label, null)

        code += IRInstruction(Opcode.LSR, IRDataType.LONG, reg1=1)
        code += IRInstruction(Opcode.RETURN)

        startSub += code
        block += startSub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.registers.setSL(1, -1) // 0xFFFFFFFF
        vm.run(false)
        // Logical shift right of 0xFFFFFFFF should be 0x7FFFFFFF (2147483647)
        vm.registers.getSL(1) shouldBe 2147483647
    }
})
