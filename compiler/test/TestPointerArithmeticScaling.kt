package prog8tests.compiler

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import prog8.ast.expressions.BinaryExpression
import prog8.ast.expressions.NumericLiteral
import prog8.ast.statements.Assignment
import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.target.Amiga500Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import prog8tests.helpers.simulate
import java.nio.file.Files

class TestPointerArithmeticScaling : FunSpec({
    val outputDir = Files.createTempDirectory("prog8test_ptr_scaling")

    test("const pointer arithmetic scaling - ubyte (scale 1)") {
        val src = $$"""
            %option no_sysinit
            main {
                const ^^ubyte p = $1000
                sub start() {
                    cx16.r0 = p + 5     ; should be $1005
                    cx16.r1 = p - 2     ; should be $1000 - 2 = $0ffe
                    cx16.r2 = &p[10]    ; should be $100a
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val startSub = result.compilerAst.entrypoint
        val assignments = startSub.statements.filterIsInstance<Assignment>()
        
        (assignments[0].value as NumericLiteral).number.toInt() shouldBe 0x1005
        (assignments[1].value as NumericLiteral).number.toInt() shouldBe 0x0ffe
        (assignments[2].value as NumericLiteral).number.toInt() shouldBe 0x100a
    }

    test("non-const pointer arithmetic scaling - ubyte (scale 1)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^ubyte @shared p = $2000
                uword @shared i = 5
                
                &ubyte res1 = $02
                &ubyte res2 = $03
                
                sub start() {
                    @(p + i) = 42
                    res1 = p[i]
                    res2 = @(p + i)
                    
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 42)
        machine.assertMemory(0x03, 42)
        machine.assertMemory(0x2005, 42)
    }

    test("non-const pointer arithmetic scaling - uword (scale 2)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^uword @shared p = $2000
                uword @shared i = 5
                
                &uword res1 = $02
                &uword res2 = $04
                &uword res3 = $06
                
                sub start() {
                    res1 = p + i        ; 2000 + 5*2 = 200a
                    res2 = p - 2        ; 2000 - 2*2 = 1ffc
                    res3 = &p[i]        ; 2000 + 5*2 = 200a
                    
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 0x0a)
        machine.assertMemory(0x03, 0x20)
        machine.assertMemory(0x04, 0xfc)
        machine.assertMemory(0x05, 0x1f)
        machine.assertMemory(0x06, 0x0a)
        machine.assertMemory(0x07, 0x20)
    }

    test("non-const pointer arithmetic scaling - float (scale 5)") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^float @shared p = $2000
                uword @shared i = 3
                
                &uword res1 = $02
                &uword res2 = $04
                &uword res3 = $06
                
                sub start() {
                    res1 = p + i        ; 2000 + 3*5 = 200f
                    res2 = p - 1        ; 2000 - 1*5 = 1ffb
                    res3 = &p[i]        ; 2000 + 3*5 = 200f
                    
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 0x0f)
        machine.assertMemory(0x03, 0x20)
        machine.assertMemory(0x04, 0xfb)
        machine.assertMemory(0x05, 0x1f)
        machine.assertMemory(0x06, 0x0f)
        machine.assertMemory(0x07, 0x20)
    }

    test("typed pointer subtraction yields element count - cx16 (scale 2, result is uword)") {
        val src = $$"""
            %option no_sysinit
            main {
                ^^uword @shared paddr1 = $2000
                ^^uword @shared paddr2 = $2006
                sub start() {
                    uword diff = paddr2 - paddr1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, src, outputDir, writeAssembly = true)!!

        // compiler AST: the subtraction infers to the target-sized (uword) address type
        val binExpr = result.compilerAst.entrypoint.statements
            .filterIsInstance<Assignment>()
            .single { it.target.identifier?.nameInSource?.singleOrNull() == "diff" }
            .value as BinaryExpression
        binExpr.inferType(result.compilerAst).getOrUndef() shouldBe result.compilationOptions.compTarget.pointerType

        // codegen AST: cast((cast(paddr2) - cast(paddr1)) >> 1)
        // (element count, /2 via shift; scaled in signed WORD so negatives work, cast back to UWORD)
        val diffAssign = result.codegenAst!!.entrypoint()!!.children
            .filterIsInstance<PtAssignment>()
            .filter { it.target.identifier?.name?.endsWith("diff") == true }
            .last()
        val casted = diffAssign.value as PtTypeCast
        casted.type.base shouldBe BaseDataType.UWORD
        val value = casted.value as PtBinaryExpression
        value.operator shouldBe ">>"
        value.type.base shouldBe BaseDataType.WORD
        val difference = value.left as PtBinaryExpression
        difference.operator shouldBe "-"
        difference.type.base shouldBe BaseDataType.WORD
        difference.left shouldBe instanceOf<PtTypeCast>()
        difference.right shouldBe instanceOf<PtTypeCast>()
        // pointer -> UWORD -> WORD nesting (the backend only supports pointer casts to the address type)
        listOf(difference.left, difference.right).forEach {
            val toSigned = it as PtTypeCast
            toSigned.type.base shouldBe BaseDataType.WORD
            val toAddr = toSigned.value as PtTypeCast
            toAddr.type.base shouldBe BaseDataType.UWORD
        }
        val shiftAmount = value.right as PtNumber
        shiftAmount.number shouldBe 1.0
    }

    test("typed pointer subtraction yields element count - amiga500 (scale 2, result is long)") {
        val src = $$"""
            main {
                ^^uword @shared paddr1 = $2000
                ^^uword @shared paddr2 = $2006
                sub start() {
                    long diff = paddr2 - paddr1
                }
            }
        """.trimIndent()
        val result = compileText(Amiga500Target(), false, src, outputDir, writeAssembly = true)!!

        val binExpr = result.compilerAst.entrypoint.statements
            .filterIsInstance<Assignment>()
            .single { it.target.identifier?.nameInSource?.singleOrNull() == "diff" }
            .value as BinaryExpression
        binExpr.inferType(result.compilerAst).getOrUndef() shouldBe result.compilationOptions.compTarget.pointerType

        val diffAssign = result.codegenAst!!.entrypoint()!!.children
            .filterIsInstance<PtAssignment>()
            .filter { it.target.identifier?.name?.endsWith("diff") == true }
            .last()
        val value = diffAssign.value as PtBinaryExpression
        value.operator shouldBe ">>"
        value.type.base shouldBe BaseDataType.LONG
        val difference = value.left as PtBinaryExpression
        difference.operator shouldBe "-"
        difference.type.base shouldBe BaseDataType.LONG
        val shiftAmount = value.right as PtNumber
        shiftAmount.number shouldBe 1.0
    }

    test("typed pointer subtraction yields element count - virtual (scale 1, just a subtraction)") {
        val src = $$"""
            %option no_sysinit
            main {
                ^^ubyte @shared paddr1 = $2000
                ^^ubyte @shared paddr2 = $2003
                long @shared diff = 0
                sub start() {
                    diff = paddr2 - paddr1
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!

        val binExpr = result.compilerAst.entrypoint.statements
            .filterIsInstance<Assignment>()
            .single { it.target.identifier?.nameInSource?.singleOrNull() == "diff" }
            .value as BinaryExpression
        binExpr.inferType(result.compilerAst).getOrUndef() shouldBe result.compilationOptions.compTarget.pointerType

        val diffAssign = result.codegenAst!!.entrypoint()!!.children
            .filterIsInstance<PtAssignment>()
            .filter { it.target.identifier?.name?.endsWith("diff") == true }
            .last()
        val value = diffAssign.value as PtBinaryExpression
        value.operator shouldBe "-"
        value.type.base shouldBe BaseDataType.LONG
        value.left shouldBe instanceOf<PtTypeCast>()
        value.right shouldBe instanceOf<PtTypeCast>()
    }

    test("typed pointer subtraction of float pointers divides by 5 - cx16") {
        val src = $$"""
            %option no_sysinit
            main {
                ^^float @shared paddr1 = $2000
                ^^float @shared paddr2 = $200a
                uword @shared diff = 0
                sub start() {
                    diff = paddr2 - paddr1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, src, outputDir, writeAssembly = true)!!
        val diffAssign = result.codegenAst!!.entrypoint()!!.children
            .filterIsInstance<PtAssignment>()
            .filter { it.target.identifier?.name?.endsWith("diff") == true }
            .last()
        val casted = diffAssign.value as PtTypeCast
        casted.type.base shouldBe BaseDataType.UWORD
        val value = casted.value as PtBinaryExpression
        value.operator shouldBe "/"
        value.type.base shouldBe BaseDataType.WORD
        val difference = value.left as PtBinaryExpression
        difference.operator shouldBe "-"
        difference.type.base shouldBe BaseDataType.WORD
        val divisor = value.right as PtNumber
        divisor.number shouldBe 5.0
        divisor.type.base shouldBe BaseDataType.WORD
    }

    test("untyped pointer subtraction is plain numeric subtraction - virtual") {
        val src = $$"""
            %option no_sysinit
            main {
                pointer @shared paddr1 = $2000
                pointer @shared paddr2 = $2003
                long @shared diff = 0
                sub start() {
                    diff = paddr2 - paddr1
                }
            }
        """.trimIndent()
        val result = compileText(VMTarget(), false, src, outputDir, writeAssembly = true)!!

        val diffAssign = result.codegenAst!!.entrypoint()!!.children
            .filterIsInstance<PtAssignment>()
            .filter { it.target.identifier?.name?.endsWith("diff") == true }
            .last()
        val value = diffAssign.value as PtBinaryExpression
        value.operator shouldBe "-"
        value.type.base shouldBe BaseDataType.LONG
        value.left shouldBe instanceOf<PtIdentifier>()
        value.right shouldBe instanceOf<PtIdentifier>()
    }

    test("typed pointer addition is rejected - cx16") {
        val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val src = $$"""
            %option no_sysinit
            main {
                ^^ubyte @shared paddr1 = $1000
                ^^ubyte @shared paddr2 = $2000
                sub start() {
                    uword sum = paddr2 + paddr1
                }
            }
        """.trimIndent()
        compileText(Cx16Target(), false, src, outputDir, errors, writeAssembly = false)
        errors.errors.any { "cannot add two pointers" in it } shouldBe true
    }

    test("typed pointer addition is rejected - amiga500") {
        val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val src = $$"""
            main {
                ^^ubyte @shared paddr1 = $1000
                ^^ubyte @shared paddr2 = $2000
                sub start() {
                    long sum = paddr2 + paddr1
                }
            }
        """.trimIndent()
        compileText(Amiga500Target(), false, src, outputDir, errors, writeAssembly = false)
        errors.errors.any { "cannot add two pointers" in it } shouldBe true
    }

    test("typed pointer addition is rejected - virtual") {
        val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val src = $$"""
            %option no_sysinit
            main {
                ^^ubyte @shared paddr1 = $1000
                ^^ubyte @shared paddr2 = $2000
                sub start() {
                    long sum = paddr2 + paddr1
                }
            }
        """.trimIndent()
        compileText(VMTarget(), false, src, outputDir, errors, writeAssembly = false)
        errors.errors.any { "cannot add two pointers" in it } shouldBe true
    }

    test("subtraction of pointers to different element types is rejected - cx16") {
        val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val src = $$"""
            %option no_sysinit
            main {
                ^^ubyte @shared paddr1 = $1000
                ^^uword @shared paddr2 = $2000
                sub start() {
                    uword diff = paddr2 - paddr1
                }
            }
        """.trimIndent()
        compileText(Cx16Target(), false, src, outputDir, errors, writeAssembly = false)
        errors.errors.any { "cannot subtract pointers of different types" in it } shouldBe true
        errors.errors.size shouldBe 1    // no follow-up "requires unsigned word or long operand" for the same expression
    }

    test("integer plus typed pointer is valid and pointer subtraction scales on 32-bit targets") {
        val src = $$"""
            main {
                ^^uword @shared paddr = $2000
                long @shared offset = 3
                long @shared plus = 0
                long @shared minus = 0
                sub start() {
                    plus = offset + paddr
                    minus = paddr - offset
                }
            }
        """.trimIndent()

        listOf(Amiga500Target(), VMTarget()).forEach { target ->
            val result = compileText(target, false, src, outputDir, writeAssembly = true)!!
            val assignments = result.codegenAst!!.entrypoint()!!.children
                .filterIsInstance<PtAssignment>()
            val plus = assignments.filter { it.target.identifier?.name?.endsWith("plus") == true }.last().value as PtBinaryExpression
            val minus = assignments.filter { it.target.identifier?.name?.endsWith("minus") == true }.last().value as PtBinaryExpression
            plus.operator shouldBe "+"
            minus.operator shouldBe "-"
            plus.type.base shouldBe BaseDataType.POINTER
            minus.type.base shouldBe BaseDataType.POINTER
        }
    }

    test("integer minus typed pointer is rejected") {
        val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
        val src = $$"""
            %option no_sysinit
            main {
                ^^ubyte @shared paddr = $2000
                uword @shared offset = 3
                sub start() {
                    uword result = offset - paddr
                }
            }
        """.trimIndent()
        compileText(Cx16Target(), false, src, outputDir, errors, writeAssembly = false)
        errors.errors.isNotEmpty() shouldBe true
    }

    test("unsupported arithmetic operators on typed pointers are rejected") {
        val operators = listOf("*", "/", "%", "&", "|", "^", "<<", ">>")
        operators.forEach { operator ->
            val errors = ErrorReporterForTests(throwExceptionAtReportIfErrors = false, keepMessagesAfterReporting = true)
            val src = $$"""
                %option no_sysinit
                main {
                    ^^ubyte @shared paddr = $2000
                    uword @shared value = 3
                    sub start() {
                        uword result = paddr $$operator value
                    }
                }
            """.trimIndent()
            compileText(Cx16Target(), false, src, outputDir, errors, writeAssembly = false)
            errors.errors.isNotEmpty() shouldBe true
        }
    }

    test("typed pointer subtraction values are correct at runtime, including negative - cx16") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^uword @shared paddr1 = $2000
                ^^uword @shared paddr2 = $2006
                &uword fwd = $02
                &uword rev = $04
                &word revSigned = $06
                sub start() {
                    fwd = paddr2 - paddr1        ; 3 elements
                    rev = paddr1 - paddr2        ; -3 wrapped = $fffd
                    revSigned = (paddr1 - paddr2) as word  ; -3 (explicit signed interpretation)
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 0x03)
        machine.assertMemory(0x03, 0x00)
        machine.assertMemory(0x04, 0xfd)
        machine.assertMemory(0x05, 0xff)
        machine.assertMemory(0x06, 0xfd)
        machine.assertMemory(0x07, 0xff)
    }

    test("float pointer subtraction values are correct at runtime, including negative - cx16") {
        val src = $$"""
            %option no_sysinit
            %launcher none
            %address $1000
            main {
                &ubyte poweroff = $f203
                ^^float @shared paddr1 = $2000
                ^^float @shared paddr2 = $200a
                &uword fwd = $02
                &uword rev = $04
                sub start() {
                    fwd = paddr2 - paddr1        ; $0a/5 = 2 elements
                    rev = paddr1 - paddr2        ; -2 wrapped = $fffe
                    poweroff = 1
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), true, src, outputDir)!!
        val machine = result.simulate()
        machine.assertMemory(0x02, 0x02)
        machine.assertMemory(0x03, 0x00)
        machine.assertMemory(0x04, 0xfe)
        machine.assertMemory(0x05, 0xff)
    }

    test("typed pointer comparisons remain valid") {
        val src = $$"""
            %option no_sysinit
            main {
                ^^ubyte @shared paddr1 = $2000
                ^^ubyte @shared paddr2 = $2003
                bool @shared eq = false
                bool @shared ne = false
                bool @shared lt = false
                bool @shared le = false
                bool @shared gt = false
                bool @shared ge = false
                sub start() {
                    eq = paddr1 == paddr2
                    ne = paddr1 != paddr2
                    lt = paddr1 < paddr2
                    le = paddr1 <= paddr2
                    gt = paddr1 > paddr2
                    ge = paddr1 >= paddr2
                }
            }
        """.trimIndent()
        val result = compileText(Cx16Target(), false, src, outputDir, writeAssembly = true)!!
        val assignments = result.codegenAst!!.entrypoint()!!.children.filterIsInstance<PtAssignment>()
        listOf("eq", "ne", "lt", "le", "gt", "ge").forEach { name ->
            val assignment = assignments.single { it.target.identifier?.name?.endsWith(name) == true }
            assignment.value shouldBe instanceOf<PtBinaryExpression>()
            (assignment.value as PtBinaryExpression).type.base shouldBe BaseDataType.BOOL
        }
    }
})
