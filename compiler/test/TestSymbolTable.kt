package prog8tests.compiler

import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.code.*
import prog8.code.ast.*
import prog8.code.core.*
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder


class TestSymbolTable: FunSpec({
    test("empty symboltable") {
        val astNode = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val st = SymbolTable(astNode)
        st.name shouldBe "test"
        st.type shouldBe StNodeType.GLOBAL
        st.children shouldBe mutableMapOf()
        st.astNode shouldBeSameInstanceAs astNode
        st.astNode.position shouldBe Position.DUMMY
    }

    test("symboltable flatten") {
        val st = makeSt()
        st.flat["zzzzz"] shouldBe null
        st.flat.getValue("msb").type shouldBe StNodeType.BUILTINFUNC
        st.flat.getValue("block2").type shouldBe StNodeType.BLOCK
        st.flat.getValue("block2.sub2.subsub.label").type shouldBe StNodeType.LABEL
        st.flat["block2.sub2.subsub.label.zzzz"] shouldBe null
    }

    test("symboltable global lookups") {
        val st = makeSt()
        st.lookupUnscoped("undefined") shouldBe null
        st.lookup("undefined") shouldBe null
        st.lookup("undefined.undefined") shouldBe null
        var default = st.lookupUnscopedOrElse("undefined") { StNode("default", StNodeType.LABEL, PtIdentifier("default", DataType.forDt(BaseDataType.BYTE), Position.DUMMY)) }
        default.name shouldBe "default"
        default = st.lookupUnscopedOrElse("undefined") { StNode("default", StNodeType.LABEL, PtIdentifier("default", DataType.forDt(BaseDataType.BYTE), Position.DUMMY)) }
        default.name shouldBe "default"

        val msbFunc = st.lookupUnscopedOrElse("msb") { fail("msb must be found") }
        msbFunc.type shouldBe StNodeType.BUILTINFUNC

        val variable = st.lookupOrElse("block1.sub2.v2") { fail("v2 must be found") }
        variable.type shouldBe StNodeType.STATICVAR
    }

    test("symboltable nested lookups") {
        val st = makeSt()

        val sub1 = st.lookupOrElse("block1.sub1") { fail("should find sub1") }
        sub1.name shouldBe "sub1"
        sub1.scopedName shouldBe "block1.sub1"
        sub1.type shouldBe StNodeType.SUBROUTINE
        sub1.children.size shouldBe 4

        val v1 = sub1.lookupUnscopedOrElse("v1") { fail("v1 must be found") } as StStaticVariable
        v1.type shouldBe StNodeType.STATICVAR
        v1.name shouldBe "v1"
        v1.dt shouldBe DataType.forDt(BaseDataType.BYTE)

        val blockc = sub1.lookupUnscopedOrElse("blockc") { fail("blockc") } as StConstant
        blockc.type shouldBe StNodeType.CONSTANT
        blockc.value shouldBe 999.0

        val subsub = st.lookupOrElse("block2.sub2.subsub") { fail("should find subsub") }
        subsub.lookupUnscoped("blockc") shouldBe null
        subsub.lookupUnscoped("label") shouldNotBe null
    }

    test("symboltable collections") {
        val st= makeSt()

        st.allVariables.size shouldBe 4
        st.allMemMappedVariables.single().scopedName shouldBe "block1.sub1.v3"
        st.allMemorySlabs.single().scopedName shouldBe "block1.sub1.slab1"
    }

    test("static vars") {
        val node = PtIdentifier("dummy", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
        val stVar1 = StStaticVariable("initialized", DataType.forDt(BaseDataType.UBYTE), null, null, null, ZeropageWish.DONTCARE, 0, node)
        stVar1.setOnetimeInitNumeric(99.0)
        val stVar2 = StStaticVariable("uninitialized", DataType.forDt(BaseDataType.UBYTE), null, null, null, ZeropageWish.DONTCARE, 0, node)
        val arrayInitNonzero = listOf(StArrayElement(1.1, null, null), StArrayElement(2.2, null, null), StArrayElement(3.3, null, null))
        val arrayInitAllzero = listOf(StArrayElement(0.0, null, null), StArrayElement(0.0, null, null), StArrayElement(0.0, null, null))
        val stVar3 = StStaticVariable("initialized", DataType.arrayFor(BaseDataType.UWORD), null, arrayInitNonzero, 3, ZeropageWish.DONTCARE, 0, node)
        val stVar4 = StStaticVariable("initialized", DataType.arrayFor(BaseDataType.UWORD), null, arrayInitAllzero, 3, ZeropageWish.DONTCARE, 0, node)
        val stVar5 = StStaticVariable("uninitialized", DataType.arrayFor(BaseDataType.UWORD), null, null, 3, ZeropageWish.DONTCARE, 0, node)

        stVar1.uninitialized shouldBe false
        stVar1.length shouldBe null
        stVar2.uninitialized shouldBe true
        stVar2.length shouldBe null
        stVar3.uninitialized shouldBe false
        stVar3.length shouldBe 3
        stVar4.uninitialized shouldBe false
        stVar4.length shouldBe 3
        stVar5.uninitialized shouldBe true
        stVar5.length shouldBe 3
    }
})


private fun makeSt(): SymbolTable {

    // first build the AST
    val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val astBlock1 = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
    val astConstant1 = PtConstant("c1", DataType.forDt(BaseDataType.UWORD), 12345.0, Position.DUMMY)
    val astConstant2 = PtConstant("blockc", DataType.forDt(BaseDataType.UWORD), 999.0, Position.DUMMY)
    astBlock1.add(astConstant1)
    astBlock1.add(astConstant2)
    val astSub1 = PtSub("sub1", emptyList(), null, Position.DUMMY)
    val astSub2 = PtSub("sub2", emptyList(), null, Position.DUMMY)
    val astSub1v1 = PtVariable(
        "v1",
        DataType.forDt(BaseDataType.BYTE),
        ZeropageWish.DONTCARE,
        0u,
        null,
        null,
        Position.DUMMY
    )
    val astSub1v2 = PtVariable(
        "v2",
        DataType.forDt(BaseDataType.BYTE),
        ZeropageWish.DONTCARE,
        0u,
        null,
        null,
        Position.DUMMY
    )
    val astSub1v3 = PtVariable(
        "v3",
        DataType.forDt(BaseDataType.FLOAT),
        ZeropageWish.DONTCARE,
        0u,
        null,
        null,
        Position.DUMMY
    )
    val astSub1v4 = PtVariable(
        "slab1",
        DataType.forDt(BaseDataType.UWORD),
        ZeropageWish.DONTCARE,
        0u,
        null,
        null,
        Position.DUMMY
    )
    val astSub2v1 = PtVariable(
        "v1",
        DataType.forDt(BaseDataType.BYTE),
        ZeropageWish.DONTCARE,
        0u,
        null,
        null,
        Position.DUMMY
    )
    val astSub2v2 = PtVariable(
        "v2",
        DataType.forDt(BaseDataType.BYTE),
        ZeropageWish.DONTCARE,
        0u,
        null,
        null,
        Position.DUMMY
    )
    astSub1.add(astSub1v1)
    astSub1.add(astSub1v2)
    astSub1.add(astSub1v3)
    astSub1.add(astSub1v4)
    astSub2.add(astSub2v2)
    astSub2.add(astSub2v2)
    astBlock1.add(astSub1)
    astBlock1.add(astSub2)
    val astBfunc = PtIdentifier("msb", DataType.forDt(BaseDataType.UBYTE), Position.DUMMY)
    astBlock1.add(astBfunc)
    val astBlock2 = PtBlock("block2", false, SourceCode.Generated("block2"), PtBlock.Options(), Position.DUMMY)
    val astSub21 = PtSub("sub1", emptyList(), null, Position.DUMMY)
    val astSub22 = PtSub("sub2", emptyList(), null, Position.DUMMY)
    val astSub221 = PtSub("subsub", emptyList(), null, Position.DUMMY)
    val astLabel = PtLabel("label", Position.DUMMY)
    astSub221.add(astLabel)
    astSub22.add(astSub221)
    astBlock2.add(astSub21)
    astBlock2.add(astSub22)
    astProgram.add(astBlock1)
    astProgram.add(astBlock2)

    // now hook up the SymbolTable on that AST
    val st = SymbolTable(astProgram)
    val block1 = StNode("block1", StNodeType.BLOCK, astBlock1)
    val sub11 = StNode("sub1", StNodeType.SUBROUTINE, astSub1)
    val sub12 = StNode("sub2", StNodeType.SUBROUTINE, astSub2)
    block1.add(sub11)
    block1.add(sub12)
    block1.add(StConstant("c1", BaseDataType.UWORD, 12345.0, astConstant1))
    block1.add(StConstant("blockc", BaseDataType.UWORD, 999.0, astConstant2))
    sub11.add(StStaticVariable("v1", DataType.forDt(BaseDataType.BYTE), null, null, null, ZeropageWish.DONTCARE, 0, astSub1v1))
    sub11.add(StStaticVariable("v2", DataType.forDt(BaseDataType.BYTE), null, null, null, ZeropageWish.DONTCARE, 0, astSub1v2))
    sub11.add(StMemVar("v3", DataType.forDt(BaseDataType.FLOAT), 12345u, null, astSub1v3))
    sub11.add(StMemorySlab("slab1", 200u, 64u, astSub1v4))
    sub12.add(StStaticVariable("v1", DataType.forDt(BaseDataType.BYTE), null, null, null, ZeropageWish.DONTCARE, 0, astSub2v1))
    sub12.add(StStaticVariable("v2", DataType.forDt(BaseDataType.BYTE), null, null, null, ZeropageWish.DONTCARE, 0, astSub2v2))
    val block2 = StNode("block2", StNodeType.BLOCK, astBlock2)
    val sub21 = StNode("sub1", StNodeType.SUBROUTINE, astSub21)
    val sub22 = StNode("sub2", StNodeType.SUBROUTINE, astSub22)
    block2.add(sub21)
    block2.add(sub22)
    val sub221 = StNode("subsub", StNodeType.SUBROUTINE, astSub221)
    sub221.add(StNode("label", StNodeType.LABEL, astLabel))
    sub22.add(sub221)

    val builtinfunc = StNode("msb", StNodeType.BUILTINFUNC, astBfunc)
    st.add(block1)
    st.add(block2)
    st.add(builtinfunc)
    return st
}
