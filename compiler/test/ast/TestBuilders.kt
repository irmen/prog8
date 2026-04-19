package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.*

class TestBuilders : FunSpec({
    val pos = Position("test", 1, 1, 1)

    context("VarDecl builder") {
        test("VarDecl builder basic usage") {
            val decl = VarDecl.builder(DataType.UBYTE, pos)
                .names("myvar")
                .build()
            
            decl.name shouldBe "myvar"
            decl.datatype shouldBe DataType.UBYTE
            decl.type shouldBe VarDeclType.VAR
            decl.origin shouldBe VarDeclOrigin.USERCODE
            decl.zeropage shouldBe ZeropageWish.DONTCARE
            decl.splitwordarray shouldBe SplitWish.DONTCARE
            decl.isPrivate shouldBe false
            decl.names.isEmpty() shouldBe true
        }

        test("VarDecl builder multiple names") {
            val decl = VarDecl.builder(DataType.UBYTE, pos)
                .names("v1", "v2", "v3")
                .build()
            
            decl.name shouldBe "<multiple>"
            decl.names shouldBe listOf("v1", "v2", "v3")
        }

        test("VarDecl builder list names") {
            val decl = VarDecl.builder(DataType.UBYTE, pos)
                .names(listOf("v1", "v2"))
                .build()
            
            decl.name shouldBe "<multiple>"
            decl.names shouldBe listOf("v1", "v2")
        }

        test("VarDecl builder copyFrom") {
            val original = VarDecl.builder(DataType.UBYTE, pos)
                .names("v1")
                .type(VarDeclType.CONST)
                .zeropage(ZeropageWish.REQUIRE_ZEROPAGE)
                .value(NumericLiteral.optimalInteger(42, pos))
                .build()
            
            val copy = VarDecl.builder(DataType.UWORD, pos)
                .copyFrom(original)
                .build()
            
            copy.datatype shouldBe DataType.UWORD
            copy.name shouldBe "v1"
            copy.type shouldBe VarDeclType.CONST
            copy.zeropage shouldBe ZeropageWish.REQUIRE_ZEROPAGE
            (copy.value as NumericLiteral).number shouldBe 42.0
        }

        test("VarDecl builder advanced properties") {
            val valExpr = NumericLiteral.optimalInteger(42, pos)
            val numColsExpr = NumericLiteral.optimalInteger(10, pos)
            val arraySize = ArrayIndex(NumericLiteral.optimalInteger(100, pos), pos)

            val decl = VarDecl.builder(DataType.UWORD, pos)
                .names("myconst")
                .alignment(4u)
                .arraysize(arraySize)
                .dirty(true)
                .isPrivate(true)
                .matrixNumCols(numColsExpr)
                .origin(VarDeclOrigin.ARRAYLITERAL)
                .sharedWithAsm(true)
                .splitwordarray(SplitWish.NOSPLIT)
                .type(VarDeclType.CONST)
                .value(valExpr)
                .zeropage(ZeropageWish.REQUIRE_ZEROPAGE)
                .build()
            
            decl.name shouldBe "myconst"
            decl.datatype shouldBe DataType.UWORD
            decl.type shouldBe VarDeclType.CONST
            decl.origin shouldBe VarDeclOrigin.ARRAYLITERAL
            decl.zeropage shouldBe ZeropageWish.REQUIRE_ZEROPAGE
            decl.splitwordarray shouldBe SplitWish.NOSPLIT
            decl.isPrivate shouldBe true
            decl.sharedWithAsm shouldBe true
            decl.alignment shouldBe 4u
            decl.dirty shouldBe true
            decl.value shouldBe valExpr
            decl.matrixNumCols shouldBe numColsExpr
            decl.arraysize shouldBe arraySize
        }

        test("VarDecl builder name is required") {
            val builder = VarDecl.builder(DataType.UBYTE, pos)
            val exception = io.kotest.assertions.throwables.shouldThrow<IllegalStateException> {
                builder.build()
            }
            exception.message shouldBe "name is required"
        }

        test("VarDecl builder at least one name required") {
            val builder = VarDecl.builder(DataType.UBYTE, pos)
            io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                builder.names()
            }
        }
    }

    context("CompilationOptions builder") {
        test("CompilationOptions builder basic usage") {
            val target = prog8.code.target.C64Target()
            val options = CompilationOptions.builder(target).build()
            
            options.compTarget shouldBe target
            options.output shouldBe target.defaultOutputType
            options.launcher shouldBe CbmPrgLauncherType.NONE
            options.zeropage shouldBe ZeropageType.DONTUSE
            options.compilerVersion shouldBe "unknown"
            options.loadAddress shouldBe target.PROGRAM_LOAD_ADDRESS
            options.memtopAddress shouldBe target.PROGRAM_MEMTOP_ADDRESS
        }

        test("CompilationOptions builder custom properties") {
            val target = prog8.code.target.Cx16Target()
            val options = CompilationOptions.builder(target)
                .output(OutputType.RAW)
                .launcher(CbmPrgLauncherType.BASIC)
                .zeropage(ZeropageType.FLOATSAFE)
                .floats(true)
                .optimize(true)
                .warnSymbolShadowing(true)
                .build()
            
            options.compTarget shouldBe target
            options.output shouldBe OutputType.RAW
            options.launcher shouldBe CbmPrgLauncherType.BASIC
            options.zeropage shouldBe ZeropageType.FLOATSAFE
            options.floats shouldBe true
            options.optimize shouldBe true
            options.warnSymbolShadowing shouldBe true
        }
    }
})
