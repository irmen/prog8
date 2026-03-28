package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.code.*
import prog8.code.ast.PtBlock
import prog8.code.ast.PtLabel
import prog8.code.ast.PtProgram
import prog8.code.ast.PtStructDecl
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.core.ZeropageWish
import prog8.code.source.SourceCode
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder

/**
 * Tests for SymbolTable construction and structural integrity.
 * 
 * These tests verify that SymbolTables are built correctly from ASTs,
 * that duplicate handling works as expected, and that parent linkage
 * is maintained properly.
 */
class TestSymbolTableConstruction: FunSpec({

    // ============================================================================
    // Helper Functions
    // ============================================================================

    fun makeSimpleSymbolTable(): SymbolTable {
        val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
        astProgram.add(astBlock)
        return SymbolTable(astProgram)
    }

    fun makeSymbolTableWithDuplicates(): Pair<SymbolTable, StStaticVariable> {
        val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
        astProgram.add(astBlock)
        val st = SymbolTable(astProgram)
        
        val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
        st.add(blockNode)
        
        val var1 = StStaticVariable("value", DataType.UWORD, null, null, null, ZeropageWish.DONTCARE, 0u, false, null)
        val var2 = StStaticVariable("value", DataType.UWORD, null, null, null, ZeropageWish.DONTCARE, 0u, false, null)
        
        blockNode.add(var1)
        return st to var1
    }

    // ============================================================================
    // Basic Construction Tests
    // ============================================================================

    test("empty symboltable creation") {
        val astNode = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val st = SymbolTable(astNode)
        st.name shouldBe "test"
        st.type shouldBe StNodeType.GLOBAL
        st.children shouldBe mutableMapOf()
        st.astNode shouldBeSameInstanceAs astNode
        st.astNode!!.position shouldBe Position.DUMMY
    }

    test("symboltable with single block") {
        val st = makeSimpleSymbolTable()
        st.name shouldBe "test"
        st.children.size shouldBe 0
    }

    test("symboltable with multiple modules") {
        val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val astBlock1 = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
        val astBlock2 = PtBlock("block2", false, SourceCode.Generated("block2"), PtBlock.Options(), Position.DUMMY)
        astProgram.add(astBlock1)
        astProgram.add(astBlock2)
        val st = SymbolTable(astProgram)
        st.name shouldBe "test"
    }

    // ============================================================================
    // Duplicate Handling Tests
    // ============================================================================

    test("adding duplicate symbol to same scope ignores second") {
        val (st, var1) = makeSymbolTableWithDuplicates()
        val blockNode = st.children["block1"]!!
        
        val var2 = StStaticVariable("value", DataType.UWORD, null, null, null, ZeropageWish.DONTCARE, 0u, false, null)
        blockNode.add(var2)  // Should be ignored silently
        
        blockNode.children["value"] shouldBeSameInstanceAs var1
        blockNode.children.size shouldBe 1
    }

    test("duplicate struct instance insertion is handled") {
        val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
        astProgram.add(astBlock)
        val st = SymbolTable(astProgram)
        
        val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
        st.add(blockNode)
        
        val structInstance1 = StStructInstance("instance1", "MyStruct", emptyList(), 10u, null)
        val structInstance2 = StStructInstance("instance1", "MyStruct", emptyList(), 10u, null)
        
        blockNode.add(structInstance1)
        blockNode.add(structInstance2)  // Should be ignored
        
        blockNode.children["instance1"] shouldBeSameInstanceAs structInstance1
        blockNode.children.size shouldBe 1
    }

    test("duplicate memory slab insertion is handled") {
        val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
        astProgram.add(astBlock)
        val st = SymbolTable(astProgram)
        
        val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
        st.add(blockNode)
        
        val slab1 = StMemorySlab("slab1", 100u, 16u, null)
        val slab2 = StMemorySlab("slab1", 200u, 32u, null)
        
        blockNode.add(slab1)
        blockNode.add(slab2)  // Should be ignored
        
        blockNode.children["slab1"] shouldBeSameInstanceAs slab1
        blockNode.children.size shouldBe 1
    }

    test("duplicate label insertion is handled") {
        val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
        val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
        astProgram.add(astBlock)
        val st = SymbolTable(astProgram)

        val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
        st.add(blockNode)

        val label1 = StNode("label1", StNodeType.LABEL, PtLabel("label1", Position.DUMMY))
        val label2 = StNode("label1", StNodeType.LABEL, PtLabel("label1", Position.DUMMY))  // Same name!

        blockNode.add(label1)
        blockNode.add(label2)  // Should be ignored

        blockNode.children["label1"] shouldBeSameInstanceAs label1
        blockNode.children.size shouldBe 1
    }

    // ============================================================================
    // Parent Linkage Tests
    // ============================================================================

    test("parent pointer set correctly on add") {
        val st = makeSimpleSymbolTable()
        val blockNode = StNode("block1", StNodeType.BLOCK, null)
        st.add(blockNode)
        
        blockNode.parent shouldBeSameInstanceAs st
    }

    test("parent pointer survives duplicate insertion") {
        val (st, var1) = makeSymbolTableWithDuplicates()
        val blockNode = st.children["block1"]!!
        
        val var2 = StStaticVariable("value", DataType.UWORD, null, null, null, ZeropageWish.DONTCARE, 0u, false, null)
        blockNode.add(var2)  // Should be ignored
        
        var1.parent shouldBeSameInstanceAs blockNode
    }

    // ============================================================================
    // Collection Tests
    // ============================================================================

    test("allVariables collection") {
        val st = makeStWithVariables()
        st.allVariables.size shouldBe 2
        st.allVariables.map { it.name } shouldBe listOf("var1", "var2")
    }

    test("allMemMappedVariables collection") {
        val st = makeStWithMemVars()
        st.allMemMappedVariables.size shouldBe 1
        st.allMemMappedVariables.single().name shouldBe "memvar1"
    }

    test("allMemorySlabs collection") {
        val st = makeStWithSlabs()
        st.allMemorySlabs.size shouldBe 1
        st.allMemorySlabs.single().name shouldBe "slab1"
    }

    test("allStructInstances collection") {
        val st = makeStWithStructInstances()
        st.allStructInstances().size shouldBe 1
        st.allStructInstances().single().name shouldBe "instance1"
    }

    test("allStructTypes collection") {
        val st = makeStWithStructs()
        st.allStructTypes().size shouldBe 1
        st.allStructTypes().single().name shouldBe "MyStruct"
    }
})

