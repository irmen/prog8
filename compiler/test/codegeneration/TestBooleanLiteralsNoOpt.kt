package prog8tests.codegeneration

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldNotBe
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8tests.helpers.compileText

/**
 * Tests for bugs that only occurred when compiling with -noopt (optimizations disabled).
 *
 * These tests ensure that boolean literals in logical expressions are handled correctly
 * when the optimizer doesn't remove them.
 */
class TestBooleanLiteralsNoOpt: FunSpec({

    val outputDir = tempdir().toPath()

    test("boolean literal in AND expression with -noopt") {
        // Bug: "bool literal in logical expr should have been optimized away"
        // This occurred when compiling with -noopt and boolean literals in logical expressions
        val text = """
main {
    sub start() {
        ubyte flag = 1
        
        ; Boolean literal on right side of AND
        while flag == 0 and true {
            flag = 2
        }
        
        ; Boolean literal on right side of AND (false)
        while flag == 1 and false {
            flag = 3
        }
    }
}"""
        // Test with C64 target and VM target to ensure the fix works
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("boolean literal in OR expression with -noopt") {
        val text = """
main {
    sub start() {
        ubyte flag = 1
        
        ; Boolean literal on right side of OR
        if flag == 1 or true {
            flag = 2
        }
        
        if flag == 0 or false {
            flag = 3
        }
    }
}"""
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("boolean literal in XOR expression with -noopt") {
        val text = """
main {
    sub start() {
        ubyte flag = 1
        
        ; Boolean literals with XOR
        if true xor false {
            flag = 2
        }
        
        if false xor true {
            flag = 3
        }
    }
}"""
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("comparison with boolean literal in if condition with -noopt") {
        // Bug: "expected comparison operator" when logical operators were used
        // with boolean literals that evaluated to 0
        val text = """
main {
    sub start() {
        ubyte a = 5
        
        ; This pattern caused crash: comparison followed by AND with false (which is 0)
        if a == 5 and false {
            a = 1
        } else {
            a = 2
        }
        
        ; AND with true
        if a == 2 and true {
            a = 3
        }
        
        ; OR with false
        if a == 3 or false {
            a = 4
        }
        
        ; OR with true
        if a == 4 or true {
            a = 5
        }
    }
}"""
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("complex logical expression with multiple boolean literals -noopt") {
        val text = """
main {
    sub start() {
        ubyte x = 0
        ubyte y = 1
        
        ; Multiple boolean literals in complex expression
        if (x == 0 and true) or (y == 1 and false) {
            x = 1
        }
        
        ; Nested logical operations
        if (x == 1 and true) and (y == 1 or false) {
            x = 2
        }
        
        ; XOR with comparisons
        if (x == 2 xor false) {
            x = 3
        }
    }
}"""
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("boolean literal with pointer dereference in condition -noopt") {
        // Test the original bug pattern from floatparse.p8
        val text = """
main {
    sub start() {
        uword stringptr = $1000
        ubyte flag = 0
        
        poke(stringptr, '0')
        
        ; Pattern similar to: while mantissa == 0 and @(stringptr) == '0'
        ; but with boolean literal
        while flag == 0 and @(stringptr) == '0' {
            flag = 1
        }
        
        ; With boolean literal
        while flag == 1 and true {
            flag = 2
        }
    }
}"""
        compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), false, text, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("same tests with optimizations enabled still work") {
        // Ensure our fix doesn't break the optimized compilation path
        val text = """
main {
    sub start() {
        ubyte flag = 1
        
        ; These should be optimized away completely
        while flag == 0 and true {
            flag = 2
        }
        
        if flag == 1 and false {
            flag = 3
        }
        
        if true xor false {
            flag = 4
        }
    }
}"""
        compileText(C64Target(), true, text, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), true, text, outputDir, writeAssembly = true) shouldNotBe null
    }
})
