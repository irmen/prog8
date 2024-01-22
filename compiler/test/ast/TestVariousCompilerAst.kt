package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
import prog8.code.target.Cx16Target
import prog8.code.target.VMTarget
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestVariousCompilerAst: FunSpec({
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
        leftval1.type shouldBe DataType.UWORD
        leftval1.number shouldBe 1.0
        val leftval2 = assign2expr.left.constValue(result.compilerAst)!!
        leftval2.type shouldBe DataType.UWORD
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
        ubyte bb1 = 199
        ubyte bb2 = 12
        ubyte @shared bb3 = bb1%bb2
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
        uword @shared vaddr = bottom[col] as uword << 8          ; a mkword will get inserted here
    }
}"""
        val result = compileText(VMTarget(), optimize=true, src, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 8
        val assignUbb = ((st[5] as Assignment).value as TypecastExpression)
        assignUbb.type shouldBe DataType.UBYTE
        assignUbb.expression shouldBe instanceOf<IdentifierReference>()
        val assignVaddr = (st[7] as Assignment).value as FunctionCallExpression
        assignVaddr.target.nameInSource shouldBe listOf("mkword")
        val tc = assignVaddr.args[0] as TypecastExpression
        tc.type shouldBe DataType.UBYTE
        tc.expression shouldBe instanceOf<ArrayIndexedExpression>()
    }
})

