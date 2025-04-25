package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import prog8.ast.Program
import prog8.ast.expressions.AddressOf
import prog8.ast.expressions.IdentifierReference
import prog8.ast.expressions.NumericLiteral
import prog8.ast.expressions.PrefixExpression
import prog8.ast.statements.Assignment
import prog8.ast.statements.Block
import prog8.ast.statements.Subroutine
import prog8.ast.statements.VarDecl
import prog8.code.core.Position
import prog8.code.source.SourceCode
import prog8.parser.Prog8Parser
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder


class TestIdentifierRef: FunSpec({

    test("constructor and equality") {
        val ident1 = IdentifierReference(listOf("a", "b"), Position("file", 1, 2, 3))
        val ident1same = IdentifierReference(listOf("a", "b"), Position("file", 1, 2, 3))
        val ident1copy = ident1.copy()
        val ident2 = IdentifierReference(listOf("a", "b", "c"), Position("file", 1, 2, 3))
        val ident3 = IdentifierReference(listOf("a", "b"), Position("file2", 11, 22, 33))

        ident1.nameInSource shouldBe listOf("a", "b")
        ident1.isSimple shouldBe true
        (ident1 isSameAs ident1same) shouldBe true
        (ident1 isSameAs ident1copy) shouldBe true
        (ident1 isSameAs ident2) shouldBe false
        (ident1 isSameAs ident3) shouldBe true      // as opposed to inequality, they do refer to the same symbol!
        (ident1 == ident1same) shouldBe true
        (ident1 == ident1copy) shouldBe true
        (ident1 == ident2) shouldBe false
        (ident1 == ident3) shouldBe false

        val pfx = PrefixExpression("-", NumericLiteral.optimalInteger(1, Position.DUMMY), Position.DUMMY)
        (ident1 isSameAs pfx) shouldBe false
    }

    test("target methods") {
        val src= SourceCode.Text("""
            main {
                ubyte bb
                uword ww
                sub start() {
                    ww++
                    ww = &main
                }
            } """)
        val module = Prog8Parser.parseModule(src)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val mstmts = (module.statements.single() as Block).statements
        val stmts = mstmts.filterIsInstance<Subroutine>().single().statements
        val wwref = (stmts[0] as Assignment).target.identifier!!
        val mainref = ((stmts[1] as Assignment).value as AddressOf).identifier
        wwref.nameInSource shouldBe listOf("ww")
        wwref.wasStringLiteral() shouldBe false
        wwref.targetStatement(program) shouldBe instanceOf<VarDecl>()
        wwref.targetVarDecl()!!.name shouldBe "ww"
        wwref.targetVarDecl()!!.parent shouldBe instanceOf<Block>()
        mainref.nameInSource shouldBe listOf("main")
        mainref.wasStringLiteral() shouldBe false
        mainref.targetStatement(program) shouldBe instanceOf<Block>()
    }
})
