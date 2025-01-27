package prog8tests.compiler.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.ast.PtBlock
import prog8.code.ast.PtVariable
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.target.Cx16Target
import prog8tests.helpers.compileText
import kotlin.io.path.readBytes
import kotlin.io.path.readText

class TestLibrary: FunSpec({

    test("library compilation") {
        val src="""
%address  ${'$'}A050
%memtop   ${'$'}C000
%output   library

%import textio


main {
    ; Create a jump table as first thing in the library.
    uword[] @shared @nosplit jumptable = [
        ; NOTE: the compiler has inserted a single JMP instruction at the start of the 'main' block, that jumps to the start() routine.
        ;       This is convenient because the rest of the jump table simply follows it,
        ;       making the first jump neatly be the required initialization routine for the library (initializing variables and BSS region).
        ;       btw, ${'$'}4c = opcode for JMP.
        ${'$'}4c00, &library.func1,
        ${'$'}4c00, &library.func2,
    ]

    sub start() {
        ; has to be here for initialization (BSS, variables init).
        %asm {{
            nop
        }}
    }

}


library {
    sub func1() {
        cx16.r0L++
    }

    sub func2() {
        cx16.r0L--
    }
}"""

        val result = compileText(Cx16Target(), true, src, writeAssembly = true)!!
        val ep = result.codegenAst!!.entrypoint()
        val main = ep!!.parent as PtBlock
        main.name shouldBe "p8b_main"
        val jumptable = main.children[0] as PtVariable
        jumptable.name shouldBe "p8v_jumptable"
        jumptable.type shouldBe DataType.arrayFor(BaseDataType.UWORD, false)
        jumptable.arraySize shouldBe 4u
        val asm = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm").readText()
        println(asm)
        val bin = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".bin").readBytes().map { it.toUByte() }

        val loadAddr = 0xa050u
        val startAddr = loadAddr + 0xbu
        val func1Addr = loadAddr + 0x10u
        val func2Addr = loadAddr + 0x13u
        fun msb(addr: UInt) = addr.shr(8).toUByte()
        fun lsb(addr: UInt) = addr.and(255u).toUByte()
        fun offset(addr: UInt) = (addr-loadAddr).toInt()+2

        // PRG header
        bin[0] shouldBe lsb(loadAddr)
        bin[1] shouldBe msb(loadAddr)
        // first jump table entry; a JMP to start() inserted by prog8 itself
        bin[2] shouldBe 0x4cu   // JMP
        bin[3] shouldBe lsb(startAddr)
        bin[4] shouldBe msb(startAddr)
        bin[5] shouldBe 0x00u   // 0 padding
        // second jump table entry (first array element!)
        bin[6] shouldBe 0x4cu   // JMP
        bin[7] shouldBe lsb(func1Addr)
        bin[8] shouldBe msb(func1Addr)
        bin[9] shouldBe 0x00u   // 0 padding
        // third jump table entry (second array element!)
        bin[10] shouldBe 0x4cu  // JMP
        bin[11] shouldBe lsb(func2Addr)
        bin[12] shouldBe msb(func2Addr)
        bin[13] shouldNotBe 0x00u   // no more padding after jump table

        // check start() contents
        bin[offset(startAddr)] shouldBe 0x20u   // JSR  (to clear_bss)
        bin[offset(startAddr)+3] shouldBe 0xeau   // NOP
        bin[offset(startAddr)+4] shouldBe 0x60u   // RTS

        // check func1
        bin[offset(func1Addr)] shouldBe 0xe6u   // INC
        // check func2
        bin[offset(func2Addr)] shouldBe 0xC6u   // DEC
    }
})