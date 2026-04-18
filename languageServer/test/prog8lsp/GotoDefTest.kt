package prog8lsp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class GotoDefTest: FunSpec({
    lateinit var harness: LspTestHarness
    val testUri = "file:///test.p8"

    beforeTest {
        harness = LspTestHarness()
        harness.setup()
    }

    afterTest {
        harness.shutdown()
    }

    test("go to definition - variable use site") {
        val code = """
            main {
                sub start() {
                    ubyte val1 = 99
                    ubyte val2 = val1
                }
            }
        """.trimIndent()
        harness.openDocument(testUri, code)
        
        // val1 in "ubyte val2 = val1"
        // line 3 (0-indexed), char 21
        // u b y t e _ v a l 2 _ = _ v a l 1
        // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 
        // In trimIndent(), it is:
        // sub start() {
        // 01234567890123
        //     ubyte val2 = val1
        //     012345678901234567890
        // val1 starts at col 17
        
        val locations = harness.definition(testUri, 3, 22)
        
        locations.size shouldBe 1
        locations[0].range.start.line shouldBe 2 // Declaration at line 2 (0-indexed)
    }
})
