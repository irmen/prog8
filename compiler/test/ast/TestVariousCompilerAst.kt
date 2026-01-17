package prog8tests.ast

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
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
import prog8.code.target.*
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText

class TestVariousCompilerAst: FunSpec({
    
    val outputDir = tempdir().toPath()
    
    context("arrays") {

        test("invalid array element proper errormessage") {
            val text="""
            main {
                sub start() {
                    uword[] commands = ["abc", 1.234]
                }
            }"""
            val errors = ErrorReporterForTests()
            compileText(C64Target(), false, text, outputDir, writeAssembly = true, errors=errors) shouldBe null
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
            compileText(C64Target(), false, text, outputDir, writeAssembly = true) shouldNotBe null
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
            compileText(C64Target(), false, text, outputDir, writeAssembly = false, errors = errors) shouldBe null
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
            compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
        }
    }

    context("alias statement") {
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
            compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
        }

        test("infinite alias loop detected") {
            val src="""
main {
    sub start() {
        alias vv = vv
        alias xx = xx.yy
        alias zz = mm
        alias mm = zz
    }
}"""
            val errors = ErrorReporterForTests()
            compileText(VMTarget(), false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.size shouldBe 3
            errors.errors[0] shouldContain "references itself"
            errors.errors[1] shouldContain "undefined symbol: xx.yy"
            errors.errors[2] shouldContain "references itself"
        }

        test("alias scopes") {
            val src="""
main {
    sub start() {
        alias mything = other.thing
        alias myvariable = other.variable

        mything()
        myvariable ++

        other.thing2()
        other.variable2 ++

        alias nid = structdefs.element.value
        nid++
    }
}

other {
    sub thing() {
        cx16.r0++
    }

    ubyte @shared variable

    alias thing2 = thing
    alias variable2 = variable
}

structdefs {
    struct Node {
        ubyte value
        uword value2
    }

    ^^Node @shared element
}"""
            compileText(VMTarget(), false, src, outputDir, writeAssembly=false) shouldNotBe null
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
            compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors=errors) shouldBe null
            errors.errors.size shouldBe 2
            errors.errors[0] shouldContain "undefined symbol: txt.print2222"
            errors.errors[1] shouldContain "undefined symbol: txt.DEFAULT_WIDTH_XXX"
        }

        test("aliased function call with wrong args count gives correct error") {
            val src="""
main {
    sub start() {
        alias func1 = actualfunc
        alias func2 = mkword
        alias func3 = func1
        alias func4 = func2

        ; all wrong:
        func1(1,2)
        func1()
        func2(1,2,3,4)
        func2()
        func3()
        func4()

        ; all ok:
        func1(1)
        cx16.r0 = func2(1,2)
        func3(1)
        cx16.r0 = func4(1,2)

        sub actualfunc(ubyte a) {
            a++
        }
    }
}"""
            val errors = ErrorReporterForTests()
            compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors=errors) shouldBe null
            errors.errors.size shouldBe 6
            errors.errors[0] shouldContain "invalid number of arguments: expected 1 but got 2"
            errors.errors[1] shouldContain "invalid number of arguments: expected 1 but got 0"
            errors.errors[2] shouldContain "invalid number of arguments: expected 2 but got 4"
            errors.errors[3] shouldContain "invalid number of arguments: expected 2 but got 0"
            errors.errors[4] shouldContain "invalid number of arguments: expected 1 but got 0"
            errors.errors[5] shouldContain "invalid number of arguments: expected 2 but got 0"
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
            val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=true)!!
            val stmts = result.compilerAst.entrypoint.statements
            stmts.size shouldBe 17
            val result2 = compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true)!!
            val stmts2 = result2.compilerAst.entrypoint.statements
            stmts2.size shouldBe 17
        }

        test("string concatenation and repeats") {
            val src="""
        main {
            sub start() {
                str @shared name = "part1" + "part2"
                str @shared rept = "rep"*4
                test("xx1" + "xx2")
                test("xyz" * 4)
            }
            sub test(str message) {
            }
        }"""
            val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=true)!!
            val stmts = result.compilerAst.entrypoint.statements
            stmts.size shouldBe 5
            val name1 = stmts[0] as VarDecl
            val rept1 = stmts[1] as VarDecl
            (name1.value as StringLiteral).value shouldBe "part1part2"
            (rept1.value as StringLiteral).value shouldBe "reprepreprep"
            val name2strcopy = stmts[2] as IFunctionCall
            val rept2strcopy = stmts[3] as IFunctionCall
            val name2 = (name2strcopy.args.first() as AddressOf).identifier!!
            val rept2 = (rept2strcopy.args.first() as AddressOf).identifier!!
            (name2.targetVarDecl()!!.value as StringLiteral).value shouldBe "xx1xx2"
            (rept2.targetVarDecl()!!.value as StringLiteral).value shouldBe "xyzxyzxyzxyz"
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
            compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.single() shouldContain  "cannot use byte value"
        }

        test("string indexing bounds checks") {
            val src="""
main {
    sub start() {
        ubyte[] array = ['h', 'e', 'l', 'l', 'o', 0]
        str name = "hello"

        name[5] = '!'       ; don't do this in real code...
        name[5] = 0
        name[6] = 99        ; out of bounds
        name[-1] = 99       ; ok
        name[-5] = 99       ; ok

        cx16.r0L = name[5]
        cx16.r1L = name[6]   ; out of bounds
        cx16.r1L = name[-1]  ; ok
        cx16.r1L = name[-5]  ; ok

        array[5] = '!'
        array[5] = 0
        array[6] = 99        ; out of bounds
        array[-1] = 99       ; ok
        array[-5] = 99       ; ok
        array[-6] = 99       ; ok

        cx16.r0L = array[5]
        cx16.r1L = array[6]  ; out of bounds
        cx16.r1L = array[-1] ; ok
        cx16.r1L = array[-5] ; ok
        cx16.r1L = array[-6] ; ok
    }
}"""

            val errors = ErrorReporterForTests()
            compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.size shouldBe 4
            errors.errors[0] shouldContain ":9:13: index out of bounds"
            errors.errors[1] shouldContain ":14:24: index out of bounds"
            errors.errors[2] shouldContain ":20:14: index out of bounds"
            errors.errors[3] shouldContain ":26:25: index out of bounds"
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
            compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
            errors.errors.size shouldBe 2
            errors.errors[0] shouldContain "has result value"
            errors.errors[1] shouldContain "has result value"
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
            val result = compileText(VMTarget(), optimize = false, src, outputDir, writeAssembly = false)!!
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
    }
}"""
            val result = compileText(Cx16Target(), optimize=true, src, outputDir, writeAssembly=false)!!
            val st = result.compilerAst.entrypoint.statements
            st.size shouldBe 13
            st[0] shouldBe instanceOf<VarDecl>()    // x
            st[2] shouldBe instanceOf<VarDecl>()    // y
            st[4] shouldBe instanceOf<VarDecl>()    // z
            st[6] shouldBe instanceOf<VarDecl>()    // k
            st[8] shouldBe instanceOf<VarDecl>()    // l
            st[10] shouldBe instanceOf<VarDecl>()    // m
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
            compileText(Cx16Target(), false, src, outputDir) shouldNotBe null
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
            val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors=errors)!!
            errors.warnings.size shouldBe 6
            errors.infos.size shouldBe 0
            errors.warnings.all { "dirty variable" in it } shouldBe true
            val start = result.compilerAst.entrypoint
            val st = start.statements
            st.size shouldBe 10
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBeGreaterThan 1
        errors.clear()
        compileText(C64Target(), optimize=true, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
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
        compileText(C64Target(), false, text, outputDir, writeAssembly = false) shouldBe null
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
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
        val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 8
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
        val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 10
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
        val result = compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false)!!
        val stmts = result.compilerAst.entrypoint.statements
        stmts.size shouldBe 5
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors=errors) shouldNotBe null
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
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

        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
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
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "use if"
    }

    test("when on range expressions is ok") {
        val src="""

