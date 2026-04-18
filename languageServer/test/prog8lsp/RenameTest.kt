package prog8lsp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier

class RenameTest: FunSpec({
    lateinit var harness: LspTestHarness
    val testUri = "file:///test.p8"

    beforeTest {
        harness = LspTestHarness()
        harness.setup()
    }

    afterTest {
        harness.shutdown()
    }

    test("rename variable - scope awareness check") {
        val code = """
            main {
                sub a() {
                    ubyte x = 1
                    ubyte y = x
                }
                sub b() {
                    ubyte x = 2
                    ubyte z = x
                }
            }
        """.trimIndent()
        harness.openDocument(testUri, code)
        
        // Rename 'x' in sub a() to 'newName'
        // Line 2: ubyte x = 1
        // Line 3: ubyte y = x
        
        val renameParams = RenameParams()
        renameParams.textDocument = TextDocumentIdentifier(testUri)
        renameParams.position = org.eclipse.lsp4j.Position(2, 14) // 'x' in "ubyte x = 1"
        renameParams.newName = "newName"
        
        val workspaceEdit = harness.server.textDocumentService.rename(renameParams).get()
        
        val edits = workspaceEdit.changes[testUri]!!
        // If scope-aware, it should only have 2 edits (line 2 and line 3)
        // If not scope-aware, it might have 4 edits (line 2, 3, 6, 7)
        
        edits.size shouldBe 2
        edits.any { it.range.start.line == 2 } shouldBe true
        edits.any { it.range.start.line == 3 } shouldBe true
        edits.any { it.range.start.line == 6 } shouldBe false
        edits.any { it.range.start.line == 7 } shouldBe false
    }
})
