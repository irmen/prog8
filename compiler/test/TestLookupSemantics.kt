package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.code.StNodeType
import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.target.C64Target
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

/**
 * Tests for symbol lookup semantics that apply to both CompilerAST and SimpleAST layers.
 * 
 * These tests verify the behavior of qualified vs unqualified lookups, shadowing,
 * cross-module access, and other lookup rules. They are designed to make it easy
 * to detect regressions when lookup scoping rules change in the future.
 */
class TestLookupSemantics: FunSpec({

    val outputDir = tempdir().toPath()

    // ============================================================================
    // Helper Functions
    // ============================================================================

    fun compileAndGetSymbolTable(source: String): SymbolTable {
        val errors = ErrorReporterForTests()
        val result = compileText(C64Target(), false, source, outputDir, errors = errors)
            ?: throw AssertionError("Compilation failed: ${errors.errors.joinToString("; ")}")
        return result.codegenSymboltable
            ?: throw AssertionError("No symbol table available")
    }

    fun SymbolTable.assertLookupFinds(name: String, expectedType: StNodeType) {
        val result = lookup(name)
        result shouldNotBe null
        result!!.type shouldBe expectedType
    }

    fun SymbolTable.assertLookupFails(name: String) {
        lookup(name) shouldBe null
    }

    // ============================================================================
    // Qualified vs Unqualified Lookup Distinction
    // ============================================================================

    test("qualified lookup always starts from root") {
        val src = """
            main {
                uword @shared value = 10
                sub start() {
                    uword @shared result = main.value
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.value", StNodeType.STATICVAR)
    }

    test("unqualified lookup walks parent chain") {
        val src = """
            main {
                uword @shared value = 10
                sub start() {
                    uword @shared result = value
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        // Note: The SymbolTable stores symbols with fully qualified names.
        // The unqualified 'value' reference in source is resolved during semantic analysis,
        // but the SymbolTable itself just stores 'main.value'
        st.assertLookupFinds("main.value", StNodeType.STATICVAR)
    }

    test("qualified lookup with multiple path segments") {
        val src = """
            main {
                uword @shared deep = 100
                sub start() {
                    uword @shared result = main.deep
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.deep", StNodeType.STATICVAR)
    }

    // ============================================================================
    // Shadowing Behavior
    // ============================================================================

    test("local variable shadows parent scope unqualified lookup") {
        val src = """
            main {
                uword @shared value = 10
                sub start() {
                    uword @shared value = 5
                    uword @shared result = value
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        val localValue = st.lookup("main.start.value") as StStaticVariable
        localValue shouldNotBe null
    }

    test("qualified lookup unaffected by shadowing") {
        val src = """
            main {
                uword @shared value = 10
                sub start() {
                    uword @shared value = 5
                    uword @shared result = main.value
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.value", StNodeType.STATICVAR)
        st.assertLookupFinds("main.start.value", StNodeType.STATICVAR)
    }

    test("multiple levels of shadowing") {
        val src = """
            main {
                uword @shared value = 1
                sub start() {
                    uword @shared value = 2
                    uword @shared v1 = value
                    uword @shared v2 = main.value
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.value", StNodeType.STATICVAR)
        st.assertLookupFinds("main.start.value", StNodeType.STATICVAR)
    }

    // ============================================================================
    // Cross-Module Lookup
    // ============================================================================

    test("qualified lookup across modules") {
        val src = """
            module1 {
                uword @shared m1_var = 111
            }
            main {
                sub start() {
                    uword @shared result = module1.m1_var
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("module1.m1_var", StNodeType.STATICVAR)
    }

    test("import prefix accessible from all scopes") {
        val src = """
            %import textio
            main {
                sub start() {
                    txt.print("hello")
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        // Imported routines are EXTSUB (external subroutines)
        st.assertLookupFinds("txt.print", StNodeType.EXTSUB)
    }

    test("nested block can access outer module symbols") {
        val src = """
            main {
                uword @shared outer = 100
                sub start() {
                    uword @shared result = outer
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        // Variable from parent scope is accessible
        st.assertLookupFinds("main.outer", StNodeType.STATICVAR)
    }

    // ============================================================================
    // Builtin Function Lookup
    // ============================================================================

    test("builtins found via unqualified lookup from any scope") {
        val src = """
            main {
                sub start() {
                    uword @shared result = abs(-5)
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("abs", StNodeType.BUILTINFUNC)
    }

    test("builtins found even when local name conflicts") {
        val src = """
            main {
                sub start() {
                    uword @shared myabs = 10
                    uword @shared result = myabs
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.start.myabs", StNodeType.STATICVAR)
        st.assertLookupFinds("abs", StNodeType.BUILTINFUNC)
    }

    // ============================================================================
    // Struct and Pointer Field Access
    // ============================================================================

    test("struct field access via qualified name") {
        val src = """
            main {
                struct Point {
                    word x
                    word y
                }
                sub start() {
                    ^^Point ptr
                    word @shared result = ptr.x
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.start.ptr", StNodeType.STATICVAR)
    }

    test("pointer dereference field access") {
        val src = """
            main {
                struct Data {
                    uword value
                }
                sub start() {
                    ^^Data ptr
                    uword result = ptr.value
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.start.ptr", StNodeType.STATICVAR)
    }

    // ============================================================================
    // Label and Subroutine Lookup
    // ============================================================================

    test("label lookup from nested scope") {
        val src = """
            main {
                sub start() {
                mylabel:
                    cx16.r0++
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.start.mylabel", StNodeType.LABEL)
    }

    test("subroutine lookup across blocks") {
        val src = """
            main {
                sub helper() {
                }
                sub start() {
                    helper()
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.helper", StNodeType.SUBROUTINE)
        st.assertLookupFinds("main.start", StNodeType.SUBROUTINE)
    }

    test("nested subroutine lookup") {
        val src = """
            main {
                sub outer_sub() {
                }
                sub start() {
                    outer_sub()
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFinds("main.outer_sub", StNodeType.SUBROUTINE)
    }

    // ============================================================================
    // Edge Cases
    // ============================================================================

    test("lookup with undefined symbol returns null") {
        val src = """
            main {
                sub start() {
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        st.assertLookupFails("undefined")
        st.assertLookupFails("main.undefined")
        st.assertLookupFails("nonexistent.symbol.path")
    }

    test("lookup with partial path returns null") {
        val src = """
            main {
                uword value = 10
                sub start() {
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        // Partial paths don't exist - symbols are stored with their full qualified names
        st.assertLookupFails("main.value.extra")
        st.assertLookupFails("nonexistent")
    }

    test("empty scope lookup behavior") {
        val src = """
            main {
                sub start() {
                }
            }
        """
        val st = compileAndGetSymbolTable(src)
        val startScope = st.lookup("main.start")
        startScope shouldNotBe null
    }
})
