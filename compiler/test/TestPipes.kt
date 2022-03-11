package prog8tests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.Program
import prog8.ast.expressions.*
import prog8.ast.statements.Assignment
import prog8.ast.statements.FunctionCallStatement
import prog8.ast.statements.Pipe
import prog8.ast.statements.VarDecl
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.compiler.astprocessing.AstPreprocessor
import prog8.parser.Prog8Parser.parseModule
import prog8.parser.SourceCode
import prog8tests.helpers.*


class TestPipes: FunSpec({

    test("pipe expression parse tree after preprocessing") {
        val text = """
            main {
                sub start() {
                    uword xx = 9999 |> func1() |> func2()
                                    |> func1() |> func2()
                                    |> func1()
                }
                sub func1(uword arg) -> uword {
                    return arg+1111
                }
                sub func2(uword arg) -> uword {
                    return arg+2222
                }
            }"""
        val src = SourceCode.Text(text)
        val module = parseModule(src)
        val errors = ErrorReporterForTests()
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val preprocess = AstPreprocessor(program, errors, C64Target())
        preprocess.visit(program)
        errors.errors.size shouldBe 0
        preprocess.applyModifications()

        program.entrypoint.statements.size shouldBe 1
        val pipe = (program.entrypoint.statements.single() as VarDecl).value as PipeExpression
        pipe.source shouldBe NumericLiteral(DataType.UWORD, 9999.0, Position.DUMMY)
        pipe.segments.size shouldBe 5
        var call = pipe.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func1")
        call.args.size shouldBe 0
        call = pipe.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func2")
        call.args.size shouldBe 0
        call = pipe.segments[2] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func1")
        call.args.size shouldBe 0
        call = pipe.segments[3] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func2")
        call.args.size shouldBe 0
        call = pipe.segments[4] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func1")
        call.args.size shouldBe 0
    }

    test("pipe statement parse tree after preprocessing") {
        val text = """
            main {
                sub start() {
                    9999 |> func1() |> func2()
                             |> func1() |> func2()
                             |> func3()
                }
                sub func1(uword arg) -> uword {
                    return arg+1111
                }
                sub func2(uword arg) -> uword {
                    return arg+2222
                }
                sub func3(uword arg) {
                    ; nothing
                }
            }"""
        val src = SourceCode.Text(text)
        val module = parseModule(src)
        val errors = ErrorReporterForTests()
        val program = Program("test", DummyFunctions, DummyMemsizer, DummyStringEncoder)
        program.addModule(module)
        val preprocess = AstPreprocessor(program, errors, C64Target())
        preprocess.visit(program)
        errors.errors.size shouldBe 0
        preprocess.applyModifications()

        program.entrypoint.statements.size shouldBe 1
        val pipe = program.entrypoint.statements.single() as Pipe
        pipe.source shouldBe NumericLiteral(DataType.UWORD, 9999.0, Position.DUMMY)
        pipe.segments.size shouldBe 5
        var call = pipe.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func1")
        call.args.size shouldBe 0
        call = pipe.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func2")
        call.args.size shouldBe 0
        call = pipe.segments[2] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func1")
        call.args.size shouldBe 0
        call = pipe.segments[3] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func2")
        call.args.size shouldBe 0
        call = pipe.segments[4] as IFunctionCall
        call.target.nameInSource shouldBe listOf("func3")
        call.args.size shouldBe 0
    }

    test("correct pipe statements (no opt)") {
        val text = """
            %import floats
            %import textio
            
            main {
                sub start() {

                    1.234 |> addfloat() 
                          |> floats.print_f()
                    
                    startvalue(99) |> addword()
                         |> txt.print_uw()

                    9999 |> abs() |> txt.print_uw()
                    9999 |> txt.print_uw()
                    99 |> abs() |> txt.print_ub()
                    99 |> txt.print_ub()
                }

                sub startvalue(ubyte arg) -> uword {
                    return arg+9999
                }
                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }"""
        val result = compileText(C64Target(), optimize = false, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        val pipef = stmts[0] as Pipe
        pipef.source shouldBe instanceOf<NumericLiteral>()
        pipef.segments.size shouldBe 2
        var call = pipef.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("addfloat")
        call = pipef.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("floats", "print_f")

        val pipew = stmts[1] as Pipe
        pipef.source shouldBe instanceOf<NumericLiteral>()
        pipew.segments.size shouldBe 2
        call = pipew.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("addword")
        call = pipew.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("txt", "print_uw")

        stmts[2] shouldBe instanceOf<Pipe>()
        stmts[3] shouldBe instanceOf<Pipe>()
        stmts[4] shouldBe instanceOf<Pipe>()
        stmts[5] shouldBe instanceOf<Pipe>()
    }

    test("correct pipe statements (with opt)") {
        val text = """
            %import floats
            %import textio
            
            main {
                sub start() {

                    1.234 |> addfloat() 
                          |> floats.print_f()
                    
                    startvalue(99) |> addword()
                         |> txt.print_uw()

                    ; these should be optimized into just the function calls:
                    9999 |> abs() |> txt.print_uw()
                    9999 |> txt.print_uw()
                    99 |> abs() |> txt.print_ub()
                    99 |> txt.print_ub()
                }

                sub startvalue(ubyte arg) -> uword {
                    return arg+9999
                }
                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }"""
        val result = compileText(C64Target(), optimize = true, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        val pipef = stmts[0] as Pipe
        pipef.source shouldBe instanceOf<FunctionCallExpression>()
        (pipef.source as IFunctionCall).target.nameInSource shouldBe listOf("addfloat")
        pipef.segments.size shouldBe 1
        val callf = pipef.segments[0] as IFunctionCall
        callf.target.nameInSource shouldBe listOf("floats", "print_f")

        val pipew = stmts[1] as Pipe
        pipef.source shouldBe instanceOf<FunctionCallExpression>()
        (pipew.source as IFunctionCall).target.nameInSource shouldBe listOf("startvalue")
        pipew.segments.size shouldBe 2
        val callw = pipew.segments[1] as IFunctionCall
        callw.target.nameInSource shouldBe listOf("txt", "print_uw")

        var stmt = stmts[2] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_uw")
        stmt = stmts[3] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_uw")
        stmt = stmts[4] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_ub")
        stmt = stmts[5] as FunctionCallStatement
        stmt.target.nameInSource shouldBe listOf("txt", "print_ub")
    }

    test("incorrect type in pipe statement") {
        val text = """
            %option enable_floats
            
            main {
                sub start() {

                    1.234 |> addfloat() 
                          |> addword() |> addword()
                }

                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "incompatible"
    }

    test("correct pipe expressions (no opt)") {
        val text = """
            %import floats
            %import textio
            
            main {
                sub start() {
                    float @shared fl = 1.234 |> addfloat() 
                                        |> addfloat()
                    
                    uword @shared ww = startvalue(99) |> addword()
                                        |> addword()
                                        
                    ubyte @shared cc = 30 |> sin8u() |> cos8u()
                    cc = cc |> sin8u() |> cos8u()
                }

                sub startvalue(ubyte arg) -> uword {
                    return arg+9999
                }
                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }"""
        val result = compileText(C64Target(), optimize = false, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 8
        val assignf = stmts[1] as Assignment
        val pipef = assignf.value as PipeExpression
        pipef.source shouldBe instanceOf<NumericLiteral>()
        pipef.segments.size shouldBe 2
        var call = pipef.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("addfloat")
        call = pipef.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("addfloat")


        val assignw = stmts[3] as Assignment
        val pipew = assignw.value as PipeExpression
        pipew.source shouldBe instanceOf<IFunctionCall>()
        pipew.segments.size shouldBe 2
        call = pipew.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("addword")
        call = pipew.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("addword")

        var assigncc = stmts[5] as Assignment
        val value = assigncc.value as PipeExpression
        value.source shouldBe NumericLiteral(DataType.UBYTE, 30.0, Position.DUMMY)
        value.segments.size shouldBe 2
        call = value.segments[0] as IFunctionCall
        call.target.nameInSource shouldBe listOf("sin8u")
        call = value.segments[1] as IFunctionCall
        call.target.nameInSource shouldBe listOf("cos8u")

        assigncc = stmts[6] as Assignment
        val pipecc = assigncc.value as PipeExpression
        pipecc.source shouldBe instanceOf<IdentifierReference>()
        pipecc.segments.size shouldBe 2
        pipecc.segments[0] shouldBe instanceOf<BuiltinFunctionCall>()
        pipecc.segments[1] shouldBe instanceOf<BuiltinFunctionCall>()
    }

    test("correct pipe expressions (with opt)") {
        val text = """
            %import floats
            %import textio
            
            main {
                sub start() {
                    float @shared fl = 1.234 |> addfloat() 
                                        |> addfloat()
                    
                    uword @shared ww = startvalue(99) |> addword()
                                        |> addword()
                                        
                    ubyte @shared cc = 30 |> sin8u() |> cos8u()     ; will be optimized away into a const number
                    cc = cc |> sin8u() |> cos8u()
                }

                sub startvalue(ubyte arg) -> uword {
                    return arg+9999
                }
                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }
            """
        val result = compileText(C64Target(), optimize = true, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 8
        val assignf = stmts[1] as Assignment
        val pipef = assignf.value as PipeExpression
        pipef.source shouldBe instanceOf<FunctionCallExpression>()
        pipef.segments.size shouldBe 1
        pipef.segments[0] shouldBe instanceOf<FunctionCallExpression>()

        val assignw = stmts[3] as Assignment
        val pipew = assignw.value as PipeExpression
        pipew.source shouldBe instanceOf<FunctionCallExpression>()
        pipew.segments.size shouldBe 2
        pipew.segments[0] shouldBe instanceOf<FunctionCallExpression>()
        pipew.segments[1] shouldBe instanceOf<FunctionCallExpression>()

        var assigncc = stmts[5] as Assignment
        val value = assigncc.value as NumericLiteral
        value.number shouldBe 190.0

        assigncc = stmts[6] as Assignment
        val pipecc = assigncc.value as PipeExpression
        pipecc.source shouldBe instanceOf<BuiltinFunctionCall>()
        (pipecc.source as BuiltinFunctionCall).target.nameInSource shouldBe listOf("sin8u")

        pipecc.segments.size shouldBe 1
        pipecc.segments[0] shouldBe instanceOf<BuiltinFunctionCall>()
        (pipecc.segments[0] as BuiltinFunctionCall).target.nameInSource shouldBe listOf("cos8u")
    }

    test("incorrect type in pipe expression") {
        val text = """
            %option enable_floats
            
            main {
                sub start() {
                    uword result = 1.234 |> addfloat() 
                                         |> addword() |> addword()
                }

                sub addfloat(float fl) -> float {
                    return fl+2.22
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), false, text, errors=errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "incompatible"
    }

    test("correct pipe statement with builtin expression") {
        val text = """
            %import textio
            
            main {
                sub start() {
                    uword ww = 9999
                    ubyte bb = 99
                    ww |> abs() |> txt.print_uw()
                    bb |> abs() |> txt.print_ub()
                }
            }
        """
        val result = compileText(C64Target(), true, text, writeAssembly = true)!!
        val stmts = result.program.entrypoint.statements
        stmts.size shouldBe 7
        val pipeww = stmts[4] as Pipe
        pipeww.source shouldBe instanceOf<BuiltinFunctionCall>()
        pipeww.segments.size shouldBe 1
        pipeww.segments[0] shouldBe instanceOf<IFunctionCall>()

        val pipebb = stmts[5] as Pipe
        pipebb.source shouldBe instanceOf<BuiltinFunctionCall>()
        pipebb.segments.size shouldBe 1
        pipebb.segments[0] shouldBe instanceOf<IFunctionCall>()
    }

    test("pipe statement with type errors") {
        val text = """
            %import textio
            
            main {
                sub start() {
                    uword ww = 9999
                    9999 |> abs() |> txt.print_ub()
                    ww |> abs() |> txt.print_ub()
                }
            }
        """
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize = false, text, writeAssembly = true, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "UWORD incompatible"
        errors.errors[1] shouldContain "UWORD incompatible"
    }

    test("pipe detects invalid number of args") {
        val text = """
            main {
                sub start() {
                    uword ww = startvalue() |> addword()
                                        |> addword()
                                        
                    ubyte cc = 30 |> sin8u(99) |> cos8u(22)
                }

                sub startvalue(ubyte arg) -> uword {
                    return arg+9999
                }
                sub addword(uword ww) -> uword {
                    return ww+2222
                }
            }"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize = false, text, writeAssembly = false, errors=errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain ":4:32: invalid number of arguments"
        errors.errors[1] shouldContain ":7:44: invalid number of arguments"
        errors.errors[2] shouldContain ":7:57: invalid number of arguments"
    }
})
