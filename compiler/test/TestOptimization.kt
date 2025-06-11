package prog8tests.compiler

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
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
import prog8.code.ast.PtAssignTarget
import prog8.code.ast.PtAssignment
import prog8.code.ast.PtFunctionCall
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8.vm.VmRunner
import prog8tests.helpers.*
import kotlin.io.path.readText


class TestOptimization: FunSpec({
    val outputDir = tempdir().toPath()
    
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
        val result = compileText(C64Target(), true, sourcecode, outputDir)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val startSub = mainBlock.statements.single() as Subroutine
        result.compilerAst.entrypoint shouldBeSameInstanceAs startSub
        withClue("only start sub should remain") {
            startSub.name shouldBe "start"
        }
        withClue("compiler has inserted return in empty subroutines") {
            startSub.statements.single() shouldBe instanceOf<Return>()
        }
    }

    test("scan all asmsubs to see if another subroutine is referenced") {
        val src="""
main {
    sub foo() {
        cx16.r0++
    }
    asmsub baz() {
        %asm{{
            jsr p8s_foo
            jmp blah
        }}
    }
    asmsub bar() {
        %asm{{
            inx
            jmp p8s_foo
        }}
    }
    sub start() {
        bar()
    }
}"""
        compileText(C64Target(), true, src, outputDir) shouldNotBe null
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
        val result = compileText(C64Target(), true, sourcecode, outputDir)!!
        val mainBlock = result.compilerAst.entrypoint.definingBlock
        val startSub = mainBlock.statements[0] as Subroutine
        val emptySub = mainBlock.statements[1] as Subroutine
        result.compilerAst.entrypoint shouldBeSameInstanceAs startSub
        startSub.name shouldBe "start"
        emptySub.name shouldBe "empty"
        withClue("compiler has inserted return in empty subroutines") {
            emptySub.statements.single() shouldBe instanceOf<Return>()
        }
    }

    test("don't remove empty subroutine if it's referenced in vardecl") {
        val sourcecode = """
main {
    ubyte tw = other.width()
    sub start() {
        tw++
    }
}

other {
    sub width() -> ubyte {
        cx16.r0++
        return 80
    }
}"""
        compileText(C64Target(), true, sourcecode, outputDir, writeAssembly = true) shouldNotBe null
        compileText(VMTarget(), true, sourcecode, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("generated constvalue from typecast inherits proper parent linkage") {
        val number = NumericLiteral(BaseDataType.UBYTE, 11.0, Position.DUMMY)
        val tc = TypecastExpression(number, BaseDataType.BYTE, false, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        tc.linkParents(ParentSentinel)
        tc.parent shouldNotBe null
        number.parent shouldNotBe null
        tc shouldBeSameInstanceAs number.parent
        val constvalue = tc.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteral>()
        constvalue.number shouldBe 11.0
        constvalue.type shouldBe BaseDataType.BYTE
        constvalue.parent shouldBeSameInstanceAs tc.parent
    }

    test("generated constvalue from prefixexpr inherits proper parent linkage") {
        val number = NumericLiteral(BaseDataType.UBYTE, 11.0, Position.DUMMY)
        val pfx = PrefixExpression("-", number, Position.DUMMY)
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        pfx.linkParents(ParentSentinel)
        pfx.parent shouldNotBe null
        number.parent shouldNotBe null
        pfx shouldBeSameInstanceAs number.parent
        val constvalue = pfx.constValue(program)!!
        constvalue shouldBe instanceOf<NumericLiteral>()
        constvalue.number shouldBe -11.0
        constvalue.type shouldBe BaseDataType.BYTE
        constvalue.parent shouldBeSameInstanceAs pfx.parent
    }

    test("various 'not' operator rewrites even without optimizations") {
        val src = """
            main {
                sub start() {
                    bool @shared a1
                    bool @shared a2
                    a1 = not a1                 ; a1 = not a1
                    a1 = not not a1             ; a1 = a1,  so removed totally
                    a1 = not not not a1         ; a1 = not a1
                    a1 = not a1 or not a2       ; a1 = not (a1 and a2)
                    a1 = not a1 and not a2      ; a1 = not (a1 or a2)
                }
            }
        """
        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 9

        val value1 = (stmts[4] as Assignment).value as PrefixExpression
        val value2 = (stmts[5] as Assignment).value as PrefixExpression
        val value3 = (stmts[6] as Assignment).value as PrefixExpression
        val value4 = (stmts[7] as Assignment).value as PrefixExpression
        value1.operator shouldBe "not"
        value2.operator shouldBe "not"
        value3.operator shouldBe "not"
        value4.operator shouldBe "not"
        value1.expression shouldBe instanceOf<IdentifierReference>()
        value2.expression shouldBe instanceOf<IdentifierReference>()
        (value3.expression as BinaryExpression).operator shouldBe "and"
        (value4.expression as BinaryExpression).operator shouldBe "or"
    }

    test("various 'not' operator rewrites with optimizations") {
        val src = """
            main {
                sub start() {
                    bool @shared a1
                    bool @shared a2
                    a1 = not a1                 ; a1 = not a1
                    a1 = not not a1             ; a1 = a1,  so removed totally
                    a1 = not not not a1         ; a1 = not a1
                    a1 = not a1 or not a2       ; a1 = not (a1 and a2)
                    a1 = not a1 and not a2      ; a1 = not (a1 or a2)
                }
            }
        """
        val result = compileText(C64Target(), true, src, outputDir, writeAssembly = true)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 9

        val value1 = (stmts[4] as Assignment).value as PrefixExpression
        val value2 = (stmts[5] as Assignment).value as PrefixExpression
        val value3 = (stmts[6] as Assignment).value as PrefixExpression
        val value4 = (stmts[7] as Assignment).value as PrefixExpression
        value1.operator shouldBe "not"
        value2.operator shouldBe "not"
        value3.operator shouldBe "not"
        value4.operator shouldBe "not"
        value1.expression shouldBe instanceOf<IdentifierReference>()
        value2.expression shouldBe instanceOf<IdentifierReference>()
        (value3.expression as BinaryExpression).operator shouldBe "and"
        (value4.expression as BinaryExpression).operator shouldBe "or"
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
        val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly = false)!!
        val assignFF = result.compilerAst.entrypoint.statements.dropLast(1).last() as Assignment
        assignFF.isAugmentable shouldBe true
        assignFF.target.identifier!!.nameInSource shouldBe listOf("ff")
        val value = assignFF.value as BinaryExpression
        value.operator shouldBe "+"
        (value.left as? IdentifierReference)?.nameInSource shouldBe listOf("ff")
        value.right shouldBe instanceOf<TypecastExpression>()

        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly = true) shouldNotBe null
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
                    unused_but_shared = usedvar
                }
            }
        """
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        result.compilerAst.entrypoint.statements.size shouldBe 8
        val alldecls = result.compilerAst.entrypoint.allDefinedSymbols.toList()
        alldecls.map { it.first } shouldBe listOf("unused_but_shared", "usedvar_only_written", "usedvar")
    }

    test("unused variable removal from subscope") {
        val src="""
            main {
                sub start()  {
                    if cx16.r0!=0 {
                        uword xx            ; to be removed
                        cx16.r0 = 0
                    }
                    func2()
            
                    sub func2() {
                         uword yy           ; to be removed
                         yy=99              ; to be removed
                         cx16.r0 = 0
                         rol(cx16.r0)
                    }
                }
            }"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        result.compilerAst.entrypoint.statements.size shouldBe 4
        val ifstmt = result.compilerAst.entrypoint.statements[0] as IfElse
        ifstmt.truepart.statements.size shouldBe 1
        (ifstmt.truepart.statements[0] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "r0")
        val func2 = result.compilerAst.entrypoint.statements.last() as Subroutine
        func2.statements.size shouldBe 3
        (func2.statements[0] as Assignment).target.identifier!!.nameInSource shouldBe listOf("cx16", "r0")
    }

    test("unused subroutine removal") {
        val src="""
main {
    sub strip() {
        lstrip()
    }

    sub lstrip() {
        lstripped()
    }

    sub lstripped() {
        cx16.r0++
        evenmore()
        cx16.r1++
    }

    sub evenmore() {
        cx16.r1++
        cx16.r2++
    }

    sub start() {
        ; nothing
    }
}"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        result.compilerAst.entrypoint.statements.size shouldBe 1
        result.compilerAst.entrypoint.definingScope.statements.size shouldBe 1
    }

    test("test simple augmented assignment optimization correctly initializes all variables") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1
                    z1 = 10
                    ubyte @shared z2
                    z2 = ~z2
                    bool @shared z3
                    z3 = not z3
                    uword @shared z4
                    z4 = (z4 as ubyte)
                    ubyte @shared z5
                    z5 = z1+z5+5
                    ubyte @shared z6
                    z6 = z1+z6-5
                }
            }"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        /* expected:
        ubyte z1
        z1 = 10
        ubyte z2
        z2 = 255
        bool z3
        z3 = true
        uword z4
        z4 = 0
        ubyte z5
        z5 = z1 + 5
        ubyte z6
        z6 = z1 - 5
        */
        val statements = result.compilerAst.entrypoint.statements
        statements.size shouldBe 13
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
        z1init.value shouldBe NumericLiteral(BaseDataType.UBYTE, 10.0, Position.DUMMY)
        z2decl.name shouldBe "z2"
        z2init.value shouldBe NumericLiteral(BaseDataType.UBYTE, 255.0, Position.DUMMY)
        z3decl.name shouldBe "z3"
        z3init.value shouldBe NumericLiteral(BaseDataType.BOOL, 1.0, Position.DUMMY)
        z4decl.name shouldBe "z4"
        z4init.value shouldBe NumericLiteral(BaseDataType.UWORD, 0.0, Position.DUMMY)
        z5decl.name shouldBe "z5"
        (z5init.value as BinaryExpression).operator shouldBe "+"
        (z5init.value as BinaryExpression).right shouldBe NumericLiteral(BaseDataType.UBYTE, 5.0, Position.DUMMY)
        z6decl.name shouldBe "z6"
        (z6init.value as BinaryExpression).operator shouldBe "-"
        (z6init.value as BinaryExpression).right shouldBe NumericLiteral(BaseDataType.UBYTE, 5.0, Position.DUMMY)
    }

    test("force_output option should work with optimizing memwrite assignment") {
        val src="""
            main {
                %option force_output
                
                sub start() {
                    uword @shared aa
                    ubyte @shared zz
                    @(aa) = zz + 32    
                }
            }
        """

        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val assign=stmts[4] as Assignment
        (assign.target.memoryAddress?.addressExpression as IdentifierReference).nameInSource shouldBe listOf("aa")
    }

    test("don't optimize memory writes away") {
        val src="""
            main {
                sub start() {
                    uword @shared aa
                    ubyte @shared zz
                    @(aa) = zz + 32   ; do not optimize this away!
                }
            }
        """
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val assign=stmts[4] as Assignment
        (assign.target.memoryAddress?.addressExpression as IdentifierReference).nameInSource shouldBe listOf("aa")
    }

    test("correctly process constant prefix numbers") {
        val src="""
            main {
                sub start()  {
                    ubyte @shared z1 = 1
                    ubyte @shared z2 = + 1
                    ubyte @shared z3 = ~ 1
                    bool @shared z4 = not 1
                    byte @shared z5 = - 1
                }
            }
        """
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 11
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain  "out of range"
        errors.errors[1] shouldContain  "cannot assign word to byte"
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
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
        val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=true)!!
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
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
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
        stmts.filterIsInstance<Assignment>().size shouldBe 3
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
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
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
        stmts.size shouldBe 8
        stmts.filterIsInstance<VarDecl>().size shouldBe 2
        stmts.filterIsInstance<Assignment>().size shouldBe 5
        val assignXX1 = stmts[1] as Assignment
        assignXX1.target.identifier!!.nameInSource shouldBe listOf("xx")
        assignXX1.value shouldBe NumericLiteral(BaseDataType.UWORD, 20.0, Position.DUMMY)
        val assignXX2 = stmts[6] as Assignment
        assignXX2.target.identifier!!.nameInSource shouldBe listOf("xx")
        val xxValue = assignXX2.value as BinaryExpression
        xxValue.operator shouldBe "+"
        (xxValue.left as? IdentifierReference)?.nameInSource shouldBe listOf("xx")
        xxValue.right shouldBe NumericLiteral(BaseDataType.UWORD, 10.0, Position.DUMMY)
    }

    test("multi-comparison with many values replaced by containment check on heap variable") {
        val src="""
        main {
            sub start() {
                ubyte @shared source=99
                ubyte @shared thingy=42
        
                if source==3 or source==4 or source==99 or source==1 or source==2 or source==42
                    thingy++
            }
        }"""

        compileText(VMTarget(), optimize=true, src, outputDir, writeAssembly=true) shouldNotBe null

        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        /*
        expected result:
        ubyte[] auto_heap_var = [1,2,3,4,42,99]
        ubyte source
        source = 99
        ubyte thingy
        thingy = 42
        if source in auto_heap_var
            thingy++
         */
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 7
        val ifStmt = stmts[5] as IfElse
        val containment = ifStmt.condition as ContainmentCheck
        (containment.element as IdentifierReference).nameInSource shouldBe listOf("source")
        (containment.iterable as IdentifierReference).nameInSource.single() shouldStartWith "auto_heap_value"
        val arrayDecl = stmts[0] as VarDecl
        arrayDecl.isArray shouldBe true
        arrayDecl.arraysize?.constIndex() shouldBe 6
        val arrayValue = arrayDecl.value as ArrayLiteral
        arrayValue.type shouldBe InferredTypes.InferredType.known(DataType.arrayFor(BaseDataType.UBYTE))
        arrayValue.value shouldBe listOf(
            NumericLiteral.optimalInteger(1, Position.DUMMY),
            NumericLiteral.optimalInteger(2, Position.DUMMY),
            NumericLiteral.optimalInteger(3, Position.DUMMY),
            NumericLiteral.optimalInteger(4, Position.DUMMY),
            NumericLiteral.optimalInteger(42, Position.DUMMY),
            NumericLiteral.optimalInteger(99, Position.DUMMY))
    }

    test("multi-comparison with few values replaced by inline containment check") {
        val src="""
        main {
            sub start() {
                ubyte @shared source=99
                ubyte @shared thingy=42
        
                if source==3 or source==4 or source==99
                    thingy++
            }
        }"""

        compileText(VMTarget(), optimize=true, src, outputDir, writeAssembly=true) shouldNotBe null

        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val ifStmt = stmts[4] as IfElse
        val containment = ifStmt.condition as ContainmentCheck
        (containment.element as IdentifierReference).nameInSource shouldBe listOf("source")
        val array = (containment.iterable as ArrayLiteral)
        array.value.size shouldBe 3
        array.value.map { (it as NumericLiteral).number } shouldBe listOf(3.0, 4.0, 99.0)
    }

    test("invalid multi-comparison (not all equals) not replaced") {
        val src="""
        main {
            sub start() {
                ubyte @shared source=99
                ubyte @shared thingy=42
        
                if source==3 or source==4 or source!=99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val ifStmt = stmts[4] as IfElse
        ifStmt.condition shouldBe instanceOf<BinaryExpression>()
    }

    test("invalid multi-comparison (not all same needle) not replaced") {
        val src="""
        main {
            sub start() {
                ubyte @shared source=99
                ubyte @shared thingy=42
        
                if source==3 or source==4 or thingy==99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val ifStmt = stmts[4] as IfElse
        ifStmt.condition shouldBe instanceOf<BinaryExpression>()
    }

    test("invalid multi-comparison (not all or) not replaced") {
        val src="""
        main {
            sub start() {
                ubyte @shared source=99
                ubyte @shared thingy=42
        
                if source==3 or source==4 and source==99 or source==1
                    thingy++
            }
        }"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 6
        val ifStmt = stmts[4] as IfElse
        ifStmt.condition shouldBe instanceOf<BinaryExpression>()
    }

    test("pointer indexing inside other expression ok") {
        val src="""
            main{
                sub start () {
                    uword @shared eRef
                    if eRef[3] & 10 ==0 {
                      return
                    }
                }
            }"""
        val result = compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 4
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
        var result = compileText(Cx16Target(), true, srcX16, outputDir, writeAssembly = true)!!
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
        result = compileText(C64Target(), true, srcC64, outputDir, writeAssembly = true)!!
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
        compileText(C64Target(), true, text, outputDir, writeAssembly = false) shouldNotBe null
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
        compileText(VMTarget(), true, text, outputDir, writeAssembly = false) shouldNotBe null
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
        compileText(Cx16Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
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
        test2(main.test.nested.counter)
    }

    sub test2(ubyte value) {
        value++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(Cx16Target(), true, src, outputDir, writeAssembly = false, errors = errors) shouldNotBe null
    }

    test("var to const") {
        val src="""
main {
    sub start() {
        ubyte xx=10         ; to const
        ubyte @shared yy=20 ; remain var
        cx16.r0L = xx+yy
    }
}"""
        val errors = ErrorReporterForTests()
        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = false, errors = errors)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 5
        val xxConst = st[0] as VarDecl
        xxConst.type shouldBe VarDeclType.CONST
        xxConst.name shouldBe "xx"
        (xxConst.value as? NumericLiteral)?.number shouldBe 10.0
        (st[1] as VarDecl).type shouldBe VarDeclType.VAR
        val expr = (st[3] as Assignment).value as BinaryExpression
        (expr.left as? IdentifierReference)?.nameInSource shouldBe listOf("yy")
        (expr.right as? NumericLiteral)?.number shouldBe 10.0
    }

    test("var to const inside typecast") {
        val src="""
main {
    uword variable  ; will be made a const

    sub start() {
        cx16.r0 = msb(variable) as uword
    }
}"""
        compileText(VMTarget(), true, src, outputDir, writeAssembly = false) shouldNotBe null
    }

    test("De Morgan's laws") {
        val src="""
main {
    sub start() {
        bool @shared a1
        bool @shared a2

        if not a1 or not a2
            cx16.r0++
        if not a1 and not a2
            cx16.r0++
            
        if cx16.r0L==42 or not a2
            cx16.r0L++
        if not a2 or cx16.r0L==42
            cx16.r0L++
            
    }
}"""
        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 9
        val if1c = (st[4] as IfElse).condition as PrefixExpression
        val if2c = (st[5] as IfElse).condition as PrefixExpression
        val if3c = (st[6] as IfElse).condition as PrefixExpression
        val if4c = (st[7] as IfElse).condition as PrefixExpression
        if1c.operator shouldBe "not"
        if2c.operator shouldBe "not"
        if3c.operator shouldBe "not"
        if4c.operator shouldBe "not"
        (if1c.expression as BinaryExpression).operator shouldBe "and"
        (if2c.expression as BinaryExpression).operator shouldBe "or"
        (if3c.expression as BinaryExpression).operator shouldBe "and"
        (if4c.expression as BinaryExpression).operator shouldBe "and"
    }

    test("absorption laws") {
        val src="""
main {
    sub start() {
        bool @shared a
        bool @shared b

        if a or (a and b)
            cx16.r0 ++
        if a or (b and a)
            cx16.r0 ++
        if a and (a or b)
            cx16.r0 ++
        if a and (b or a)
            cx16.r0 ++

        if a and (b and a)
            cx16.r0 ++
        if a or (b or a)
            cx16.r0 ++
        if (b and a) and b
            cx16.r0 ++
        if (b or a) or b
            cx16.r0 ++
    }
}"""
        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 13
        val if1 = st[4] as IfElse
        val if2 = st[5] as IfElse
        val if3 = st[6] as IfElse
        val if4 = st[7] as IfElse
        (if1.condition as IdentifierReference).nameInSource shouldBe listOf("a")
        (if2.condition as IdentifierReference).nameInSource shouldBe listOf("a")
        (if3.condition as IdentifierReference).nameInSource shouldBe listOf("a")
        (if4.condition as IdentifierReference).nameInSource shouldBe listOf("a")
        val if5 = st[8] as IfElse
        val if6 = st[9] as IfElse
        val if7 = st[10] as IfElse
        val if8 = st[11] as IfElse
        val if5bc = if5.condition as BinaryExpression
        val if6bc = if6.condition as BinaryExpression
        val if7bc = if7.condition as BinaryExpression
        val if8bc = if8.condition as BinaryExpression
        if5bc.left shouldBe instanceOf<IdentifierReference>()
        if5bc.right shouldBe instanceOf<IdentifierReference>()
        if6bc.left shouldBe instanceOf<IdentifierReference>()
        if6bc.right shouldBe instanceOf<IdentifierReference>()
        if7bc.left shouldBe instanceOf<IdentifierReference>()
        if7bc.right shouldBe instanceOf<IdentifierReference>()
        if8bc.left shouldBe instanceOf<IdentifierReference>()
        if8bc.right shouldBe instanceOf<IdentifierReference>()
    }

    test("funky bitshifts") {
        val src="""
main {   
    sub start() {
        const uword one = 1
        const uword two = 2
        uword @shared answer = one * two >> 8
        funcw(one * two >> 8)

        const uword uw1 = 99
        const uword uw2 = 22
        uword @shared answer2 = uw1 * uw2 >> 8      ; optimized into  msb(uw1*uw2) as uword
        funcw(uw1 * uw2 >> 8)

        uword @shared uw3 = 99
        uword @shared uw4 = 22
        uword @shared answer3 = uw3 * uw4 >> 8      ; optimized into  msb(uw1*uw2) as uword
        funcw(uw3 * uw4 >> 8)
    }

    sub funcw(uword ww) {
        cx16.r0++
    }
    
}"""

        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 18

        val answerValue = (st[3] as Assignment).value
        answerValue shouldBe NumericLiteral(BaseDataType.UWORD, 0.0, Position.DUMMY)

        val funcarg1 = (st[4] as FunctionCallStatement).args.single()
        funcarg1 shouldBe NumericLiteral(BaseDataType.UWORD, 0.0, Position.DUMMY)

        val answer2Value = (st[8] as Assignment).value
        answer2Value shouldBe NumericLiteral(BaseDataType.UWORD, 8.0, Position.DUMMY)

        val funcarg2 = (st[9] as FunctionCallStatement).args.single()
        funcarg2 shouldBe NumericLiteral(BaseDataType.UWORD, 8.0, Position.DUMMY)

        val answer3ValueTc = (st[15] as Assignment).value as TypecastExpression
        answer3ValueTc.type shouldBe BaseDataType.UWORD
        val answer3Value = answer3ValueTc.expression as FunctionCallExpression
        answer3Value.target.nameInSource shouldBe listOf("msb")
        answer3Value.args.single() shouldBe instanceOf<BinaryExpression>()

        val funcarg3tc = (st[16] as FunctionCallStatement).args.single() as TypecastExpression
        funcarg3tc.type shouldBe BaseDataType.UWORD
        val funcarg3 = funcarg3tc.expression as FunctionCallExpression
        funcarg3.target.nameInSource shouldBe listOf("msb")
        funcarg3.args.single() shouldBe instanceOf<BinaryExpression>()
    }

    test("no operand swap on logical expressions with shortcircuit evaluation") {
        val src="""
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    str scanline_buf = "?"* 20

    sub start() {
        if diskio.f_open("test.prg") and diskio.f_read(scanline_buf, 2)==2
            cx16.r0++

        if diskio.f_open("test.prg") or diskio.f_read(scanline_buf, 2)==2
            cx16.r0++

        if diskio.f_open("test.prg") xor diskio.f_read(scanline_buf, 2)==2
            cx16.r0++
    }
}"""
        val result = compileText(Cx16Target(), true, src, outputDir, writeAssembly = false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 4
        val ifCond1 = (st[0] as IfElse).condition as BinaryExpression
        val ifCond2 = (st[1] as IfElse).condition as BinaryExpression
        val ifCond3 = (st[2] as IfElse).condition as BinaryExpression
        (ifCond1.left as FunctionCallExpression).target.nameInSource shouldBe listOf("diskio", "f_open")
        (ifCond2.left as FunctionCallExpression).target.nameInSource shouldBe listOf("diskio", "f_open")
        (ifCond3.left as FunctionCallExpression).target.nameInSource shouldBe listOf("diskio", "f_open")
        val right1 = ifCond1.right as BinaryExpression
        val right2 = ifCond2.right as BinaryExpression
        val right3 = ifCond3.right as BinaryExpression
        (right1.left as FunctionCallExpression).target.nameInSource shouldBe listOf("diskio", "f_read")
        (right2.left as FunctionCallExpression).target.nameInSource shouldBe listOf("diskio", "f_read")
        (right3.left as FunctionCallExpression).target.nameInSource shouldBe listOf("diskio", "f_read")
    }

    test("eliminate same target register assignments") {
        val src="""
%zeropage basicsafe
%option no_sysinit

main {
    extsub ${'$'}2000 = func1() clobbers(X) -> ubyte @A, word @R0, byte @R1
    extsub ${'$'}3000 = func2() clobbers(X) -> ubyte @A, uword @R0, uword @R1
    extsub ${'$'}4000 = func3() clobbers(X) -> ubyte @R0

    sub start() {
        bool flag
        void cbm.GETIN()
        flag, cx16.r1L = cbm.GETIN()
        void, cx16.r0s, cx16.r1sL = func1()
        void, cx16.r2, cx16.r1 = func2()
        cx16.r0L = func3()
        cx16.r0H = func3()
    }
}"""
        val result = compileText(C64Target(), true, src, outputDir, writeAssembly = true)!!
        val st = result.codegenAst!!.entrypoint()!!.children
        st.size shouldBe 9
        (st[2] as PtFunctionCall).name shouldBe "cbm.GETIN"
        (st[2] as PtFunctionCall).void shouldBe true
        val a1 = st[3] as PtAssignment
        (a1.value as PtFunctionCall).name shouldBe "cbm.GETIN"
        a1.multiTarget shouldBe true
        a1.children.size shouldBe 3
        (a1.children[0] as PtAssignTarget).void shouldBe false
        (a1.children[0] as PtAssignTarget).identifier!!.name shouldBe "p8b_main.p8s_start.p8v_flag"
        (a1.children[1] as PtAssignTarget).void shouldBe false
        (a1.children[1] as PtAssignTarget).identifier!!.name shouldBe "cx16.r1L"

        (st[4] as PtFunctionCall).name shouldBe "p8b_main.p8s_func1"
        (st[4] as PtFunctionCall).void shouldBe true
        val a2 = st[5] as PtAssignment
        (a2.value as PtFunctionCall).name shouldBe "p8b_main.p8s_func2"
        a2.multiTarget shouldBe true
        a2.children.size shouldBe 4
        (a2.children[0] as PtAssignTarget).void shouldBe true
        (a2.children[1] as PtAssignTarget).void shouldBe false
        (a2.children[1] as PtAssignTarget).identifier!!.name shouldBe "cx16.r2"
        (a2.children[2] as PtAssignTarget).void shouldBe true

        (st[6] as PtFunctionCall).name shouldBe "p8b_main.p8s_func3"
        (st[6] as PtFunctionCall).void shouldBe true
        val a3 = st[7] as PtAssignment
        (a3.value as PtFunctionCall).name shouldBe "p8b_main.p8s_func3"
        a3.multiTarget shouldBe false
        a3.children.size shouldBe 2
        (a3.children[0] as PtAssignTarget).void shouldBe false
        (a3.children[0] as PtAssignTarget).identifier!!.name shouldBe "cx16.r0H"
    }

    test("symbol table correct in asmgen after earlier optimization steps") {
        val src="""
main {
    sub start() {
        uword @shared bulletRef, enemyRef
        const ubyte BD_Y = 10
        const ubyte EN_Y = 11

        if bulletRef[BD_Y] == enemyRef[EN_Y] or bulletRef[BD_Y] == enemyRef[EN_Y] + 1 {
            cx16.r0++
        }
    }
}"""
        compileText(VMTarget(), true, src, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("optimizing inlined functions must reference proper scopes") {
        val src="""
main {
    sub start() {
        void other.sub1()
        cx16.r0L = other.sub1()+other.sub1()
    }

}

other {
    sub  sub2() -> ubyte{
        cx16.r0++
        cx16.r1++
        return cx16.r0L
    }

    sub sub1() -> ubyte {
        return sub2()
    }
}"""

        compileText(VMTarget(), true, src, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
    }

    test("if-else should not have 'not' in the condition even after optimization steps") {
        val src="""
main {
    sub start() {
        if (cx16.r0 & 1) as bool == false
            cx16.r1++
        else
            cx16.r2++

        if (cx16.r0 & 1) as bool == true
            cx16.r1++
        else
            cx16.r2++

        if not((cx16.r0 & 1) as bool)
            cx16.r1++
        else
            cx16.r2++

        if (cx16.r0 & 1) as bool
            cx16.r1++
        else
            cx16.r2++
    }
}"""
        compileText(VMTarget(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(C64Target(), false, src, outputDir, writeAssembly = false) shouldNotBe null
        compileText(VMTarget(), true, src, outputDir, writeAssembly = true) shouldNotBe null
        compileText(C64Target(), true, src, outputDir, writeAssembly = true) shouldNotBe null
    }

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
})
