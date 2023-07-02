package prog8tests.ast

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.instanceOf
import prog8.ast.IFunctionCall
import prog8.ast.expressions.*
import prog8.ast.statements.Assignment
import prog8.ast.statements.InlineAssembly
import prog8.ast.statements.VarDecl
import prog8.code.core.DataType
import prog8.code.core.Position
import prog8.code.target.C64Target
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

        cx16.r0L= name=="foo"
        cx16.r1L= name!="foo"
        cx16.r2L= name<"foo"
        cx16.r3L= name>"foo"

        cx16.r0L= nameptr=="foo"
        cx16.r1L= nameptr!="foo"
        cx16.r2L= nameptr<"foo"
        cx16.r3L= nameptr>"foo"

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
        stmts.size shouldBe 16
        val result2 = compileText(VMTarget(), optimize=false, src, writeAssembly=true)!!
        val stmts2 = result2.compilerAst.entrypoint.statements
        stmts2.size shouldBe 16
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
            ubyte pixel_side1 = pget(2, YY[2]+1) in ARRAY
            ubyte pixel_side2 = pget(2, 2) in ARRAY
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
        cx16.r0L = not (3 in array)
        cx16.r1L = 3 not in array
    }
}
"""
        val result = compileText(C64Target(), optimize=false, src, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 3
        val value1 = (stmts[1] as Assignment).value as BinaryExpression
        val value2 = (stmts[2] as Assignment).value as BinaryExpression
        value1.operator shouldBe "=="
        value1.left shouldBe instanceOf<ContainmentCheck>()
        (value1.right as NumericLiteral).number shouldBe 0.0
        value2.operator shouldBe "=="
        value2.left shouldBe instanceOf<ContainmentCheck>()
        (value2.right as NumericLiteral).number shouldBe 0.0
    }

    test("const pointer variable indexing works") {
        val src="""
main {
    sub start() {
        const uword pointer=$1000
        cx16.r0L = pointer[2]
        pointer[2] = cx16.r0L
    }
}
"""
        compileText(C64Target(), optimize=false, src, writeAssembly=false) shouldNotBe null
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
        ubyte variable=55
        when variable
        {
            33 -> cx16.r0++
            else -> cx16.r1++
        }

        if variable {
            cx16.r0++
        } else {
            cx16.r1++
        }

        if variable { cx16.r0++ }
        else { cx16.r1++ }

        if variable
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
})