main {

    sub start()  {
        when cx16.r0L {
            21 to 29 step 2 -> cx16.r1L++
            else -> cx16.r1L--
        }
    }
}"""
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false).shouldNotBeNull()
    }

    test("when on range expressions outside value datatype is error") {
        val src="""

main {

    sub start()  {
        when cx16.r0L {
            300 to 400 -> cx16.r1L++
            else -> cx16.r1L--
        }
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 1
        errors.errors[0] shouldContain "values must be constant numbers"
    }


    test("sizeof number const evaluation in vardecl") {
        val src="""
main {
    sub start() {
        uword @shared size1 = sizeof(22222)
        uword @shared size2 = sizeof(2.2)
    }
}"""
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
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
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
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
        val result=compileText(VMTarget(), optimize=true, src, outputDir, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 12

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

        val result=compileText(Cx16Target(), optimize=false, src, outputDir, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 7
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
        ubyte @shared ubb = lsb(col as uword)
        uword @shared vaddr = bottom[cx16.r0L] as uword << 8          ; a mkword will get inserted here
    }
}"""
        val result = compileText(VMTarget(), optimize=true, src, outputDir, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 8
        val assignUbbVal = (st[4] as Assignment).value as IdentifierReference
        assignUbbVal.inferType(result.compilerAst) shouldBe InferredTypes.knownFor(BaseDataType.BYTE)
        val assignVaddr = (st[6] as Assignment).value as FunctionCallExpression
        assignVaddr.target.nameInSource shouldBe listOf("mkword")
        val tc = assignVaddr.args[0] as TypecastExpression
        tc.type shouldBe DataType.UBYTE
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=true, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldEndWith "cannot assign to 'void'"
        errors.errors[1] shouldEndWith "cannot assign to 'void', perhaps a void function call was intended"
        errors.errors[2] shouldEndWith "cannot assign to 'void', perhaps a void function call was intended"
    }

    test("datatype consistencies") {
        DataType.UNDEFINED.isUndefined shouldBe true
        DataType.LONG.isLong shouldBe true
        DataType.WORD.isWord shouldBe true
        DataType.UWORD.isWord shouldBe true
        DataType.BYTE.isByte shouldBe true
        DataType.UBYTE.isByte shouldBe true
        DataType.BOOL.isBool shouldBe true
        DataType.STR.isString shouldBe true
        DataType.FLOAT.isFloat shouldBe true

        DataType.forDt(BaseDataType.UNDEFINED).isUndefined shouldBe true
        DataType.forDt(BaseDataType.LONG).isLong shouldBe true
        DataType.forDt(BaseDataType.WORD).isWord shouldBe true
        DataType.forDt(BaseDataType.UWORD).isWord shouldBe true
        DataType.forDt(BaseDataType.BYTE).isByte shouldBe true
        DataType.forDt(BaseDataType.UBYTE).isByte shouldBe true
        DataType.forDt(BaseDataType.BOOL).isBool shouldBe true
        DataType.forDt(BaseDataType.STR).isString shouldBe true
        DataType.forDt(BaseDataType.FLOAT).isFloat shouldBe true

        DataType.arrayFor(BaseDataType.UBYTE, true).isUnsignedByteArray shouldBe true
        DataType.arrayFor(BaseDataType.LONG).isLongArray shouldBe true
        DataType.arrayFor(BaseDataType.FLOAT).isFloatArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD).isUnsignedWordArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD).isArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD).isSplitWordArray shouldBe true
        DataType.arrayFor(BaseDataType.UWORD, false).isSplitWordArray shouldBe false

        shouldThrow<NoSuchElementException> {
            DataType.forDt(BaseDataType.ARRAY)
        }
        shouldThrow<NoSuchElementException> {
            DataType.forDt(BaseDataType.ARRAY_SPLITW)
        }
        shouldThrow<NoSuchElementException> {
            DataType.arrayFor(BaseDataType.ARRAY)
        }
        shouldThrow<NoSuchElementException> {
            DataType.arrayFor(BaseDataType.UNDEFINED)
        }
    }

    test("array of strings becomes array of uword pointers") {
        val src="""
            main {
                sub start() {
                    str variable = "name1"
                    str[2] @shared names = [ variable, "name2" ]
                }
            }"""

        val result = compileText(C64Target(), false, src, outputDir, writeAssembly = true)
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
        st2.size shouldBe 4
        (st2[1] as PtVariable).name shouldBe "p8v_variable"
        (st2[2] as PtVariable).name shouldBe "p8v_names"
        val array2 = (st2[2] as PtVariable).value as PtArray
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
        return 999
    }
}"""
        val result = compileText(Cx16Target(), optimize=true, src, outputDir, writeAssembly=true)!!
        val main = result.codegenAst!!.allBlocks().single {it.name=="p8b_main"}
        val sub = main.children[1] as PtSub
        sub.scopedName shouldBe "p8b_main.p8s_test"

        // check the desugaring of the defer statements
        sub.children[0] shouldBe instanceOf<PtSubSignature>()
        (sub.children[1] as PtVariable).name shouldBe "p8v_prog8_defers_mask"

        val firstDefer = sub.children[3] as PtAugmentedAssign
        firstDefer.operator shouldBe "|="
        firstDefer.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        firstDefer.value.asConstInteger() shouldBe 4

        val firstIf = sub.children[4] as PtIfElse
        val deferInIf = firstIf.ifScope.children[0] as PtAugmentedAssign
        deferInIf.operator shouldBe "|="
        deferInIf.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        deferInIf.value.asConstInteger() shouldBe 2

        val lastDefer = sub.children[6] as PtAugmentedAssign
        lastDefer.operator shouldBe "|="
        lastDefer.target.identifier?.name shouldBe "p8b_main.p8s_test.p8v_prog8_defers_mask"
        lastDefer.value.asConstInteger() shouldBe 1

        val ifelse = sub.children[5] as PtIfElse
        val ifscope = ifelse.ifScope.children[0] as PtNodeGroup
        val ifscope_push = ifscope.children[0] as PtFunctionCall
        val ifscope_defer = ifscope.children[1] as PtFunctionCall
        val ifscope_return = ifscope.children[2] as PtReturn
        ifscope_defer.name shouldBe "p8b_main.p8s_test.p8s_prog8_invoke_defers"
        ifscope_push.name shouldBe "sys.pushw"
        (ifscope_return.children.single() as PtFunctionCall).name shouldBe "sys.popw"

        val ending = sub.children[7] as PtFunctionCall
        ending.name shouldBe "p8b_main.p8s_test.p8s_prog8_invoke_defers"
        sub.children[8] shouldBe instanceOf<PtReturn>()
        val handler = sub.children[9] as PtSub
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
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false, errors = errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[0] shouldContain "loop variable can only loop over"
        errors.errors[1] shouldContain "undefined symbol"
    }

    test("multi vardecl with immediate initialization from multi-return value functioncall") {
        val src="""
main {
    sub start() {
        ubyte @shared x,y,z = multi()
    }
    sub multi() -> ubyte, ubyte, ubyte {
        return 1,2,3
    }
}"""
        val result1 = compileText(VMTarget(), optimize=true, src, outputDir, writeAssembly=true)!!
        val st1 = result1.codegenAst!!.entrypoint()!!.children
        st1.size shouldBe 6
        (st1[1] as PtVariable).name shouldBe "main.start.x"
        (st1[2] as PtVariable).name shouldBe "main.start.y"
        (st1[3] as PtVariable).name shouldBe "main.start.z"
        st1[4].children.size shouldBe 4
        st1[4].children.dropLast(1).map { (it as PtAssignTarget).identifier!!.name } shouldBe listOf("main.start.x", "main.start.y", "main.start.z")
        ((st1[4] as PtAssignment).value as PtFunctionCall).name shouldBe "main.multi"

        val result2 = compileText(Cx16Target(), optimize=true, src, outputDir, writeAssembly=true)!!
        val st2 = result2.codegenAst!!.entrypoint()!!.children
        st2.size shouldBe 6
        (st2[1] as PtVariable).name shouldBe "p8v_x"
        (st2[2] as PtVariable).name shouldBe "p8v_y"
        (st2[3] as PtVariable).name shouldBe "p8v_z"
        st2[4].children.size shouldBe 4
        st2[4].children.dropLast(1).map { (it as PtAssignTarget).identifier!!.name } shouldBe listOf("p8b_main.p8s_start.p8v_x", "p8b_main.p8s_start.p8v_y", "p8b_main.p8s_start.p8v_z")
        ((st2[4] as PtAssignment).value as PtFunctionCall).name shouldBe "p8b_main.p8s_multi"
    }

    test("address-of a uword pointer with word index should not overflow") {
        val src= """
main {
    sub start() {
        const uword cbuffer = $2000
        uword @shared buffer = $2000

        cx16.r1 = &cbuffer[2000]
        cx16.r5 = &buffer[2000]
        
        cx16.r3 = &cbuffer[cx16.r0]
        cx16.r4 = &buffer[cx16.r0]
    }
}"""
        compileText(Cx16Target(), optimize=false, src, outputDir, writeAssembly=true) shouldNotBe null
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true) shouldNotBe null
    }

    test("using the cx16 virtual registers as various datatypes") {
        val src="""
main {
    sub start() {
        uword uw = 9999
        word sw = -2222
        ubyte ub = 42
        byte sb = -99
        bool bb = true

        cx16.r0 = uw
        cx16.r0s = sw
        cx16.r0L = ub
        cx16.r0H = ub
        cx16.r0sL = sb
        cx16.r0sH = sb
        cx16.r0bL = bb
        cx16.r0bH = bb

        uw = cx16.r0
        sw = cx16.r0s
        ub = cx16.r0L
        ub = cx16.r0H
        sb = cx16.r0sL
        sb = cx16.r0sH
        bb = cx16.r0bL
        bb = cx16.r0bH
    }
}"""

        compileText(Cx16Target(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
        compileText(PETTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
        compileText(C128Target(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
    }

    test("on..goto and on..call") {
        val src="""
main {
    sub start() {
        cx16.r13L = 1
        cx16.r12L = 0

        on cx16.r12L+1 call (
            thing.func1,
            thing.func2,
            thing.func3)
        else {
            ; not jumped
            cx16.r0++
        }

        on cx16.r13L+1 goto (thing.func1, thing.func2, thing.func3)
        
        test2()
    }
    
    sub test2() {
        ubyte @shared thing
        on thing call (lblA, lblB, lblC)
        on thing goto (lblA, lblB, lblC)
        lblA:
            cx16.r0++
            goto lblDone
        lblB:
            cx16.r1++
            goto lblDone
        lblC:
            cx16.r2++
        lblDone:
    }
}

thing {
    sub func1() {
        cx16.r10 += 1
    }
    sub func2() {
        cx16.r10 += 2
    }
    sub func3() {
        cx16.r10 += 3
    }
}"""

        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=true) shouldNotBe null
        compileText(C64Target(), optimize=false, src, outputDir, writeAssembly=true) shouldNotBe null
        compileText(Cx16Target(), optimize=false, src, outputDir, writeAssembly=true) shouldNotBe null
    }
}

    test("pointer arrays are split by default") {
        val src="""
%option enable_floats

main {
    sub start() {
        ^^bool[10]  @shared barray
        ^^word[10]  @shared warray
        ^^float[10] @shared farray
    }
}"""

        val result = compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false)!!
        val st = result.compilerAst.entrypoint.statements
        st.size shouldBe 4
        val decls = st.filterIsInstance<VarDecl>()
        decls.size shouldBe 3
        decls.all { it.datatype.sub!=null } shouldBe true
        decls.all { it.datatype.isPointerArray } shouldBe true
        decls.all { it.datatype.isSplitWordArray } shouldBe true
    }

    test("on..call in nested scope compiles correctly with temp variable introduced") {
        val src="""
main {
    sub start() {
        if_cs {
            on cx16.r0L call (func,func)
        }
    }

    sub func() {
    }
}"""

        compileText(VMTarget(), optimize=false, src, outputDir, writeAssembly=false) shouldNotBe null
    }

    test("breakpoint after expression") {
        val src="""
main {
    ubyte a,b

    sub start() {
        func1()
        func2()
    }

    sub func1() {
        a = b
        %breakpoint     ; parse error
    }

    sub func2() {
        a = b
        %breakpoint!     ; parse ok
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), optimize=false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 3
        errors.errors[1] shouldContain "12:10: undefined symbol: breakpoint"
    }

    test("struct names cannot be keywords") {
        val src="""
main {
    sub start() {
        struct on {
            bool flag
        }

        struct step {
            bool flag
        }

        struct inline {
            bool flag
        }

        struct call {
            bool flag
        }
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), optimize=false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 4
        errors.errors[0] shouldContain "struct name cannot be a keyword"
        errors.errors[1] shouldContain "struct name cannot be a keyword"
        errors.errors[2] shouldContain "struct name cannot be a keyword"
        errors.errors[3] shouldContain "builtin function cannot be redefined"
    }

    test("string and array multiplication require integer multiplicand") {
        val src="""
main {
    sub start() {
        const bool DERP = true

        for cx16.r0L in 0 to 10 {
            print_strXY(1,2,"irmen"*DERP,22,false)
            print_strXY(1,2,[1,3,4]*DERP,22,false)
        }
    }

    sub print_strXY(ubyte col, ubyte row, str txtstring, ubyte colors, bool convertchars) {
        cx16.r0L++
    }
}"""
        val errors = ErrorReporterForTests()
        compileText(VMTarget(), optimize=false, src, outputDir, errors=errors) shouldBe null
        errors.errors.size shouldBe 2
        errors.errors[0] shouldContain "can only multiply string by integer constant"
        errors.errors[1] shouldContain "can only multiply array by integer constant"
    }
})

