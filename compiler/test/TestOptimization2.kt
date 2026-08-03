package prog8tests.compiler

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.ast.walk.IAstVisitor
import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.intermediate.IRFileReader
import prog8.vm.VmRunner
import prog8.vm.VmVariableAllocator
import prog8tests.helpers.*
import kotlin.io.path.readText


class TestOptimization2: FunSpec({
    val outputDir = tempdir().toPath()

    test("boolean comparisons without optimization can be assembled") {
        val src="""
main {
    sub start() {
        bool @shared pre_start, xxx

        if (pre_start != false and xxx) {
            return
        } else if (pre_start != false and xxx) {
            return
        }
    }
}"""
        compileText(C64Target(), false, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("correct unused block removal for virtual target") {
        val src="""
main {
    sub start() {
        cx16.r0++
    }
}

some_block {
    uword buffer = memory("arena", 2000, 0)
}


other_block {
    sub  redherring  (uword buffer)  {
        %ir {{
            loadm.w r99000,other_block.redherring.buffer
        }}
    }
}
"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = true)!!
        val virtfile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        VmRunner().runProgram(virtfile.readText(), false)
    }

    test("correct unused block removal for c64 target") {
        val src="""
main {
    sub start() {
        cx16.r0++
    }
}

some_block {
    uword buffer = memory("arena", 2000, 0)
}


other_block {
    sub  redherring  (uword buffer)  {
        %asm {{
            lda  #<p8b_other_block.p8s_redherring.p8v_buffer
            ldy  #>p8b_other_block.p8s_redherring.p8v_buffer
        }}
    }
}"""
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
    }

    test("complicated if statement optimization") {
        val src="""
main {
    sub start() {
        bool @shared ans
        ans = false
        if (ans == true) {
            return
        } else {
            goto done
        }
    done:
        return
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 6
        val ifelseCond = (st[2] as IfElse).condition as PrefixExpression
        ifelseCond.operator shouldBe "not"
        (ifelseCond.expression as IdentifierReference).nameInSource shouldBe listOf("ans")
    }

    test("inline multi-value returns in void statement context") {
        // Tests that subroutines with multi-value returns are inlined when called with 'void'
        // The void calls should be removed entirely if return values are simple (no side effects)
        val src="""
main {
    ubyte @shared v1 = 10
    ubyte @shared v2 = 20
    ubyte @shared v3 = 30
    
    sub start() {
        ubyte @shared tmp
        
        ; These void calls should be removed by the inliner
        void get_single()
        void multi_literals()
        void multi_vars()
        
        ; These expression calls should remain (but may be inlined)
        tmp = get_single()
        tmp, tmp = multi_literals()
        tmp, tmp, tmp = multi_vars()
    }
    
    sub get_single() -> ubyte {
        return 42
    }
    
    sub multi_literals() -> ubyte, ubyte {
        return 10, 20
    }
    
    sub multi_vars() -> ubyte, ubyte, ubyte {
        return v1, v2, v3
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val startSub = mainBlock.statements.find { it is Subroutine && it.name == "start" }!! as Subroutine

        // Count FunctionCallStatement nodes in start() - the void calls should be removed
        // Only the expression context calls should remain
        val voidCalls = startSub.statements.filterIsInstance<FunctionCallStatement>()

        // All 3 void calls should have been removed
        voidCalls.size shouldBe 0
    }

    test("multi-value returns inlined in expression context") {
        // Tests that multi-value returns ARE inlined when values are captured (for simple cases)
        val src="""
main {
    ubyte @shared gv1 = 10
    ubyte @shared gv2 = 20
    
    sub start() {
        ubyte result_a, result_b
        result_a, result_b = get_globals()
        cx16.r0 = result_a + result_b  ; use the variables to prevent removal
    }

    sub get_globals() -> ubyte, ubyte {
        return gv1, gv2
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val startSub = mainBlock.statements.find { it is Subroutine && it.name == "start" }!! as Subroutine
        val stmts = startSub.statements

        // Multi-value assignment should be split into separate assignments (no multi-target)
        val multiAssigns = stmts.filterIsInstance<Assignment>().filter {
            it.target.multi?.isNotEmpty() == true
        }
        multiAssigns.size shouldBe 0

        // Should have separate single assignments for result_a and result_b (not the cx16.r0 assignment)
        val resultAssigns = stmts.filterIsInstance<Assignment>().filter {
            it.target.identifier?.nameInSource?.lastOrNull() in listOf("result_a", "result_b")
        }
        resultAssigns.size shouldBe 2

        // Check the values are identifier references (to gv1 and gv2)
        val idAssigns = resultAssigns.filter { it.value is IdentifierReference }
        idAssigns.size shouldBe 2
    }

    test("inline zero-return void calls are removed") {
        // Tests that subroutines with empty return statements are inlined when called with 'void'
        // Only safe when there are no side effects in the subroutine body
        val src = """
main {
    sub start() {
        void empty_return()
        cx16.r0++
    }
    sub empty_return() {
        return
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Void call should be removed entirely (no side effects)
        val voidCalls = startSub.statements.filterIsInstance<FunctionCallStatement>()
        voidCalls.size shouldBe 0
    }

    test("void calls with side effects preserve the side effects through inlining") {
        // Tests that void calls containing function calls (side effects) preserve the side effects
        // The inliner recursively inlines the entire call chain, preserving all side effects
        val src = """
main {
    sub start() {
        void with_side_effect()
        cx16.r0++
    }
    sub with_side_effect() {
        helper()  ; this is a side effect
        return
    }
    sub helper() {
        cx16.r1++
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // The void call is inlined along with helper(), and the side effect (cx16.r1++) is preserved
        // Check that cx16.r1++ appears in the start() subroutine (proving side effect was preserved)
        val hasSideEffect = startSub.statements.any { stmt ->
            if (stmt is Assignment) {
                stmt.target.identifier?.nameInSource?.lastOrNull() == "r1" &&
                    stmt.value is BinaryExpression
            } else false
        }
        hasSideEffect shouldBe true
    }

    test("inline single-value returns in expression context") {
        // Tests that single-value returns are inlined in expression context (parameterless subs only)
        val src = """
main {
    ubyte @shared gv = 100
    
    sub start() {
        ubyte result
        result = get_global()
        cx16.r0 = result + gv  ; use result to prevent optimization
    }
    sub get_global() -> ubyte {
        return gv
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>().find { it.name == "start" }!!
        val stmts = startSub.statements

        // Should have single assignment with identifier reference (gv), not function call
        val assigns = stmts.filterIsInstance<Assignment>()
            .filter { it.target.identifier?.nameInSource?.lastOrNull() == "result" }
        assigns.size shouldBe 1
        assigns[0].value shouldBe instanceOf<IdentifierReference>()
    }

    test("inline three-value returns in expression context") {
        // Tests that three-value returns are inlined when values are captured
        val src = """
main {
    ubyte @shared gv1 = 10
    ubyte @shared gv2 = 20
    ubyte @shared gv3 = 30

    sub start() {
        ubyte result_a, result_b, result_c
        result_a, result_b, result_c = get_globals()
        cx16.r0 = result_a + result_b + result_c
    }
    sub get_globals() -> ubyte, ubyte, ubyte {
        return gv1, gv2, gv3
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val startSub = mainBlock.statements.filterIsInstance<Subroutine>().find { it.name == "start" }!!
        val stmts = startSub.statements

        // Multi-assignment should be split into separate assignments (no multi-target)
        val multiAssigns = stmts.filterIsInstance<Assignment>()
            .filter { it.target.multi?.isNotEmpty() == true }
        multiAssigns.size shouldBe 0

        // Should have 3 separate single assignments
        val resultAssigns = stmts.filterIsInstance<Assignment>()
            .filter { it.target.identifier?.nameInSource?.lastOrNull() in listOf("result_a", "result_b", "result_c") }
        resultAssigns.size shouldBe 3

        // All should be identifier references
        val idAssigns = resultAssigns.filter { it.value is IdentifierReference }
        idAssigns.size shouldBe 3
    }

    test("inline void call with one parameter") {
        // Tests that void calls with one parameter are inlined when args are simple
        val src = """
main {
    sub start() {
        void take_one(1)
        cx16.r0++
    }
    sub take_one(ubyte p) {
        return
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Void call should be removed entirely
        val hasVoidCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement &&
                stmt.target.nameInSource.last() == "take_one" &&
                stmt.void
        }
        hasVoidCall shouldBe false
    }

    xtest("inline void call with two parameters") {
        // Tests that void calls with two parameters are inlined when args are simple
        val src = """
main {
    sub start() {
        void take_two(1, 2)
        cx16.r0++
    }
    sub take_two(ubyte a, ubyte b) {
        return
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Void call should be removed entirely
        val hasVoidCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement &&
                stmt.target.nameInSource.last() == "take_two" &&
                stmt.void
        }
        hasVoidCall shouldBe false
    }

    test("inline call with one return value and one parameter") {
        // Tests that function calls returning one value with one parameter are inlined
        // and that the parameter is correctly substituted with the argument
        val src = """
main {
    ubyte @shared gv = 100

    sub start() {
        ubyte result
        result = get_value(42)
        cx16.r0 = result + gv
    }
    sub get_value(ubyte x) -> ubyte {
        return gv + x
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Should have assignment with binary expression (parameter substituted with literal 42)
        val assigns = startSub.statements.filterIsInstance<Assignment>()
            .filter { it.target.identifier?.nameInSource?.lastOrNull() == "result" }
        assigns.size shouldBe 1
        val value = assigns[0].value
        value shouldBe instanceOf<BinaryExpression>()
        // Verify the parameter x was replaced with argument 42
        val binExpr = value as BinaryExpression
        binExpr.right shouldBe instanceOf<NumericLiteral>()
    }

    xtest("inline call with two return values and two parameters") {
        // Tests that function calls returning two values with two parameters are inlined
        // and that both parameters are correctly substituted
        val src = """
main {
    ubyte @shared v1 = 10
    ubyte @shared v2 = 20

    sub start() {
        ubyte a, b
        a, b = get_two(1, 2)
        cx16.r0 = a + b
    }
    sub get_two(ubyte x, ubyte y) -> ubyte, ubyte {
        return v1 + x, v2 + y
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint
        val stmts = startSub.statements

        // Multi-assignment should be split into separate assignments
        val multiAssigns = stmts.filterIsInstance<Assignment>()
            .filter { it.target.multi?.isNotEmpty() == true }
        multiAssigns.size shouldBe 0

        // Should have 2 separate single assignments
        val resultAssigns = stmts.filterIsInstance<Assignment>()
            .filter { it.target.identifier?.nameInSource?.lastOrNull() in listOf("a", "b") }
        resultAssigns.size shouldBe 2

        // All should be binary expressions (parameter substitution occurred)
        val binAssigns = resultAssigns.filter { it.value is BinaryExpression }
        binAssigns.size shouldBe 2

        // Verify parameters were replaced with arguments (1 and 2)
        // The right side of each binary expression should be a NumericLiteral
        binAssigns.forEach { assign ->
            val binExpr = assign.value as BinaryExpression
            binExpr.right shouldBe instanceOf<NumericLiteral>()
        }
    }

    test("call with two parameters where one has a register alias is not inlined") {
        // Tests that a function call with two parameters where one has a register alias is NOT inlined
        val src = """
main {
    ubyte @shared v1 = 10
    ubyte @shared v2 = 20

    sub start() {
        ubyte a
        a = get_two(1, 2)
        cx16.r0 = a
    }
    sub get_two(ubyte x, ubyte y @R0) -> ubyte {
        return v1
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint
        val stmts = startSub.statements

        // Should NOT be inlined, so the FunctionCallExpression should still exist in start()
        val callNodes = stmts.filter { stmt ->
            if (stmt is Assignment) stmt.value is FunctionCallExpression && (stmt.value as FunctionCallExpression).target.nameInSource.last() == "get_two"
            else false
        }

        // There should be exactly one call remaining
        callNodes.size shouldBe 1
    }

    xtest("inline void call with six parameters") {
        // Tests that void calls with six parameters are inlined when args are simple
        val src = """
main {
    sub start() {
        void take_six(1, 2, 3, 4, 5, 6)
        cx16.r0++
    }
    sub take_six(ubyte a, ubyte b, ubyte c, ubyte d, ubyte e, ubyte f) {
        return
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Void call should be removed entirely
        val hasVoidCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement &&
                stmt.target.nameInSource.last() == "take_six" &&
                stmt.void
        }
        hasVoidCall shouldBe false
    }

    test("parameterized subroutine with 'inline' is inlined (1 parameter)") {
        val src = """
main {
    sub start() {
        foo(1)
    }
    inline sub foo(ubyte x) {
        cx16.r0 = x
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Should no longer contain a function call to foo
        val hasFooCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement &&
                stmt.target.nameInSource.last() == "foo"
        }
        hasFooCall shouldBe false
    }

    test("parameterized subroutine WITHOUT 'inline' is auto-inlined (1 parameter)") {
        val src = """
main {
    sub start() {
        foo(1)
    }
    sub foo(ubyte x) {
        cx16.r0 = x
    }
}"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Should no longer contain a function call to foo
        val hasFooCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement &&
                stmt.target.nameInSource.last() == "foo"
        }
        hasFooCall shouldBe false
    }

    test("parameterized subroutine with 1 simple argument is inlined (auto-inlining)") {
        val src = """
        main {
            sub start() {
                ubyte @shared a = 10
                ubyte @shared b = add_one(a)
                ubyte @shared c = add_one(20)
            }
            sub add_one(ubyte x) -> ubyte {
                return x + 1
            }
        }"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Should no longer contain a function call to add_one
        val hasAddOneCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement &&
                stmt.target.nameInSource.last() == "add_one"
        }
        val hasAddOneCallExpr = startSub.statements.any { stmt ->
            (stmt as? Assignment)?.value.let { it is FunctionCallExpression && it.target.nameInSource.last() == "add_one" }
        }
        hasAddOneCall shouldBe false
        hasAddOneCallExpr shouldBe false

        // Should have assignments with incremented values or binary expressions
        val assigns = startSub.statements.filterIsInstance<Assignment>()
        assigns.any { (it.value as? BinaryExpression)?.right is NumericLiteral && (it.value as BinaryExpression).operator == "+" } shouldBe true
    }

    test("parameterized subroutine with 1 simple argument is inlined (manual 'inline')") {
        val src = """
        main {
            sub start() {
                ubyte @shared a = 10
                ubyte @shared b = square(a)
            }
            inline sub square(ubyte x) -> ubyte {
                return x * x
            }
        }"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Should no longer contain a function call to square
        val hasSquareCall = startSub.statements.any { stmt ->
            (stmt as? Assignment)?.value.let { it is FunctionCallExpression && it.target.nameInSource.last() == "square" }
        }
        hasSquareCall shouldBe false
    }

    test("parameterized subroutine with complex argument is NOT inlined") {
        val src = """
        main {
            sub start() {
                ubyte @shared b = add_one(get_val())
            }
            sub get_val() -> ubyte {
                cx16.r0++
                return 10
            }
            inline sub add_one(ubyte x) -> ubyte {
                return x + 1
            }
        }"""
        val result = compileText(VMTarget(), true, src, outputDir, writeAssembly = false)!!
        val startSub = result.compilerAst.entrypoint

        // Should STILL contain a function call to add_one because get_val() has side effects
        val hasAddOneCall = startSub.statements.any { stmt ->
            (stmt as? Assignment)?.value.let { it is FunctionCallExpression && it.target.nameInSource.last() == "add_one" }
        }
        hasAddOneCall shouldBe true
    }


    test("memory-mapped IO reads should not be eliminated (c64)") {
        val src = $$"""
main {
    &ubyte io_reg = $d021
    ubyte @shared result
    sub start() {
        result = io_reg
        result = io_reg
        result = io_reg
        io_reg = 0
        io_reg = 0
        io_reg = 0
    }
}"""
        val result = compileText(C64Target(), true, src, outputDir, writeAssembly = true)!!

        // Check the SimpleAst (codegenAst) before assembly: should have 3 reads and 3 writes to io_reg
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.drop(1).filterIsInstance<PtAssignment>()
        val ioRegReads = assignments.count { a ->
            (a.value as? PtIdentifier)?.name?.endsWith("io_reg")==true
        }
        val ioRegWrites = assignments.count { a ->
            a.target.identifier?.name?.endsWith("io_reg")==true
        }
        ioRegReads shouldBe 3
        ioRegWrites shouldBe 3

        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()

        // $d021 is an IO address so every read and write should be there and not optimized away, there should be 3 loads and 3 stores
        val ioAccessCount = asm.lines().count { (it.contains("lda ") || it.contains("sta ") || it.contains("stz ")) && it.contains("io_reg") }
        ioAccessCount shouldBe 6
    }

    test("memory-mapped IO reads should not be eliminated (cx16)") {
        val src = $$"""
main {
    &ubyte io_reg = $9f01
    ubyte @shared result
    sub start() {
        result = io_reg
        result = io_reg
        result = io_reg
        io_reg = 0
        io_reg = 0
        io_reg = 0
    }
}"""
        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = true)!!

        // Check the SimpleAst (codegenAst) before assembly: should have 3 reads and 3 writes to io_reg
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.drop(1).filterIsInstance<PtAssignment>()
        val ioRegReads = assignments.count { a ->
            (a.value as? PtIdentifier)?.name?.endsWith("io_reg")==true
        }
        val ioRegWrites = assignments.count { a ->
            a.target.identifier?.name?.endsWith("io_reg")==true
        }
        ioRegReads shouldBe 3
        ioRegWrites shouldBe 3

        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()

        // $d021 is an IO address so every read and write should be there and not optimized away, there should be 3 loads and 3 stores
        val ioAccessCount = asm.lines().count { (it.contains("lda ") || it.contains("sta ") || it.contains("stz ")) && it.contains("io_reg") }
        ioAccessCount shouldBe 6
    }

    test("redundant initialization warning only shown for explicit initializers") {
        val srcWithInit = """
            %import textio
            main {
                sub start() {
                    ubyte a = 0
                    txt.print("gap\n")
                    a = 10
                    txt.print_ub(a)
                }
            }
        """.trimIndent()
        val errorsWithInit = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, srcWithInit, outputDir, errors = errorsWithInit)
        errorsWithInit.warnings.any { "variable 'a' (declared at line 4) is only assigned here" in it } shouldBe true

        val srcWithoutInit = """
            %import textio
            main {
                sub start() {
                    ubyte a
                    txt.print("gap\n")
                    a = 10
                    txt.print_ub(a)
                }
            }
        """.trimIndent()
        val errorsWithoutInit = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(VMTarget(), true, srcWithoutInit, outputDir, errors = errorsWithoutInit)
        errorsWithoutInit.warnings.shouldBeEmpty()
    }

    test("redundant initialization is removed even if warning is suppressed") {
        val srcWithoutInit = """
            main {
                sub start() {
                    ubyte a
                    cx16.r0++   ; gap
                    a = 10
                    cx16.r1 = a
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, srcWithoutInit, outputDir)!!
        val startSub = result.compilerAst.entrypoint
        // 'a' should now be a CONST so there are no assignments to it anymore
        val assignments = startSub.statements.filterIsInstance<Assignment>()
        assignments.filter { it.target.identifier?.nameInSource?.singleOrNull() == "a" }.size shouldBe 0
        val aDecl = startSub.allDefinedSymbols.find { it.first == "a" }!!.second as VarDecl
        aDecl.type shouldBe VarDeclType.CONST
        aDecl.value!!.constValue(result.compilerAst)!!.number shouldBe 10.0
    }

    test("inline keyword on regular subroutine") {
        val src = """
            %import textio
            main {
                sub start() {
                    txt.print_ub(foo())
                }
                inline sub foo() -> ubyte {
                    return 42
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val startSub = result.compilerAst.entrypoint

        // foo() should be inlined, so there should be a call to txt.print_ub with 42
        val printCall = startSub.statements.filterIsInstance<FunctionCallStatement>()
            .find { it.target.nameInSource.last() == "print_ub" }
        printCall shouldNotBe null
        printCall!!.args[0].constValue(result.compilerAst)!!.number shouldBe 42.0

        // The original foo() call should be gone
        val fooCall = startSub.statements.any { it is FunctionCallStatement && it.target.nameInSource.last() == "foo" }
        fooCall shouldBe false
    }
    test("multiplication by negative power of two (simpleAst)") {
        val src = """
            main {
                sub start() {
                    word @shared x = 10
                    word y1 = x * -2
                    word y2 = x * -4
                    word y3 = -8 * x
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.filterIsInstance<PtAssignment>()

        val y1 = assignments.find { it.target.identifier?.name?.endsWith("y1") == true }!!.value as PtBinaryExpression
        val y2 = assignments.find { it.target.identifier?.name?.endsWith("y2") == true }!!.value as PtBinaryExpression
        val y3 = assignments.find { it.target.identifier?.name?.endsWith("y3") == true }!!.value as PtBinaryExpression

        y1.operator shouldBe "<<"
        (y1.left as PtPrefix).operator shouldBe "-"
        (y1.right as PtNumber).number shouldBe 1.0

        y2.operator shouldBe "<<"
        (y2.left as PtPrefix).operator shouldBe "-"
        (y2.right as PtNumber).number shouldBe 2.0

        y3.operator shouldBe "<<"
        (y3.left as PtPrefix).operator shouldBe "-"
        (y3.right as PtNumber).number shouldBe 3.0
    }

    test("self-referential subroutine is not inlined (would cause infinite loop)") {
        val src = """
            main {
                sub start() {
                    void self_ref()
                    cx16.r0++
                }
                sub self_ref() {
                    self_ref()
                }
            }
        """
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val startSub = result.compilerAst.entrypoint

        // The self-referential call should NOT be inlined - verify the function call still exists
        val hasSelfRefCall = startSub.statements.any { stmt ->
            stmt is FunctionCallStatement && stmt.target.nameInSource.lastOrNull() == "self_ref"
        }
        hasSelfRefCall shouldBe true
    }

    test("string literals in struct instances are not deduplicated") {
        val src = """
            main {
                struct Node {
                    str name
                }
                
                ^^Node n1 = ^^Node:["test"]
                ^^Node n2 = ^^Node:["test"]
                
                sub start() {
                    cx16.r0 = n1^^.name
                    cx16.r1 = n2^^.name
                }
            }
        """
        val errors = ErrorReporterForTests()
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        if (result == null) {
            println(errors.errors)
            fail("Compilation failed: " + errors.errors)
        }

        class InitializerCollector : IAstVisitor {
            val initializers = mutableListOf<StaticStructInitializer>()
            override fun visit(initializer: StaticStructInitializer) {
                initializers.add(initializer)
                // Default traversal
                initializer.structname.accept(this)
                initializer.args.forEach { it.accept(this) }
            }
        }

        val collector = InitializerCollector()
        result.compilerAst.modules.forEach { it.accept(collector) }

        collector.initializers.size shouldBe 2

        // The first argument is the string (the "name" field)
        fun getIdentifier(expr: Expression): IdentifierReference {
            return when (expr) {
                is IdentifierReference -> expr
                is AddressOf -> expr.identifier!!
                else -> fail("Expected IdentifierReference or AddressOf, got " + expr::class.simpleName)
            }
        }
        val name1 = getIdentifier(collector.initializers[0].args[0])
        val name2 = getIdentifier(collector.initializers[1].args[0])

        // They should point to different interned strings
        name1.nameInSource shouldNotBe name2.nameInSource
    }

    test("signed modulo strength reduction") {
        val src = """
            main {
                sub start() {
                    byte @shared x = -5
                    byte @shared res = x % 8
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.filterIsInstance<IPtAssignment>()
        val resAssign = assignments.find { it.target.identifier?.name?.contains("res") == true }
            ?: fail("Could not find assignment to 'res'")
        val resMod = resAssign.value
        resMod shouldBe instanceOf<PtBinaryExpression>()
        (resMod as PtBinaryExpression).operator shouldBe "%"
    }

    test("unsigned comparison wraparound") {
        val src = """
            main {
                sub start() {
                    ubyte @shared x = 10
                    ubyte @shared y = 0
                    bool @shared b1 = x <= (y - 1)
                    ubyte @shared y2 = 255
                    bool @shared b2 = x >= (y2 + 1)
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, src, outputDir)!!
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.filterIsInstance<IPtAssignment>()

        val b1Assign = assignments.find { it.target.identifier?.name?.contains("b1") == true }
            ?: fail("Could not find assignment to 'b1'")
        val b1 = b1Assign.value as PtBinaryExpression
        // Might be swapped to (y-1) >= x
        if (b1.operator == "<=") {
            (b1.left as PtIdentifier).name shouldContain "x"
        } else {
            b1.operator shouldBe ">="
            (b1.right as PtIdentifier).name shouldContain "x"
        }

        val b2Assign = assignments.find { it.target.identifier?.name?.contains("b2") == true }
            ?: fail("Could not find assignment to 'b2'")
        val b2 = b2Assign.value as PtBinaryExpression
        // Might be swapped to (y2+1) <= x
        if (b2.operator == ">=") {
            (b2.left as PtIdentifier).name shouldContain "x"
        } else {
            b2.operator shouldBe "<="
            (b2.right as PtIdentifier).name shouldContain "x"
        }
    }

    test("identity folding with side effects (volatility)") {
        val src = """
            main {
                sub start() {
                    bool @shared b = peek(${'$'}d012) == peek(${'$'}d012)
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        val result = compileText(C64Target(), true, src, outputDir, errors = errors)
        if (result == null) {
            println(errors.errors)
            fail("Compilation failed: " + errors.errors)
        }
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.filterIsInstance<IPtAssignment>()
        val bAssign = assignments.find { it.target.identifier?.name?.contains("b") == true }
            ?: fail("Could not find assignment to 'b'")
        val b = bAssign.value

        // Should not be folded to 'true'
        b shouldBe instanceOf<PtBinaryExpression>()
        (b as PtBinaryExpression).operator shouldBe "=="
        b.left shouldBe instanceOf<PtMemoryByte>()
        b.right shouldBe instanceOf<PtMemoryByte>()
    }

    test("floating-point algebraic identities") {
        val src = """
            %option enable_floats
            main {
                sub start() {
                    float @shared f = 0.0
                    float @shared res1 = f * 0.0
                    float @shared res2 = 0.0 * f
                    float @shared res3 = 0.0 / f
                }
            }
        """.trimIndent()
        val errors = ErrorReporterForTests()
        val result = compileText(VMTarget(), true, src, outputDir, errors = errors)
        if (result == null) {
            println(errors.errors)
            fail("Compilation failed: " + errors.errors)
        }
        val sub = result.codegenAst!!.entrypoint()!!
        val assignments = sub.children.filterIsInstance<IPtAssignment>()

        val res1Assign = assignments.find { it.target.identifier?.name?.contains("res1") == true }!!
        res1Assign.value shouldBe instanceOf<PtBinaryExpression>()
        (res1Assign.value as PtBinaryExpression).operator shouldBe "*"

        val res2Assign = assignments.find { it.target.identifier?.name?.contains("res2") == true }!!
        res2Assign.value shouldBe instanceOf<PtBinaryExpression>()
        (res2Assign.value as PtBinaryExpression).operator shouldBe "*"

        val res3Assign = assignments.find { it.target.identifier?.name?.contains("res3") == true }!!
        res3Assign.value shouldBe instanceOf<PtBinaryExpression>()
        (res3Assign.value as PtBinaryExpression).operator shouldBe "/"
    }
    test("asmsub arguments correctly handled when inlined (assembly check)") {
        val src = """
            main {
                sub start() {
                    ubyte @shared a = 10
                    call_asm(a)
                }
                inline sub call_asm(ubyte x) {
                    my_asmsub(x + 1)
                }
                asmsub my_asmsub(ubyte val @A) {
                    %asm {{
                        sta $02
                        rts
                    }}
                }
            }
        """.trimIndent()

        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = true)!!
        val asmFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".asm")
        val asm = asmFile.readText()
        // verify that the argument passing code (inc a for x+1) is present
        asm shouldContain "inc  a"
        asm shouldContain "p8s_my_asmsub"
    }

    test("inliner relinks parent pointers (static check)") {
        val src = """
            main {
                sub start() {
                    call_asm(10)
                }
                inline sub call_asm(ubyte x) {
                    foo(x)
                }
                sub foo(ubyte x) {}
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val program = result.compilerAst
        val root = program.namespace

        fun checkParents(node: Node, expectedParent: Node) {
            if (node !== root as Node && node.parent !== expectedParent && expectedParent !== ParentSentinel) {
                fail("Parent mismatch at ${node::class.simpleName} ($node): expected $expectedParent but got ${node.parent}")
            }
            when (node) {
                is IStatementContainer -> node.statements.forEach { checkParents(it, node) }
                is Subroutine -> {
                    node.parameters.forEach { checkParents(it, node) }
                    node.statements.forEach { checkParents(it, node) }
                }
                is FunctionCallStatement -> {
                    checkParents(node.target, node)
                    node.args.forEach { checkParents(it, node) }
                }
                is FunctionCallExpression -> {
                    checkParents(node.target, node)
                    node.args.forEach { checkParents(it, node) }
                }
                is BinaryExpression -> {
                    checkParents(node.left, node)
                    checkParents(node.right, node)
                }
                is PrefixExpression -> checkParents(node.expression, node)
                is Return -> node.values.forEach { checkParents(it, node) }
                is Assignment -> {
                    checkParents(node.target, node)
                    checkParents(node.value, node)
                }
                is VarDecl -> node.value?.let { checkParents(it, node) }
                is Module -> node.statements.forEach { checkParents(it, node) }
                is GlobalNamespace -> node.modules.forEach { checkParents(it, node) }
                is Block -> node.statements.forEach { checkParents(it, node) }
            }
        }

        checkParents(root, ParentSentinel)
    }

    test("A = A +/- (B +/- N) split keeps correct values (sign-bug regression)") {
        // Regression test for the "A = A +/- (B +/- N)" split in StatementOptimizer.
        // The split rewrites  A = A op1 (B op2 N)  into  A = A op1 B ; A = A secondOp N
        // where secondOp must keep the combined expression equivalent:
        //   A+(B+N)=A+N,  A+(B-N)=A-N,  A-(B+N)=A-N,  A-(B-N)=A+N
        // The buggy version hardcoded secondOp = op2, which gave the wrong sign
        // for the A-... cases (e.g. A-(B-N) became A-B-N instead of A-B+N).
        val source = """
            main {
                sub start() {
                    uword @shared a = 10
                    uword @shared b = 3
                    a = a + (b + 5)      ; 10+3+5 = 18

                    uword @shared c = 20
                    uword @shared d = 4
                    c = c - (d - 2)      ; 20-(4-2) = 18

                    uword @shared g = 50
                    uword @shared h = 5
                    g = g - (h + 3)      ; 50-(5+3) = 42

                    uword @shared e = 100
                    uword @shared f = 7
                    e = e + (f - 3)      ; 100+7-3 = 104
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, source, outputDir)!!
        val irFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = irFile.readText()
        val irProgram = IRFileReader().read(irContent)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations

        VmRunner().runAndTestProgram(irContent) { vm ->
            // a = a + (b + 5)   -> 10+3+5  = 18
            vm.memory.getUW(allocations["main.start.a"]!!) shouldBe 18u
            // c = c - (d - 2)   -> 20-(4-2) = 18   (buggy split gave 14)
            vm.memory.getUW(allocations["main.start.c"]!!) shouldBe 18u
            // g = g - (h + 3)   -> 50-(5+3) = 42   (buggy split gave 48)
            vm.memory.getUW(allocations["main.start.g"]!!) shouldBe 42u
            // e = e + (f - 3)   -> 100+7-3 = 104
            vm.memory.getUW(allocations["main.start.e"]!!) shouldBe 104u
        }
    }

    test("A = A +/- (B * N) is NOT split (only additive inner op splits)") {
        // Regression: the split optimization may only fire when the inner operator
        // is + or - (e.g. A = A - (B * N) must stay a subtraction of a product).
        // The midpoint disc algorithm in monogfx relies on this:
        //   decisionOver2 -= radius * 2    must NOT become   decisionOver2 = decisionOver2 - radius ; decisionOver2 = decisionOver2 + 2
        val source = """
            main {
                sub start() {
                    word @shared d = 5
                    ubyte @shared r = 3
                    r++
                    d -= r * $0002      ; 5 - (4*2) = -3
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), true, source, outputDir)!!
        val irFile = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".p8ir")
        val irContent = irFile.readText()
        val irProgram = IRFileReader().read(irContent)
        irProgram.st.stripAllPrefixes()
        val allocations = VmVariableAllocator(irProgram.st, irProgram.encoding, irProgram.options.compTarget).allocations

        VmRunner().runAndTestProgram(irContent) { vm ->
            // if the multiplier had been wrongly split into "- r ; + 2" the result would be 5-4+2 = 3
            vm.memory.getSW(allocations["main.start.d"]!!) shouldBe (-3).toShort()
        }
    }
})
