package prog8tests

import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.Program
import prog8.ast.base.DataType
import prog8.ast.base.ParentSentinel
import prog8.ast.base.Position
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.printProgram
import prog8.codegen.target.C64Target
import prog8.compiler.BeforeAsmGenerationAstChanger
import prog8.compiler.BeforeAsmTypecastCleaner
import prog8.compilerinterface.*
import prog8tests.helpers.*
import prog8tests.helpers.DummyFunctions
import prog8tests.helpers.DummyMemsizer
import prog8tests.helpers.DummyStringEncoder
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.assertSuccess
import prog8tests.helpers.compileText
import prog8tests.helpers.generateAssembly

class TestOptimization: FunSpec({
    test("remove empty subroutine except start") {
        val sourcecode = """
            main {
                sub start() {
                }
                sub empty() {
                    ; going to be removed
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val toplevelModule = result.program.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements.single() as Subroutine
        result.program.entrypoint shouldBeSameInstanceAs startSub
        withClue("only start sub should remain") {
            startSub.name shouldBe "start"
        }
        withClue("compiler has inserted return in empty subroutines") {
            startSub.statements.single() shouldBe instanceOf<Return>()
        }
    }

    test("don't remove empty subroutine if it's referenced") {
        val sourcecode = """
            main {
                sub start() {
                    uword xx = &empty
                    xx++
                }
                sub empty() {
                    ; should not be removed
                }
            }
        """
        val result = compileText(C64Target, true, sourcecode).assertSuccess()
        val toplevelModule = result.program.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements[0] as Subroutine
        val emptySub = mainBlock.statements[1] as Subroutine
        result.program.entrypoint shouldBeSameInstanceAs startSub
        startSub.name shouldBe "start"
        emptySub.name shouldBe "empty"
        withClue("compiler has inserted return in empty subroutines") {
            emptySub.statements.single() shouldBe instanceOf<Return>()
        }
    }

    test("generated constvalue from typecast inherits proper parent linkage") {
        val number = NumericLiteralValue(DataType.UBYTE, 11.0, Position.DUMMY)
        val tc = TypecastExpression(number, DataType.BYTE, false, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        tc.linkParents(ParentSentinel)
        tc.parent shouldNotBe null
        number.parent shouldNotBe null
        tc shouldBeSameInstanceAs number.parent
        val constvalue = tc.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteralValue>()
        constvalue.number shouldBe 11.0
        constvalue.type shouldBe DataType.BYTE
        constvalue.parent shouldBeSameInstanceAs tc.parent
    }

    test("generated constvalue from prefixexpr inherits proper parent linkage") {
        val number = NumericLiteralValue(DataType.UBYTE, 11.0, Position.DUMMY)
        val pfx = PrefixExpression("-", number, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        pfx.linkParents(ParentSentinel)
        pfx.parent shouldNotBe null
        number.parent shouldNotBe null
        pfx shouldBeSameInstanceAs number.parent
        val constvalue = pfx.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteralValue>()
        constvalue.number shouldBe -11.0
        constvalue.type shouldBe DataType.BYTE
        constvalue.parent shouldBeSameInstanceAs pfx.parent
    }

    test("const folding multiple scenarios +/-") {
        val source = """
            main {
                const ubyte boardHeightC = 20
                const ubyte boardOffsetC = 3

                sub start() {
                    uword load_location = 12345
                    word llw = 12345
                    cx16.r0 = load_location + 8000 + 1000 + 1000
                    cx16.r2 = 8000 + 1000 + 1000 + load_location
                    cx16.r4 = load_location + boardOffsetC + boardHeightC - 1
                    cx16.r5s = llw - 900 - 999
                    cx16.r7s = llw - 900 + 999
                }
            }"""
        val result = compileText(C64Target, true, source, writeAssembly = false).assertSuccess()
        // expected:
//        uword load_location
//        load_location = 12345
//        word llw
//        llw = 12345
//        cx16.r0 = load_location
//        cx16.r0 += 10000
//        cx16.r2 = load_location
//        cx16.r2 += 10000
//        cx16.r4 = load_location
//        cx16.r4 += 22
//        cx16.r5s = llw
//        cx16.r5s -= 1899
//        cx16.r7s = llw
//        cx16.r7s += 99
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 14

        val addR0value = (stmts[5] as Assignment).value
        val binexpr0 = addR0value as BinaryExpression
        binexpr0.operator shouldBe "+"
        binexpr0.right shouldBe NumericLiteralValue(DataType.UWORD, 10000.0, Position.DUMMY)
        val addR2value = (stmts[7] as Assignment).value
        val binexpr2 = addR2value as BinaryExpression
        binexpr2.operator shouldBe "+"
        binexpr2.right shouldBe NumericLiteralValue(DataType.UWORD, 10000.0, Position.DUMMY)
        val addR4value = (stmts[9] as Assignment).value
        val binexpr4 = addR4value as BinaryExpression
        binexpr4.operator shouldBe "+"
        binexpr4.right shouldBe NumericLiteralValue(DataType.UWORD, 22.0, Position.DUMMY)
        val subR5value = (stmts[11] as Assignment).value
        val binexpr5 = subR5value as BinaryExpression
        binexpr5.operator shouldBe "-"
        binexpr5.right shouldBe NumericLiteralValue(DataType.UWORD, 1899.0, Position.DUMMY)
        val subR7value = (stmts[13] as Assignment).value
        val binexpr7 = subR7value as BinaryExpression
        binexpr7.operator shouldBe "+"
        binexpr7.right shouldBe NumericLiteralValue(DataType.UWORD, 99.0, Position.DUMMY)
    }

    test("const folding multiple scenarios * and /") {
        val source = """
            main {
                sub start() {
                    word llw = 300
                    cx16.r0s = 9 * 2 * 10 * llw
                    cx16.r1s = llw * 9 * 2 * 10
                    cx16.r2s = llw / 30 / 3
                    cx16.r3s = llw / 2 * 10
                    cx16.r4s = llw * 90 / 5     ; not optimized because of loss of integer division precision
                }
            }"""
        val result = compileText(C64Target, true, source, writeAssembly = false).assertSuccess()
        printProgram(result.program)
        // expected:
//        word llw
//        llw = 300
//        cx16.r0s = llw
//        cx16.r0s *= 180
//        cx16.r1s = llw
//        cx16.r1s *= 180
//        cx16.r2s = llw
//        cx16.r2s /= 90
//        cx16.r3s = llw
//        cx16.r3s *= 5
//        cx16.r4s = llw
//        cx16.r4s *= 90
//        cx16.r4s /= 5
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 13

        val mulR0Value = (stmts[3] as Assignment).value
        val binexpr0 = mulR0Value as BinaryExpression
        binexpr0.operator shouldBe "*"
        binexpr0.right shouldBe NumericLiteralValue(DataType.UWORD, 180.0, Position.DUMMY)
        val mulR1Value = (stmts[5] as Assignment).value
        val binexpr1 = mulR1Value as BinaryExpression
        binexpr1.operator shouldBe "*"
        binexpr1.right shouldBe NumericLiteralValue(DataType.UWORD, 180.0, Position.DUMMY)
        val divR2Value = (stmts[7] as Assignment).value
        val binexpr2 = divR2Value as BinaryExpression
        binexpr2.operator shouldBe "/"
        binexpr2.right shouldBe NumericLiteralValue(DataType.UWORD, 90.0, Position.DUMMY)
        val mulR3Value = (stmts[9] as Assignment).value
        val binexpr3 = mulR3Value as BinaryExpression
        binexpr3.operator shouldBe "*"
        binexpr3.right shouldBe NumericLiteralValue(DataType.UWORD, 5.0, Position.DUMMY)
        val mulR4Value = (stmts[11] as Assignment).value
        val binexpr4 = mulR4Value as BinaryExpression
        binexpr4.operator shouldBe "*"
        binexpr4.right shouldBe NumericLiteralValue(DataType.UWORD, 90.0, Position.DUMMY)
        val divR4Value = (stmts[12] as Assignment).value
        val binexpr4b = divR4Value as BinaryExpression
        binexpr4b.operator shouldBe "/"
        binexpr4b.right shouldBe NumericLiteralValue(DataType.UWORD, 5.0, Position.DUMMY)
    }

    test("constantfolded and silently typecasted for initializervalues") {
        val sourcecode = """
            main {
                sub start() {
                    const ubyte TEST = 10
                    byte @shared x1 = TEST as byte + 1
                    byte @shared x2 = 1 + TEST as byte
                    ubyte @shared y1 = TEST + 1 as byte
                    ubyte @shared y2 = 1 as byte + TEST
                }
            }
        """
        val result = compileText(C64Target, false, sourcecode).assertSuccess()
        val mainsub = result.program.entrypoint
        mainsub.statements.size shouldBe 10
        val declTest = mainsub.statements[0] as VarDecl
        val declX1 = mainsub.statements[1] as VarDecl
        val initX1 = mainsub.statements[2] as Assignment
        val declX2 = mainsub.statements[3] as VarDecl
        val initX2 = mainsub.statements[4] as Assignment
        val declY1 = mainsub.statements[5] as VarDecl
        val initY1 = mainsub.statements[6] as Assignment
        val declY2 = mainsub.statements[7] as VarDecl
        val initY2 = mainsub.statements[8] as Assignment
        mainsub.statements[9] shouldBe instanceOf<Return>()
        (declTest.value as NumericLiteralValue).number shouldBe 10.0
        declX1.value shouldBe null
        declX2.value shouldBe null
        declY1.value shouldBe null
        declY2.value shouldBe null
        (initX1.value as NumericLiteralValue).type shouldBe DataType.BYTE
        (initX1.value as NumericLiteralValue).number shouldBe 11.0
        (initX2.value as NumericLiteralValue).type shouldBe DataType.BYTE
        (initX2.value as NumericLiteralValue).number shouldBe 11.0
        (initY1.value as NumericLiteralValue).type shouldBe DataType.UBYTE
        (initY1.value as NumericLiteralValue).number shouldBe 11.0
        (initY2.value as NumericLiteralValue).type shouldBe DataType.UBYTE
        (initY2.value as NumericLiteralValue).number shouldBe 11.0
    }

    test("typecasted assignment from ubyte logical expressoin to uword var") {
        val src = """
            main {
                sub start() {
                    ubyte bb
                    uword ww
                    ww = not bb or not ww       ; expression combining ubyte and uword
                }
            }
        """
        val result = compileText(C64Target, false, src, writeAssembly = false).assertSuccess()

        // ww = ((( not bb as uword)  or  not ww) as uword)
        val wwAssign = result.program.entrypoint.statements.last() as Assignment
        val expr = wwAssign.value as TypecastExpression

        wwAssign.target.identifier?.nameInSource shouldBe listOf("ww")
        expr.type shouldBe DataType.UWORD
        expr.expression.inferType(result.program) istype DataType.UBYTE shouldBe true
    }

    test("intermediate assignment steps have correct types for codegen phase (BeforeAsmGenerationAstChanger)") {
        val src = """
            main {
                sub start() {
                    ubyte bb
                    uword ww
                    bb = not bb or not ww       ; expression combining ubyte and uword
                }
            }
        """
        val result = compileText(C64Target, false, src, writeAssembly = false).assertSuccess()

        // bb = (( not bb as uword)  or  not ww)
        val bbAssign = result.program.entrypoint.statements.last() as Assignment
        val expr = bbAssign.value as BinaryExpression
        expr.operator shouldBe "or"
        expr.left shouldBe instanceOf<TypecastExpression>() // casted to word
        expr.right shouldBe instanceOf<PrefixExpression>()
        expr.left.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UWORD
        expr.right.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UWORD
        expr.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UBYTE

        val options = CompilationOptions(OutputType.RAW, LauncherType.NONE, ZeropageType.DONTUSE, emptyList(), false, true, C64Target)
        val changer = BeforeAsmGenerationAstChanger(result.program, options, ErrorReporterForTests())
        changer.visit(result.program)
        while(changer.applyModifications()>0) {
            changer.visit(result.program)
        }
        val cleaner = BeforeAsmTypecastCleaner(result.program, ErrorReporterForTests())
        cleaner.visit(result.program)
        while(cleaner.applyModifications()>0) {
            cleaner.visit(result.program)
        }
        // assignment is now split into:
        //     bb =  not bb
        //     bb = (bb or  not ww)

        val assigns = result.program.entrypoint.statements.filterIsInstance<Assignment>()
        val bbAssigns = assigns.filter { it.value !is NumericLiteralValue }
        bbAssigns.size shouldBe 2

        bbAssigns[0].target.identifier!!.nameInSource shouldBe listOf("bb")
        bbAssigns[0].value shouldBe instanceOf<PrefixExpression>()
        (bbAssigns[0].value as PrefixExpression).operator shouldBe "not"
        (bbAssigns[0].value as PrefixExpression).expression shouldBe IdentifierReference(listOf("bb"), Position.DUMMY)
        bbAssigns[0].value.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UBYTE

        bbAssigns[1].target.identifier!!.nameInSource shouldBe listOf("bb")
        val bbAssigns1expr = bbAssigns[1].value as BinaryExpression
        bbAssigns1expr.operator shouldBe "or"
        bbAssigns1expr.left shouldBe IdentifierReference(listOf("bb"), Position.DUMMY)
        bbAssigns1expr.right shouldBe instanceOf<PrefixExpression>()
        (bbAssigns1expr.right as PrefixExpression).operator shouldBe "not"
        (bbAssigns1expr.right as PrefixExpression).expression shouldBe IdentifierReference(listOf("ww"), Position.DUMMY)
        bbAssigns1expr.inferType(result.program).getOrElse { fail("dt") } shouldBe DataType.UBYTE

        val asm = generateAssembly(result.program, options)
        asm.valid shouldBe true
    }

    test("intermediate assignment steps generated for typecasted expression") {
        val src = """
            main {
                sub start() {
                    ubyte r
                    ubyte @shared bb = (cos8(r)/2 + 100) as ubyte
                }
            }
        """
        val result = compileText(C64Target, true, src, writeAssembly = true).assertSuccess()
        /* turned into:
        ubyte r
        r = 0
        ubyte bb
        prog8_lib.retval_interm_b = cos8(r)
        prog8_lib.retval_interm_b >>= 1
        prog8_lib.retval_interm_b += 100
        bb = prog8_lib.retval_interm_b
        return
         */
        val st = result.program.entrypoint.statements
        st.size shouldBe 8
        st.last() shouldBe instanceOf<Return>()
        var assign = st[3] as Assignment
        assign.target.identifier!!.nameInSource shouldBe listOf("prog8_lib","retval_interm_b")
        assign = st[4] as Assignment
        assign.target.identifier!!.nameInSource shouldBe listOf("prog8_lib","retval_interm_b")
        assign = st[5] as Assignment
        assign.target.identifier!!.nameInSource shouldBe listOf("prog8_lib","retval_interm_b")
        assign = st[6] as Assignment
        assign.target.identifier!!.nameInSource shouldBe listOf("bb")
    }

    test("asmgen correctly deals with float typecasting in augmented assignment") {
        val src="""
            %option enable_floats
            
            main {
                sub start() {
                    ubyte ub
                    float ff = 1.0
                    ff += (ub as float)         ; operator doesn't matter
                }
            }
        """
        val result = compileText(C64Target, optimize=false, src, writeAssembly = false).assertSuccess()
        val assignFF = result.program.entrypoint.statements.last() as Assignment
        assignFF.isAugmentable shouldBe true
        assignFF.target.identifier!!.nameInSource shouldBe listOf("ff")
        val value = assignFF.value as BinaryExpression
        value.operator shouldBe "+"
        value.left shouldBe IdentifierReference(listOf("ff"), Position.DUMMY)
        value.right shouldBe instanceOf<TypecastExpression>()

        val asm = generateAssembly(result.program)
        asm.valid shouldBe true
    }

    test("unused variable removal") {
        val src="""
            main {
                sub start() {
                    ubyte unused
                    ubyte @shared unused_but_shared     ; this one should remain
                    ubyte usedvar_only_written
                    usedvar_only_written=2
                    usedvar_only_written++
                    ubyte usedvar                       ; and this one too
                    usedvar = msb(usedvar)
                }
            }
        """
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        result.program.entrypoint.statements.size shouldBe 4       // unused_but_shared decl, unused_but_shared=0,  usedvar decl, usedvar assign
        val (decl, assign, decl2, assign2) = result.program.entrypoint.statements
        decl shouldBe instanceOf<VarDecl>()
        (decl as VarDecl).name shouldBe "unused_but_shared"
        assign shouldBe instanceOf<Assignment>()
        decl2 shouldBe instanceOf<VarDecl>()
        (decl2 as VarDecl).name shouldBe "usedvar"
        assign2 shouldBe instanceOf<Assignment>()
    }

    test("unused variable removal from subscope") {
        val src="""
            main {
                sub start()  {
                    if cx16.r0 {
                        uword xx = 42       ; to be removed
                        xx=99               ; to be removed
                        cx16.r0 = 0
                    }
                    func2()
            
                    sub func2() {
                         uword yy = 33      ; to be removed
                         yy=99              ; to be removed
                         cx16.r0 = 0
                    }
                }
            }"""
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        result.program.entrypoint.statements.size shouldBe 3
        val ifstmt = result.program.entrypoint.statements[0] as IfElse
        ifstmt.truepart.statements.size shouldBe 1
        (ifstmt.truepart.statements[0] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "r0")
        val func2 = result.program.entrypoint.statements[2] as Subroutine
        func2.statements.size shouldBe 1
        (func2.statements[0] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "r0")
    }

    test("test simple augmented assignment optimization correctly initializes all variables") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1
                    z1 = 10
                    ubyte @shared z2
                    z2 = ~z2
                    ubyte @shared z3
                    z3 = not z3
                    uword @shared z4
                    z4 = (z4 as ubyte)
                    ubyte @shared z5
                    z5 = z1+z5+5
                    ubyte @shared z6
                    z6 = z1+z6-5
                }
            }"""
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        /* expected:
        ubyte z1
        z1 = 10
        ubyte z2
        z2 = 255
        ubyte z3
        z3 = 1
        uword z4
        z4 = 0
        ubyte z5
        z5 = z1
        z5 += 5
        ubyte z6
        z6 = z1
        z6 -= 5
        */
        val statements = result.program.entrypoint.statements
        statements.size shouldBe 14
        val z1decl = statements[0] as VarDecl
        val z1init = statements[1] as Assignment
        val z2decl = statements[2] as VarDecl
        val z2init = statements[3] as Assignment
        val z3decl = statements[4] as VarDecl
        val z3init = statements[5] as Assignment
        val z4decl = statements[6] as VarDecl
        val z4init = statements[7] as Assignment
        val z5decl = statements[8] as VarDecl
        val z5init = statements[9] as Assignment
        val z5plus = statements[10] as Assignment
        val z6decl = statements[11] as VarDecl
        val z6init = statements[12] as Assignment
        val z6plus = statements[13] as Assignment

        z1decl.name shouldBe "z1"
        z1init.value shouldBe NumericLiteralValue(DataType.UBYTE, 10.0, Position.DUMMY)
        z2decl.name shouldBe "z2"
        z2init.value shouldBe NumericLiteralValue(DataType.UBYTE, 255.0, Position.DUMMY)
        z3decl.name shouldBe "z3"
        z3init.value shouldBe NumericLiteralValue(DataType.UBYTE, 1.0, Position.DUMMY)
        z4decl.name shouldBe "z4"
        z4init.value shouldBe NumericLiteralValue(DataType.UBYTE, 0.0, Position.DUMMY)
        z5decl.name shouldBe "z5"
        z5init.value shouldBe IdentifierReference(listOf("z1"), Position.DUMMY)
        z5plus.isAugmentable shouldBe true
        (z5plus.value as BinaryExpression).operator shouldBe "+"
        (z5plus.value as BinaryExpression).right shouldBe NumericLiteralValue(DataType.UBYTE, 5.0, Position.DUMMY)
        z6decl.name shouldBe "z6"
        z6init.value shouldBe IdentifierReference(listOf("z1"), Position.DUMMY)
        z6plus.isAugmentable shouldBe true
        (z6plus.value as BinaryExpression).operator shouldBe "-"
        (z6plus.value as BinaryExpression).right shouldBe NumericLiteralValue(DataType.UBYTE, 5.0, Position.DUMMY)
    }

    test("force_output option should work with optimizing memwrite assignment") {
        val src="""
            main {
                %option force_output
                
                sub start() {
                    uword aa
                    ubyte zz
                    @(aa) = zz + 32    
                }
            }
        """

        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 6
        val assign=stmts.last() as Assignment
        (assign.target.memoryAddress?.addressExpression as IdentifierReference).nameInSource shouldBe listOf("aa")
    }

    test("don't optimize memory writes away") {
        val src="""
            main {
                sub start() {
                    uword aa
                    ubyte zz
                    @(aa) = zz + 32   ; do not optimize this away!
                }
            }
        """
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 6
        val assign=stmts.last() as Assignment
        (assign.target.memoryAddress?.addressExpression as IdentifierReference).nameInSource shouldBe listOf("aa")
    }

    test("correctly process constant prefix numbers") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1 = 1
                    ubyte @shared z2 = + 1
                    ubyte @shared z3 = ~ 1
                    ubyte @shared z4 = not 1
                    byte @shared z5 = - 1
                }
            }
        """
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 10
        stmts.filterIsInstance<VarDecl>().size shouldBe 5
        stmts.filterIsInstance<Assignment>().size shouldBe 5
    }

    test("correctly process constant prefix numbers with type mismatch and give error") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1 = - 1
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target, optimize=true, src, writeAssembly=false, errors = errors).assertFailure()
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain  "type of value BYTE doesn't match target UBYTE"
    }

    test("test augmented expression asmgen") {
        val src = """
        main {
            sub start() {
                ubyte c
                ubyte r
                ubyte q
                r = (q+r)-c
                q=r
                r = q+(r-c)
                q=r
            }
        }"""
        val result = compileText(C64Target, optimize=false, src, writeAssembly=true).assertSuccess()
        result.program.entrypoint.statements.size shouldBe 11
        result.program.entrypoint.statements.last() shouldBe instanceOf<Return>()
    }

    test("keep the value initializer assignment if the next one depends on it") {
        val src="""
        main {
            sub start() {
                uword @shared yy
                yy = 20             ; ok to remove =0 initializer before this
                uword @shared zz
                zz += 60            ; NOT ok to remove initializer, should evaluate to 60
                ubyte @shared xx
                xx = 6+sin8u(xx)     ; NOT ok to remove initializer
            }
        }
        """
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        /* expected result:
        uword yy
        yy = 20
        uword zz
        zz = 60
        ubyte xx
        xx = sin8u(xx)
        xx += 6
         */
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        stmts.filterIsInstance<VarDecl>().size shouldBe 3
        stmts.filterIsInstance<Assignment>().size shouldBe 4
    }

    test("only substitue assignments with 0 after a =0 initializer if it is the same variable") {
        val src="""
        main {
            sub start() {
                uword @shared xx
                xx = xx + 20    ; is same var so can be changed just fine into xx=20
                uword @shared yy
                xx = 20
                yy = 0          ; is other var..
                xx = xx+10      ; so this should not be changed into xx=10
            }
        }"""
        val result = compileText(C64Target, optimize=true, src, writeAssembly=false).assertSuccess()
        /*
        expected result:
        uword xx
        xx = 20
        uword yy
        yy = 0
        xx = 20
        yy = 0
        xx += 10
         */
        printProgram(result.program)

        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        stmts.filterIsInstance<VarDecl>().size shouldBe 2
        stmts.filterIsInstance<Assignment>().size shouldBe 5
        val assignXX1 = stmts[1] as Assignment
        assignXX1.target.identifier!!.nameInSource shouldBe listOf("xx")
        assignXX1.value shouldBe NumericLiteralValue(DataType.UBYTE, 20.0, Position.DUMMY)
        val assignXX2 = stmts.last() as Assignment
        assignXX2.target.identifier!!.nameInSource shouldBe listOf("xx")
        val xxValue = assignXX2.value as BinaryExpression
        xxValue.operator shouldBe "+"
        xxValue.left shouldBe IdentifierReference(listOf("xx"), Position.DUMMY)
        xxValue.right shouldBe NumericLiteralValue(DataType.UBYTE, 10.0, Position.DUMMY)
    }
})
