package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.ast.*
import prog8.code.core.BaseDataType
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.core.SubType
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestVariousCompilerAst: FunSpec({
    context("arrays") {

        test("invalid array element proper errormessage") {
            val text="""
            main {
                sub start() {
                    uword[] commands = ["abc", 1.234]
                }
            }"""
            val errors = ErrorReporterForTests()
            compileText(C64Target(), false, text, writeAssembly = true, errors=errors) shouldBe null
            errors.errors.size shouldBe 1
            errors.errors[0] shouldContain "value has incompatible type"
        }

        test("array literals") {
            val text="""
%zeropage basicsafe

main {
    sub start() {
        ubyte b1
        ubyte b2
        ubyte[] array1 = [1,2,3]
        ubyte[] array2 = [9,8,7]

        uword[] @shared addresses1 = [&b1, &b2]
        uword[] @shared addresses2 = [array1, array2]
        uword[] @shared addresses3 = [&array1, &array2]
        uword[] @shared addresses4 = ["string1", "string2"]
        uword[] @shared addresses5 = [1111, 2222]
    }
}"""
            compileText(C64Target(), false, text, writeAssembly = true) shouldNotBe null
        }

        test("array init size mismatch error") {
            val text="""
main {
    sub start() {
        ubyte[10] uba = [1,2,3]
        bool[10] bba = [true, false, true]
    }
}"""
            val errors = ErrorReporterForTests()
            compileText(C64Target(), false, text, writeAssembly = false, errors = errors) shouldBe null
            errors.errors.size shouldBe 2
            errors.errors[0] shouldContain "size mismatch"
            errors.errors[1] shouldContain "size mismatch"
        }

        test("returning array as uword") {
            val src =  """
main {
    sub start() {
        cx16.r0 = getarray()
    }

    sub getarray() -> uword {
        return [11,22,33]
    }
}"""
            compileText(VMTarget(), optimize=false, src, writeAssembly=false) shouldNotBe null
        }
    }

    context("alias") {
        test("aliases ok") {
            val src="""
main {
    alias print = txt.print
    alias width = txt.DEFAULT_WIDTH

    sub start() {
        alias print2 = txt.print
        alias width2 = txt.DEFAULT_WIDTH
        print("one")
        print2("two")
        txt.print_ub(width)
        txt.print_ub(width2)
        
        ; chained aliases
        alias chained = print2
        chained("chained")
    }
}

txt {
    const ubyte DEFAULT_WIDTH = 80
    sub print_ub(ubyte value) {
        ; nothing
    }
    sub print(str msg) {
        ; nothing
    }
}

"""
            compileText(C64Target(), optimize=false, src, writeAssembly=false) shouldNotBe null
        }

        test("wrong alias gives correct error") {
            val src="""
main {
    alias print = txt.print2222
    alias width = txt.DEFAULT_WIDTH

    sub start() {
        alias print2 = txt.print
        alias width2 = txt.DEFAULT_WIDTH_XXX
        print("one")
        print2("two")
        txt.print_ub(width)
        txt.print_ub(width2)
    }
}

txt {
    const ubyte DEFAULT_WIDTH = 80
    sub print_ub(ubyte value) {
        ; nothing
    }
    sub print(str msg) {
        ; nothing
    }
}

"""
            val errors = ErrorReporterForTests()
            compileText(C64Target(), optimize=false, src, writeAssembly=false, errors=errors) shouldBe null
            errors.errors.size shouldBe 2
            errors.errors[0] shouldContain "undefined symbol: txt.print2222"
            errors.errors[1] shouldContain "undefined symbol: txt.DEFAULT_WIDTH_XXX"
        }
    }

    context("strings") {
        test("string comparisons") {
            val src="""
main {

    sub start() {
        str name = "name"
        uword nameptr = &name
        bool result

        result = name=="foo"
        result = name!="foo"
        result = name<"foo"
        result = name>"foo"

        result = nameptr=="foo"
        result = nameptr!="foo"
        result = nameptr<"foo"
        result = nameptr>"foo"

        void compare(name, "foo")
        void compare(name, "name")
        void compare(nameptr, "foo")
        void compare(nameptr, "name")
    }

    sub compare(str s1, str s2) -> ubyte {
        if s1==s2
            return 42
        return 0
    }
}"""
            val result = compileText(C64Target(), optimize=false, src, writeAssembly=true)!!
            val stmts = result.compilerAst.entrypoint.statements
            stmts.size shouldBe 17
            val result2 = compileText(VMTarget(), optimize=false, src, writeAssembly=true)!!
            val stmts2 = result2.compilerAst.entrypoint.statements
            stmts2.size shouldBe 17
        }

        test("string concatenation and repeats") {
            val src="""
        main {
            sub start() {
                str @shared name = "part1" + "part2"
                str @shared rept = "rep"*4
                const ubyte times = 3
                name = "xx1" + "xx2"
                rept = "xyz" * (times+1)
            }
        }"""
            val result = compileText(C64Target(), optimize=false, src, writeAssembly=true)!!
            val stmts = result.compilerAst.entrypoint.statements
            stmts.size shouldBe 6
            val name1 = stmts[0] as VarDecl
            val rept1 = stmts[1] as VarDecl
            (name1.value as StringLiteral).value shouldBe "part1part2"
            (rept1.value as StringLiteral).value shouldBe "reprepreprep"
            val name2strcopy = stmts[3] as IFunctionCall
            val rept2strcopy = stmts[4] as IFunctionCall
            val name2 = name2strcopy.args.first() as IdentifierReference
            val rept2 = rept2strcopy.args.first() as IdentifierReference
            (name2.targetVarDecl(result.compilerAst)!!.value as StringLiteral).value shouldBe "xx1xx2"
            (rept2.targetVarDecl(result.compilerAst)!!.value as StringLiteral).value shouldBe "xyzxyzxyzxyz"
        }

        test("char as str param is error") {
            val src = """
main {
    sub start() {
        print('@')
    }

    sub print(str message) {
    }
}"""
            val errors = ErrorReporterForTests()
            compileText(VMTarget(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.single() shouldContain  "cannot use byte value"
        }
    }

    context("return value") {
        test("missing return value is a syntax error") {
            val src="""
main {
    sub start() {
        cx16.r0 = runit1()
        cx16.r1 = runit2()
    }

    sub runit1() -> uword {
        repeat {
            cx16.r0++
            goto runit1
        }
    }

    sub runit2() -> uword {
        cx16.r0++
    }
}"""
            val errors = ErrorReporterForTests()
            compileText(C64Target(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.size shouldBe 2
            errors.errors[0] shouldContain "has result value"
            errors.errors[1] shouldContain "has result value"
        }

        test("missing return value is not a syntax error if there's an external goto") {
            val src="""
main {
    sub start() {
        cx16.r0 = runit1()
        runit2()
    }

    sub runit1() -> uword {
        repeat {
            cx16.r0++
            goto runit2
        }
    }

    sub runit2() {
        cx16.r0++
    }
}"""
            compileText(C64Target(), optimize=false, src, writeAssembly=false) shouldNotBe null
        }
    }

    context("variable declarations") {
        test("multi-var decls in scope with initializer") {
            val src="""
main {
    sub start() {
        ubyte w

        for w in 0 to 20 {
            ubyte @zp x,y,z=13
            ubyte q,r,s
            x++
            y++
            z++
        }
    }
}"""
            val result = compileText(VMTarget(), optimize = false, src, writeAssembly = false)!!
            val st = result.compilerAst.entrypoint.statements
            /*
        sub start () {
            ubyte s
            s = 0
            ubyte r
            r = 0
            ubyte q
            q = 0
            ubyte @zp z
            ubyte @zp y
            ubyte @zp x
            ubyte w
            for w in 0 to 20 step 1  {
                z = 13
                y = 13
                x = 13
                x++
                y++
                z++
            }
        }
             */
            val vars = st.filterIsInstance<VarDecl>()
            vars.size shouldBe 7
            vars.all { it.names.size<=1 } shouldBe true
            vars.map { it.name }.toSet() shouldBe setOf("s","r","q","z","y","x","w")
            val forloop = st.single { it is ForLoop } as ForLoop
            forloop.body.statements[0] shouldBe instanceOf<Assignment>()
            forloop.body.statements[1] shouldBe instanceOf<Assignment>()
            forloop.body.statements[2] shouldBe instanceOf<Assignment>()
        }

        test("multi vardecls smart desugaring") {
            val src="""
main {
    sub start() {
        ubyte @shared x,y,z
        ubyte @shared k,l,m = 42
        uword @shared r,s,t = sys.progend()
    }
}"""
            val result = compileText(Cx16Target(), optimize=true, src, writeAssembly=false)!!
            val st = result.compilerAst.entrypoint.statements
            st.size shouldBe 18
            st[0] shouldBe instanceOf<VarDecl>()    // x
            st[2] shouldBe instanceOf<VarDecl>()    // y
            st[4] shouldBe instanceOf<VarDecl>()    // z
            st[6] shouldBe instanceOf<VarDecl>()    // k
            st[8] shouldBe instanceOf<VarDecl>()    // l
            st[10] shouldBe instanceOf<VarDecl>()    // m
            st[12] shouldBe instanceOf<VarDecl>()    // r
            st[14] shouldBe instanceOf<VarDecl>()    // s
            st[16] shouldBe instanceOf<VarDecl>()    // t
            val valX = (st[1] as Assignment).value
            (valX as NumericLiteral).number shouldBe 0.0
            val valY = (st[3] as Assignment).value
            (valY as NumericLiteral).number shouldBe 0.0
            val valZ = (st[5] as Assignment).value
            (valZ as NumericLiteral).number shouldBe 0.0
            val valK = (st[7] as Assignment).value
            (valK as NumericLiteral).number shouldBe 42.0
            val valL = (st[9] as Assignment).value
            (valL as NumericLiteral).number shouldBe 42.0
            val valM = (st[11] as Assignment).value
            (valM as NumericLiteral).number shouldBe 42.0
            val valR = (st[13] as Assignment).value
            (valR as FunctionCallExpression).target.nameInSource shouldBe listOf("sys", "progend")
            val valS = (st[15] as Assignment).value
            (valS as IdentifierReference).nameInSource shouldBe listOf("r")
            val valT = (st[17] as Assignment).value
            (valT as IdentifierReference).nameInSource shouldBe listOf("r")
        }

        test("various multi var decl symbol lookups") {
            val src="""
main {
    sub start() {
        uword @shared a,b
        b = a
        cx16.r1L = lsb(a)
        funcw(a)
        funcb(lsb(a))
    }

    sub funcw(uword arg) {
        arg++
    }

    sub funcb(ubyte arg) {
        arg++
    }
}"""
            compileText(Cx16Target(), false, src) shouldNotBe null
        }

        test("@dirty variables") {
            val src="""
%import floats

main {
    uword @shared @dirty globw
    uword @shared globwi = 4444
    float @shared @dirty globf
    float @shared globfi = 4
    ubyte[5] @shared @dirty globarr1
    ubyte[] @shared globarr2 = [11,22,33,44,55]

    sub start() {
        uword @shared @dirty locw
        uword @shared locwi = 4444
        float @shared @dirty locf
        float @shared locfi = 4.0
        ubyte[5] @shared @dirty locarr1
        ubyte[] @shared locarr2 = [11,22,33,44,55]

        sys.clear_carry()
    }
}"""

            val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
            val result = compileText(C64Target(), optimize=false, src, writeAssembly=false, errors=errors)!!
            errors.warnings.size shouldBe 6
            errors.infos.size shouldBe 0
            errors.warnings.all { "dirty variable" in it } shouldBe true
            val start = result.compilerAst.entrypoint
            val st = start.statements
            st.size shouldBe 9
            val assignments = st.filterIsInstance<Assignment>()
            assignments.size shouldBe 2
            assignments[0].target.identifier?.nameInSource shouldBe listOf("locwi")
            assignments[1].target.identifier?.nameInSource shouldBe listOf("locfi")
            val blockst = start.definingBlock.statements
            blockst.size shouldBe(9)
            val blockassignments = blockst.filterIsInstance<Assignment>()
            blockassignments.size shouldBe 2
            blockassignments[0].target.identifier?.nameInSource shouldBe listOf("globwi")
            blockassignments[1].target.identifier?.nameInSource shouldBe listOf("globfi")
        }
    }

context("various") {
    test("no crash for all sorts of undefined variables in complex expression") {
        val src = """
%import floats

main {
    sub start() {
        x_position = (((floats.cos(CORNER_ANGLE + theta) * distance_to_corner) + (position_offset as float)) * 256.0) as word
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBeGreaterThan 1
        errors.clear()
        compileText(C64Target(), optimize=true, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBeGreaterThan 1
    }

    test("symbol names in inline assembly blocks") {
        val names1 = InlineAssembly("""
            
        """, false, Position.DUMMY).names
        names1 shouldBe emptySet()

        val names2 = InlineAssembly("""
label:   lda #<value
         sta ${'$'}ea
         sta 123
label2: 
         sta  othervalue    ; but not these in the comments
; also not these
        ;;   ...or these
   // valid words  123456
        """, false, Position.DUMMY).names

        names2 shouldBe setOf("label", "lda", "sta", "ea", "value", "label2", "othervalue", "valid", "words")
    }

    test("invalid && operator") {
        val text="""
main {
    sub start() {
        uword b1
        uword b2
        uword b3 = b1 && b2     ; invalid syntax: '&&' is not an operator, 'and' should be used instead
    }
}"""
        compileText(C64Target(), false, text, writeAssembly = false) shouldBe null
    }

    test("pointervariable indexing allowed with >255") {
        val src="""
main {
    sub start() {
        uword pointer = ${'$'}2000
        @(pointer+${'$'}1000) = 123
        ubyte @shared ub = @(pointer+${'$'}1000)
        pointer[${'$'}1000] = 99
        ub = pointer[${'$'}1000]
        uword index = ${'$'}1000
        pointer[index] = 55
        ub = pointer[index]
    }
}"""
        compileText(C64Target(), optimize=false, src, writeAssembly=false) shouldNotBe null
    }

    test("bitshift left of const byte not converted to word") {
        val src="""
main {
    sub start() {
        ubyte shift = 10
        uword value = 1<<shift
        value++
        value = 1<<shift
        value++
    }
}"""
        val result = compileText(C64Target(), optimize=false, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 7
        val assign1expr = (stmts[3] as Assignment).value as BinaryExpression
        val assign2expr = (stmts[5] as Assignment).value as BinaryExpression
        assign1expr.operator shouldBe "<<"
        val leftval1 = assign1expr.left.constValue(result.compilerAst)!!
        leftval1.type shouldBe BaseDataType.UWORD
        leftval1.number shouldBe 1.0
        val leftval2 = assign2expr.left.constValue(result.compilerAst)!!
        leftval2.type shouldBe BaseDataType.UWORD
        leftval2.number shouldBe 1.0
    }

    test("hoisting vars with complex initializer expressions to outer scope") {
        val src="""
main {
    sub pget(uword @zp x, uword y) -> ubyte {
        return lsb(x+y)
    }

    sub start() {
        uword[128] YY
        ubyte[] ARRAY = [1, 5, 2]
        repeat {
            bool pixel_side1 = pget(2, YY[2]+1) in ARRAY
            bool pixel_side2 = pget(2, 2) in ARRAY
            ubyte[] array2 = [1,2,3]
        }
    }
}"""
        val result = compileText(C64Target(), optimize=false, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 9
    }

    test("alternative notation for negative containment check") {
        val src="""
main {
    sub start() {
        ubyte[] array=[1,2,3]
        bool result
        result = not (3 in array)
        result = 3 not in array
    }
}
"""
        val result = compileText(C64Target(), optimize=false, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 4
        val value1 = (stmts[2] as Assignment).value as PrefixExpression
        val value2 = (stmts[3] as Assignment).value as PrefixExpression
        value1.operator shouldBe "not"
        value2.operator shouldBe "not"
        value1.expression shouldBe instanceOf<ContainmentCheck>()
        value2.expression shouldBe instanceOf<ContainmentCheck>()
    }

    test("unroll good") {
        val src="""
main {
    sub start() {
        unroll 200 {
            cx16.r0++
            poke(2000,2)
        }
    }
}
"""
        val errors = ErrorReporterForTests(keepMessagesAfterReporting = true)
        compileText(C64Target(), optimize=false, src, writeAssembly=false, errors=errors) shouldNotBe null
        errors.warnings.size shouldBe 1
        errors.warnings[0] shouldContain "large number of unrolls"
    }

    test("unroll bad") {
        val src="""
main {
    sub start() {
        repeat {
            unroll 80 {
                cx16.r0++
                when cx16.r0 {
                    1 -> cx16.r0++
                    else -> cx16.r0++
                }
                break
            }
        }
    }
}
"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "invalid statement in unroll loop"
        errors.errors[1] shouldContain "invalid statement in unroll loop"
    }

    test("various curly brace styles") {
        val src="""
main
{
    sub start()
    {
        ubyte @shared variable=55
        when variable
        {
            33 -> cx16.r0++
            else -> cx16.r1++
        }

        if variable!=0 {
            cx16.r0++
        } else {
            cx16.r1++
        }

        if variable!=0 { cx16.r0++ }
        else { cx16.r1++ }

        if variable!=0
        {
            cx16.r0++
        }
        else
        {
            cx16.r1++
        }

        other.othersub()
    }
}


other {
    sub othersub() {
        cx16.r0++
    }
}"""

        compileText(VMTarget(), optimize=false, src, writeAssembly=false) shouldNotBe null
    }

    test("when on booleans") {
        val src = """
main
{
    sub start()
    {
        bool choiceVariable=true
        when choiceVariable {
          false -> cx16.r0++
          true -> cx16.r1++
        }
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(VMTarget(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "use if"
    }

    test("sizeof number const evaluation in vardecl") {
        val src="""
main {
    sub start() {
        uword @shared size1 = sizeof(22222)
        uword @shared size2 = sizeof(2.2)
    }
}"""
        compileText(VMTarget(), optimize=false, src, writeAssembly=false) shouldNotBe null
    }

    test("'not in' operator parsing") {
        val src="""
main {
    sub start() {
        str test = "test"
        bool @shared insync
        if not insync
            insync=true
        if insync not in test
            insync=true
    }
}"""
        compileText(VMTarget(), optimize=false, src, writeAssembly=false) shouldNotBe null
    }

    test("no chained comparison modifying expression semantics") {
        val src="""
main {
    sub start() {
        ubyte @shared n=20
        ubyte @shared x=10
        bool @shared result1, result2

        if n < x {
          ; nothing here, conditional gets inverted
        } else {
            cx16.r0++
        }
        result1 = n<x == false
        result2 = not n<x
    }
}"""
        val result=compileText(VMTarget(), optimize=true, src, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 11

        val ifCond = (st[8] as IfElse).condition as BinaryExpression
        ifCond.operator shouldBe ">="
        (ifCond.left as IdentifierReference).nameInSource shouldBe listOf("n")
        (ifCond.right as IdentifierReference).nameInSource shouldBe listOf("x")
        val assign1 = (st[9] as Assignment).value as BinaryExpression
        val assign2 = (st[10] as Assignment).value as BinaryExpression
        assign1.operator shouldBe ">="
        (assign1.left as IdentifierReference).nameInSource shouldBe listOf("n")
        (assign1.right as IdentifierReference).nameInSource shouldBe listOf("x")
        assign2.operator shouldBe ">="
        (assign1.left as IdentifierReference).nameInSource shouldBe listOf("n")
        (assign1.right as IdentifierReference).nameInSource shouldBe listOf("x")
    }

    test("modulo is not directive") {
        val src="""
main {
    sub start() {
        ubyte @shared bb = 199
        ubyte @shared cc = 12
        ubyte @shared bb2 = bb%cc
    }
}"""

        val result=compileText(Cx16Target(), optimize=false, src, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 6
        val value = (st[5] as Assignment).value as BinaryExpression
        value.operator shouldBe "%"
    }

    test("isSame on binary expressions") {
        val left1 = NumericLiteral.optimalInteger(1, Position.DUMMY)
        val right1 = NumericLiteral.optimalInteger(2, Position.DUMMY)
        val expr1 = BinaryExpression(left1, "/", right1, Position.DUMMY)
        val left2 = NumericLiteral.optimalInteger(1, Position.DUMMY)
        val right2 = NumericLiteral.optimalInteger(2, Position.DUMMY)
        val expr2 = BinaryExpression(left2, "/", right2, Position.DUMMY)
        (expr1 isSameAs expr2) shouldBe true
        val left3 = NumericLiteral.optimalInteger(2, Position.DUMMY)
        val right3 = NumericLiteral.optimalInteger(1, Position.DUMMY)
        val expr3 = BinaryExpression(left3, "/", right3, Position.DUMMY)
        (expr1 isSameAs expr3) shouldBe false
    }

    test("isSame on binary expressions with associative operators") {
        val left1 = NumericLiteral.optimalInteger(1, Position.DUMMY)
        val right1 = NumericLiteral.optimalInteger(2, Position.DUMMY)
        val expr1 = BinaryExpression(left1, "+", right1, Position.DUMMY)
        val left2 = NumericLiteral.optimalInteger(1, Position.DUMMY)
        val right2 = NumericLiteral.optimalInteger(2, Position.DUMMY)
        val expr2 = BinaryExpression(left2, "+", right2, Position.DUMMY)
        (expr1 isSameAs expr2) shouldBe true
        val left3 = NumericLiteral.optimalInteger(2, Position.DUMMY)
        val right3 = NumericLiteral.optimalInteger(1, Position.DUMMY)
        val expr3 = BinaryExpression(left3, "+", right3, Position.DUMMY)
        (expr1 isSameAs expr3) shouldBe true
    }

    test("mkword insertion with signed values gets correct type cast") {
        val src = """
main {
    sub start() {
        byte[10] @shared bottom
        byte @shared col = 20
        col++
        ubyte @shared ubb = lsb(col as uword)
        uword @shared vaddr = bottom[cx16.r0L] as uword << 8          ; a mkword will get inserted here
    }
}"""
        val result = compileText(VMTarget(), optimize=true, src, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 8
        val assignUbbVal = ((st[5] as Assignment).value as TypecastExpression)
        assignUbbVal.type shouldBe BaseDataType.UBYTE
        assignUbbVal.expression shouldBe instanceOf<IdentifierReference>()
        val assignVaddr = (st[7] as Assignment).value as FunctionCallExpression
        assignVaddr.target.nameInSource shouldBe listOf("mkword")
        val tc = assignVaddr.args[0] as TypecastExpression
        tc.type shouldBe BaseDataType.UBYTE
        tc.expression shouldBe instanceOf<ArrayIndexedExpression>()
    }

    test("void assignment is invalid") {
        val src="""
main {
    extsub $2000 = multi() -> ubyte @A, ubyte @Y
    extsub $3000 = single() -> ubyte @A
    
    sub start() {
        void, void = multi()        ; ok
        cx16.r0L, void = multi()    ; ok
        void, cx16.r0L = multi()    ; ok
        void multi()                ; ok
        void single()               ; ok
        void = 3333                 ; fail!
        void = single()             ; fail!
        void = multi()              ; fail!
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize=false, src, writeAssembly=true, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldEndWith "cannot assign to 'void'"
        errors.errors[1] shouldEndWith "cannot assign to 'void', perhaps a void function call was intended"
        errors.errors[2] shouldEndWith "cannot assign to 'void', perhaps a void function call was intended"
    }

    test("datatype subtype consistencies") {
        shouldThrow<NoSuchElementException> {
            SubType.forDt(BaseDataType.STR)
        }
        shouldThrow<NoSuchElementException> {
            SubType.forDt(BaseDataType.UNDEFINED)
        }
        shouldThrow<NoSuchElementException> {
            SubType.forDt(BaseDataType.ARRAY_SPLITW)
        }
        shouldThrow<NoSuchElementException> {
            SubType.forDt(BaseDataType.ARRAY)
        }
        SubType.forDt(BaseDataType.FLOAT).dt shouldBe BaseDataType.FLOAT
    }

    test("datatype consistencies") {
        shouldThrow<NoSuchElementException> {
            DataType.forDt(BaseDataType.ARRAY)
        }
        shouldThrow<NoSuchElementException> {
            DataType.forDt(BaseDataType.ARRAY_SPLITW)
        }
        DataType.forDt(BaseDataType.UNDEFINED).isUndefined shouldBe true
        DataType.forDt(BaseDataType.LONG).isLong shouldBe true
        DataType.forDt(BaseDataType.WORD).isWord shouldBe true
        DataType.forDt(BaseDataType.UWORD).isWord shouldBe true
        DataType.forDt(BaseDataType.BYTE).isByte shouldBe true
        DataType.forDt(BaseDataType.UBYTE).isByte shouldBe true

        shouldThrow<NoSuchElementException> {
            DataType.arrayFor(BaseDataType.ARRAY)
        }
        shouldThrow<NoSuchElementException> {
            DataType.arrayFor(BaseDataType.LONG)
        }
        shouldThrow<NoSuchElementException> {
            DataType.arrayFor(BaseDataType.UNDEFINED)
        }
        DataType.arrayFor(BaseDataType.UBYTE, true).isUnsignedByteArray shouldBe true
        DataType.arrayFor(BaseDataType.FLOAT).isFloatArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD).isUnsignedWordArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD).isArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD).isSplitWordArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD, false).isSplitWordArray shouldBe false
    }

    test("array of strings becomes array of uword pointers") {
        val src="""
            main {
                sub start() {
                    str variable = "name1"
                    str[2] @shared names = [ variable, "name2" ]
                }
            }"""

        val result = compileText(C64Target(), false, src, writeAssembly = true)
        result shouldNotBe null

        val st1 = result!!.compilerAst.entrypoint.statements
        st1.size shouldBe 3
        st1[0] shouldBe instanceOf<VarDecl>()
        st1[1] shouldBe instanceOf<VarDecl>()
        (st1[0] as VarDecl).name shouldBe "variable"
        (st1[1] as VarDecl).name shouldBe "names"
        val array1 = (st1[1] as VarDecl).value as ArrayLiteral
        array1.type.isArray shouldBe true
        array1.type.getOrUndef() shouldBe DataType.arrayFor(BaseDataType.UWORD, true)

        val ast2 = result.codegenAst!!
        val st2 = ast2.entrypoint()!!.children
        st2.size shouldBe 3
        (st2[0] as PtVariable).name shouldBe "p8v_variable"
        (st2[1] as PtVariable).name shouldBe "p8v_names"
        val array2 = (st2[1] as PtVariable).value as PtArray
        array2.type shouldBe DataType.arrayFor(BaseDataType.UWORD, true)
    }

    test("defer syntactic sugaring") {
        val src="""
main {
    sub start() {
        void test()
    }

    sub test() -> uword {
        defer {
            cx16.r0++
            cx16.r1++
        }
        
        if cx16.r0==0 {
            defer cx16.r1++
        }

        if cx16.r0==0
            return cx16.r0+cx16.r1
        defer cx16.r2++
    }
}"""
        val result = compileText(Cx16Target(), optimize=true, src, writeAssembly=true)!!
        val main = result.codegenAst!!.allBlocks().single {it.name=="p8b_main"}
        val sub = main.children[1] as PtSub
        sub.scopedName shouldBe "p8b_main.p8s_test"

        // check the desugaring of the defer statements
        (sub.children[0] as PtVariable).name shouldBe "p8v_prog8_defers_mask"

        val firstDefer = sub.children[2] as PtAugmentedAssign
        firstDefer.operator shouldBe "|="
        firstDefer.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        firstDefer.value.asConstInteger() shouldBe 4

        val firstIf = sub.children[3] as PtIfElse
        val deferInIf = firstIf.ifScope.children[0] as PtAugmentedAssign
        deferInIf.operator shouldBe "|="
        deferInIf.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        deferInIf.value.asConstInteger() shouldBe 2

        val lastDefer = sub.children[5] as PtAugmentedAssign
        lastDefer.operator shouldBe "|="
        lastDefer.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        lastDefer.value.asConstInteger() shouldBe 1

        val ifelse = sub.children[4] as PtIfElse
        val ifscope = ifelse.ifScope.children[0] as PtNodeGroup
        val ifscope_push = ifscope.children[0] as PtFunctionCall
        val ifscope_defer = ifscope.children[1] as PtFunctionCall
        val ifscope_return = ifscope.children[2] as PtReturn
        ifscope_defer.name shouldBe "p8b_main.p8s_test.p8s_prog8_invoke_defers"
        ifscope_push.name shouldBe "sys.pushw"
        (ifscope_return.value as PtFunctionCall).name shouldBe "sys.popw"

        val ending = sub.children[6] as PtFunctionCall
        ending.name shouldBe "p8b_main.p8s_test.p8s_prog8_invoke_defers"
        sub.children[7] shouldBe instanceOf<PtReturn>()
        val handler = sub.children[8] as PtSub
        handler.name shouldBe "p8s_prog8_invoke_defers"
    }

    test("unknown variable in for loop gives proper errors") {
        val src="""
main {
    sub start() {
        ubyte i
        for i in 0 to count - 1 {
            break
        }
    }
}"""

        val errors = ErrorReporterForTests()
        compileText(C64Target(), optimize=false, src, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "loop variable can only loop over"
        errors.errors[1] shouldContain "undefined symbol"
    }
}

})

