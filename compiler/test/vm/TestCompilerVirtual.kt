package prog8tests.vm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.statements.Assignment
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.intermediate.IRDataType
import prog8.intermediate.IRFileReader
import prog8.intermediate.IRSubroutine
import prog8.intermediate.Opcode
import prog8.vm.VmRunner
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestCompilerVirtual: FunSpec({
    val outputDir = tempdir().toPath()

    test("linear words array with pointers") {
        val src = """
main {
    sub start() {
        str localstr = "hello"
        ubyte[] otherarray = [1,2,3]
        uword[] @nosplit words = [1111,2222,"three",&localstr,&otherarray]
        uword @shared zz = &words
        bool result = 2222 in words
        zz = words[2]
        zz++
        zz = words[3]
    }
}"""
        val target = VMTarget()
        val result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)
    }

    test("split words array with pointers") {
        val src = """
main {
    sub start() {
        str localstr = "hello"
        ubyte[] otherarray = [1,2,3]
        uword[] words = [1111,2222,"three",&localstr,&otherarray]
        uword @shared zz = &words
        bool result = 2222 in words
        zz = words[2]
        zz++
        zz = words[3]
    }
}"""
        val target = VMTarget()
        val result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)
    }

    test("taking address of split arrays works") {
        val src="""
main {
    sub start() {
        cx16.r0L=0
        if cx16.r0L==0 {
            uword[] addresses = [scores2, start]
            uword[] scores1 = [10, 25, 50, 100]
            uword[] scores2 = [100, 250, 500, 1000]

            cx16.r0 = &scores1
            cx16.r1 = &scores2
            cx16.r2 = &addresses
        }
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true, errors=errors) shouldNotBe null
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 0
    }

    test("compile virtual: str args and return type, and global var init") {
        val src = """
main {
    ubyte @shared dvar = test.dummy()
    
    sub start() {
        sub testsub(str s1) -> str {
            return "result"
        }
        
        uword result = testsub("arg")
    }
}

test {
    sub dummy() -> ubyte {
        cx16.r0++
        return 80
    }
}"""
        val target = VMTarget()
        var result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        var virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)

        result = compileText(target, true, src, outputDir, writeAssembly = true)!!
        virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)
    }

    test("compile virtual: nested labels") {
        val src = """
main {
    sub start() {
        uword i
        uword k

        repeat {
mylabel0:
            goto mylabel0
        }

        while cx16.r0==0 {
mylabel1:
            goto mylabel1
        }

        do {
mylabel2:
            goto mylabel2
        } until cx16.r0==1

        repeat cx16.r0 {
mylabel3:
            goto mylabel3
        }

        for cx16.r0L in 0 to 2 {
mylabel4:
            goto mylabel4
        }

        for cx16.r0L in cx16.r1L to cx16.r2L {
mylabel5:
            goto mylabel5
        }

mylabel_outside:        
        for i in 0 to 10 {
mylabel_inside:        
            if i==100 {
                goto mylabel_outside
                goto mylabel_inside
            }
            while k <= 10 {
                k++
            }
            do {
                k--
            } until k==0
            for k in 0 to 5 {
                i++
            }
            repeat 10 {
                k++
            }
        }
    }
}"""

        val target1 = C64Target()
        compileText(target1, false, src, outputDir, writeAssembly = false) shouldNotBe null

        val target = VMTarget()
        compileText(target, false, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("case sensitive symbols") {
        val src = """
main {
    sub start() {
        ubyte @shared bytevar = 11      ; var at 0
        ubyte @shared byteVAR = 22      ; var at 1
        ubyte @shared ByteVar = 33      ; var at 2
        ubyte @shared total = bytevar+byteVAR+ByteVar   ; var at 3
        goto skipLABEL
SkipLabel:
        return
skipLABEL:
        bytevar = 42
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.memory.getUB(0) shouldBe 42u
            vm.memory.getUB(3) shouldBe 66u
        }
    }

    test("memory slabs") {
        val src = """
main {
    sub start() {
        uword slab1 = memory("slab1", 2000, 64)
        slab1[10]=42
        slab1[11]=43
        ubyte @shared value1 = slab1[10]     ; var at 2
        ubyte @shared value2 = slab1[11]     ; var at 3
    }
}"""
        val target = VMTarget()
        val result = compileText(target, true, src, outputDir, writeAssembly = true)!!
        val start = result.compilerAst.entrypoint
        start.statements.size shouldBe 9
        ((start.statements[1] as Assignment).value as FunctionCallExpression).target.nameInSource shouldBe listOf("memory")
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.memory.getUB(2) shouldBe 42u
            vm.memory.getUB(3) shouldBe 43u
        }
    }

    test("memory mapped var as for loop counter") {
        val src = """
main {
    sub start() {
        for cx16.r0 in 0 to 10 {
            cx16.r1++
        }
    }
}"""
        val othertarget = Cx16Target()
        compileText(othertarget, true, src, outputDir, writeAssembly = true) shouldNotBe null

        val target = VMTarget()
        var result = compileText(target, true, src, outputDir, writeAssembly = true)!!
        var virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.stepCount shouldBe 59
        }

        result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.stepCount shouldBe 59
        }
    }

    test("inline asm for virtual target should be IR") {
        val src = """
main {
  sub start() {
    %asm {{
        lda #99
        tay
        rts
    }}
  }
}"""
        val othertarget = Cx16Target()
        compileText(othertarget, true, src, outputDir, writeAssembly = true) shouldNotBe null

        val target = VMTarget()
        val result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val exc = shouldThrow<Exception> {
            VmRunner().runProgram(virtfile.readText(), false)
        }
        exc.message shouldContain("encountered unconverted inline assembly chunk")
    }

    test("inline asm for virtual target with IR is accepted and converted to regular instructions") {
        val src = """
main {
  sub start() {
    %ir {{
        incm.b $2000
        return
    }}
  }
}"""
        val target = VMTarget()
        val result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSrc = virtfile.readText()
        irSrc.shouldContain("incm.b $2000")
        irSrc.shouldNotContain("</ASM>")
        VmRunner().runProgram(irSrc, false)
    }

    test("addresses from labels/subroutines not yet supported in VM") {
        val src = """
main {
    sub start() {

mylabel:
        ubyte variable
        uword @shared pointer1 = &main.start
        uword @shared pointer2 = &start
        uword @shared pointer3 = &main.start.mylabel
        uword @shared pointer4 = &mylabel
        uword[] @shared ptrs = [&variable, &start, &main.start, &mylabel, &main.start.mylabel]
    }
}

"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val exc = shouldThrow<Exception> {
            VmRunner().runProgram(virtfile.readText(), false)
        }
        exc.message shouldContain("cannot yet load a label address as a value")
    }

    test("nesting with overlapping names is ok (doesn't work for 64tass)") {
        val src="""
main {
    sub start() {
        main()
        main.start.start()
        main.main()

        sub main() {
            cx16.r0++
        }
        sub start() {
            cx16.r0++
        }
    }

    sub main() {
        cx16.r0++
    }
}"""

        val target = VMTarget()
        compileText(target, false, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("compile virtual: short code for if-goto") {
        val src = """
main {
    sub start() {
        if_cc
            goto ending
        if_cs
            goto ending
        if cx16.r0==0 goto ending
        if cx16.r0!=0 goto ending
        if cx16.r0s>0 goto ending
        if cx16.r0s<0 goto ending
    ending:
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        result.compilerAst.entrypoint.statements.size shouldBe 8
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(virtfile)
        val start = irProgram.blocks[0].children[0] as IRSubroutine
        val instructions = start.chunks.flatMap { c->c.instructions }
        instructions.size shouldBe 11
        instructions.last().opcode shouldBe Opcode.RETURN
    }

    test("repeat counts (const)") {
        val src="""
main {
    sub start() {
        cx16.r0 = 0
        repeat 255 {
            cx16.r0++
        }
        repeat 256 {
            cx16.r0++
        }
        repeat 257 {
            cx16.r0++
        }
        repeat 1023 {
            cx16.r0++
        }
        repeat 1024 {
            cx16.r0++
        }
        repeat 1025 {
            cx16.r0++
        }
        repeat 65534 {
            cx16.r0++
        }
        repeat 65535 {
            cx16.r0++
        }
        repeat 0 {
            cx16.r0++
        }
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
        val start = result.codegenAst!!.entrypoint()!!
        start.children.size shouldBe 12
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.memory.getUW(0xff02) shouldBe 3837u      // $ff02 = cx16.r0
        }
    }

    test("repeat counts (variable)") {
        val src="""
main {
    sub start() {
        uword count
        cx16.r0 = 0
        count=255
        repeat count {
            cx16.r0++
        }
        count=256
        repeat count {
            cx16.r0++
        }
        count=257
        repeat count {
            cx16.r0++
        }
        count=1023
        repeat count {
            cx16.r0++
        }
        count=1024
        repeat count {
            cx16.r0++
        }
        count=1025
        repeat count {
            cx16.r0++
        }
        count=65534
        repeat count {
            cx16.r0++
        }
        count=65535
        repeat count {
            cx16.r0++
        }
        count=0
        repeat count {
            cx16.r0++
        }
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
        val start = result.codegenAst!!.entrypoint()!!
        start.children.size shouldBe 23
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.memory.getUW(0xff02) shouldBe 3837u      // $ff02 = cx16.r0
        }
    }

    test("asm chunk labels in IR code") {
        val src="""
main {
    sub start() {
        instructions.match()
    }
}

instructions {
    asmsub  match() {
        %asm {{
            rts
        }}
    }

    %asm {{
        nop
    }}
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("IR codegen for while loop with shortcircuit") {
        val src="""
main {
    sub start() {
        cx16.r0L=1
        while cx16.r0L < 10 and cx16.r0L>0 {
            cx16.r0L++
        }
    }
}"""
        compileText(VMTarget(), true, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("push() and pop() generate correct IR instructions") {
        val src="""
main {
    sub start() {
        ubyte bb
        uword ww
        sys.push(42)
        bb++
        bb=sys.pop()
        sys.pushw(9999)
        ww++
        ww=sys.popw()
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(virtfile)
        val start = irProgram.blocks[0].children[0] as IRSubroutine
        val instructions = start.chunks.flatMap { c->c.instructions }
        instructions.size shouldBe 13
        instructions[3].opcode shouldBe Opcode.PUSH
        instructions[3].type shouldBe IRDataType.BYTE
        instructions[5].opcode shouldBe Opcode.POP
        instructions[5].type shouldBe IRDataType.BYTE
        instructions[8].opcode shouldBe Opcode.PUSH
        instructions[8].type shouldBe IRDataType.WORD
        instructions[10].opcode shouldBe Opcode.POP
        instructions[10].type shouldBe IRDataType.WORD
    }

})