package prog8lsp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SignatureHelpTest: FunSpec({
    lateinit var harness: LspTestHarness
    val testUri = "file:///test.p8"

    beforeTest {
        harness = LspTestHarness()
        harness.setup()
    }

    afterTest {
        harness.shutdown()
    }

    test("signature help - basic function call") {
        val code = """
            main {
                sub start() {
                    foo(1, 2)
                }
                sub foo(ubyte a, ubyte b) {
                }
            }
        """.trimIndent()
        harness.openDocument(testUri, code)
        
        // Cursor after '('
        val help1 = harness.signatureHelp(testUri, 2, 11)
        help1 shouldNotBe null
        help1!!.signatures.size shouldBe 1
        help1.signatures[0].label shouldBe "sub foo(a: ubyte, b: ubyte)"
        help1.activeParameter shouldBe 0
        
        // Cursor after ','
        val help2 = harness.signatureHelp(testUri, 2, 14)
        help2 shouldNotBe null
        help2!!.activeParameter shouldBe 1
    }

    test("signature help - function call in expression") {
        val code = """
            main {
                sub start() {
                    ubyte x = foo(10, 20)
                }
                sub foo(ubyte a, ubyte b) -> ubyte {
                    return a + b
                }
            }
        """.trimIndent()
        harness.openDocument(testUri, code)
        
        // Cursor inside parentheses of foo(10, 20)
        val help = harness.signatureHelp(testUri, 2, 21)
        help shouldNotBe null
        help!!.signatures.size shouldBe 1
        help.signatures[0].label shouldBe "sub foo(a: ubyte, b: ubyte) -> ubyte"
    }
})
