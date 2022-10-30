package prog8tests.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.ast.expressions.BuiltinFunctionCall
import prog8.ast.statements.Assignment
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.vm.VmRunner
import prog8tests.helpers.compileText
import kotlin.io.path.readText

class TestCompilerVirtual: FunSpec({
    test("compile virtual: any all sort reverse builtin funcs") {
        val src = """
main {

    sub start() {
        uword[] words = [1111,2222,0,4444,3333]
        ubyte result = all(words)
        result++
        result = any(words)
        result++
        sort(words)
        reverse(words)
    }
}"""
        val target = VMTarget()
        val result = compileText(target, true, src, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText())
    }

    test("compile virtual: array with pointers") {
        val src = """
main {
    sub start() {
        str localstr = "hello"
        ubyte[] otherarray = [1,2,3]
        uword[] words = [1111,2222,"three",&localstr,&otherarray]
        uword @shared zz = &words
        ubyte result = 2222 in words
        zz = words[2]
        zz++
        zz = words[3]
    }
}"""
        val othertarget = Cx16Target()
        compileText(othertarget, true, src, writeAssembly = true, keepIR=true) shouldNotBe null
        val target = VMTarget()
        val result = compileText(target, true, src, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText())
    }

    test("compile virtual: str args and return type") {
        val src = """
main {

    sub start() {
        sub testsub(str s1) -> str {
            return "result"
        }
        
        uword result = testsub("arg")
    }
}"""
        val target = VMTarget()
        var result = compileText(target, false, src, writeAssembly = true)!!
        var virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText())

        result = compileText(target, true, src, writeAssembly = true)!!
        virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText())
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

        while cx16.r0 {
mylabel1:
            goto mylabel1
        }

        do {
mylabel2:
            goto mylabel2
        } until cx16.r0

; TODO fix this for vm codegen:
        repeat cx16.r0 {
mylabel3:
            goto mylabel3
        }

; TODO fix this for vm codegen:
        for cx16.r0L in 0 to 2 {
mylabel4:
            goto mylabel4
        }

; TODO fix this for vm codegen:
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
        compileText(target1, false, src, writeAssembly = false) shouldNotBe null

        val target = VMTarget()
        compileText(target, false, src, writeAssembly = true) shouldNotBe null
    }

    test("case sensitive symbols") {
        val src = """
main {
    sub start() {
        ubyte bytevar = 11      ; var at 0
        ubyte byteVAR = 22      ; var at 1
        ubyte ByteVar = 33      ; var at 2
        ubyte @shared total = bytevar+byteVAR+ByteVar   ; var at 3
        goto skipLABEL
SkipLabel:
        return
skipLABEL:
        bytevar = 42
    }
}"""
        val othertarget = Cx16Target()
        compileText(othertarget, true, src, writeAssembly = true, keepIR=true) shouldNotBe null
        val target = VMTarget()
        val result = compileText(target, true, src, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
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
        val result = compileText(target, true, src, writeAssembly = true)!!
        val start = result.program.entrypoint
        start.statements.size shouldBe 9
        ((start.statements[1] as Assignment).value as BuiltinFunctionCall).name shouldBe "memory"
        val virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
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
        compileText(othertarget, true, src, writeAssembly = true, keepIR=true) shouldNotBe null

        val target = VMTarget()
        val result = compileText(target, false, src, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.program.name + ".p8ir")
        VmRunner().runAndTestProgram(virtfile.readText()) { vm ->
            vm.stepCount shouldBe 49
        }
    }
})