package prog8tests

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.*
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.core.ZeropageWish

class TestSymbolTable: FunSpec({
    test("empty symboltable") {
        val st = SymbolTable()
        st.scopedName shouldBe emptyList()
        st.name shouldBe ""
        st.type shouldBe StNodeType.GLOBAL
        st.children shouldBe mutableMapOf()
        st.position shouldBe Position.DUMMY
    }

    test("symboltable flatten") {
        val st = makeSt()
        st.flat[listOf("zzzzz")] shouldBe null
        st.flat.getValue(listOf("sin")).type shouldBe StNodeType.BUILTINFUNC
        st.flat.getValue(listOf("block2")).type shouldBe StNodeType.BLOCK
        st.flat.getValue(listOf("block2", "sub2", "subsub", "label")).type shouldBe StNodeType.LABEL
        st.flat[listOf("block2", "sub2", "subsub", "label", "zzzz")] shouldBe null
    }

    test("symboltable global lookups") {
        val st = makeSt()
        st.lookup("undefined") shouldBe null
        st.lookup(listOf("undefined")) shouldBe null
        var default = st.lookupOrElse("undefined") { StNode("default", StNodeType.LABEL, Position.DUMMY) }
        default.name shouldBe "default"
        default = st.lookupOrElse(listOf("undefined")) { StNode("default", StNodeType.LABEL, Position.DUMMY) }
        default.name shouldBe "default"

        val sinfunc = st.lookupOrElse("sin") { fail("sin must be found") }
        sinfunc.type shouldBe StNodeType.BUILTINFUNC

        val variable = st.lookupOrElse(listOf("block1", "sub2", "v2")) { fail("v2 must be found") }
        variable.type shouldBe StNodeType.STATICVAR
    }

    test("symboltable nested lookups") {
        val st = makeSt()

        val sub1 = st.lookupOrElse(listOf("block1", "sub1")) { fail("should find sub1") }
        sub1.name shouldBe "sub1"
        sub1.scopedName shouldBe listOf("block1", "sub1")
        sub1.type shouldBe StNodeType.SUBROUTINE
        sub1.children.size shouldBe 2

        val v1 = sub1.lookupOrElse("v1") { fail("v1 must be found") } as StStaticVariable
        v1.type shouldBe StNodeType.STATICVAR
        v1.name shouldBe "v1"
        v1.dt shouldBe DataType.BYTE

        val blockc = sub1.lookupOrElse("blockc") { fail("blockc") } as StConstant
        blockc.type shouldBe StNodeType.CONSTANT
        blockc.value shouldBe 999.0

        val subsub = st.lookupOrElse(listOf("block2", "sub2", "subsub")) { fail("should find subsub") }
        subsub.lookup("blockc") shouldBe null
        subsub.lookup("label") shouldNotBe null
    }
})


private fun makeSt(): SymbolTable {
    val st = SymbolTable()
    val block1 = StNode("block1", StNodeType.BLOCK, Position.DUMMY)
    val sub11 = StNode("sub1", StNodeType.SUBROUTINE, Position.DUMMY)
    val sub12 = StNode("sub2", StNodeType.SUBROUTINE, Position.DUMMY)
    block1.add(sub11)
    block1.add(sub12)
    block1.add(StConstant("c1", DataType.UWORD, 12345.0, Position.DUMMY))
    block1.add(StConstant("blockc", DataType.UWORD, 999.0, Position.DUMMY))
    sub11.add(StStaticVariable("v1", DataType.BYTE, null, null, null, null, ZeropageWish.DONTCARE, Position.DUMMY))
    sub11.add(StStaticVariable("v2", DataType.BYTE, null, null, null, null, ZeropageWish.DONTCARE, Position.DUMMY))
    sub12.add(StStaticVariable("v1", DataType.BYTE, null, null, null, null, ZeropageWish.DONTCARE, Position.DUMMY))
    sub12.add(StStaticVariable("v2", DataType.BYTE, null, null, null, null, ZeropageWish.DONTCARE, Position.DUMMY))

    val block2 = StNode("block2", StNodeType.BLOCK, Position.DUMMY)
    val sub21 = StNode("sub1", StNodeType.SUBROUTINE, Position.DUMMY)
    val sub22 = StNode("sub2", StNodeType.SUBROUTINE, Position.DUMMY)
    block2.add(sub21)
    block2.add(sub22)
    val sub221 = StNode("subsub", StNodeType.SUBROUTINE, Position.DUMMY)
    sub221.add(StNode("label", StNodeType.LABEL, Position.DUMMY))
    sub22.add(sub221)

    val builtinfunc = StNode("sin", StNodeType.BUILTINFUNC, Position.DUMMY)
    st.add(block1)
    st.add(block2)
    st.add(builtinfunc)
    return st
}