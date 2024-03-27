package prog8tests

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import prog8.code.core.ZeropageType
import prog8.code.core.internedStringsModuleName
import prog8.code.target.C64Target
import prog8.code.target.VMTarget
import prog8.compiler.determineCompilationOptions
import prog8.compiler.parseMainModule
import prog8tests.helpers.ErrorReporterForTests
import prog8tests.helpers.compileText
import prog8tests.helpers.outputDir


class TestImportedModulesOrderAndOptions: FunSpec({

    test("testImportedModuleOrderAndMainModuleCorrect") {
        val result = compileText(
            C64Target(), false, """
%import textio
%import floats

main {
    sub start() {
        ; nothing
    }
}
""")!!
        result.compilerAst.toplevelModule.name shouldStartWith "on_the_fly_test"

        val moduleNames = result.compilerAst.modules.map { it.name }
        withClue("main module must be first") {
            moduleNames[0] shouldStartWith "on_the_fly_test"
        }
        withClue("module order in parse tree") {
            moduleNames.drop(1) shouldBe listOf(
                internedStringsModuleName,
                "textio",
                "syslib",
                "conv",
                "shared_cbm_textio_functions",
                "floats",
                "shared_floats_functions",
                "math",
                "prog8_lib"
            )
        }
        result.compilerAst.toplevelModule.name shouldStartWith "on_the_fly_test"
    }

    test("testCompilationOptionsCorrectFromMain") {
        val result = compileText(
            C64Target(), false, """
%import textio
%import floats
%zeropage dontuse
%option no_sysinit

main {
    sub start() {
        ; nothing
    }
}
""")!!
        result.compilerAst.toplevelModule.name shouldStartWith "on_the_fly_test"
        val options = determineCompilationOptions(result.compilerAst, C64Target())
        options.floats shouldBe true
        options.zeropage shouldBe ZeropageType.DONTUSE
        options.noSysInit shouldBe true
    }

    test("testModuleOrderAndCompilationOptionsCorrectWithJustImports") {
        val errors = ErrorReporterForTests()
        val sourceText = """
%import textio
%import floats
%option no_sysinit
%zeropage dontuse            

main {
    sub start() {
        ; nothing
    }
}
"""
        val filenameBase = "on_the_fly_test_" + sourceText.hashCode().toUInt().toString(16)
        val filepath = outputDir.resolve("$filenameBase.p8")
        filepath.toFile().writeText(sourceText)
        val (program, options, importedfiles) = parseMainModule(filepath, errors, C64Target(), emptyList())

        program.toplevelModule.name shouldBe filenameBase
        withClue("all imports other than the test source must have been internal resources library files") {
            importedfiles.size shouldBe 1
        }
        withClue("module order in parse tree") {
            program.modules.map { it.name } shouldBe
                listOf(
                    internedStringsModuleName,
                    filenameBase,
                    "textio", "syslib", "conv", "shared_cbm_textio_functions", "floats", "shared_floats_functions", "math", "prog8_lib"
                )
        }
        options.floats shouldBe true
        options.noSysInit shouldBe true
        withClue("zeropage option must be correctly taken from main module, not from float module import logic") {
            options.zeropage shouldBe ZeropageType.DONTUSE
        }
    }

    test("merge option works on library modules") {
        val src="""
%zeropage basicsafe
%import textio

txt {
    %option merge
    sub println(uword string) {
        txt.print(string)
        txt.nl()
    }
}

main {
    sub start() {
        txt.lowercase()
        txt.println("Hello, world1")
        txt.println("Hello, world2")
        txt.println("Hello, world3")
    }
}"""
        compileText(VMTarget(), optimize = false, src) shouldNotBe null
    }


})
