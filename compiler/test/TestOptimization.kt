package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.instanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import prog8.ast.ParentSentinel
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.*


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
        val result = compileText(C64Target(), true, sourcecode)!!
        val toplevelModule = result.compilerAst.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements.single() as Subroutine
        result.compilerAst.entrypoint shouldBeSameInstanceAs startSub
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
        val result = compileText(C64Target(), true, sourcecode)!!
        val toplevelModule = result.compilerAst.toplevelModule
        val mainBlock = toplevelModule.statements.single() as Block
        val startSub = mainBlock.statements[0] as Subroutine
        val emptySub = mainBlock.statements[1] as Subroutine
        result.compilerAst.entrypoint shouldBeSameInstanceAs startSub
        startSub.name shouldBe "start"
        emptySub.name shouldBe "empty"
        withClue("compiler has inserted return in empty subroutines") {
            emptySub.statements.single() shouldBe instanceOf<Return>()
        }
    }

    test("generated constvalue from typecast inherits proper parent linkage") {
        val number = NumericLiteral(DataType.UBYTE, 11.0, Position.DUMMY)
        val tc = TypecastExpression(number, DataType.BYTE, false, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        tc.linkParents(ParentSentinel)
        tc.parent shouldNotBe null
        number.parent shouldNotBe null
        tc shouldBeSameInstanceAs number.parent
        val constvalue = tc.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteral>()
        constvalue.number shouldBe 11.0
        constvalue.type shouldBe DataType.BYTE
        constvalue.parent shouldBeSameInstanceAs tc.parent
    }

    test("generated constvalue from prefixexpr inherits proper parent linkage") {
        val number = NumericLiteral(DataType.UBYTE, 11.0, Position.DUMMY)
        val pfx = PrefixExpression("-", number, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        pfx.linkParents(ParentSentinel)
        pfx.parent shouldNotBe null
        number.parent shouldNotBe null
        pfx shouldBeSameInstanceAs number.parent
        val constvalue = pfx.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteral>()
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
        val result = compileText(C64Target(), true, source, writeAssembly = false)!!
        // expected:
//        uword load_location
//        load_location = 12345
//        word llw
//        llw = 12345
//        cx16.r0 = load_location + 10000
//        cx16.r2 = load_location + 10000
//        cx16.r4 = load_location + 22
//        cx16.r5s = llw - 1899
//        cx16.r7s = llw + 99
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 9

        val addR0value = (stmts[4] as Assignment).value
        val binexpr0 = addR0value as BinaryExpression
        binexpr0.operator shouldBe "+"
        binexpr0.right shouldBe NumericLiteral(DataType.UWORD, 10000.0, Position.DUMMY)
        val addR2value = (stmts[5] as Assignment).value
        val binexpr2 = addR2value as BinaryExpression
        binexpr2.operator shouldBe "+"
        binexpr2.right shouldBe NumericLiteral(DataType.UWORD, 10000.0, Position.DUMMY)
        val addR4value = (stmts[6] as Assignment).value
        val binexpr4 = addR4value as BinaryExpression
        binexpr4.operator shouldBe "+"
        binexpr4.right shouldBe NumericLiteral(DataType.UWORD, 22.0, Position.DUMMY)
        val subR5value = (stmts[7] as Assignment).value
        val binexpr5 = subR5value as BinaryExpression
        binexpr5.operator shouldBe "-"
        binexpr5.right shouldBe NumericLiteral(DataType.UWORD, 1899.0, Position.DUMMY)
        val subR7value = (stmts[8] as Assignment).value
        val binexpr7 = subR7value as BinaryExpression
        binexpr7.operator shouldBe "+"
        binexpr7.right shouldBe NumericLiteral(DataType.UWORD, 99.0, Position.DUMMY)
    }

    test("const folding multiple scenarios * and / (floats)") {
        val source = """
            %option enable_floats
            main {
                sub start() {
                    float llw = 300.0
                    float result
                    result = 9 * 2 * 10 * llw
                    result++
                    result = llw * 9 * 2 * 10
                    result++
                    result = llw / 30 / 3
                    result++
                    result = llw / 2 * 10
                    result++
                    result = llw * 90 / 5
                }
            }"""
        val result = compileText(C64Target(), true, source, writeAssembly = false)!!
        // expected:
//        float llw
//        llw = 300.0
//        float result
//        result = llw * 180.0
//        result++
//        result = llw * 180.0
//        result++
//        result = llw / 90.0
//        result++
//        result = llw * 5.0
//        result++
//        result = llw * 18.0
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 12

        val mulR0Value = (stmts[3] as Assignment).value
        val binexpr0 = mulR0Value as BinaryExpression
        binexpr0.operator shouldBe "*"
        binexpr0.right shouldBe NumericLiteral(DataType.FLOAT, 180.0, Position.DUMMY)
        val mulR1Value = (stmts[5] as Assignment).value
        val binexpr1 = mulR1Value as BinaryExpression
        binexpr1.operator shouldBe "*"
        binexpr1.right shouldBe NumericLiteral(DataType.FLOAT, 180.0, Position.DUMMY)
        val divR2Value = (stmts[7] as Assignment).value
        val binexpr2 = divR2Value as BinaryExpression
        binexpr2.operator shouldBe "/"
        binexpr2.right shouldBe NumericLiteral(DataType.FLOAT, 90.0, Position.DUMMY)
        val mulR3Value = (stmts[9] as Assignment).value
        val binexpr3 = mulR3Value as BinaryExpression
        binexpr3.operator shouldBe "*"
        binexpr3.right shouldBe NumericLiteral(DataType.FLOAT, 5.0, Position.DUMMY)
        binexpr3.left shouldBe instanceOf<IdentifierReference>()
        val mulR4Value = (stmts[11] as Assignment).value
        val binexpr4 = mulR4Value as BinaryExpression
        binexpr4.operator shouldBe "*"
        binexpr4.right shouldBe NumericLiteral(DataType.FLOAT, 18.0, Position.DUMMY)
        binexpr4.left shouldBe instanceOf<IdentifierReference>()
    }

    test("const folding multiple scenarios * and / (integers)") {
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
        val result = compileText(C64Target(), true, source, writeAssembly = false)!!
        // expected:
//        word llw
//        llw = 300
//        cx16.r0s = llw * 180
//        cx16.r1s = llw * 180
//        cx16.r2s = llw / 90
//        cx16.r3s = llw /2 *10
//        cx16.r4s = llw *90 /5
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 7

        val mulR0Value = (stmts[2] as Assignment).value
        val binexpr0 = mulR0Value as BinaryExpression
        binexpr0.operator shouldBe "*"
        binexpr0.right shouldBe NumericLiteral(DataType.UWORD, 180.0, Position.DUMMY)
        val mulR1Value = (stmts[3] as Assignment).value
        val binexpr1 = mulR1Value as BinaryExpression
        binexpr1.operator shouldBe "*"
        binexpr1.right shouldBe NumericLiteral(DataType.UWORD, 180.0, Position.DUMMY)
        val divR2Value = (stmts[4] as Assignment).value
        val binexpr2 = divR2Value as BinaryExpression
        binexpr2.operator shouldBe "/"
        binexpr2.right shouldBe NumericLiteral(DataType.UWORD, 90.0, Position.DUMMY)
        val mulR3Value = (stmts[5] as Assignment).value
        val binexpr3 = mulR3Value as BinaryExpression
        binexpr3.operator shouldBe "*"
        binexpr3.right shouldBe NumericLiteral(DataType.UWORD, 10.0, Position.DUMMY)
        binexpr3.left shouldBe instanceOf<BinaryExpression>()
        val mulR4Value = (stmts[6] as Assignment).value
        val binexpr4 = mulR4Value as BinaryExpression
        binexpr4.operator shouldBe "/"
        binexpr4.right shouldBe NumericLiteral(DataType.UWORD, 5.0, Position.DUMMY)
        binexpr4.left shouldBe instanceOf<BinaryExpression>()
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
        val result = compileText(C64Target(), false, sourcecode)!!
        val mainsub = result.compilerAst.entrypoint
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
        (declTest.value as NumericLiteral).number shouldBe 10.0
        declX1.value shouldBe null
        declX2.value shouldBe null
        declY1.value shouldBe null
        declY2.value shouldBe null
        (initX1.value as NumericLiteral).type shouldBe DataType.BYTE
        (initX1.value as NumericLiteral).number shouldBe 11.0
        (initX2.value as NumericLiteral).type shouldBe DataType.BYTE
        (initX2.value as NumericLiteral).number shouldBe 11.0
        (initY1.value as NumericLiteral).type shouldBe DataType.UBYTE
        (initY1.value as NumericLiteral).number shouldBe 11.0
        (initY2.value as NumericLiteral).type shouldBe DataType.UBYTE
        (initY2.value as NumericLiteral).number shouldBe 11.0
    }

    test("various 'not' operator rewrites even without optimizations on") {
        val src = """
            main {
                sub start() {
                    ubyte a1
                    ubyte a2
                    a1 = not not a1             ; a1 = a1==0
                    a1 = not a1 or not a2       ; a1 = a1==0 | a2==0
                    a1 = not a1 and not a2      ; a1 = a1==0 & a2==0
                }
            }
        """
        val result = compileText(C64Target(), false, src, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 8

        val value1 = (stmts[4] as Assignment).value as BinaryExpression
        val value2 = (stmts[5] as Assignment).value as BinaryExpression
        val value3 = (stmts[6] as Assignment).value as BinaryExpression
        value1.operator shouldBe "=="
        value1.right shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        value2.operator shouldBe "|"
        value3.operator shouldBe "&"
    }

    test("asmgen correctly deals with float typecasting in augmented assignment") {
        val src="""
            %import floats
            
            main {
                sub start() {
                    ubyte ub
                    float ff = 1.0
                    ff += (ub as float)         ; operator doesn't matter
                }
            }
        """
        val result = compileText(C64Target(), optimize=false, src, writeAssembly = false)!!
        val assignFF = result.compilerAst.entrypoint.statements.last() as Assignment
        assignFF.isAugmentable shouldBe true
        assignFF.target.identifier!!.nameInSource shouldBe listOf("ff")
        val value = assignFF.value as BinaryExpression
        value.operator shouldBe "+"
        (value.left as? IdentifierReference)?.nameInSource shouldBe listOf("ff")
        value.right shouldBe instanceOf<TypecastExpression>()

        compileText(C64Target(), optimize=false, src, writeAssembly = true) shouldNotBe null
    }

    test("unused variable removal") {
        val src="""
            main {
                sub start() {
                    ubyte unused                        ; removed
                    ubyte @shared unused_but_shared     ; this one should remain
                    ubyte usedvar_only_written          ; not removed because has multiple assignments
                    usedvar_only_written=2
                    usedvar_only_written++
                    ubyte usedvar                       ; and this one remains too
                    usedvar = msb(usedvar)
                }
            }
        """
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        result.compilerAst.entrypoint.statements.size shouldBe 7
        val alldecls = result.compilerAst.entrypoint.allDefinedSymbols.toList()
        alldecls.map { it.first } shouldBe listOf("unused_but_shared", "usedvar_only_written", "usedvar")
    }

    test("unused variable removal from subscope") {
        val src="""
            main {
                sub start()  {
                    if cx16.r0 {
                        uword xx            ; to be removed
                        cx16.r0 = 0
                    }
                    func2()
            
                    sub func2() {
                         uword yy           ; to be removed
                         yy=99              ; to be removed
                         cx16.r0 = 0
                         cx16.r0++
                    }
                }
            }"""
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        result.compilerAst.entrypoint.statements.size shouldBe 3
        val ifstmt = result.compilerAst.entrypoint.statements[0] as IfElse
        ifstmt.truepart.statements.size shouldBe 1
        (ifstmt.truepart.statements[0] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "r0")
        val func2 = result.compilerAst.entrypoint.statements[2] as Subroutine
        func2.statements.size shouldBe 2
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
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
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
        z5 = z1 + 5
        ubyte z6
        z6 = z1 - 5
        */
        val statements = result.compilerAst.entrypoint.statements
        statements.size shouldBe 12
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
        val z6decl = statements[10] as VarDecl
        val z6init = statements[11] as Assignment

        z1decl.name shouldBe "z1"
        z1init.value shouldBe NumericLiteral(DataType.UBYTE, 10.0, Position.DUMMY)
        z2decl.name shouldBe "z2"
        z2init.value shouldBe NumericLiteral(DataType.UBYTE, 255.0, Position.DUMMY)
        z3decl.name shouldBe "z3"
        z3init.value shouldBe NumericLiteral(DataType.UBYTE, 1.0, Position.DUMMY)
        z4decl.name shouldBe "z4"
        z4init.value shouldBe NumericLiteral(DataType.UBYTE, 0.0, Position.DUMMY)
        z5decl.name shouldBe "z5"
        (z5init.value as BinaryExpression).operator shouldBe "+"
        (z5init.value as BinaryExpression).right shouldBe NumericLiteral(DataType.UBYTE, 5.0, Position.DUMMY)
        z6decl.name shouldBe "z6"
        (z6init.value as BinaryExpression).operator shouldBe "-"
        (z6init.value as BinaryExpression).right shouldBe NumericLiteral(DataType.UBYTE, 5.0, Position.DUMMY)
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

        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 5
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
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 5
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
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 10
        stmts.filterIsInstance<VarDecl>().size shouldBe 5
        stmts.filterIsInstance<Assignment>().size shouldBe 5
    }

    test("correctly process constant prefix numbers with type mismatch and give error") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1 = - 200
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain  "out of range"
    }

    test("out of range cast should give error") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1 = - 200 as ubyte
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain  "no cast"
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
        val result = compileText(C64Target(), optimize=false, src, writeAssembly=true)!!
        result.compilerAst.entrypoint.statements.size shouldBe 11
        result.compilerAst.entrypoint.statements.last() shouldBe instanceOf<Return>()
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
                xx = 6+lsb(mkword(xx,22))   ; is not an initializer because it references xx
            }
        }
        """
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        /* expected result:
        uword yy
        yy = 20
        uword zz
        zz = 60
        ubyte xx
        xx = 0
        xx = abs(xx)
        xx += 6
         */
        val stmts = result.compilerAst.entrypoint.statements
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
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
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
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 7
        stmts.filterIsInstance<VarDecl>().size shouldBe 2
        stmts.filterIsInstance<Assignment>().size shouldBe 5
        val assignXX1 = stmts[1] as Assignment
        assignXX1.target.identifier!!.nameInSource shouldBe listOf("xx")
        assignXX1.value shouldBe NumericLiteral(DataType.UBYTE, 20.0, Position.DUMMY)
        val assignXX2 = stmts.last() as Assignment
        assignXX2.target.identifier!!.nameInSource shouldBe listOf("xx")
        val xxValue = assignXX2.value as BinaryExpression
        xxValue.operator shouldBe "+"
        (xxValue.left as? IdentifierReference)?.nameInSource shouldBe listOf("xx")
        xxValue.right shouldBe NumericLiteral(DataType.UBYTE, 10.0, Position.DUMMY)
    }

    test("multi-comparison replaced by containment check") {
        val src="""
        main {
            sub start() {
                ubyte source=99
                ubyte thingy=42
        
                if source==3 or source==4 or source==99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        /*
        expected result:
        ubyte[] auto_heap_var = [1,4,99,3]
        ubyte source
        source = 99
        ubyte thingy
        thingy = 42
        if source in auto_heap_var
            thingy++
         */
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val ifStmt = stmts[5] as IfElse
        val containment = ifStmt.condition as ContainmentCheck
        (containment.element as IdentifierReference).nameInSource shouldBe listOf("source")
        (containment.iterable as IdentifierReference).nameInSource.single() shouldStartWith "auto_heap_value"
        val arrayDecl = stmts[0] as VarDecl
        arrayDecl.isArray shouldBe true
        arrayDecl.arraysize?.constIndex() shouldBe 4
        val arrayValue = arrayDecl.value as ArrayLiteral
        arrayValue.type shouldBe InferredTypes.InferredType.known(DataType.ARRAY_UB)
        arrayValue.value shouldBe listOf(
            NumericLiteral.optimalInteger(1, Position.DUMMY),
            NumericLiteral.optimalInteger(3, Position.DUMMY),
            NumericLiteral.optimalInteger(4, Position.DUMMY),
            NumericLiteral.optimalInteger(99, Position.DUMMY))
    }

    test("invalid multi-comparison (not all equals) not replaced") {
        val src="""
        main {
            sub start() {
                ubyte source=99
                ubyte thingy=42
        
                if source==3 or source==4 or source!=99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 5
        val ifStmt = stmts[4] as IfElse
        ifStmt.condition shouldBe instanceOf<BinaryExpression>()
    }

    test("invalid multi-comparison (not all same needle) not replaced") {
        val src="""
        main {
            sub start() {
                ubyte source=99
                ubyte thingy=42
        
                if source==3 or source==4 or thingy==99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 5
        val ifStmt = stmts[4] as IfElse
        ifStmt.condition shouldBe instanceOf<BinaryExpression>()
    }

    test("invalid multi-comparison (not all or) not replaced") {
        val src="""
        main {
            sub start() {
                ubyte source=99
                ubyte thingy=42
        
                if source==3 or source==4 and source==99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 5
        val ifStmt = stmts[4] as IfElse
        ifStmt.condition shouldBe instanceOf<BinaryExpression>()
    }

    test("pointer indexing inside other expression ok") {
        val src="""
            main{
                sub start () {
                    uword eRef
                    if eRef[3] and 10  {
                      return
                    }
                }
            }"""
        val result = compileText(C64Target(), optimize=true, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 3
    }

    test("repeated assignments to IO register should remain") {
        val srcX16="""
main {
    sub start() {
        ubyte @shared xx
        xx = 42
        xx = 42  ; removed
        xx = 42  ; removed
        cx16.VERA_DATA0 = 0
        cx16.VERA_DATA0 = 0
        cx16.VERA_DATA0 = 0
        @($9fff) = 0
        @($9fff) = 0
        @($9fff) = 0
        return
    }
}"""
        var result = compileText(Cx16Target(), true, srcX16, writeAssembly = true)!!
        var statements = result.compilerAst.entrypoint.statements
        statements.size shouldBe 9
        (statements[1] as Assignment).target.identifier!!.nameInSource shouldBe listOf("xx")
        (statements[2] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "VERA_DATA0")
        (statements[3] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "VERA_DATA0")
        (statements[4] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "VERA_DATA0")
        (statements[5] as Assignment).target.memoryAddress!!.addressExpression.constValue(result.compilerAst)!!.number shouldBe 0x9fff
        (statements[6] as Assignment).target.memoryAddress!!.addressExpression.constValue(result.compilerAst)!!.number shouldBe 0x9fff
        (statements[7] as Assignment).target.memoryAddress!!.addressExpression.constValue(result.compilerAst)!!.number shouldBe 0x9fff

        val srcC64="""
main {
    sub start() {
        ubyte @shared xx
        xx = 42
        xx = 42  ;removed
        xx = 42  ;removed
        c64.EXTCOL = 0
        c64.EXTCOL = 0
        c64.EXTCOL = 0
        @(53281) = 0
        @(53281) = 0
        @(53281) = 0
        return
    }
}"""
        result = compileText(C64Target(), true, srcC64, writeAssembly = true)!!
        statements = result.compilerAst.entrypoint.statements
        statements.size shouldBe 9
        (statements[1] as Assignment).target.identifier!!.nameInSource shouldBe listOf("xx")
        (statements[2] as Assignment).target.identifier!!.nameInSource shouldBe listOf("c64", "EXTCOL")
        (statements[3] as Assignment).target.identifier!!.nameInSource shouldBe listOf("c64", "EXTCOL")
        (statements[4] as Assignment).target.identifier!!.nameInSource shouldBe listOf("c64", "EXTCOL")
        (statements[5] as Assignment).target.memoryAddress!!.addressExpression.constValue(result.compilerAst)!!.number shouldBe 53281.0
        (statements[6] as Assignment).target.memoryAddress!!.addressExpression.constValue(result.compilerAst)!!.number shouldBe 53281.0
        (statements[7] as Assignment).target.memoryAddress!!.addressExpression.constValue(result.compilerAst)!!.number shouldBe 53281.0
    }

    test("no crash on sorting unused array") {
        val text="""
main {
	ubyte[5] cards = [ 14, 6, 29, 16, 3 ]

	sub start() {
	    sort(cards)
	}
}"""
        compileText(C64Target(), true, text, writeAssembly = false) shouldNotBe null
    }

    test("no string error when inlining") {
        val text="""
main {
    sub start() {
        test()
    }

    sub test() {
        cx16.r0 = "abc"
    }
}"""
        compileText(C64Target(), true, text, writeAssembly = false) shouldNotBe null
    }

    test("replacing string print by chrout with referenced string elsewhere shouldn't give string symbol error") {
        val text="""
%import textio

main {
    sub start() {
        str key = "test"
        txt.print(":")
        if key != ":" {
            cx16.r0++
        }
    }
}
"""
        compileText(VMTarget(), true, text, writeAssembly = false) shouldNotBe null
    }

    test("sub only called by asm should not be optimized away") {
        val src="""
main {
    sub start() {
        %asm{{
            jsr p8s_test
        }}
    }

    sub test() {
        cx16.r0++
    }
}"""
        compileText(Cx16Target(), true, src, writeAssembly = true) shouldNotBe null
    }

    test("no crash for making var in removed/inlined subroutine fully scoped") {
        val src="""
main {
    sub start() {
        test()
    }

    sub test() {
        sub nested() {
            ubyte counter
            counter++
        }
        test2(main.test.nested.counter)      ; shouldn't crash but just give nice error
    }

    sub test2(ubyte value) {
        value++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), true, src, writeAssembly = false, errors = errors) shouldBe null
        errors.errors.single() shouldContain  "undefined symbol"
    }
})
