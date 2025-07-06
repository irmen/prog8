package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.ArrayIndexedExpression
import prog8.ast.expressions.DirectMemoryRead
import prog8.ast.expressions.PtrDereference
import prog8.ast.statements.*
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

    test("basic pointers") {
        val src="""
%option enable_floats

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
        // TODO compileText(C64Target(), false, src, outputDir) shouldNotBe null
    }

    test("struct pointers") {
        val src="""
%option enable_floats

main {
    struct Node {
        bool bb
        float f
        word w
        ^^Node next
    }

    sub start() {
        ^^Node n1 = Node(false, 1.1, 1111, 0)
        ^^Node n2 = Node(false, 2.2, 2222, 0)
        ^^Node n3 = Node(true, 3.3, 3333, 0)

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
        // TODO compileText(C64Target(), false, src, outputDir) shouldNotBe null
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
%option enable_floats

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
        listptr2 = listptr[999]        
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 28

        val a_zz = (st[20] as Assignment).value
        a_zz shouldBe instanceOf<ArrayIndexedExpression>()
        val a_fl = (st[21] as Assignment).value
        a_fl shouldBe instanceOf<ArrayIndexedExpression>()
        val a_bb = (st[22] as Assignment).value
        a_bb shouldBe instanceOf<ArrayIndexedExpression>()
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

        override fun sameas(other: ISubType): Boolean {
            return other is Struct && other.scopedNameString == scopedNameString
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

        DataType.pointer(Struct("struct")).typeForAddressOf(false) shouldBe DataType.UWORD
        DataType.pointerFromAntlr(listOf("struct")).typeForAddressOf(false) shouldBe DataType.UWORD

        DataType.pointer(BaseDataType.BOOL).typeForAddressOf(false) shouldBe DataType.UWORD
    }

    test("address-of struct fields") {
        val src="""
%option enable_floats
%import textio

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
        cx16.r4 = &l1.next.s
        cx16.r5 = &l1.next.n
        cx16.r6 = &l1.next.f
        cx16.r7 = &l1.next.b
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
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

    test("array indexing on non pointer fields give correct error messages") {
        val src="""
main {
    struct List {
        bool s
        ubyte n
        uword ptr
    }
    sub start() {
        ^^List @shared l1 = List()
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
        ^^List l1 = List()  
        ^^List l2 = List()  
        l1.s[2] = 1
        l2.n=10
        
        ^^List l3 = List()
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

    test("indexing pointers with index 0 is just a direct pointer dereference") {
        val src="""
%option enable_floats
main {
    struct List {
        ^^uword s
        ubyte n
    }
    sub start() {
        ^^List l1 = List()
        ^^word @shared wptr
        ^^float @shared fptr
        float f1,f2

        cx16.r0 = l1.s^^
        cx16.r1 = l1^^.s^^
        cx16.r2 = l1.s^^
        cx16.r3 = l1.s[0]
        cx16.r4 = l1^^.s[0]

        l1.s^^ = 4242
        l1^^.s^^ = 4242
        l1.s^^ = 4242
        l1.s[0] = 4242
        ;; l1^^.s[0] = 4242        ; TODO fix parse syntax error

        cx16.r0s = wptr[0]
        cx16.r1s = wptr^^
        wptr^^ = 4242
        wptr[0] = 4242

        f1 = fptr^^
        f2 = fptr[0]
        fptr^^ = 1.234
        fptr[0] = 1.234
    }
}"""

        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 28
        val dr0 = (st[10] as Assignment).value as PtrDereference
        val dr1 = (st[11] as Assignment).value as PtrDereference
        val dr2 = (st[12] as Assignment).value as PtrDereference
        val dr3 = (st[13] as Assignment).value as PtrDereference
        val dr4 = (st[14] as Assignment).value as PtrDereference

        val dr5 = (st[15] as Assignment).target.pointerDereference!!
        val dr6 = (st[16] as Assignment).target.pointerDereference!!
        val dr7 = (st[17] as Assignment).target.pointerDereference!!
        val dr8 = (st[18] as Assignment).target.pointerDereference!!

        val dr9 = (st[19] as Assignment).value as PtrDereference
        val dr10 = (st[20] as Assignment).value as PtrDereference
        val dr11 = (st[21] as Assignment).target.pointerDereference!!
        val dr12 = (st[22] as Assignment).target.pointerDereference!!

        val dr13 = (st[23] as Assignment).value as PtrDereference
        val dr14 = (st[24] as Assignment).value as PtrDereference
        val dr15 = (st[25] as Assignment).target.pointerDereference!!
        val dr16 = (st[26] as Assignment).target.pointerDereference!!

        dr0.chain shouldBe listOf("l1", "s")
        dr0.derefLast shouldBe true
        dr1.chain shouldBe listOf("l1", "s")
        dr1.derefLast shouldBe true
        dr2.chain shouldBe listOf("l1", "s")
        dr2.derefLast shouldBe true
        dr3.chain shouldBe listOf("l1", "s")
        dr3.derefLast shouldBe true
        dr4.chain shouldBe listOf("l1", "s")
        dr4.derefLast shouldBe true

        dr5.chain shouldBe listOf("l1", "s")
        dr5.derefLast shouldBe true
        dr6.chain shouldBe listOf("l1", "s")
        dr6.derefLast shouldBe true
        dr7.chain shouldBe listOf("l1", "s")
        dr7.derefLast shouldBe true
        dr8.chain shouldBe listOf("l1", "s")
        dr8.derefLast shouldBe true

        dr9.chain shouldBe listOf("wptr")
        dr9.derefLast shouldBe true
        dr10.chain shouldBe listOf("wptr")
        dr10.derefLast shouldBe true
        dr11.chain shouldBe listOf("wptr")
        dr11.derefLast shouldBe true
        dr12.chain shouldBe listOf("wptr")
        dr12.derefLast shouldBe true

        dr13.chain shouldBe listOf("fptr")
        dr13.derefLast shouldBe true
        dr14.chain shouldBe listOf("fptr")
        dr14.derefLast shouldBe true
        dr15.chain shouldBe listOf("fptr")
        dr15.derefLast shouldBe true
        dr16.chain shouldBe listOf("fptr")
        dr16.derefLast shouldBe true
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
    }

    test("assigning pointer dereferences via memcopy") {
        val src="""
%option enable_floats

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

        l1^^ = l2^^
    }
}"""

        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 10
        (st[8] as FunctionCallStatement).target.nameInSource shouldBe listOf("sys", "memcopy")
    }

    test("assigning pointer dereferences should be same type") {
        val src="""
%option enable_floats

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
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "doesn't match"
        errors.errors[1] shouldContain "assigning to struct instance not supported"
        errors.errors[2] shouldContain "assigning to struct instance not supported"
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
    }

    test("a.b.c[i]^^ as expression where pointer is primitive type") {
        val src="""
%option enable_floats

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
    }

    test("a.b.c[i]^^.value = X where pointer is struct gives good error message") {
        val src="""
main {
    sub start() {
        other.foo.listarray[3]^^.value = cx16.r0
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
        listarray[3]^^.value = cx16.r0
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "no support for"
        errors.errors[1] shouldContain "no support for"
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
%option enable_floats

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
    }

    test("passing arrays to subroutines via typed pointer parameters") {
        val src="""
%import floats
%import textio

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
    }

    test("hoist variable decl and initializer correctly in case of pointer type variable as well") {
        val src="""
%import textio

main {

    sub start() {
        txt.print("one\n")
        if true {
            txt.print("two\n")
            ^^bool @shared successor = find_successor()     ; testcase here
        }
        txt.print("four\n")

        sub find_successor() -> uword {
            txt.print("three\n")
            cx16.r0++
            return 0
        }
    }
}"""
        val result = compileText(VMTarget(), false, src, outputDir)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 6
        st[0] shouldBe instanceOf<VarDecl>()
        (st[1] as FunctionCallStatement).target.nameInSource shouldBe listOf("txt", "print")
        st[2] shouldBe instanceOf<IfElse>()
        (st[3] as FunctionCallStatement).target.nameInSource shouldBe listOf("txt", "print")
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
        ^^Node n = Node("question string", "animal name string")
        ^^ubyte @shared q = n.question
        ^^ubyte @shared a = n.animal
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
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
%option enable_floats

main {

    sub start() {
        ^^ubyte[4] array1 = [1000, 1100, 1200, 1300]
        ^^byte[4] array2 = [1000, 1100, 1200, 1300]
        ^^bool[4] array3 = [1000, 1100, 1200, 1300]
        ^^word[4] array4 = [1000, 1100, 1200, 1300]
        ^^uword[4] array5 = [1000, 1100, 1200, 1300]
        ^^float[4] array6 = [1000, 1100, 1200, 1300]
        ^^long[4] array7 = [1000, 1100, 1200, 1300]
        ^^str[4] array8 = [1000, 1100, 1200, 1300]

        cx16.r0 = array1[2]
        cx16.r1 = array2[2]
        cx16.r2 = array3[2]
        cx16.r3 = array4[2]
        cx16.r4 = array5[2]
        cx16.r5 = array6[2]
        cx16.r6 = array7[2]
        cx16.r7 = array8[2]
    }
}"""

        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
    }

    xtest("array of pointers as subroutine param - not the primitive type pointers") {
        val src="""
%option enable_floats

main {

    sub start() {
        ^^ubyte[4] array1
        ^^byte[4] array2
        ^^bool[4] array3
        ^^word[4] array4
        ^^uword[4] array5
        ^^float[4] array6
        ^^long[4] array7
        ^^str[4] array8

        error1(array1)
        error2(array2)
        error3(array3)
        error4(array4)
        error5(array5)
        error6(array6)
        error7(array7)
        error8(array8)
    }

    sub error1(^^ubyte ptr) {
        cx16.r0++
    }
    sub error2(^^byte ptr) {
        cx16.r0++
    }
    sub error3(^^bool ptr) {
        cx16.r0++
    }
    sub error4(^^word ptr) {
        cx16.r0++
    }
    sub error5(^^uword ptr) {
        cx16.r0++
    }
    sub error6(^^float ptr) {
        cx16.r0++
    }
    sub error7(^^long ptr) {
        cx16.r0++
    }
    sub error8(^^str ptr) {
        cx16.r0++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), false, src, outputDir, errors = errors) shouldBe null
        errors.errors.size shouldBe 999
        // TODO
    }

    xtest("array of pointers as subroutine param") {
        val src="""
%option enable_floats

main {

    sub start() {
        ^^ubyte[4] array1
        ^^byte[4] array2
        ^^bool[4] array3
        ^^word[4] array4
        ^^uword[4] array5
        ^^float[4] array6
        ^^long[4] array7
        ^^str[4] array8

        ok1(array1)
        ok2(array2)
        ok3(array3)
        ok4(array4)
        ok5(array5)
        ok6(array6)
        ok7(array7)
        ok8(array8)
    }

    sub ok1(^^ubyte[] ptr) {
        cx16.r0 = ptr
    }
    sub ok2(^^byte[] ptr) {
        cx16.r0 = ptr
    }
    sub ok3(^^bool[] ptr) {
        cx16.r0 = ptr
    }
    sub ok4(^^word[] ptr) {
        cx16.r0 = ptr
    }
    sub ok5(^^uword[] ptr) {
        cx16.r0 = ptr
    }
    sub ok6(^^float[] ptr) {
        cx16.r0 = ptr
    }
    sub ok7(^^long[] ptr) {
        cx16.r0 = ptr
    }
    sub ok8(^^str[] ptr) {
        cx16.r0 = ptr
    }
}"""
        compileText(VMTarget(), false, src, outputDir) shouldNotBe null
    }
})