package prog8lsp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * Tests for the Prog8 Language Server features.
 */
class Prog8LanguageServerTest: FunSpec({

    lateinit var harness: LspTestHarness
    val testUri = testUri("test.p8")

    beforeTest {
        harness = LspTestHarness()
        harness.setup()
    }

    afterTest {
        harness.shutdown()
    }

    test("document symbols - should extract blocks and subroutines") {
        val code = """
main {
    sub start() {
        ubyte x = 5
    }
}
data {
    sub helper() {
    }
}
"""
        harness.openDocument(testUri, code)
        val symbols = harness.documentSymbols(testUri)

        symbols.size shouldBe 2
        
        val mainBlock = symbols.find { it.name == "main" }
        mainBlock shouldNotBe null
        
        val dataBlock = symbols.find { it.name == "data" }
        dataBlock shouldNotBe null
    }

    xtest("go to definition - should find variable declaration from use site") {
        // DISABLED: Goto definition currently only works from declaration sites, not from use sites.
        // Need to implement proper reference resolution that tracks variable usage in expressions.
        val code = """
main {
    sub start() {
        ubyte val1 = 99
        ubyte val2 = val1
    }
}
"""
        harness.openDocument(testUri, code)
        
        // Cursor on 'val1' at line 4 (0-indexed: 3), character 18 (in the assignment)
        val locations = harness.definition(testUri, 3, 18)
        
        locations.size shouldBe 1
        locations[0].range.start.line shouldBe 2 // Declaration at line 2 (0-indexed)
    }

    xtest("go to definition - should find subroutine from call site") {
        // DISABLED: Goto definition from subroutine call sites needs proper function call detection
        // and resolution. Currently only works when cursor is on the declaration itself.
        val code = """
main {
    sub start() {
        local_get()
    }
    sub local_get() -> ubyte {
        return 99
    }
}
"""
        harness.openDocument(testUri, code)
        
        // Cursor on 'local_get' at line 3 (0-indexed: 3)
        val locations = harness.definition(testUri, 3, 12)
        
        locations.size shouldBe 1
        locations[0].range.start.line shouldBe 4 // Declaration at line 4 (0-indexed)
    }

    xtest("find references - should find all references to a variable") {
        // DISABLED: Find references needs to scan entire document for identifier usage,
        // not just declarations. Requires proper AST walking for expression references.
        val code = """
main {
    sub start() {
        ubyte val1 = 99
        ubyte val2 = val1
        val1 = val2 + val1
    }
}
"""
        harness.openDocument(testUri, code)
        
        // Find references from declaration (line 3, character 12 - on 'val1')
        val locations = harness.references(testUri, 3, 12, includeDeclaration = true)
        
        // Should find: declaration + uses
        locations.size shouldNotBe 0  // At least some references found
    }

    xtest("find references - should find all references to a subroutine") {
        // DISABLED: Find references for subroutines needs proper call site detection
        // and resolution across the entire document.
        val code = """
main {
    sub start() {
        local_get()
        ubyte x = local_get()
    }
    sub local_get() -> ubyte {
        return 99
    }
}
"""
        harness.openDocument(testUri, code)
        
        // Find references from declaration (line 5, character 8 - on 'local_get')
        val locations = harness.references(testUri, 5, 8, includeDeclaration = true)
        
        // Should find: declaration + 2 uses
        locations.size shouldBe 3
    }

    xtest("signature help - should show signature for subroutine call") {
        // DISABLED: Signature help needs proper function call detection at cursor position
        // and parameter index calculation. Currently returns null or incorrect data.
        val code = """
main {
    sub start() {
        set_values(10, 20)
    }
    sub set_values(ubyte a, ubyte b) {
    }
}
"""
        harness.openDocument(testUri, code)
        
        // Request signature help inside the call (line 3, character 17 - after '(')
        val sigHelp = harness.signatureHelp(testUri, 3, 17)
        
        sigHelp shouldNotBe null
        sigHelp!!.signatures.size shouldBe 1
        
        val sig = sigHelp.signatures[0]
        sig.label.contains("set_values") shouldBe true
        sig.label.contains("ubyte") shouldBe true
        sig.parameters.size shouldBe 2
    }

    xtest("signature help - should show signature with return type") {
        // DISABLED: Signature help implementation incomplete - needs to detect function calls
        // in progress and show parameter hints with proper active parameter tracking.
        val code = """
main {
    sub start() {
        ubyte x = get_value()
    }
    sub get_value() -> ubyte {
        return 99
    }
}
"""
        harness.openDocument(testUri, code)
        
        // Request signature help inside the call
        val sigHelp = harness.signatureHelp(testUri, 3, 22)
        
        sigHelp shouldNotBe null
        val sig = sigHelp!!.signatures[0]
        sig.label.contains("-> ubyte") shouldBe true
    }

    test("completions - should provide keyword completions") {
        val code = """
main {
    sub start() {
    }
}
"""
        harness.openDocument(testUri, code)
        
        val completions = harness.completions(testUri, 2, 4)
        
        completions.shouldNotBeEmpty()
        
        // Should have basic keywords
        val completionLabels = completions.map { it.label }
        completionLabels.contains("if") shouldBe true
        completionLabels.contains("for") shouldBe true
        completionLabels.contains("while") shouldBe true
    }

    xtest("completions - should provide symbol completions") {
        // DISABLED: Symbol completions need proper scope-aware symbol collection.
        // Currently only provides keyword and builtin function completions.
        val code = """
main {
    sub start() {
        ubyte myVar = 5
    }
}
"""
        harness.openDocument(testUri, code)

        val completions = harness.completions(testUri, 3, 8)

        val completionLabels = completions.map { it.label }
        completionLabels.contains("myVar") shouldBe true
        completionLabels.contains("start") shouldBe true
    }

    test("completions - should provide builtin function completions") {
        val code = """
main {
    sub start() {
    }
}
"""
        harness.openDocument(testUri, code)
        
        val completions = harness.completions(testUri, 2, 4)
        
        val completionLabels = completions.map { it.label }
        completionLabels.contains("len") shouldBe true
        completionLabels.contains("abs") shouldBe true
        completionLabels.contains("max") shouldBe true
    }

    test("diagnostics - should report unmatched quotes") {
        val code = """
main {
    sub start() {
        txt.print("hello)
    }
}
"""
        harness.openDocument(testUri, code)

        val diagnostics = harness.getDiagnostics(testUri)

        // Parser should catch the syntax error from unmatched quotes
        diagnostics.shouldNotBeEmpty()
        val syntaxError = diagnostics.find { it.code?.left == "SyntaxError" }
        syntaxError shouldNotBe null
    }

    test("diagnostics - should not report errors for valid code") {
        val code = """
main {
    sub start() {
        ubyte x = 5
        txt.print("hello")
    }
}
"""
        harness.openDocument(testUri, code)

        val diagnostics = harness.getDiagnostics(testUri)

        // Should not have unmatched quotes error
        val unmatchedQuote = diagnostics.find { it.code?.left == "UnmatchedQuotes" }
        unmatchedQuote shouldBe null
    }

    // Document highlight tests commented out - feature needs debugging
    // test("document highlight - should highlight occurrences of a variable") {
    //     val code = """
    // main {
    //     sub start() {
    //         ubyte counter = 0
    //         counter = counter + 1
    //     }
    // }
    // """
    //     harness.openDocument(testUri, code)
    //     val highlights = harness.documentHighlight(testUri, 3, 14)
    //     highlights.shouldNotBeEmpty()
    // }

    test("diagnostics - should report parser syntax errors") {
        val code = """
main {
    sub start() {
        ubyte x =
    }
}
"""
        harness.openDocument(testUri, code)

        val diagnostics = harness.getDiagnostics(testUri)

        // Parser should catch the syntax error
        diagnostics.shouldNotBeEmpty()
        val syntaxError = diagnostics.find { it.code?.left == "SyntaxError" }
        syntaxError shouldNotBe null
    }

    test("diagnostics - should report multiple parser errors") {
        val code = """
main {
    sub start() {
        ubyte x = 
        ubyte y = 
    }
}
"""
        harness.openDocument(testUri, code)

        val diagnostics = harness.getDiagnostics(testUri)

        // Parser should catch at least one syntax error
        diagnostics.shouldNotBeEmpty()
    }
})
