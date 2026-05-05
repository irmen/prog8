package prog8tests.vm

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import prog8.ast.expressions.FunctionCallExpression
import prog8.ast.statements.Assignment
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.intermediate.*
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
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
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
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
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
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
        var result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!
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
            vm.memory.getUB(0u) shouldBe 42u
            vm.memory.getUB(3u) shouldBe 66u
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
        val result = compileText(target, false, src, outputDir, writeAssembly = true)!!
        val start = result.compilerAst.entrypoint
        start.statements.size shouldBe 9
        ((start.statements[1] as Assignment).value as FunctionCallExpression).target.nameInSource shouldBe listOf("memory")
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.memory.getUB(2u) shouldBe 42u
            vm.memory.getUB(3u) shouldBe 43u
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

    test("if-expression skips redundant CMPI when condition sets flags") {
        val src = """
main {
    sub start() {
        bool flag = true
        cx16.r0L = if flag then 1 else 0
    }
}"""
        val result = compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(virtfile)
        val start = irProgram.blocks[0].children[0] as IRSubroutine
        val instructions = start.chunks.flatMap { c->c.instructions }

        // Find LOADM for 'flag' variable - use contains match since scoped name may vary
        val loadmIdx = instructions.indexOfFirst { it.opcode == Opcode.LOADM && it.labelSymbol?.contains("flag") == true }
        loadmIdx shouldBeGreaterThan -1

        // After LOADM, there should be a BSTEQ/BSTNE branch, not CMPI #0
        val nextInstr = instructions[loadmIdx + 1]
        nextInstr.opcode shouldBeIn setOf(Opcode.BSTEQ, Opcode.BSTNE)
    }

    test("if-statement skips redundant CMPI when condition sets flags") {
        val src = """
main {
    sub start() {
        bool flag = true
        if flag {
            cx16.r0L = 1
        } else {
            cx16.r0L = 0
        }
    }
}"""
        val result = compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(virtfile)
        val start = irProgram.blocks[0].children[0] as IRSubroutine
        val instructions = start.chunks.flatMap { c->c.instructions }

        // Find LOADM for 'flag' variable - use contains match since scoped name may vary
        val loadmIdx = instructions.indexOfFirst { it.opcode == Opcode.LOADM && it.labelSymbol?.contains("flag") == true }
        loadmIdx shouldBeGreaterThan -1

        // After LOADM, there should be a BSTEQ/BSTNE branch, not CMPI #0
        val nextInstr = instructions[loadmIdx + 1]
        nextInstr.opcode shouldBeIn setOf(Opcode.BSTEQ, Opcode.BSTNE)
    }

    test("if-expression omits CMPI since CALL now sets flags") {
        val src = """
main {
    sub getflag() -> bool { return true }
    sub start() {
        cx16.r0L = if getflag() then 1 else 0
    }
}"""
        val result = compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(virtfile)
        val start = irProgram.blocks[0].children[0] as IRSubroutine
        val instructions = start.chunks.flatMap { c->c.instructions }

        // Should NOT contain CMPI #0 after function call anymore (since CALL effectively sets flags now)
        val cmpiInstr = instructions.find { it.opcode == Opcode.CMPI && it.immediate == 0 }
        cmpiInstr shouldBe null
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
            vm.memory.getUW(0xff02u) shouldBe 3837u      // $ff02 = cx16.r0
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
        start.children.size shouldBe 22
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.memory.getUW(0xff02u) shouldBe 3837u      // $ff02 = cx16.r0
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
        push(42)
        bb++
        bb=pop()
        pushw(9999)
        ww++
        ww=popw()
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

    test("typed address-of a const pointer with non-const array indexing") {
        val src= """
main {
    sub start() {
        const uword cbuffer = $2000
        uword @shared buffer = $2000

        cx16.r2 = @(cbuffer + cx16.r0)
        cx16.r1 = &cbuffer[cx16.r0]

        cx16.r3 = @(buffer + cx16.r0)
        cx16.r4 = &buffer[cx16.r0]     
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), true)
    }

    test("correct reg types with sqrt") {
        val src= """
main  {
    sub start() {
        cx16.r0L= sqrt(cx16.r1)
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
    }

    test("SSA basic blocks") {
        val src= """
main  {
    sub start() {
        func()
    }

    sub func() {
        if cx16.r0<10 or cx16.r0>319 {
            cx16.r1++
        }
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irProgram = IRFileReader().read(virtfile)
        val func = irProgram.blocks[0].children[1] as IRSubroutine
        func.label shouldBe "main.func"
        func.chunks.size shouldBe 4
        for(chunk in func.chunks) {
            if(chunk.next == null) {
                chunk.instructions.last().opcode shouldBeIn OpcodesThatBranchUnconditionally
            }
            val branches = chunk.instructions.filter { it.opcode in OpcodesThatEndSSAblock }
            branches.size shouldBeLessThanOrEqual 1
        }
    }

    test("deeply scoped variable references") {
        val src= $"""
main {
    sub start() {
        main.sub1.sub2.sub3.variable = 100
    }

    sub sub1() {
        sub sub2() {
            sub sub3() {
                ubyte @shared variable
            }
        }
    }
}"""
        compileText(Cx16Target(), true, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), true)
    }

    test("uninitialized struct instance is zeroed out in BSS") {
        val src = """
main {
    struct Point {
        word x
        word y
    }

    sub start() {
        ; Create an uninitialized struct instance - should be zeroed out at startup
        ^^Point emptyPoint = ^^Point : []
        
        ; Copy struct fields to shared variables so we can verify them
        main.xval = emptyPoint.x
        main.yval = emptyPoint.y
    }
    
    ; Shared variables to store results
    word @shared xval
    word @shared yval  
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = virtfile.readText()
        
        // Verify the IR file contains STRUCTINSTANCESNOINIT section
        irContent.shouldContain("<STRUCTINSTANCESNOINIT>")
        irContent.shouldContain("</STRUCTINSTANCESNOINIT>")
        
        // Parse the IR to get variable allocations and verify memory
        val irProgram = IRFileReader().read(irContent)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        
        VmRunner().runAndTestProgram(irContent) { vm ->
            // Get the addresses of the shared variables
            val xvalAddr = allocations["main.xval"]!!
            val yvalAddr = allocations["main.yval"]!!
            
            // These should be 0 because they were copied from the zeroed-out struct
            vm.memory.getUW(xvalAddr) shouldBe 0u
            vm.memory.getUW(yvalAddr) shouldBe 0u
            
            // Also verify the struct instance itself is zeroed
            val structAddr = allocations.values.first { addr -> 
                addr > xvalAddr && addr > yvalAddr 
            }
            vm.memory.getUW(structAddr) shouldBe 0u      // x field
            vm.memory.getUW(structAddr + 2u) shouldBe 0u  // y field
        }
    }

    test("uninitialized struct instance with more fields is zeroed out") {
        val src = """
main {
    struct Data {
        byte a
        byte b
        word c
        ubyte d
        uword e
    }

    sub start() {
        ; Create an uninitialized struct instance with multiple fields
        ^^Data empty = ^^Data : []
        
        ; Copy all fields to shared variables to verify they are zeroed
        main.a_result = empty.a
        main.b_result = empty.b
        main.c_result = empty.c
        main.d_result = empty.d
        main.e_result = empty.e
    }
    
    ; Shared variables at known addresses to store results
    byte @shared a_result
    byte @shared b_result
    word @shared c_result
    ubyte @shared d_result
    uword @shared e_result
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = virtfile.readText()
        
        // Verify the IR file contains STRUCTINSTANCESNOINIT section
        irContent.shouldContain("<STRUCTINSTANCESNOINIT>")
        
        // Parse the IR to get variable allocations and verify memory
        val irProgram = IRFileReader().read(irContent)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        
        // Run the program and verify all struct fields were zeroed
        VmRunner().runAndTestProgram(irContent) { vm ->
            // Check all shared variables are 0
            vm.memory.getUB(allocations["main.a_result"]!!) shouldBe 0u
            vm.memory.getUB(allocations["main.b_result"]!!) shouldBe 0u
            vm.memory.getUW(allocations["main.c_result"]!!) shouldBe 0u
            vm.memory.getUB(allocations["main.d_result"]!!) shouldBe 0u
            vm.memory.getUW(allocations["main.e_result"]!!) shouldBe 0u
        }
    }

    test("mix of initialized and uninitialized struct instances") {
        val src = """
main {
    struct Point {
        word x
        word y
    }

    sub start() {
        ; Initialized struct instance
        ^^Point initPoint = ^^Point : [100, 200]
        
        ; Uninitialized struct instance - should be zeroed
        ^^Point emptyPoint = ^^Point : []
        
        ; Another initialized one
        ^^Point initPoint2 = ^^Point : [300, 400]
        
        ; Another uninitialized one
        ^^Point emptyPoint2 = ^^Point : []
        
        ; Copy values to shared variables for verification
        main.x1 = initPoint.x
        main.y1 = initPoint.y
        main.ex1 = emptyPoint.x
        main.ey1 = emptyPoint.y
        main.x2 = initPoint2.x
        main.y2 = initPoint2.y
        main.ex2 = emptyPoint2.x
        main.ey2 = emptyPoint2.y
    }
    
    ; Shared variables to store results
    word @shared x1
    word @shared y1
    word @shared ex1
    word @shared ey1
    word @shared x2
    word @shared y2
    word @shared ex2
    word @shared ey2
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = virtfile.readText()
        
        // Verify the IR file contains both sections
        irContent.shouldContain("<STRUCTINSTANCESNOINIT>")
        irContent.shouldContain("<STRUCTINSTANCES>")
        
        // Parse the IR to get variable allocations
        val irProgram = IRFileReader().read(irContent)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations
        
        // Run the program and verify both initialized and uninitialized values
        VmRunner().runAndTestProgram(irContent) { vm ->
            // Check initialized struct values
            vm.memory.getUW(allocations["main.x1"]!!) shouldBe 100u
            vm.memory.getUW(allocations["main.y1"]!!) shouldBe 200u
            vm.memory.getUW(allocations["main.x2"]!!) shouldBe 300u
            vm.memory.getUW(allocations["main.y2"]!!) shouldBe 400u
            
            // Check uninitialized struct values (should be zeroed)
            vm.memory.getUW(allocations["main.ex1"]!!) shouldBe 0u
            vm.memory.getUW(allocations["main.ey1"]!!) shouldBe 0u
            vm.memory.getUW(allocations["main.ex2"]!!) shouldBe 0u
            vm.memory.getUW(allocations["main.ey2"]!!) shouldBe 0u
        }
    }

    test("struct with pointer fields can be compiled and run") {
        val src = """
main {
    struct State {
        uword c
        ^^State out
        ^^State out1
        uword lastlist
    }

    sub start() {
        ^^State matchstate = [258, 0, 0, 0]
        main.matchstatePtr = matchstate
        main.lastlistPtr = &matchstate.lastlist
    }
    
    ^^State @shared matchstatePtr
    uword @shared lastlistPtr
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = virtfile.readText()

        irContent.shouldContain("^^main.State")
        VmRunner().runProgram(irContent, false)
    }

    test("nosplit word array += and -= with variable index and value") {
        // This test verifies the fix for LOADX/STOREX with element size multiplication
        // Previously failed with -noopt because index wasn't multiplied by element size
        val src = """
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; Test += with variable index, constant value
        uword[] @nosplit arr1 = [100, 200, 300]
        ubyte idx1 = 1
        arr1[idx1] += 100

        ; Test += with variable index, variable value
        uword[] @nosplit arr2 = [100, 200, 300]
        ubyte idx2 = 2
        uword val2 = 25
        arr2[idx2] += val2

        ; Test -= with variable index, variable value
        uword[] @nosplit arr3 = [500, 400, 300]
        ubyte idx3 = 1
        uword val3 = 150
        arr3[idx3] -= val3

        ; Test *= with variable index, constant value
        uword[] @nosplit arr4 = [10, 20, 30]
        ubyte idx4 = 2
        arr4[idx4] *= 5

        ; Store results for verification
        main.r1 = arr1[0]
        main.r2 = arr1[1]
        main.r3 = arr1[2]
        main.r4 = arr2[0]
        main.r5 = arr2[1]
        main.r6 = arr2[2]
        main.r7 = arr3[0]
        main.r8 = arr3[1]
        main.r9 = arr3[2]
        main.r10 = arr4[0]
        main.r11 = arr4[1]
        main.r12 = arr4[2]
    }

    uword @shared r1
    uword @shared r2
    uword @shared r3
    uword @shared r4
    uword @shared r5
    uword @shared r6
    uword @shared r7
    uword @shared r8
    uword @shared r9
    uword @shared r10
    uword @shared r11
    uword @shared r12
}"""
        // Test with optimizations OFF to ensure the non-optimized path works
        val result = compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = virtfile.readText()

        // Parse and run
        val irProgram = IRFileReader().read(irContent)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations

        VmRunner().runAndTestProgram(irContent) { vm ->
            // arr1: [100, 200+100, 300] = [100, 300, 300]
            vm.memory.getUW(allocations["main.r1"]!!) shouldBe 100u
            vm.memory.getUW(allocations["main.r2"]!!) shouldBe 300u
            vm.memory.getUW(allocations["main.r3"]!!) shouldBe 300u

            // arr2: [100, 200, 300+25] = [100, 200, 325]
            vm.memory.getUW(allocations["main.r4"]!!) shouldBe 100u
            vm.memory.getUW(allocations["main.r5"]!!) shouldBe 200u
            vm.memory.getUW(allocations["main.r6"]!!) shouldBe 325u

            // arr3: [500, 400-150, 300] = [500, 250, 300]
            vm.memory.getUW(allocations["main.r7"]!!) shouldBe 500u
            vm.memory.getUW(allocations["main.r8"]!!) shouldBe 250u
            vm.memory.getUW(allocations["main.r9"]!!) shouldBe 300u

            // arr4: [10, 20, 30*5] = [10, 20, 150]
            vm.memory.getUW(allocations["main.r10"]!!) shouldBe 10u
            vm.memory.getUW(allocations["main.r11"]!!) shouldBe 20u
            vm.memory.getUW(allocations["main.r12"]!!) shouldBe 150u
        }
    }

    test("divmod does not crash when results are unused (optimization issue)") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                sub start() {
                    ubyte num = 230
                    ubyte div = 13
                    ubyte d, r = divmod(num, div)
                }
            }
        """.trimIndent()

        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)
    }

    test("divmod results are correct with optimizations") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                ubyte d_res
                ubyte r_res
                sub start() {
                    d_res = 0
                    r_res = 0
                    ubyte num = 230
                    ubyte div = 13
                    ubyte d, r = divmod(num, div)
                    d_res = d
                    r_res = r
                }
            }
        """.trimIndent()

        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!

        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSource = virtfile.readText()
        val irProgram = IRFileReader().read(irSource)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations

        VmRunner().runAndTestProgram(irSource) { vm ->
            vm.memory.getUB(allocations["main.d_res"]!!) shouldBe 17u
            vm.memory.getUB(allocations["main.r_res"]!!) shouldBe 9u
        }
    }

    test("subtraction with constant left operand is correct (non-commutative)") {
        val src = """
            %zeropage basicsafe
            %option no_sysinit
            main {
                word sub_res
                word div_res
                word mod_res
                sub start() {
                    word x = 10
                    sub_res = 20 - x
                    div_res = 100 / x
                    mod_res = 25 % x
                }
            }
        """.trimIndent()

        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!

        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irSource = virtfile.readText()
        val irProgram = IRFileReader().read(irSource)
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations

        VmRunner().runAndTestProgram(irSource) { vm ->
            vm.memory.getUW(allocations["main.sub_res"]!!) shouldBe 10u
            vm.memory.getUW(allocations["main.div_res"]!!) shouldBe 10u
            vm.memory.getUW(allocations["main.mod_res"]!!) shouldBe 5u
        }
    }

    test("memory() in array should not crash IRFileWriter") {
        val code = """
            %zeropage basicsafe
            %import textio
            main {
                uword[2] addresses = [ memory("a1", 10, 0), memory("a2", 20, 0) ]
                sub start() {
                    txt.print_uw(addresses[0])
                }
            }
        """.trimIndent()

        val errors = ErrorReporterForTests()
        val result = compileText(VMTarget(), true, code, outputDir, errors)
        withClue(errors.errors.joinToString("\n")) {
            result shouldNotBe null
        }
    }

    test("LONG constants and variables should not crash IRFileWriter") {
        val code = """
            %zeropage basicsafe
            %import textio
            main {
                const long CL = $12345678
                long vl = $87654321
                sub start() {
                    txt.print_l(CL)
                    txt.print_l(vl)
                }
            }
        """.trimIndent()

        val errors = ErrorReporterForTests()
        val result = compileText(VMTarget(), true, code, outputDir, errors)
        withClue(errors.errors.joinToString("\n")) {
            result shouldNotBe null
        }
    }
    
})
