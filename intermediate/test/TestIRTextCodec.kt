import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.Statusflag
import prog8.intermediate.*


class TestIRTextCodec: FunSpec({

    fun roundtrip(text: String) {
        IRTextCodec.print(IRTextCodec.parse(text)) shouldBe text
    }

    test("registers and immediates") {
        roundtrip("load.w r1.w,#\$1234.w")
        roundtrip("load.b r1.b,#\$2a.b")
        roundtrip("load.f fr1.f,#3.14.f")
        roundtrip("load.p r1.p,#main.items")
        roundtrip("load.w r1.w,#main.items+4")
        roundtrip("loadr.l r1.l,r2.l")
        roundtrip("nop")
        roundtrip("return")
    }

    test("memory references") {
        roundtrip("loadi.b r1.b,[r2.p+8]")
        roundtrip("loadi.b r1.b,[r2.p]")
        roundtrip("loadx.w r1.w,[main.items+4+r2.w*2]")
        roundtrip("loadx.w r1.w,[main.items+r2.w]")
        roundtrip("loadm.w r0.w,[sys.wait.jiffies]")
        roundtrip("loadm.b r0.b,[\$d020]")
        roundtrip("storem.b r0.b,[main.var+2]")
        roundtrip("storezm.w [main.var]")
        roundtrip("storezx.b [main.array+r3.w*4]")
        roundtrip("storeim.b #\$2a.b,[main.var]")
        roundtrip("incm.w [main.var]")
        roundtrip("addm.b r5.b,[main.var]")
        roundtrip("addim.b #3.b,[main.var]")
    }

    test("hardware slots") {
        roundtrip("loadhr.w r1.w,s10.w")
        roundtrip("storehr.b r1.b,s0.b")
        roundtrip("loadhfaczero.f fr3.f")
    }

    test("branches and jumps") {
        roundtrip("jump main.label")
        roundtrip("jump \$c000")
        roundtrip("jumpi (r5.p)")
        roundtrip("bsteq main.label")
        roundtrip("bstcc main.label")
        roundtrip("bgt.b r1.b,#10.b,main.label")
        roundtrip("bgtr.w r1.w,r2.w,main.label")
        roundtrip("bgesr.l r1.l,r2.l,main.label")
        roundtrip("bles.b r1.b,#-5.b,main.label")
    }

    test("two-result division") {
        roundtrip("divmod.w r1.w,r2.w,#7.w")
        roundtrip("sdivmod.b r1.b,r2.b,#7.b")
        roundtrip("divmodr.w r1.w,r2.w")
        roundtrip("sdivmodr.w r1.w,r2.w")
    }

    test("divmod preserves quotient and remainder register order") {
        val original = IRInstructions.divmodImmediate(Opcode.DIVMOD, IRDataType.WORD, quotient = 1, remainder = 2, value = 7)
        val parsed = (parseIRCodeLine(original.toString()) as ParsedIRLine.Instruction).value
        parsed.requireDest().registerNumber shouldBe 1
        parsed.requireDestB().registerNumber shouldBe 2
        parsed shouldBe original
    }

    test("all call opcodes") {
        roundtrip("call main.foo()")
        roundtrip("call main.foo(main.foo.arg=r1.b,r2.w):r3.w")
        roundtrip("call \$ffd2(r5.b@s0)")
        roundtrip("call main.foo(r1.b):r2.b@s0,@Pc")
        roundtrip("calli (r6.p)()")
        roundtrip("callfar #2,\$c000(r1.b)")
        roundtrip("callfar #2,-30,Open(r1.l@s10):r2.l@s10")
        roundtrip("callfarvb r3.b,\$c000(r1.b)")
        roundtrip("syscall 13(r99000.w):r99100.b")
        roundtrip("syscall 0()")
        roundtrip("call main.foo() !memory=read,status=sets")
    }

    test("call site details are preserved") {
        val call = IRTextCodec.parse("call main.foo(main.foo.arg=r1.b,r2.w@s3):r3.w,@Pc")
        val site = call.requireCallSite()
        site.target shouldBe CallTarget.Direct(CodeReference.Label("main.foo"))
        site.arguments.size shouldBe 2
        site.arguments[0].location shouldBe CallLocation.ParameterMemory("main.foo.arg")
        site.arguments[0].source.registerNumber shouldBe 1
        site.arguments[1].location shouldBe CallLocation.HardwareRegister(CallingConventionSlot(3))
        site.results.size shouldBe 2
        site.results[0].destination!!.registerNumber shouldBe 3
        site.results[1].destination shouldBe null
        site.results[1].location shouldBe CallLocation.StatusFlag(Statusflag.Pc)
    }

    test("amiga library call keeps the signed lvo") {
        val call = IRTextCodec.parse("callfar #2,-30,Open(r1.l@s10)")
        val target = call.requireCallSite().target as CallTarget.AmigaLibrary
        target.library shouldBe 2
        target.lvo shouldBe -30
        target.name shouldBe "Open"
    }

    test("misc instructions") {
        roundtrip("push.w r1.w")
        roundtrip("pop.f fr1.f")
        roundtrip("pushst")
        roundtrip("clc")
        roundtrip("breakpoint")
        roundtrip("align #\$0100.l")
        roundtrip("bittst.b r1.b,#3.b")
        roundtrip("concat.b r1.w,r2.b,r3.b")
        roundtrip("ext.b r1.w,r2.b")
        roundtrip("lsigb.w r1.b,r2.w")
        roundtrip("fcomp.f r1.b,fr2.f,fr3.f")
        roundtrip("ffromub.f fr1.f,r2.b")
        roundtrip("sgn.w r1.b,r2.w")
        roundtrip("sqrt.l r1.w,r2.l")
    }

    test("parse rejects wrong operand kinds") {
        shouldThrow<IRParseException> { IRTextCodec.parse("load.w r1.w") }               // missing immediate
        shouldThrow<IRParseException> { IRTextCodec.parse("load.w r1.w,#1.w,#2.w") }     // too many operands
        shouldThrow<IRParseException> { IRTextCodec.parse("loadm.w r1.w,main.var") }     // memory needs brackets
        shouldThrow<IRParseException> { IRTextCodec.parse("loadi.w r1.w,[main.var]") }   // needs indirect memory
        shouldThrow<IRParseException> { IRTextCodec.parse("loadm.w [main.var],r1.w") }   // wrong slot order
        shouldThrow<IRParseException> { IRTextCodec.parse("addr.w fr1.f,r2.w") }         // float register in int slot
        shouldThrow<IRParseException> { IRTextCodec.parse("bogus.w r1.w") }              // unknown opcode
        shouldThrow<IRParseException> { IRTextCodec.parse("jump (r1.p)") }               // jump needs a static target
    }

    test("parse rejects legacy operand spellings and malformed values") {
        shouldThrow<IRParseException> { IRTextCodec.parse("load.b r1,#42") }
        shouldThrow<IRParseException> { IRTextCodec.parse("load.b r1.b,42.b") }
        shouldThrow<IRParseException> { IRTextCodec.parse("loadhr.b r1.b,s0") }
        shouldThrow<IRParseException> { IRTextCodec.parse("loadi.b r1.b,[r2]") }
        shouldThrow<IRParseException> { IRTextCodec.parse("jumpi (r2)") }
        shouldThrow<IRParseException> { IRTextCodec.parse("load.b r100000.b,#1.b") }
        shouldThrow<IRParseException> { IRTextCodec.parse("load.b r1.b,#1.5.b") }
        shouldThrow<IRParseException> { IRTextCodec.parse("loadx.b r1.b,[main.items+fr2.f]") }
        shouldThrow<IRParseException> { IRTextCodec.parse("load.b r1.b,,#1.b") }
    }

    test("direct call metadata is preserved") {
        val instruction = IRInstructions.call(
            CallSite(CallTarget.Direct(codeAddress(0xffd2u), "kernal.chrout"))
        )
        val parsed = IRTextCodec.parse(instruction.toString())
        val parsedTarget = parsed.requireCallSite().target as CallTarget.Direct
        val originalTarget = instruction.requireCallSite().target as CallTarget.Direct
        parsedTarget.externalName shouldBe originalTarget.externalName
        ((parsedTarget.reference as CodeReference.Absolute).address.value ==
                (originalTarget.reference as CodeReference.Absolute).address.value) shouldBe true
        (parsedTarget == originalTarget) shouldBe true
        (parsed.requireCallSite().effects == instruction.requireCallSite().effects) shouldBe true
        (parsed == instruction) shouldBe true
        instruction.toString() shouldBe "call \$ffd2,kernal.chrout()"
    }

    test("label lines are parsed by parseIRCodeLine") {
        val label = parseIRCodeLine("_main.label:")
        label shouldBe ParsedIRLine.Label("main.label")
    }
})
