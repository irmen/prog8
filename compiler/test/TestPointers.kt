package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.IMemSizer
import prog8.code.core.ISubType
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.vm.VmRunner
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import kotlin.io.path.readText


class TestPointers: FunSpec( {

    val outputDir = tempdir().toPath()

    test("basic pointers") {
        val src="""
%import floats

main {
    ^^bool g_bp
    ^^word g_bw
    ^^float g_floats

    sub start() {
        ^^bool l_bp
        ^^word l_bw
        ^^float l_floats

        assign_pointers()
        assign_values()
        assign_inplace()
        assign_deref()
        assign_uwords()
        assign_same_ptrs()
        assign_different_ptrs()

        sub assign_pointers() {
            cx16.r0 = l_bp
            cx16.r1 = l_bw
            cx16.r2 = l_floats
            cx16.r0 = g_bp
            cx16.r1 = g_bw
            cx16.r2 = g_floats
            cx16.r0 = other.g_bp
            cx16.r1 = other.g_bw
            cx16.r2 = other.g_floats
            cx16.r0 = other.func.l_bp
            cx16.r1 = other.func.l_bw
            cx16.r2 = other.func.l_floats
        }

        sub assign_deref() {
            float f
            bool b
            word w
            b = l_bp^^
            w = l_bw^^
            f = l_floats^^
            b = g_bp^^
            w = g_bw^^
            f = g_floats^^
            b = other.g_bp^^
            w = other.g_bw^^
            f = other.g_floats^^
            b = other.func.l_bp^^
            w = other.func.l_bw^^
            f = other.func.l_floats^^
        }

        sub assign_values() {
            l_bp^^ = true
            l_bw^^ = -1234
            l_floats^^ = 5.678
            g_bp^^ = true
            g_bw^^ = -1234
            g_floats^^ = 5.678
            other.g_bp^^ = true
            other.g_bw^^ = -1234
            other.g_floats^^ = 5.678
            other.func.l_bp^^ = true
            other.func.l_bw^^ = -1234
            other.func.l_floats^^ = 5.678
        }

        sub assign_same_ptrs() {
            l_bp = g_bp
            l_bw = g_bw
            l_floats = g_floats
            g_bp = other.g_bp
            g_bw = other.g_bw
            g_floats = other.g_floats
            other.g_bp = other.func.l_bp
            other.g_bw = other.func.l_bw
            other.g_floats = other.func.l_floats
        }

        sub assign_different_ptrs() {
            l_bp = g_floats as ^^bool
            l_bw = g_floats as ^^word
            l_floats = g_bp as ^^float
            other.g_bp = l_floats as ^^bool
            other.g_bw = l_floats as ^^word
            other.g_floats = l_bp as ^^float
        }

        sub assign_inplace() {
            bool b
            l_bp^^ = l_bp^^ xor b
            l_bw^^ += -1234
            l_floats^^ += 5.678
            g_bp^^ = g_bp^^ xor b
            g_bw^^ += -1234
            g_floats^^ += 5.678
            other.g_bp^^ = other.g_bp^^ xor b
            other.g_bw^^ += -1234
            other.g_floats^^ += 5.678
            other.func.l_bp^^ = other.func.l_bp^^ xor b
            other.func.l_bw^^ += -1234
            other.func.l_floats^^ += 5.678

            l_bw^^ /= 3
            l_floats^^ /= 3.0
            g_bw^^ /= 3
            g_floats^^ /= 3.0
            other.g_bw^^ /= 3
            other.g_floats^^ /= 3.0
            other.func.l_bw^^ /= 3
            other.func.l_floats^^ /= 3.0
        }

        sub assign_uwords() {
            l_bp = $9000
            l_bw = $9000
            l_floats = $9000
            g_bp = $9000
            g_bw = $9000
            g_floats = $9000
            other.g_bp = $9000
            other.g_bw = $9000
            other.g_floats = $9000
            other.func.l_bp = $9000
            other.func.l_bw = $9000
            other.func.l_floats = $9000
        }
    }
}

other {
    ^^bool g_bp
    ^^word g_bw
    ^^float g_floats

    sub func() {
        ^^bool l_bp
        ^^word l_bw
        ^^float l_floats
    }
}
"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("struct pointers") {
        val src="""
%import floats

main {
    struct Node {
        bool bb
        float f
        word w
        ^^Node next
    }

    sub start() {
        ^^Node n1 = ^^Node : [false, 1.1, 1111, 0]
        ^^Node n2 = ^^Node : [false, 2.2, 2222, 0]
        ^^Node n3 = ^^Node : [true, 3.3, 3333, 0]

        n1.next = n2
        n2.next = n3
        n3.next = 0

        bool bb = n1.bb
        float f = n1.f
        uword next = n1.next
        word w = n1.w

        bb = n2.bb
        f = n2.f
        next = n2.next
        w = n2.w

        n1.next.next.bb = false
        n1.next.next.f = 42.999
        n1.next.next.w = 5555
        n1.next.next.w++

        bb = n1.next.next.bb
        f = n1.next.next.f
    }
}
"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("pointer walking using simple dot notation should be equivalent to explicit dereference chain") {
        val src="""
main {
    struct State {
        uword c
        ^^uword ptr
        ^^State next
    }

    sub start() {
        ^^State matchstate
        cx16.r0 = matchstate^^.ptr
        cx16.r1 = matchstate^^.next^^.next^^.ptr
        cx16.r2 = matchstate.ptr
        cx16.r3 = matchstate.next.next.ptr
        cx16.r4 = matchstate.ptr^^
        cx16.r5 = matchstate.next.next.ptr^^

        matchstate^^.ptr = 2222
        matchstate^^.next^^.next^^.ptr = 2222
        matchstate.ptr = 2222
        matchstate.next.next.ptr = 2222
    }
}"""

        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 13
        val a0v = (st[2] as Assignment).value as PtrDereference
        a0v.chain shouldBe listOf("matchstate", "ptr")
        a0v.derefLast shouldBe false

        val a1v = (st[3] as Assignment).value as PtrDereference
        a1v.chain shouldBe listOf("matchstate", "next", "next", "ptr")
        a1v.derefLast shouldBe false

        val a2v = (st[4] as Assignment).value as PtrDereference
        a2v.chain shouldBe listOf("matchstate", "ptr")
        a2v.derefLast shouldBe false

        val a3v = (st[5] as Assignment).value as PtrDereference
        a3v.chain shouldBe listOf("matchstate", "next", "next", "ptr")
        a3v.derefLast shouldBe false

        val a4v = (st[6] as Assignment).value as PtrDereference
        a4v.chain shouldBe listOf("matchstate", "ptr")
        a4v.derefLast shouldBe true

        val a5v = (st[7] as Assignment).value as PtrDereference
        a5v.chain shouldBe listOf("matchstate", "next", "next", "ptr")
        a5v.derefLast shouldBe true

        val t0 = (st[8] as Assignment).target.pointerDereference!!
        t0.derefLast shouldBe false
        t0.chain shouldBe listOf("matchstate", "ptr")

        val t1 = (st[9] as Assignment).target.pointerDereference!!
        t1.derefLast shouldBe false
        t1.chain shouldBe listOf("matchstate", "next", "next", "ptr")

        val t2 = (st[10] as Assignment).target.pointerDereference!!
        t2.derefLast shouldBe false
        t2.chain shouldBe listOf("matchstate", "ptr")

        val t3 = (st[11] as Assignment).target.pointerDereference!!
        t3.derefLast shouldBe false
        t3.chain shouldBe listOf("matchstate", "next", "next", "ptr")
    }

    test("word size pointer indexing on pointers") {
        val src="""
%import floats

main {

    struct List {
        ^^uword s
        ubyte n
        ^^List next
    }

    sub start() {
        ubyte[10] array
        uword @shared wordptr
        ^^bool @shared boolptr
        ^^float @shared floatptr
        ^^byte @shared byteptr
        ^^ubyte @shared ubyteptr
        ^^List @shared listptr
        ^^List @shared listptr2

        bool @shared zz
        float @shared fl
        byte @shared bb

        zz = boolptr[999]
        fl = floatptr[999]
        bb = byteptr[999]
        cx16.r0L = ubyteptr[999]
        cx16.r1L = wordptr[999]
        cx16.r2L = array[9]
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 27

        val a_zz = (st[20] as Assignment).value
        a_zz shouldBe instanceOf<FunctionCallExpression>()
        val a_fl = (st[21] as Assignment).value
        a_fl shouldBe instanceOf<FunctionCallExpression>()
        val a_bb = (st[22] as Assignment).value
        a_bb shouldBe instanceOf<DirectMemoryRead>()
        val a_r0 = (st[23] as Assignment).value
        a_r0 shouldBe instanceOf<DirectMemoryRead>()
        val a_r1 = (st[24] as Assignment).value
        a_r1 shouldBe instanceOf<DirectMemoryRead>()
        val a_r2 = (st[25] as Assignment).value
        a_r2 shouldBe instanceOf<ArrayIndexedExpression>()
        val a_lptr2 = (st[25] as Assignment).value
        a_lptr2 shouldBe instanceOf<ArrayIndexedExpression>()
    }

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
        err.size shouldBe 3
        err[0] shouldContain "uword doesn't match"
        err[1] shouldContain "structs can only be passed via a pointer"
        err[2] shouldContain "structs can only be returned via a pointer"
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
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
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
        ^^MNode mn1 = ^^MNode : []
        mn1 = func(mn1)

        ^^thing.Node n1 = ^^thing.Node : []
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
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("string comparisons still calling string compare routine")  {
        val src="""
main {
    str s1 = "hello"
    ^^ubyte @shared ubyteptr

    sub start() {
        cx16.r0bL = s1=="wob"
        cx16.r1bL = "wob"=="s1"
        cx16.r2bL = "wob"==cx16.r0
        cx16.r3bL = cx16.r0=="wob"
        cx16.r4bL = "wob"==ubyteptr
        cx16.r5bL = ubyteptr=="wob"
        void compare1("wob")
        void compare2("wob")
    }

    sub compare1(str s2) -> bool {
        return s1==s2
    }

    sub compare2(str s2) -> bool {
        return s2==s1
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = false)!!
        val main = result.compilerAst.allBlocks.first {it.name=="main"}
        val st = main.statements.filterIsInstance<Subroutine>().first {it.name=="start"}.statements

        fun assertIsStringCompare(expr: BinaryExpression) {
            expr.operator shouldBe "=="
            (expr.right as NumericLiteral).number shouldBe 0.0
            (expr.left as FunctionCallExpression).target.nameInSource shouldBe listOf("prog8_lib_stringcompare")
        }

        st.size shouldBe 9
        val av1 = (st[0] as Assignment).value as BinaryExpression
        val av2 = (st[1] as Assignment).value as BinaryExpression
        val av3 = (st[2] as Assignment).value as BinaryExpression
        val av4 = (st[3] as Assignment).value as BinaryExpression
        val av5 = (st[4] as Assignment).value as BinaryExpression
        val av6 = (st[5] as Assignment).value as BinaryExpression
        assertIsStringCompare(av1)
        assertIsStringCompare(av2)
        assertIsStringCompare(av3)
        assertIsStringCompare(av4)
        assertIsStringCompare(av5)
        assertIsStringCompare(av6)

        val compare1 = main.statements.filterIsInstance<Subroutine>().first {it.name=="compare1"}
        val r1v = (compare1.statements.last() as Return).values.single() as BinaryExpression
        assertIsStringCompare(r1v)

        val compare2 = main.statements.filterIsInstance<Subroutine>().first {it.name=="compare2"}
        val r2v = (compare2.statements.last() as Return).values.single() as BinaryExpression
        assertIsStringCompare(r2v)
    }

    test("str or ubyte array params or return type replaced by pointer to ubyte") {
        val src="""
main {
    sub start() {
        test1("zzz")
        test2("zzz")
    }

    sub test1(str arg) -> str {
        cx16.r0++
        return cx16.r0
    }

    sub test2(ubyte[] arg) {
        cx16.r0++
    }
}"""

        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = false)!!
        val main = result.compilerAst.allBlocks.first {it.name=="main"}
        val test1 = main.statements[1] as Subroutine
        val test2 = main.statements[2] as Subroutine
        test1.name shouldBe "test1"
        test1.parameters.single().type shouldBe DataType.pointer(DataType.UBYTE)
        test1.returntypes.single() shouldBe DataType.pointer(DataType.UBYTE)
        test2.name shouldBe "test2"
        test2.parameters.single().type shouldBe DataType.pointer(DataType.UBYTE)
    }

    test("creating instances") {
        val src="""
main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        ^^MyNode @shared m1 = ^^MyNode : []
        ^^MyNode @shared m2 = ^^MyNode : [true, 0]

        ^^thing.Node @shared n1 = ^^thing.Node : []
        ^^thing.Node @shared n2 = ^^thing.Node : [true, 0]
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
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), true, src, outputDir) shouldNotBe null
    }

    test("creating instances for unused vars should all be removed") {
        val src="""
main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        ^^MyNode m1 = ^^MyNode : []
        ^^MyNode m2 = ^^MyNode : [true, 0]

        ^^thing.Node n1 = ^^thing.Node : []
        ^^thing.Node n2 = ^^thing.Node : [true, 0]
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
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), true, src, outputDir) shouldNotBe null
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
        ^^Node ptr = ^^Node : [true]      ; error
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors)
        val err = errors.errors
        err.size shouldBe 1
        err[0] shouldContain("expected 3 or 0 but got 1, missing: value, next")
    }

    test("pointer uword compatibility") {
        val src="""
main {
    struct MyNode {
        bool flag
        ^^MyNode next
    }

    sub start() {
        cx16.r0 = ^^MyNode : []

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

    test("unknown field") {
        val src="""
main {
    sub start() {
        struct Node {
            ubyte weight
        }
        ^^Node nodes
        nodes^^.zzz1 = 99
        cx16.r0L = nodes^^.zzz2
        cx16.r0L = nodes[2].zzz3
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors, writeAssembly = false) shouldBe null
        val err = errors.errors
        err.size shouldBe 5
        err[0] shouldContain("no such field 'zzz1'")
        err[1] shouldContain("invalid assignment")
        err[2] shouldContain("no such field 'zzz2'")
        err[3] shouldContain("invalid assignment")
        err[4] shouldContain("no such field 'zzz3'")
    }


    class Struct(override val scopedNameString: String) : ISubType {
        override fun memsize(sizer: IMemSizer): Int {
            TODO("Not yet implemented")
        }

        override fun sameas(other: ISubType): Boolean {
            return other is Struct && other.scopedNameString == scopedNameString
        }

        override fun getFieldType(name: String): DataType? = null
    }

    test("internal type for address-of") {
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

        DataType.pointer(Struct("struct")).typeForAddressOf(false) shouldBe DataType.UWORD
        DataType.pointerFromAntlr(listOf("struct")).typeForAddressOf(false) shouldBe DataType.UWORD

        DataType.pointer(BaseDataType.BOOL).typeForAddressOf(false) shouldBe DataType.UWORD
    }

    test("untyped and typed address-of operators") {
        val src="""
%import floats

main {
    sub start() {
        float f
        cx16.r0 = &f+1
        cx16.r1 = &&f+1
    }
}"""

        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.codegenAst!!.entrypoint()!!.children
        st.size shouldBe 6
        val r0v = (st[3] as PtAssignment).value as PtBinaryExpression
        val r1v = (st[4] as PtAssignment).value as PtBinaryExpression
        r0v.left shouldBe instanceOf<PtAddressOf>()
        r0v.right shouldBe instanceOf<PtNumber>()
        (r0v.right as PtNumber).number shouldBe 1.0
        r1v.left shouldBe instanceOf<PtAddressOf>()
        r1v.right shouldBe instanceOf<PtNumber>()
        (r1v.right as PtNumber).number shouldBe VMTarget.FLOAT_MEM_SIZE
    }

    test("untyped and typed address-of subroutines") {
        val src="""
main {
    sub start() {
        cx16.r2 = &start
        cx16.r3 = &&start
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors, writeAssembly = false) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain("5:19: no support for typed pointers to subroutines")
    }

    test("address-of struct fields") {
        val src="""
%import floats

main {
    struct List {
        uword s
        ubyte n
        float f
        bool b
        ^^List next
    }
    sub start() {
        ^^List @shared l0 = 3000
        ^^List @shared l1 = 2000
        l1.next = l0

        cx16.r0 = &l1.s
        cx16.r1 = &l1.n
        cx16.r2 = &l1.f
        cx16.r3 = &l1.b
        cx16.r4 = &l1.next
        cx16.r5 = &l1.next.s
        cx16.r6 = &l1.next.n
        cx16.r7 = &l1.next.f
        cx16.r8 = &l1.next.b
        cx16.r9 = &l1.next.next
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("address-of pointer arithmetic on alias") {
        val src="""
main {
    sub start() {
        ubyte @shared index = 3
        ubyte[10] array
        alias curframe = array

        cx16.r0 = &curframe
        cx16.r1 = &curframe[3]
        cx16.r2 = &curframe + 3
        cx16.r3 = &curframe[index]
        cx16.r4 = &curframe + index
    }
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 9
        (st[3] as Assignment).value shouldBe instanceOf<AddressOf>()
        val a1v = (st[4] as Assignment).value as AddressOf
        a1v.identifier?.nameInSource shouldBe listOf("array")
        a1v.arrayIndex?.indexExpr?.constValue(result.compilerAst)?.number shouldBe 3.0

        val a2v = (st[5] as Assignment).value as BinaryExpression
        a2v.left shouldBe instanceOf<AddressOf>()
        a2v.operator shouldBe "+"
        (a2v.right as NumericLiteral).number shouldBe 3.0

        val a3v = (st[6] as Assignment).value as AddressOf
        a3v.identifier?.nameInSource shouldBe listOf("array")
        (a3v.arrayIndex?.indexExpr as IdentifierReference).nameInSource shouldBe listOf("index")

        val a4v = (st[7] as Assignment).value as BinaryExpression
        a4v.left shouldBe instanceOf<AddressOf>()
        a4v.operator shouldBe "+"
        (a4v.right as TypecastExpression).expression shouldBe instanceOf<IdentifierReference>()
    }

    test("pointer arithmetic") {
        val src="""
main {
    sub start() {
        ^^uword @shared ptr

        add1()
        add2()
        sub1()
        sub2()

        sub add1() {
            ptr += 5
            cx16.r0 = ptr + 5
            cx16.r0 = peekw(ptr + 5)
        }

        sub add2() {
            ptr += cx16.r0L
            cx16.r0 = ptr + cx16.r0L
            cx16.r0 = peekw(ptr + cx16.r0L)
        }

        sub sub1() {
            ptr -= 5
            cx16.r0 = ptr - 5
            cx16.r0 = peekw(ptr - 5)
        }

        sub sub2() {
            ptr -= cx16.r0L
            cx16.r0 = ptr - cx16.r0L
            cx16.r0 = peekw(ptr - cx16.r0L)
        }
    }
}"""

        val result = compileText(VMTarget(), true, src, outputDir)!!
        val st = result.codegenAst!!.allBlocks().first { it.name=="main" }.children
        st.size shouldBe 5
        val add1 = (st[1] as PtSub).children
        val add2 = (st[2] as PtSub).children
        val sub1 = (st[3] as PtSub).children
        val sub2 = (st[4] as PtSub).children
        add1.size shouldBe 5
        add2.size shouldBe 5
        sub1.size shouldBe 5
        sub2.size shouldBe 5

        ((add1[1] as PtAugmentedAssign).value as PtNumber).number shouldBe 10.0
        (((add1[2] as PtAssignment).value as PtBinaryExpression).right as PtNumber).number shouldBe 10.0
        val add1peek = (add1[3] as PtAssignment).value as PtBuiltinFunctionCall
        ((add1peek.args[0] as PtBinaryExpression).right as PtNumber).number shouldBe 10.0

        val add2expr1 = (add2[1] as PtAugmentedAssign).value as PtBinaryExpression
        add2expr1.operator shouldBe "<<"
        (add2expr1.right as PtNumber).number shouldBe 1.0
        val add2expr2 = ((add2[2] as PtAssignment).value as PtBinaryExpression).right as PtBinaryExpression
        add2expr2.operator shouldBe "<<"
        (add2expr2.right as PtNumber).number shouldBe 1.0
        val add2expr3 = (((add2[3] as PtAssignment).value as PtBuiltinFunctionCall).args[0] as PtBinaryExpression).right as PtBinaryExpression
        add2expr3.operator shouldBe "<<"
        (add2expr3.right as PtNumber).number shouldBe 1.0

        ((sub1[1] as PtAugmentedAssign).value as PtNumber).number shouldBe 10.0
        (((sub1[2] as PtAssignment).value as PtBinaryExpression).right as PtNumber).number shouldBe 10.0
        val sub1peek = (sub1[3] as PtAssignment).value as PtBuiltinFunctionCall
        ((sub1peek.args[0] as PtBinaryExpression).right as PtNumber).number shouldBe 10.0

        val sub2expr1 = (sub2[1] as PtAugmentedAssign).value as PtBinaryExpression
        sub2expr1.operator shouldBe "<<"
        (sub2expr1.right as PtNumber).number shouldBe 1.0
        val sub2expr2 = ((sub2[2] as PtAssignment).value as PtBinaryExpression).right as PtBinaryExpression
        sub2expr2.operator shouldBe "<<"
        (sub2expr2.right as PtNumber).number shouldBe 1.0
        val sub2expr3 = (((sub2[3] as PtAssignment).value as PtBuiltinFunctionCall).args[0] as PtBinaryExpression).right as PtBinaryExpression
        sub2expr3.operator shouldBe "<<"
        (sub2expr3.right as PtNumber).number shouldBe 1.0
    }

    test("odd pointer arithmetic") {
        val src="""
main{

    sub start() {
        ^^ubyte @shared ptr = 2000
        cx16.r0L = (cx16.r1 - ptr) as ubyte
        cx16.r1L = (cx16.r1 - (ptr as uword)) as ubyte
        void findstr1("asdf")
        void findstr2("asdf")
    }

    sub findstr1(str haystack) -> ubyte {
        return (cx16.r3-haystack) as ubyte
    }
    sub findstr2(str haystack) -> ubyte {
        return (cx16.r3-(haystack as uword)) as ubyte
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors, writeAssembly = false) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain("6:31: unclear pointer arithmetic in expression")
        errors.errors[1] shouldContain("13:25: unclear pointer arithmetic in expression")
    }

    test("uword struct field array indexing") {
        val src="""
main {
    sub start() {
        struct List {
            uword s
            uword n
        }
        ^^List  l = ^^List : []
        l.s[2] = 42
        l.s[300] = 42
        l.n[2] = 99
        l.n[300] = 99
        l.s[cx16.r0L] = 42
        l.n[cx16.r0L] = 99
        l.s[cx16.r0L+2] = 42
        l.n[cx16.r0L+2] = 99
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("array indexing on non pointer fields give correct error messages") {
        val src="""
main {
    struct List {
        bool s
        ubyte n
        uword ptr
    }
    sub start() {
        ^^List @shared l1 = ^^List : []
        bool ss = l1.s[1]
        ubyte ub = l1.n[1]
        uword uw = l1.ptr[1]        
        l1.s[1] = 4444
        l1.n[1] = true
        l1.ptr[1] = 4444
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 8
        errors.errors[0] shouldContain "invalid assignment value"
        errors.errors[1] shouldContain "cannot array index"
        errors.errors[2] shouldContain "invalid assignment value"
        errors.errors[3] shouldContain "cannot array index"
        errors.errors[4] shouldContain "cannot array index"
        errors.errors[5] shouldContain "cannot array index"
        errors.errors[6] shouldContain "out of range"
        errors.errors[7] shouldContain "cannot assign word to byte"
    }

    test("dereferences of ptr variables mark those as used in the callgraph") {
        val src="""
main {
    struct List {
        ^^uword s
        ubyte n
        ^^List next
    }
    sub start() {
        ^^List l1 = ^^List : []  
        ^^List l2 = ^^List : []  
        l1.s[2] = 1
        l2.n=10
        
        ^^List l3 = ^^List : []
        cx16.r0L = l3.next.n        
    }
}"""

        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 10
        (st[0] as VarDecl).name shouldBe "l1"
        (st[2] as VarDecl).name shouldBe "l2"
        st[4] shouldBe instanceOf<Assignment>()
        st[5] shouldBe instanceOf<Assignment>()
        (st[6] as VarDecl).name shouldBe "l3"
        st[8] shouldBe instanceOf<Assignment>()
    }

    test("indexing pointers to structs") {
        val src="""
%import floats

main{
    struct Country {
        str name
        float population        ; millions
        uword area              ; 1000 km^2
        ubyte code
    }

    ^^Country[10] countries        ; won't be fully filled
    ubyte num_countries

    sub start() {

        thing(countries[0])
        thing(countries[1])

        ^^Country @shared fp = 9999
        countries[0] = fp
        countries[1] = fp

        thing(countries[0])
        thing(countries[1])

        countries[0] = ^^Country : ["Indonesia", 285.72, 1904, 44]
        countries[1] = ^^Country : ["Congo", 112.83, 2344, 55]

        thing(countries[0])
        thing(countries[1])

        float fl
        thing(countries[0].name)
        thing(countries[0].area)
        thingb(countries[0].code)
        fl = countries[0].population
        thing(countries[1].name)
        thing(countries[1].area)
        thingb(countries[1].code)
        fl = countries[1].population
    }
    
    sub thing(uword a) {
        a++
    }

    sub thingb(ubyte a) {
        a++
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    test("global and local pointer vars") {
        val src="""
main {
    ^^uword g_wptr

    sub start() {
        ^^uword l_wptr

        cx16.r0 = g_wptr
        cx16.r1 = g_wptr^^
        cx16.r0 = l_wptr
        cx16.r1 = l_wptr^^
    }
}"""
        compileText(VMTarget(), true, src, outputDir) shouldNotBe null
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), true, src, outputDir) shouldNotBe null
    }

    test("global struct var deref type") {
        val src="""
main {
    struct State {
        uword c
        ^^uword ptr
    }

    ^^State matchstate

    sub start() {
        cx16.r0 = matchstate.ptr
        cx16.r1 = matchstate.ptr^^
        cx16.r2 = matchstate^^.ptr^^        ; equivalent to previous
        cx16.r3 = matchstate.c
    }
}"""

        compileText(VMTarget(), true, src, outputDir) shouldNotBe null
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), true, src, outputDir) shouldNotBe null
    }

    test("local struct var deref type") {
        val src="""
main {
    struct State {
        uword c
        ^^uword ptr
    }

    sub start() {
        ^^State matchstate
        cx16.r0 = matchstate.ptr
        cx16.r1 = matchstate.ptr^^
        cx16.r2 = matchstate^^.ptr^^        ; equivalent to previous
        cx16.r3 = matchstate.c
    }
}"""

        compileText(VMTarget(), true, src, outputDir) shouldNotBe null
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), true, src, outputDir) shouldNotBe null
    }

    test("assigning pointer dereferences via memcopy") {
        val src="""
%import floats

main {
    sub start() {
        struct List {
            bool b
            word w
            float f
            ^^List next
        }

        struct Foo {
            byte bb
        }

        ^^List l1 = 2000
        ^^List l2 = 3000
        ^^Foo f1 = 4000

        l1^^ = l2^^     ; memcpy1
        l1[3] = l2^^    ; memcpy2
        l1[3]^^ = l2^^    ; memcpy3
        l1[cx16.r0] = l2^^    ; memcpy4
        l1[cx16.r0]^^ = l2^^    ; memcpy5
             
       l2^^ = l1[2]     ;memcpy6
       l2^^ = l1[2]^^   ;memcpy7
       l2^^ = l1[cx16.r0L]      ;memcpy8
       l2^^ = l1[cx16.r0L]^^   ;memcpy9

        ; TODO add more supported syntax here when they're implemented in the future
    }
}"""

        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 18
        val memcpy1 = st[8] as FunctionCallStatement
        val memcpy2 = st[9] as FunctionCallStatement
        val memcpy3 = st[10] as FunctionCallStatement
        val memcpy4 = st[11] as FunctionCallStatement
        val memcpy5 = st[12] as FunctionCallStatement
        val memcpy6 = st[13] as FunctionCallStatement
        val memcpy7 = st[14] as FunctionCallStatement
        val memcpy8 = st[15] as FunctionCallStatement
        val memcpy9 = st[16] as FunctionCallStatement

        memcpy1.target.nameInSource shouldBe listOf("sys", "memcopy")
        (memcpy1.args[0] as IdentifierReference).nameInSource shouldBe listOf("l2")
        (memcpy1.args[1] as IdentifierReference).nameInSource shouldBe listOf("l1")
        (memcpy1.args[2] as NumericLiteral).number shouldBe 13.0  // sizeof(List)

        memcpy2.target.nameInSource shouldBe listOf("sys", "memcopy")
        (memcpy2.args[0] as IdentifierReference).nameInSource shouldBe listOf("l2")
        val memcpy2value = memcpy2.args[1] as BinaryExpression
        memcpy2value.operator shouldBe "+"
        (memcpy2value.left as IdentifierReference).nameInSource shouldBe listOf("l1")
        (memcpy2value.right as NumericLiteral).number shouldBe 3.0    // just rely on pointer arithmetic later
        (memcpy2.args[2] as NumericLiteral).number shouldBe 13.0  // sizeof(List)

        (memcpy3.target isSameAs memcpy2.target) shouldBe true
        memcpy3.args.zip(memcpy2.args).all { it.first isSameAs it.second} shouldBe true

        (memcpy5.target isSameAs memcpy4.target) shouldBe true
        memcpy5.args.zip(memcpy4.args).all { it.first isSameAs it.second} shouldBe true

        (memcpy7.target isSameAs memcpy6.target) shouldBe true
        memcpy7.args.zip(memcpy6.args).all { it.first isSameAs it.second} shouldBe true

        (memcpy9.target isSameAs memcpy8.target) shouldBe true
        memcpy9.args.zip(memcpy8.args).all { it.first isSameAs it.second} shouldBe true
    }

    test("assigning pointer dereferences should be same type") {
        val src="""
%import floats

main {
    sub start() {
        struct List {
            bool b
            word w
            float f
            ^^List next
        }

        struct Foo {
            byte bb
        }

        ^^List l1 = 2000
        ^^List l2 = 3000
        ^^Foo f1 = 4000
        ^^bool bptr = 5000

        l1^^ = f1^^
        l1^^ = bptr^^
        l1^^ = 4242
    }
}"""

        val errors=ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors)
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "struct instance by value"
        errors.errors[1] shouldContain "doesn't match"
        errors.errors[2] shouldContain "assigning this value to struct instance not supported"
        errors.errors[3] shouldContain "assigning this value to struct instance not supported"
    }

    xtest("assigning struct instances") {
        val src="""
main {
    sub start() {
        struct List {
            bool b
            uword value
            float fv
        }   ; sizeof = 11

        ^^List lp1 = 10000
        ^^List lp2 = 20000

        lp2^^ = lp1^^           ; memcopy(lp1, lp2, 11)
        lp2[2] = lp1^^          ; memcopy(lp1, lp2 + 22, 11)
        lp2[2]^^ = lp1^^        ; memcopy(lp1, lp2 + 22, 11)  (same as above)  TODO fix astchecker to allow this case
        lp2^^ = lp1[2]         ; memcopy(lp1 + 22, lp2, 11)
        lp2^^ = lp1[2]^^       ; memcopy(lp1 + 22, lp2, 11)  (same as above)   TODO fix astchecker to allow this case
        lp2[3] = lp1[2]        ; memcopy(lp1 + 22, lp2 + 33, 11)  TODO fix astchecker to allow this case
    }
}"""
        val errors = ErrorReporterForTests()
        val result = compileText(VMTarget(), false, src, outputDir, errors=errors)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 99
    }

    test("a.b.c[i]^^.value as expression where pointer is struct") {
        val src="""
main {
    sub start() {
        cx16.r0 = other.foo.listarray[2].value
        cx16.r1 = other.foo.listarray[3]^^.value
        other.foo()
    }
}

other {
    sub foo() {
        struct List {
            bool b
            uword value
        }

        ^^List[10] listarray
        cx16.r0 = listarray[2].value
        cx16.r1 = listarray[3]^^.value
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("a.b.c[i]^^ as expression where pointer is primitive type") {
        val src="""
%import floats

main {
    sub start() {
        float @shared f2 = other.foo.fptrarray[3]^^
        cx16.r1s = other.foo.wptrarray[3]^^
        cx16.r2L = other.foo.ptrarray[3]^^
        cx16.r3bL = other.foo.bptrarray[3]^^
        other.foo()
    }
}

other {
    sub foo() {
        ^^word[10] wptrarray
        ^^float[10] fptrarray
        ^^ubyte[10] ptrarray
        ^^bool[10] bptrarray
        float @shared f1 = fptrarray[3]^^
        cx16.r1s = wptrarray[3]^^
        cx16.r2L = ptrarray[3]^^
        cx16.r3bL = bptrarray[3]^^
    }
}
"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("support for assigning to indexed pointers") {
        val src="""
main {

    sub start() {
        sprptr[2]^^.y = 99
        pokew(sprptr as uword + (sizeof(Sprite) as uword)*2 + offsetof(Sprite.y), 99)
        sprptr[cx16.r0L]^^.y = 99
        pokew(sprptr as uword + (sizeof(Sprite) as uword)*cx16.r0L + offsetof(Sprite.y), 99)

        sprites[2]^^.y = 99
        pokew(sprites[2] as uword + offsetof(Sprite.y), 99)
        sprites[cx16.r0L]^^.y = 99
        pokew(sprites[cx16.r0L] as uword + offsetof(Sprite.y), 99)
    }

    struct Sprite {
        ubyte x
        uword y
    }

    ^^Sprite[4] @shared sprites
    ^^Sprite @shared sprptr
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    xtest("array indexed assignment parses with and without explicit dereference after struct pointer [IGNORED because it's a parser error right now]") {
        val src="""
main {

    sub start() {
        struct Node {
            ^^uword s
        }

        ^^Node l1

        l1.s[0] = 4242
        l1^^.s[0] = 4242        ; TODO fix parse error
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), false, src, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "no support for"
        errors.errors[1] shouldContain "no support for"
    }

    xtest("a.b.c[i].value = X where pointer is struct gives good error message [IGNORED because it's a parser error right now]") {
        val src="""
main {
    sub start() {
        other.foo.listarray[2].value = cx16.r0
        other.foo()
    }
}

other {
    sub foo() {
        struct List {
            bool b
            uword value
        }

        ^^List[10] listarray
        listarray[2].value = cx16.r0
    }
}"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), false, src, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "no support for"
        errors.errors[1] shouldContain "no support for"
    }

    test("a.b.c[i]^^ as assignment target where pointer is primitive type") {
        val src="""
%import floats

main {
    sub start() {
        float @shared f2
        other.foo.fptrarray[3]^^ = f2
        other.foo.wptrarray[3]^^ = cx16.r1s
        other.foo.ptrarray[3]^^ = cx16.r2L
        other.foo.bptrarray[3]^^ = cx16.r3bL
        other.foo()
    }
}

other {
    sub foo() {
        ^^word[10] wptrarray
        ^^float[10] fptrarray
        ^^ubyte[10] ptrarray
        ^^bool[10] bptrarray
        float @shared f1
        fptrarray[3]^^ = f1
        wptrarray[3]^^ = cx16.r1s
        ptrarray[3]^^ = cx16.r2L
        bptrarray[3]^^ = cx16.r3bL
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("passing arrays to subroutines via typed pointer parameters") {
        val src="""
%import floats

main {
    struct Node {
        bool bb
        float f
        word w
        ^^Node next
    }

    sub start() {
        ^^Node[5] node_array
        node_array [0] = node_array[1] = node_array[2] = node_array[3] = node_array[4] = 7777
        bool[5] bool_array = [true, true, true, false, false]
        word[5] @nosplit word_array = [-1111,-2222,3333,4444,5555]          ; has to be nosplit
        float[5] float_array = [111.111,222.222,333.333,444.444,555.555]

        modifyb(bool_array, 2)
        modifyw(word_array, 2)
        modifyf(float_array, 2)
    }

    sub modifyb(^^bool array, ubyte index) {
        array[index] = false
    }

    sub modifyw(^^word array, ubyte index) {
        array[index] = 9999
    }

    sub modifyf(^^float array, ubyte index) {
        array[index] = 9999.999
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("array of pointers as subroutine param are all passed as ^^ubyte because of split word arrays") {
        val src="""
%import floats

main {

    sub start() {
        ^^ubyte[4] array1
        ^^byte[4] array2
        ^^bool[4] array3
        ^^word[4] array4
        ^^uword[4] array5
        ^^float[4] array6
        ^^long[4] array7

        ok(array1)
        ok(array2)
        ok(array3)
        ok(array4)
        ok(array5)
        ok(array6)
        ok(array7)
    }

    sub ok(^^ubyte ptr) {
        cx16.r0 = ptr
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("str replaced by ^^ubyte in subroutine args and return type") {
        val src="""
main {

    sub start() {
        void test("hello")
    }

    sub test(str argument) -> str {
        argument++
        return "bye"
    }
}"""
        val vmprg = compileText(VMTarget(), false, src, outputDir)!!
        val vmtest = vmprg.codegenAst!!.allBlocks().first { it.name == "main" }.children[1] as PtSub
        vmtest.signature.returns.single() shouldBe DataType.pointer(BaseDataType.UBYTE)
        (vmtest.signature.children.single() as PtSubroutineParameter).type shouldBe DataType.pointer(BaseDataType.UBYTE)
        val vminc = vmtest.children[2] as PtAugmentedAssign
        vminc.operator shouldBe "+="
        vminc.target.identifier!!.name shouldBe "main.test.argument"
        vminc.target.identifier!!.type shouldBe DataType.pointer(BaseDataType.UBYTE)
        (vminc.value as PtNumber).number shouldBe 1.0

        val c64prg = compileText(C64Target(), false, src, outputDir)!!
        val c64test = c64prg.codegenAst!!.allBlocks().first { it.name == "p8b_main" }.children[1] as PtSub
        c64test.signature.returns.single() shouldBe DataType.pointer(BaseDataType.UBYTE)
        (c64test.signature.children.single() as PtSubroutineParameter).type shouldBe DataType.pointer(BaseDataType.UBYTE)
        val c64inc = c64test.children[2] as PtAugmentedAssign
        c64inc.operator shouldBe "+="
        c64inc.target.identifier!!.name shouldBe "p8b_main.p8s_test.p8v_argument"
        c64inc.target.identifier!!.type shouldBe DataType.pointer(BaseDataType.UBYTE)
        (c64inc.value as PtNumber).number shouldBe 1.0
    }

    test("hoist variable decl and initializer correctly in case of pointer type variable as well") {
        val src="""
main {

    sub start() {
        stuff()
        if true {
            stuff()
            ^^bool @shared successor = find_successor()     ; testcase here
        }
        stuff()

        sub find_successor() -> uword {
            stuff()
            cx16.r0++
            return 0
        }
    }
    
    sub stuff() {
    }
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 6
        st[0] shouldBe instanceOf<VarDecl>()
        (st[1] as FunctionCallStatement).target.nameInSource shouldBe listOf("stuff")
        st[2] shouldBe instanceOf<IfElse>()
        (st[3] as FunctionCallStatement).target.nameInSource shouldBe listOf("stuff")
        st[4] shouldBe instanceOf<Return>()
        st[5] shouldBe instanceOf<Subroutine>()
    }

    test("initialize struct with string fields") {
        val src="""
main {
    struct Node {
        str question
        str animal
    }

    sub start() {
        ^^Node n = ^^Node : ["question string", "animal name string"]
        ^^ubyte @shared q = n.question
        ^^ubyte @shared a = n.animal
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("struct initializers in array") {
        val src="""
%option enable_floats
main {
    struct Node {
        ubyte id
        str name
        uword array
        bool flag
        float speed
    }

    sub start() {
        ^^Node[] @shared nodes = [
            ^^Node:[1,"one", 1000, true, 1.111 ],
            ^^Node:[2,"two", 2000, false, 2.222 ],
            ^^Node:[3,"three", 3000, true, 3.333 ],
            ^^Node:[],
            ^^Node:[],
            ^^Node:[],
        ]
    }
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
    }

    test("type error for invalid field initializer") {
        val src="""
main {
    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }

    sub start() {
        ^^Enemy @shared e1 = ^^Enemy: []
        ^^Enemy @shared e2 = ^^Enemy: [1,2,3,true]
        ^^Enemy @shared e3 = ^^Enemy: [1,2,3,4]      
        ^^Enemy @shared e4 = ^^Enemy: [1,2,3,4.444]  
        
        e3.elite = 99
        e4.elite = 3.444
    }
}"""

        val errors=ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "value #4 has incompatible type"
        errors.errors[1] shouldContain "value #4 has incompatible type"
        errors.errors[2] shouldContain "doesn't match target type"
        errors.errors[3] shouldContain "doesn't match target type"
    }

    test("long and short form struct initializers") {
        val src="""
%option enable_floats

main {
    struct Node {
        ubyte id
        str name
        uword array
        bool flag
        float perc
    }

    sub start() {
        ^^Node[] @shared nodeswithtype = [
            ^^Node: [1,"one", 1000, true, 1.111],
            ^^Node: [],
        ]

        ^^Node[] @shared nodeswithout = [
            [2,"two", 2000, false, 2.222],
            [],
        ]

        ^^Node @shared nptrwithtype = ^^Node : [1, "one", 1000, false, 3.333]
        ^^Node @shared nptrwithouttype = [1, "one", 1000, false, 3.333]
    }
}"""
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
    }

    test("type error for wrong type in pointer array and assignment") {
        val src="""
main {
    struct Node {
        ubyte id
    }
    struct Foobar {
        bool thing
    }

    sub start() {
        ^^Node[] onlynodes = [
            ^^Node: [],
            ^^Foobar: []
        ]
        
        uword multipleok = [
            ^^Node: [],
            ^^Foobar: []
        ]        
        
        ^^Node node = ^^Foobar: []
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(C64Target(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 6
        errors.errors[0] shouldContain "11:30: initialization value for pointer array"
        errors.errors[1] shouldContain "11:30: undefined array type"        // a bit redundant but can't be helped
        errors.errors[2] shouldContain "13:13: struct initializer element has invalid type"
        errors.errors[3] shouldContain "16:28: invalid assignment value"
        errors.errors[4] shouldContain "16:28: undefined array type"
        errors.errors[5] shouldContain "21:23: cannot assign different pointer type"
    }

    test("local and global struct pointer qualified name lookups") {
        val src="""
main {
    struct Node {
        str question
        str animal
        ^^Node negative
        ^^Node positive
    }

    ^^Node @shared first

    sub start() {
        cx16.r1 = first
        cx16.r2 = first.negative
        cx16.r3 = first^^.negative
        cx16.r4 = first.negative.animal
        cx16.r5 = first^^.negative^^.animal

        cx16.r1 = db.first
        cx16.r2 = db.first.negative

        cx16.r3 = db.first^^.negative
        cx16.r4 = db.first.negative.animal
        cx16.r5 = db.first^^.negative^^.animal

        db.first.negative.animal = 0
        db.first.negative = 0
        db.first = 0

        func(db.first)
        func(db.first.negative)
        func(db.first.negative.animal)
    }

    sub func(uword arg) {
        cx16.r0 = arg
    }
}

db {
    struct Node {
        str question
        str animal
        ^^Node negative
        ^^Node positive
    }

    ^^Node @shared first
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("str can be used without explicit cast where ^^ubyte is expected") {
        val src="""
main {
    sub start() {
        str name = "pjotr"
        ^^ubyte @shared ptr = name
        ptr = "hello"
        func(name)
        func("bye")
    }

    sub func(^^ubyte arg) {
        cx16.r0++
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 7
        (st[2] as Assignment).value shouldBe instanceOf<AddressOf>()
        (st[3] as Assignment).value shouldBe instanceOf<AddressOf>()
        (st[4] as FunctionCallStatement).args.single() shouldBe instanceOf<AddressOf>()
        (st[5] as FunctionCallStatement).args.single() shouldBe instanceOf<AddressOf>()
    }

    test("initializing arrays of pointers") {
        val src="""
%import floats

main {

    sub start() {
        ^^ubyte[4] array1 = [1000, 1100, 1200, 1300]
        ^^byte[4] array2 = [1000, 1100, 1200, 1300]
        ^^bool[4] array3 = [1000, 1100, 1200, 1300]
        ^^word[4] array4 = [1000, 1100, 1200, 1300]
        ^^uword[4] array5 = [1000, 1100, 1200, 1300]
        ^^float[4] array6 = [1000, 1100, 1200, 1300]
        ^^long[4] array7 = [1000, 1100, 1200, 1300]

        cx16.r0 = array1[2]
        cx16.r1 = array2[2]
        cx16.r2 = array3[2]
        cx16.r3 = array4[2]
        cx16.r4 = array5[2]
        cx16.r5 = array6[2]
        cx16.r6 = array7[2]
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("array indexing a pointer and a pointer array both work") {
        val src="""
main {
    struct Node {
        ubyte weight
    }

    sub start() {
        ^^Node nodes
        ^^Node[5] nodesarray

        cx16.r0L = nodesarray[2].weight
        cx16.r0L = nodes[2].weight
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("array indexing on a pointer with a word size index works") {
        val src="""
%import floats

main {
   sub start() {
        ^^ubyte @shared ptr1 = $4000
        ^^uword @shared ptr2 = $4000
        ^^float @shared ptr3 = $4000
        ^^bool @shared ptr4 = $4000
        uword @shared untyped = $4000
        float @shared fl
        bool @shared bb, bb2

        untyped[$1000] = 0
        ptr1[$1000] = 0
        ptr2[$1000] = 0
        ptr3[$1000] = 0
        ptr4[$1000] = false
        untyped[$1000] = 99
        ptr1[$1000] = 99
        ptr2[$1000] = 99
        ptr3[$1000] = 99
        ptr4[$1000] = true
        untyped[$1000] = cx16.r0L
        ptr1[$1000] = cx16.r0L
        ptr2[$1000] = cx16.r0L
        ptr3[$1000] = fl
        ptr4[$1000] = bb
        untyped[$1000] = cx16.r0L+1
        ptr1[$1000] = cx16.r0L+1
        ptr2[$1000] = cx16.r0L+1
        ptr3[$1000] = fl+1.1
        ptr4[$1000] = bb xor bb2

        untyped[$1000 + cx16.r0] = 0
        ptr1[$1000 + cx16.r0] = 0
        ptr2[$1000 + cx16.r0] = 0
        ptr3[$1000 + cx16.r0] = 0
        ptr4[$1000 + cx16.r0] = false
        untyped[$1000 + cx16.r0] = 99
        ptr1[$1000 + cx16.r0] = 99
        ptr2[$1000 + cx16.r0] = 99
        ptr3[$1000 + cx16.r0] = 99
        ptr4[$1000 + cx16.r0] = true
        untyped[$1000 + cx16.r0] = cx16.r0L
        ptr1[$1000 + cx16.r0] = cx16.r0L
        ptr2[$1000 + cx16.r0] = cx16.r0L
        ptr3[$1000 + cx16.r0] = fl
        ptr4[$1000 + cx16.r0] = bb
        untyped[$1000 + cx16.r0] = cx16.r0L+1
        ptr1[$1000 + cx16.r0] = cx16.r0L+1
        ptr2[$1000 + cx16.r0] = cx16.r0L+1
        ptr3[$1000 + cx16.r0] = fl+1.1
        ptr4[$1000 + cx16.r0] = bb xor bb2

        cx16.r0L = untyped[$1000]
        cx16.r1L = ptr1[$1000]
        cx16.r2 = ptr2[$1000]
        fl = ptr3[$1000]
        bb = ptr4[$1000]
        cx16.r0L = untyped[cx16.r0]
        cx16.r1L = ptr1[cx16.r0]
        cx16.r2 = ptr2[cx16.r0]
        fl = ptr3[cx16.r0]
        bb = ptr4[cx16.r0]
        cx16.r0L = untyped[cx16.r0+1]
        cx16.r1L = ptr1[cx16.r0+1]
        cx16.r2 = ptr2[cx16.r0+1]
        fl = ptr3[cx16.r0+1]
        bb = ptr4[cx16.r0+1]
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    test("correct type of address of split and nosplit arrays") {
        val src="""
main {
    sub start() {
        uword[10] @split @shared splitarray
        uword[10] @nosplit @shared nosplitarray

        ^^uword ptr1 = &&splitarray
        ^^ubyte ptr2 = &&splitarray
        ^^uword ptr3 = &&nosplitarray
        ^^ubyte ptr4 = &&nosplitarray
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors = errors, writeAssembly = false) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "7:24: cannot assign different pointer type, expected ^^uword or uword but got ^^ubyte"
        errors.errors[1] shouldContain "10:24: cannot assign different pointer type, expected ^^ubyte or uword but got ^^uword"
    }

    test("passing nosplit array of structpointers to a subroutine in various forms should be param type ptr to struct") {
        val src="""
main {
    struct Node {
        ubyte weight
    }

    sub start() {
        ^^Node[10] @nosplit nodearray       ; not actually possible to store this array but required for the address-ofs below
        func(nodearray[0])
        func(&&nodearray)   ; error because datatype internally is registered as a split pointer array
        func(&nodearray)
        func(nodearray)     ; error because datatype internally is registered as a split pointer array
    }

    sub func(^^Node n) {
        cx16.r0++
    }
}"""

        val errors=ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), false, src, outputDir, errors=errors, writeAssembly = false) shouldBe null
        errors.errors.size shouldBe 3
        errors.warnings.size shouldBe 0
        errors.infos.size shouldBe 0
        errors.errors[0] shouldContain "pointer arrays can only be @split"
        errors.errors[1] shouldContain "was: ^^ubyte (because arg is a @split word array) expected: ^^main.Node"
        errors.errors[2] shouldContain "was: ^^ubyte (because arg is a @split word array) expected: ^^main.Node"
    }

    test("passing split array of structpointers to a subroutine in various forms should be param type ptr to ubyte (the lsb part of the split array)") {
        val src="""
main {
    struct Node {
        ubyte weight
    }

    sub start() {
        ^^Node[10] @split nodearray
        func(&&nodearray)
        func(&nodearray)
        func(nodearray)
    }

    sub func(^^ubyte n) {
        cx16.r0++
    }
}"""
        val errors=ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), false, src, outputDir, errors=errors) shouldNotBe null
        val result = compileText(VMTarget(), false, src, outputDir, errors=errors)
        errors.errors.size shouldBe 0
        errors.warnings.size shouldBe 0
        errors.infos.size shouldBe 0
        val st = result!!.codegenAst!!.entrypoint()!!.children
        st.size shouldBe 6
        val f1a = (st[2] as PtFunctionCall).args[0]
        val f2a = (st[3] as PtFunctionCall).args[0]
        val f3a = (st[4] as PtFunctionCall).args[0]
        f1a.type shouldBe DataType.pointer(BaseDataType.UBYTE)
        f2a.type shouldBe DataType.UWORD
        f3a.type shouldBe DataType.pointer(BaseDataType.UBYTE)
    }

    test("pointer cannot be used in conditional expression in shorthand form") {
        val src="""
main {
    sub start() {
        ^^word ptr

        if ptr cx16.r0++
        if not ptr cx16.r1++

        while ptr cx16.r0++
        while not ptr cx16.r1++

        do cx16.r0++ until ptr
        do cx16.r1++ until not ptr

        cx16.r0 = if ptr 1 else 0
        cx16.r1 = if not ptr 1 else 0
    }
}"""

        val errors=ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 8
        errors.errors[0] shouldContain "condition should be a boolean"
        errors.errors[1] shouldContain "pointers don't support prefix operators"
        errors.errors[2] shouldContain "condition should be a boolean"
        errors.errors[3] shouldContain "pointers don't support prefix operators"
        errors.errors[4] shouldContain "condition should be a boolean"
        errors.errors[5] shouldContain "pointers don't support prefix operators"
        errors.errors[6] shouldContain "condition should be a boolean"
        errors.errors[7] shouldContain "pointers don't support prefix operators"
    }

    test("pointers in if expressions") {
        val src="""
main {
    sub start() {
        ^^word ptr

        if ptr!=0
            cx16.r0++
        if ptr==0
            cx16.r0++

        cx16.r0 = if ptr!=0 0 else ptr
        cx16.r1 = if ptr==0 0 else ptr
        cx16.r2 = if ptr!=0 ptr else 0
        cx16.r3 = if ptr==0 ptr else 0
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("boolean field in if statement condition") {
        val src = """
main {
    struct Enemy {
        ubyte xpos, ypos
        uword health
        bool elite
    }
     
    sub start() {
        ^^Enemy e1
        if e1.elite
            e1.health += 100
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("^^str is not valid") {
        val src="""
main {
    sub start() {
        str name = "test"
        ^^str ptr = &name
        ^^str[4] array
        ptr = foo(&name)
    }

    sub foo(^^str arg) -> ^^str {
        return arg+2
    }
}"""
        val errors=ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors, writeAssembly = false) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "^^str is not a valid pointer type"
        errors.errors[1] shouldContain "^^str is not a valid pointer type"
        errors.errors[2] shouldContain "^^str is not a valid pointer type"
        errors.errors[3] shouldContain "^^str is not a valid return type"
    }

    test("various miscellaneous pointer syntax tests") {
        val src="""
main {
    struct Node {
        ubyte weight
    }
    ^^Node @shared n1, n2
    ^^bool @shared bptr1, bptr2

    sub start() {
        ^^Node nodes
        n1^^=n2^^               ; ok
        bptr1^^=bptr2^^         ; ok
        n1^^=nodes[2]           ; ok
        n2 = nodes[2]^^         ; cannot assign instance to pointer like this yet
  }
}"""
        val errors=ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "struct instance by value"
        errors.errors[1] shouldContain "no support for getting the target value of pointer array indexing"
    }

    test("pointer variable usage detection in other block 1") {
        val src="""
main {
    sub start() {
        other.bptr^^ = true
        cx16.r0bL = other.bptr^^
    }
}

other {
    ^^bool bptr
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(VMTarget(), true, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("pointer variable usage detection in other block 2") {
        val src="""
main {
    sub start() {
        other.func.variable^^ += 3
    }
}

other {
    sub func() {
        ^^ubyte variable
    }
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(VMTarget(), true, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("float ptr inplace operations") {
        val src="""
%import floats

main {
    ^^float g_floats

    sub start() {
        ^^float l_floats

        f_add()
        f_sub()
        f_mul()
        f_div()

        sub f_add() {
            l_floats^^ += 3.0
            g_floats^^ += 3.0
            other.g_floats^^ += 3.0
            other.func.l_floats^^ += 3.0
        }

        sub f_sub() {
            l_floats^^ -= 3.0
            g_floats^^ -= 3.0
            other.g_floats^^ -= 3.0
            other.func.l_floats^^ -= 3.0
        }

        sub f_mul() {
            l_floats^^ *= 3.0
            g_floats^^ *= 3.0
            other.g_floats^^ *= 3.0
            other.func.l_floats^^ *= 3.0
        }
        
        sub f_div() {
            l_floats^^ /= 3.0
            g_floats^^ /= 3.0
            other.g_floats^^ /= 3.0
            other.func.l_floats^^ /= 3.0
        }
    }
}

other {
    %option force_output

    ^^float g_floats

    sub func() {
        ^^float l_floats
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
        compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
    }

    test("pointer comparisons against a value") {
        val src="""
%option enable_floats
%zeropage basicsafe

main {
    struct Node {
        bool flag
    }

    ^^ubyte @shared ptr1
    ^^bool @shared ptr2
    ^^uword @shared ptr3
    ^^float @shared ptr4
    ^^Node @shared ptr5

    sub start() {
        bool bb1,bb2

        bb1 = ptr1 == 5000 
        bb2 = ptr1 != 5000 
        bb1 = ptr2 == 5000
        bb2 = ptr2 != 5000
        bb1 = ptr3 == 5000
        bb2 = ptr3 != 5000
        bb1 = ptr4 == 5000
        bb2 = ptr4 != 5000
        bb1 = ptr5 == 5000
        bb2 = ptr5 != 5000

        bb1 = ptr1 < 5000
        bb2 = ptr1 > 5000
        bb1 = ptr2 < 5000
        bb2 = ptr2 > 5000
        bb1 = ptr3 < 5000
        bb2 = ptr3 > 5000
        bb1 = ptr4 < 5000
        bb2 = ptr4 > 5000
        bb1 = ptr5 < 5000
        bb2 = ptr5 > 5000

        bb1 = ptr1 == cx16.r0
        bb2 = ptr1 != cx16.r0
        bb1 = ptr2 == cx16.r0
        bb2 = ptr2 != cx16.r0
        bb1 = ptr3 == cx16.r0
        bb2 = ptr3 != cx16.r0
        bb1 = ptr4 == cx16.r0
        bb2 = ptr4 != cx16.r0
        bb1 = ptr5 == cx16.r0
        bb2 = ptr5 != cx16.r0

        bb1 = ptr1 < cx16.r0
        bb2 = ptr1 > cx16.r0
        bb1 = ptr2 < cx16.r0
        bb2 = ptr2 > cx16.r0
        bb1 = ptr3 < cx16.r0
        bb2 = ptr3 > cx16.r0
        bb1 = ptr4 < cx16.r0
        bb2 = ptr4 > cx16.r0
        bb1 = ptr5 < cx16.r0
        bb2 = ptr5 > cx16.r0

        bb1 = ptr1 == cx16.r0+10
        bb2 = ptr1 != cx16.r0+10
        bb1 = ptr2 == cx16.r0+10
        bb2 = ptr2 != cx16.r0+10
        bb1 = ptr3 == cx16.r0+10
        bb2 = ptr3 != cx16.r0+10
        bb1 = ptr4 == cx16.r0+10
        bb2 = ptr4 != cx16.r0+10
        bb1 = ptr5 == cx16.r0+10
        bb2 = ptr5 != cx16.r0+10

        bb1 = ptr1 < cx16.r0+10
        bb2 = ptr1 > cx16.r0+10
        bb1 = ptr2 < cx16.r0+10
        bb2 = ptr2 > cx16.r0+10
        bb1 = ptr3 < cx16.r0+10
        bb2 = ptr3 > cx16.r0+10
        bb1 = ptr4 < cx16.r0+10
        bb2 = ptr4 > cx16.r0+10
        bb1 = ptr5 < cx16.r0+10
        bb2 = ptr5 > cx16.r0+10
        
        if ptr1 == 5000  cx16.r0++
        if ptr1 != 5000  cx16.r0++
        if ptr3 == 5000  cx16.r0++
        if ptr3 != 5000  cx16.r0++
        if ptr1 > 5000  cx16.r0++
        if ptr3 > 5000  cx16.r0++

        if ptr1 == cx16.r0  cx16.r0++
        if ptr1 != cx16.r0  cx16.r0++
        if ptr3 == cx16.r0  cx16.r0++
        if ptr3 != cx16.r0  cx16.r0++
        if ptr1 > cx16.r0  cx16.r0++
        if ptr3 > cx16.r0  cx16.r0++

        if ptr1 == cx16.r0+10  cx16.r0++
        if ptr1 != cx16.r0+10  cx16.r0++
        if ptr3 == cx16.r0+10  cx16.r0++
        if ptr3 != cx16.r0+10  cx16.r0++
        if ptr1 > cx16.r0+10  cx16.r0++
        if ptr3 > cx16.r0+10  cx16.r0++        
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
        compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    test("struct and pointer aliasing") {
        val src="""
main {
    sub start() {
        alias1()
        alias2()
        alias3()
        alias4()
        alias5()
    }

    sub alias1() {
        alias TheNode = structdefs.Node
        ^^TheNode node = 20000
        node.value = 100
    }

    sub alias2() {
        ^^structdefs.Node node = 20000
        alias thing = node
        thing.value=200
    }

    sub alias3() {
        alias TheNode = structdefs.Node
        ^^TheNode node = 20000
        node++
    }

    sub alias4() {
        alias currentElement = structdefs.element
        currentElement = 20000

        ; all 3 should be the same:
        structdefs.element.value = 42
        currentElement.value = 42
        currentElement^^.value = 42

        ; all 3 should be the same:
        structdefs.element.value2 = 4242
        currentElement.value2 = 4242
        currentElement^^.value2 = 4242

        cx16.r0 = currentElement^^.value2
        cx16.r1 = currentElement.value2
    }
    
    sub alias5() {
        alias nid = structdefs.element.value
        nid++
    }
}

structdefs {
    struct Node {
        ubyte value
        uword value2
    }

    ^^Node @shared element
}
"""

        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.compilerAst.allBlocks.single{it.name == "main"}
        val alias4 = (st.statements[4] as Subroutine).statements
        alias4.size shouldBe 10
        val a1t = (alias4[1] as Assignment).target
        val a2t = (alias4[2] as Assignment).target
        val a3t = (alias4[3] as Assignment).target
        val a4t = (alias4[4] as Assignment).target
        val a5t = (alias4[5] as Assignment).target
        val a6t = (alias4[6] as Assignment).target
        a1t.pointerDereference!!.chain shouldBe listOf("structdefs", "element", "value")
        a2t.pointerDereference!!.chain shouldBe listOf("structdefs", "element", "value")
        a3t.pointerDereference!!.chain shouldBe listOf("structdefs", "element", "value")
        a4t.pointerDereference!!.chain shouldBe listOf("structdefs", "element", "value2")
        a5t.pointerDereference!!.chain shouldBe listOf("structdefs", "element", "value2")
        a6t.pointerDereference!!.chain shouldBe listOf("structdefs", "element", "value2")
    }

    test("assigning field with same name should not confuse compiler") {
        val src="""
main {

    sub start() {
        ubyte @shared ok = sprites[2].y    ; this one is fine...
        ubyte @shared y = sprites[2].y     ; this used to crash
    }

    struct Sprite {
        uword x
        ubyte y
    }

    ^^Sprite[4] @shared sprites
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("0-indexed optimizations") {
        val src="""
main {
    struct Sprite {
        uword x
        ubyte y
    }

    ^^Sprite[4] @shared sprites
    ^^Sprite @shared sprptr

    sub start() {
        sprptr.y = 99
        sprptr[0]^^.y = 99
        ;; sprites[0]^^.y = 99     ; no change here.    TODO: this syntax doesn't compile yet...
        cx16.r0 = &sprptr[0]

        cx16.r2L = sprptr.y
        cx16.r0L = sprptr[0].y
        cx16.r1L = sprites[0].y     ; no change here, need first array element
        cx16.r0 = sprites[0]        ; no change here, need first array element
        cx16.r0 = sprites[0]        ; no change here, need first array element
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 8
        val a1 = st[0] as Assignment
        val a2 = st[1] as Assignment
        val a3 = st[2] as Assignment
        val a4 = st[3] as Assignment
        val a5 = st[4] as Assignment
        val a6 = st[5] as Assignment
        val a7 = st[6] as Assignment

        a1.target.arrayIndexedDereference shouldBe null
        a1.target.pointerDereference!!.chain shouldBe listOf("sprptr", "y")
        a2.target.arrayIndexedDereference shouldBe null
        a2.target.pointerDereference!!.chain shouldBe listOf("sprptr", "y")

        (a3.value as? AddressOf)?.identifier?.nameInSource shouldBe listOf("sprptr")
        (a4.value as? PtrDereference)?.chain shouldBe listOf("sprptr", "y")
        (a5.value as? PtrDereference)?.chain shouldBe listOf("sprptr", "y")
        val be6 = a6.value as BinaryExpression      // this one is an actual array and we need the first element so no change here
        be6.operator shouldBe "."
        be6.left shouldBe instanceOf<ArrayIndexedExpression>()
        be6.right shouldBe instanceOf<IdentifierReference>()
        (a7.value as? ArrayIndexedExpression)?.indexer?.constIndex() shouldBe 0
    }
})