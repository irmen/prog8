import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.StNodeType
import prog8.code.SymbolTableMaker
import prog8.code.ast.*
import prog8.code.core.*
import prog8.code.source.SourceCode
import prog8.code.target.VMTarget
import prog8.codegen.intermediate.prefixScopedName
import prog8.codegen.intermediate.prefixSymbols
import prog8.codegen.intermediate.typePrefixChar


class TestSymbolPrefixer : FunSpec({

    // --- prefixScopedName tests ---

    test("prefixScopedName single name with type char") {
        prefixScopedName("main", 'b') shouldBe "p8b_main"
        prefixScopedName("start", 's') shouldBe "p8s_start"
        prefixScopedName("x", 'v') shouldBe "p8v_x"
        prefixScopedName("SIZE", 'c') shouldBe "p8c_SIZE"
        prefixScopedName("label", 'l') shouldBe "p8l_label"
        prefixScopedName("Point", 't') shouldBe "p8t_Point"
    }

    test("prefixScopedName dotted scoped name") {
        prefixScopedName("main.start", 's') shouldBe "p8b_main.p8s_start"
        prefixScopedName("main.start.x", 'v') shouldBe "p8b_main.p8s_start.p8v_x"
        prefixScopedName("main.start.looplabel", 'l') shouldBe "p8b_main.p8s_start.p8l_looplabel"
        prefixScopedName("main.SIZE", 'c') shouldBe "p8b_main.p8c_SIZE"
        prefixScopedName("lib.module.func", 's') shouldBe "p8b_lib.p8s_module.p8s_func"
    }

    test("prefixScopedName generated label is not prefixed") {
        val genLabel = "p8_label_gen_42"
        prefixScopedName(genLabel, 's') shouldBe genLabel
        prefixScopedName("main.start.$genLabel", 'v') shouldBe "p8b_main.p8s_start.$genLabel"
    }

    // --- typePrefixChar tests ---

    test("typePrefixChar maps all StNodeTypes correctly") {
        typePrefixChar(StNodeType.BLOCK) shouldBe 'b'
        typePrefixChar(StNodeType.SUBROUTINE) shouldBe 's'
        typePrefixChar(StNodeType.EXTSUB) shouldBe 's'
        typePrefixChar(StNodeType.LABEL) shouldBe 'l'
        typePrefixChar(StNodeType.STATICVAR) shouldBe 'v'
        typePrefixChar(StNodeType.MEMVAR) shouldBe 'v'
        typePrefixChar(StNodeType.CONSTANT) shouldBe 'c'
        typePrefixChar(StNodeType.BUILTINFUNC) shouldBe 's'
        typePrefixChar(StNodeType.MEMORYSLAB) shouldBe 'v'
        typePrefixChar(StNodeType.STRUCT) shouldBe 't'
        typePrefixChar(StNodeType.STRUCTINSTANCE) shouldBe 'i'
    }

    // --- full program prefixing tests ---

    test("prefixSymbols prefixes a simple program") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val varX = PtVariable("x", DataType.UBYTE, ZeropageWish.DONTCARE, 0u, false, null, null, Position.DUMMY)
        sub.add(varX)
        block.add(sub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()

        val updatedSt = prefixSymbols(program, options, st)

        // AST names should be prefixed
        block.name shouldBe "p8b_main"
        sub.name shouldBe "p8s_start"
        varX.name shouldBe "p8v_x"

        // symbol table should have prefixed entries
        updatedSt.lookup("p8b_main") shouldNotBe null
        updatedSt.lookup("p8b_main.p8s_start") shouldNotBe null
        updatedSt.lookup("p8b_main.p8s_start.p8v_x") shouldNotBe null
    }

    test("prefixSymbols handles subroutine parameters") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val param = PtSubroutineParameter("arg", DataType.UBYTE, null, Position.DUMMY)
        sub.add(param)
        block.add(sub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        param.name shouldBe "p8v_arg"
    }

    test("prefixSymbols handles constants") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val constant = PtConstant("SIZE", DataType.UWORD, 100.0, null, Position.DUMMY)
        block.add(constant)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        constant.name shouldBe "p8c_SIZE"
    }

    test("prefixSymbols handles labels") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val label = PtLabel("mylabel", Position.DUMMY)
        sub.add(label)
        block.add(sub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        label.name shouldBe "p8l_mylabel"
    }

    test("prefixSymbols handles generated labels without prefixing them") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val genLabel = PtLabel("p8_label_gen_42", Position.DUMMY)
        sub.add(genLabel)
        block.add(sub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        genLabel.name shouldBe "p8_label_gen_42"
    }

    test("prefixSymbols handles struct declarations") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val struct = PtStructDecl("Point", listOf(
            PtStructField(DataType.UBYTE, "x", null),
            PtStructField(DataType.UBYTE, "y", null)
        ), Position.DUMMY)
        block.add(struct)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        struct.name shouldBe "p8t_Point"
    }

    test("prefixSymbols handles asmsub") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val asmsub = PtAsmSub("routine", PtAsmSub.Address(null, null, 0x5000u), setOf(CpuRegister.Y), emptyList(), emptyList(), false, Position.DUMMY)
        block.add(asmsub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        asmsub.name shouldBe "p8s_routine"
    }

    test("prefixSymbols handles memmapped variables") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val memvar = PtMemMapped("reg", DataType.UBYTE, 0xd020u, null, Position.DUMMY)
        block.add(memvar)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        memvar.name shouldBe "p8v_reg"
    }

    // --- noSymbolPrefixing tests ---

    test("noSymbolPrefixing blocks keep their names intact") {
        val program = PtProgram("test", VMTarget())
        val libOptions = PtBlock.Options(noSymbolPrefixing = true)
        val libBlock = PtBlock("lib", false, SourceCode.Generated("test"), libOptions, Position.DUMMY)
        val libSub = PtSub("func", emptyList(), emptyList(), Position.DUMMY)
        libBlock.add(libSub)
        program.add(libBlock)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        libBlock.name shouldBe "lib"   // no prefix
        libSub.name shouldBe "func"    // no prefix
    }

    test("references from user block to noSymbolPrefixing block are not prefixed") {
        val program = PtProgram("test", VMTarget())

        // Library block with no symbol prefixing
        val libOptions = PtBlock.Options(noSymbolPrefixing = true)
        val libBlock = PtBlock("lib", false, SourceCode.Generated("test"), libOptions, Position.DUMMY)
        val libSub = PtSub("print", emptyList(), emptyList(), Position.DUMMY)
        libBlock.add(libSub)
        program.add(libBlock)

        // User block (regular)
        val userBlock = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val userSub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        userBlock.add(userSub)
        program.add(userBlock)

        val options = basicTestOptions()

        // Now add a function call referencing the library sub - this must happen AFTER making the st
        // because the st is needed for lookup during prefixing
        val call = PtFunctionCall("lib.print", false, false, emptyArray(), Position.DUMMY)
        userSub.add(call)

        // Rebuild st after adding the call
        val st2 = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st2)

        // The call site should NOT be prefixed (lib has noSymbolPrefixing)
        call.name shouldBe "lib.print"
    }

    test("references from noSymbolPrefixing block to regular block get prefixed") {
        val program = PtProgram("test", VMTarget())

        // Regular user block
        val userBlock = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val userSub = PtSub("helper", emptyList(), emptyList(), Position.DUMMY)
        userBlock.add(userSub)
        program.add(userBlock)

        // Library block with no symbol prefixing
        val libOptions = PtBlock.Options(noSymbolPrefixing = true)
        val libBlock = PtBlock("lib", false, SourceCode.Generated("test"), libOptions, Position.DUMMY)
        val libSub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val call = PtFunctionCall("main.helper", false, false, emptyArray(), Position.DUMMY)
        libSub.add(call)
        libBlock.add(libSub)
        program.add(libBlock)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        // The call site from lib to main should get prefixed (new node, get from parent)
        val newCall = libSub.children.find { it is PtFunctionCall } as PtFunctionCall
        newCall.name shouldBe "p8b_main.p8s_helper"
    }

    test("identifier references are prefixed according to their target type") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val varX = PtVariable("x", DataType.UBYTE, ZeropageWish.DONTCARE, 0u, false, null, null, Position.DUMMY)
        sub.add(varX)

        // Add an identifier reference to the variable
        val idRef = PtIdentifier("main.start.x", DataType.UBYTE, Position.DUMMY)
        sub.add(idRef)

        block.add(sub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()
        prefixSymbols(program, options, st)

        // The identifier reference should have been prefixed (new node, get from parent)
        val newIdRef = sub.children.find { it is PtIdentifier } as PtIdentifier
        newIdRef.name shouldBe "p8b_main.p8s_start.p8v_x"
    }

    test("prefixSymbols exception for unknown identifier") {
        val program = PtProgram("test", VMTarget())
        val block = PtBlock("main", false, SourceCode.Generated("test"), PtBlock.Options(), Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), Position.DUMMY)
        val idRef = PtIdentifier("nonexistent", DataType.UBYTE, Position.DUMMY)
        sub.add(idRef)
        block.add(sub)
        program.add(block)

        val options = basicTestOptions()
        val st = SymbolTableMaker(program, options).make()

        shouldThrow<AssemblyError> {
            prefixSymbols(program, options, st)
        }
    }
})

private fun basicTestOptions(): CompilationOptions {
    val target = prog8.code.target.VMTarget()
    return CompilationOptions.builder(target)
        .output(OutputType.RAW)
        .zeropage(ZeropageType.DONTUSE)
        .floats(true)
        .compilerVersion("99.99")
        .build()
}
