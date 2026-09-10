import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.Statusflag
import prog8.intermediate.*


class TestStructuredCalls: FunSpec({

    test("normal call passes arguments through parameter memory") {
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("main.foo")),
            arguments = listOf(
                Calls.argument(1, IRDataType.BYTE, CallLocation.ParameterMemory("main.foo.arg")),
                Calls.argument(2, IRDataType.WORD)
            ),
            results = listOf(Calls.result(3, IRDataType.WORD))
        )
        val call = IRInstructions.call(Opcode.CALL, site)
        call.requireCallSite().arguments[0].location shouldBe CallLocation.ParameterMemory("main.foo.arg")
        call.requireCallSite().arguments[1].location shouldBe CallLocation.Default
        call.uses shouldBe setOf(VirtualRegister.int(1), VirtualRegister.int(2))
        call.definitions shouldBe setOf(VirtualRegister.int(3))
    }

    test("asmsub call uses hardware register slots") {
        val site = CallSite(
            CallTarget.Direct(CodeReference.Absolute(MemoryAddress(0xffd2u))),
            arguments = listOf(Calls.argument(1, IRDataType.BYTE, CallLocation.HardwareRegister(CallingConventionSlot(0)))),
            results = listOf(Calls.result(2, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(12))))
        )
        site.arguments[0].location shouldBe CallLocation.HardwareRegister(CallingConventionSlot(0))
        site.results[0].location shouldBe CallLocation.HardwareRegister(CallingConventionSlot(12))
    }

    test("call result has exactly one explicit location") {
        val destination = RegisterOperand(
            VirtualRegister.IntReg(RegisterNum(4)),
            IRDataType.WORD,
            OperandRole.CALL_RESULT,
            OperandDirection.DEF
        )
        val result = CallResult(
            destination,
            CallLocation.HardwareRegister(CallingConventionSlot(12))
        )
        result.location shouldBe CallLocation.HardwareRegister(CallingConventionSlot(12))
    }

    test("multiple results are kept in order") {
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("main.foo")),
            results = listOf(
                Calls.result(1, IRDataType.BYTE, CallLocation.HardwareRegister(CallingConventionSlot(10))),
                Calls.result(2, IRDataType.WORD, CallLocation.HardwareRegister(CallingConventionSlot(11))),
                CallResult(null, CallLocation.StatusFlag(Statusflag.Pc))
            )
        )
        site.results.map { it.destination?.registerNumber } shouldBe listOf(1, 2, null)
        site.results[2].location shouldBe CallLocation.StatusFlag(Statusflag.Pc)
    }

    test("a result without destination register needs an explicit location") {
        shouldThrow<IllegalArgumentException> {
            CallResult(null, CallLocation.Default)
        }
    }

    test("indirect call") {
        val target = CallTarget.Direct(codeIndirect(6))
        val call = IRInstructions.call(Opcode.CALLI, CallSite(target))
        call.opcode shouldBe Opcode.CALLI
        call.uses shouldBe setOf(VirtualRegister.int(6))
    }

    test("Amiga LVO retains signed offset") {
        val target = CallTarget.AmigaLibrary(2, -30, "Open")
        target.lvo shouldBe -30
        target.library shouldBe 2
        shouldThrow<IllegalArgumentException> { CallTarget.AmigaLibrary(2, 30, "Open") }
    }

    test("banked call keeps bank and address") {
        val banked = CallTarget.Banked(3, CodeReference.Absolute(MemoryAddress(0xc000u)), "ext.sub")
        val call = IRInstructions.call(Opcode.CALLFAR, CallSite(banked))
        (call.requireCallSite().target as CallTarget.Banked).bank shouldBe 3
        call.requireCallSite().externalName shouldBe "ext.sub"

        val varbanked = CallTarget.BankedVariable(
            RegisterOperand(VirtualRegister.int(5), IRDataType.BYTE, OperandRole.VALUE, OperandDirection.USE),
            CodeReference.Absolute(MemoryAddress(0xc000u))
        )
        val call2 = IRInstructions.call(Opcode.CALLFARVB, CallSite(varbanked))
        call2.uses shouldBe setOf(VirtualRegister.int(5))
    }

    test("syscall") {
        val call = IRInstructions.syscall(
            13,
            arguments = listOf(Calls.argument(99000, IRDataType.WORD)),
            results = listOf(Calls.result(99100, IRDataType.BYTE))
        )
        call.opcode shouldBe Opcode.SYSCALL
        (call.requireCallSite().target as CallTarget.SystemCall).number shouldBe 13
        call.uses shouldBe setOf(VirtualRegister.int(99000))
        call.definitions shouldBe setOf(VirtualRegister.int(99100))
    }

    test("call site registers can be remapped") {
        val site = CallSite(
            CallTarget.Direct(CodeReference.Label("main.foo")),
            arguments = listOf(Calls.argument(1, IRDataType.BYTE)),
            results = listOf(Calls.result(2, IRDataType.BYTE))
        )
        val mapped = site.mapRegisters { if(it == VirtualRegister.int(1)) VirtualRegister.int(9) else it }
        mapped.arguments[0].source.registerNumber shouldBe 9
        mapped.results[0].destination!!.registerNumber shouldBe 2
        mapped.arguments[0].location shouldBe CallLocation.Default
    }

    test("call sites are only allowed on call opcodes") {
        shouldThrow<IllegalArgumentException> {
            IRInstructions.call(Opcode.CALL, CallSite(CallTarget.SystemCall(1)))
        }
        shouldThrow<IllegalArgumentException> {
            IRInstructions.call(Opcode.SYSCALL, CallSite(CallTarget.Direct(CodeReference.Label("main.foo"))))
        }
    }
})
