package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtReturn
import prog8.code.ast.PtSubSignature
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IMemSizer
import prog8.code.core.ISubType
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8.vm.VmRunner
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText


class TestPointers: FunSpec( {

    val outputDir = tempdir().toPath()

    test("block scoping still parsed correctly") {
        val src="""
main {
    sub start() {
        readbyte(&thing.name)       
        readbyte(&thing.name[1])    
        readbyte(&thing.array[1])   
    }

    sub readbyte(uword @requirezp ptr) {
        ptr=0
    }
}

thing {
    str name = "error"
    ubyte[10] array
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), true)
    }

    test("passing struct instances to subroutines and returning a struct instance is not allowed") {
        val src="""
main {
    sub start() {
    }
    
    struct Node {
        bool flag
    }
    
    sub faulty(Node arg) -> Node {
        return cx16.r0
    }
}
"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors)
        val err = errors.errors
        err.size shouldBe 4
        err[0] shouldContain "struct instances cannot be declared"
        err[1] shouldContain "uword doesn't match"
        err[2] shouldContain "structs can only be passed via a pointer"
        err[3] shouldContain "structs can only be returned via a pointer"
    }

    test("pointers in subroutine return values") {
        val src="""
main {
    sub start() {
        ^^thing.Node @shared ptr = thing.new()
    }
}

thing {
    struct Node {
        bool flag
        ^^Node next
    }

    sub new() -> ^^Node {
        cx16.r0++
        ^^Node pointer = 2000
        return pointer
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        // TODO compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    test("pointers in subroutine parameters") {
        val src="""
main {
    struct MNode {
        bool flag
        ^^MNode next
    }

    sub func(^^MNode pointer) -> ^^MNode {
        cx16.r0++
        return pointer.next
    }

    sub start() {
        ^^MNode mn1 = MNode()
        mn1 = func(mn1)

        ^^thing.Node n1 = thing.Node()
        n1 = thing.func(n1)
    }
}

thing {
    struct Node {
        bool flag
        ^^Node next
    }

    sub func(^^Node pointer) -> ^^Node {
        cx16.r0++
        return pointer.next
    }

}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        // TODO compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    test("creating instances") {
        val src="""
main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        ^^MyNode @shared m1 = MyNode()
        ^^MyNode @shared m2 = MyNode(true, 0)

        ^^thing.Node @shared n1 = thing.Node()
        ^^thing.Node @shared n2 = thing.Node(true, 0)
    }
}

thing {
    struct Node {
        bool flag
        ^^Node next
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(VMTarget(), true, src, outputDir) shouldNotBe null
        // TODO compileText(C64Target(), false, src, outputDir) shouldNotBe null
        // TODO compileText(C64Target(), true, src, outputDir) shouldNotBe null
    }

    test("creating instances for unused vars should all be removed") {
        val src="""
main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        ^^MyNode m1 = MyNode()
        ^^MyNode m2 = MyNode(true, 0)

        ^^thing.Node n1 = thing.Node()
        ^^thing.Node n2 = thing.Node(true, 0)
    }
}

thing {
    struct Node {
        bool flag
        ^^Node next
    }
}"""

        var result = compileText(VMTarget(), true, src, outputDir)!!
        withClue("all variables should have been optimized away") {
            val start = result.codegenAst!!.entrypoint()!!
            start.children.size shouldBe 2
            start.children[0] shouldBe instanceOf<PtSubSignature>()
            start.children[1] shouldBe instanceOf<PtReturn>()
        }
        // TODO compileText(C64Target(), true, src, outputDir) shouldNotBe null
    }

    test("creating instances should have correct number of args") {
        val src="""
main {
    struct Node {
        bool flag
        ubyte value
        ^^Node next
    }

    sub start() {
        ^^Node ptr = Node(true)      ; error
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors)
        val err = errors.errors
        err.size shouldBe 1
        err[0] shouldContain("expected 3 or 0, got 1")
    }

    test("pointer uword compatibility") {
        val src="""
main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        cx16.r0 = MyNode()

        ^^MyNode @shared ptr1 = cx16.r0

        ptr1 = 2000
        ptr1 = 20
        ptr1 = 20.2222
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors)
        val err = errors.errors
        err.size shouldBe 1
        err[0] shouldContain("15:16: incompatible value type, can only assign uword or correct pointer")
    }


    class Struct(override val scopedNameString: String) : ISubType {
        override fun memsize(sizer: IMemSizer): Int {
            TODO("Not yet implemented")
        }
    }

    test("type of & operator (address-of)") {
        DataType.BYTE.typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.BYTE)
        DataType.WORD.typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.WORD)
        DataType.FLOAT.typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.FLOAT)
        DataType.UNDEFINED.typeForAddressOf(false) shouldBe DataType.UWORD
        DataType.UNDEFINED.typeForAddressOf(true) shouldBe DataType.pointer(BaseDataType.UBYTE)
        DataType.STR.typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.UBYTE)
        DataType.arrayFor(BaseDataType.FLOAT, false).typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.FLOAT)
        DataType.arrayFor(BaseDataType.FLOAT, false).typeForAddressOf(true) shouldBe DataType.pointer(BaseDataType.UBYTE)
        DataType.arrayFor(BaseDataType.UWORD, false).typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.UWORD)
        DataType.arrayFor(BaseDataType.UWORD, true).typeForAddressOf(false) shouldBe DataType.pointer(BaseDataType.UBYTE)
        DataType.arrayFor(BaseDataType.UWORD, false).typeForAddressOf(true) shouldBe DataType.pointer(BaseDataType.UBYTE)
        DataType.arrayFor(BaseDataType.UWORD, true).typeForAddressOf(true) shouldBe DataType.pointer(BaseDataType.UBYTE)

        DataType.pointerToType(Struct("struct")).typeForAddressOf(false) shouldBe DataType.UWORD
        DataType.pointerFromAntlr(listOf("struct")).typeForAddressOf(false) shouldBe DataType.UWORD

        DataType.pointer(BaseDataType.BOOL).typeForAddressOf(false) shouldBe DataType.UWORD
    }

    test("uword struct field array indexing") {
        val src="""
main {
    sub start() {
        struct List {
            uword s
            uword n
        }
        ^^List  l = List()
        l.s[2] = 42
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
    }

    test("uword as pointer versus pointer to uword difference") {
        val src="""
main {
    sub start() {
        uword  @shared ptr1
        ^^uword  @shared ptr2

        ptr1[2] = 1
        ptr2[2] = 1
    }
}"""

        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.codegenAst!!.entrypoint()!!.children
        st.size shouldBe 8
        val a1 = st[5] as PtAssignment
        val a2 = st[6] as PtAssignment
        a1.target.memory shouldNotBe null
        a2.target.array shouldNotBe null
    }

})