// ================================================================================
// Helper Functions for Collection Tests
// ================================================================================

private fun makeStWithVariables(): SymbolTable {
    val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
    astProgram.add(astBlock)
    val st = SymbolTable(astProgram)
    
    val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
    st.add(blockNode)
    
    val var1 = StStaticVariable("var1", DataType.UWORD, null, null, null, ZeropageWish.DONTCARE, 0u, false, null)
    val var2 = StStaticVariable("var2", DataType.BYTE, null, null, null, ZeropageWish.DONTCARE, 0u, false, null)
    blockNode.add(var1)
    blockNode.add(var2)
    
    return st
}

private fun makeStWithMemVars(): SymbolTable {
    val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
    astProgram.add(astBlock)
    val st = SymbolTable(astProgram)
    
    val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
    st.add(blockNode)
    
    val memvar = StMemVar("memvar1", DataType.UWORD, 0xD000u, null, null)
    blockNode.add(memvar)
    
    return st
}

private fun makeStWithSlabs(): SymbolTable {
    val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
    astProgram.add(astBlock)
    val st = SymbolTable(astProgram)
    
    val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
    st.add(blockNode)
    
    val slab = StMemorySlab("slab1", 100u, 16u, null)
    blockNode.add(slab)
    
    return st
}

private fun makeStWithStructInstances(): SymbolTable {
    val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
    astProgram.add(astBlock)
    val st = SymbolTable(astProgram)
    
    val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
    st.add(blockNode)
    
    val instance = StStructInstance("instance1", "MyStruct", emptyList(), 10u, null)
    blockNode.add(instance)
    
    return st
}

private fun makeStWithStructs(): SymbolTable {
    val astProgram = PtProgram("test", DummyMemsizer, DummyStringEncoder)
    val astBlock = PtBlock("block1", false, SourceCode.Generated("block1"), PtBlock.Options(), Position.DUMMY)
    astProgram.add(astBlock)
    val st = SymbolTable(astProgram)
    
    val blockNode = StNode("block1", StNodeType.BLOCK, astBlock)
    st.add(blockNode)
    
    val structDecl = PtStructDecl("MyStruct", emptyList(), Position.DUMMY)
    val struct = StStruct("MyStruct", emptyList(), 10u, "MyStruct", structDecl)
    blockNode.add(struct)
    
    return st
}
