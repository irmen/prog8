import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.instanceOf
import prog8.code.StStaticVariable
import prog8.code.SymbolTable
import prog8.code.core.CbmPrgLauncherType
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.ZeropageType
import prog8.code.target.Cx16Target
import prog8.intermediate.IRFileReader
import prog8.intermediate.IRFileWriter
import prog8.intermediate.IRProgram
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.readLines
import kotlin.io.path.writeText

class TestIRFileInOut: FunSpec({
    test("test IR writer") {
        val st = SymbolTable()
        val target = Cx16Target()
        val tempdir = Path.of(System.getProperty("java.io.tmpdir"))
        val options = CompilationOptions(
            OutputType.RAW,
            CbmPrgLauncherType.NONE,
            ZeropageType.DONTUSE,
            emptyList(),
            floats = false,
            noSysInit = true,
            compTarget = target,
            loadAddress = target.machine.PROGRAM_LOAD_ADDRESS,
            outputDir = tempdir
        )
        val program = IRProgram("unittest-irwriter", st, options, target)
        val writer = IRFileWriter(program)
        writer.writeFile()
        val generatedFile = tempdir.resolve("unittest-irwriter.p8ir")
        val lines = generatedFile.readLines()
        lines.first() shouldBe "<PROGRAM NAME=unittest-irwriter>"
        lines.last() shouldBe "</PROGRAM>"
        generatedFile.deleteExisting()
        lines.size shouldBeGreaterThan 20
    }

    test("test IR reader") {
        val source="""<PROGRAM NAME=test-ir-reader>
<OPTIONS>
compTarget=virtual
output=PRG
launcher=BASIC
zeropage=KERNALSAFE
loadAddress=0
dontReinitGlobals=false
evalStackBaseAddress=null
</OPTIONS>

<VARIABLES>
uword sys.wait.jiffies=0 zp=DONTCARE
</VARIABLES>

<MEMORYMAPPEDVARIABLES>
&uword cx16.r0=65282
</MEMORYMAPPEDVARIABLES>

<MEMORYSLABS>
</MEMORYSLABS>

<INITGLOBALS>
<C>
load.b r1,42
</C>
</INITGLOBALS>

<BLOCK NAME=main ADDRESS=null ALIGN=NONE POS=[examples/test.p8: line 2 col 2-5]>
<SUB NAME=main.start RETURNTYPE=null POS=[examples/test.p8: line 4 col 6-8]>
<PARAMS>
</PARAMS>
<C>
return
</C>
</SUB>
</BLOCK>

<BLOCK NAME=sys ADDRESS=null ALIGN=NONE POS=[library:/prog8lib/virtual/syslib.p8: line 3 col 2-4]>
<SUB NAME=sys.wait RETURNTYPE=null POS=[library:/prog8lib/virtual/syslib.p8: line 15 col 6-8]>
<PARAMS>
uword sys.wait.jiffies
</PARAMS>
<INLINEASM POS=[library:/prog8lib/virtual/syslib.p8: line 17 col 10-13]>
            loadm.w r0,sys.wait.jiffies
            syscall 13
</INLINEASM>
<C>
return
</C>
</SUB>
</BLOCK>
</PROGRAM>
"""
        val tempfile = kotlin.io.path.createTempFile(suffix = ".p8ir")
        tempfile.writeText(source)
        val filepart = tempfile.name.dropLast(5)
        val reader = IRFileReader(tempfile.parent, filepart)
        val program = reader.readFile()
        tempfile.deleteExisting()
        program.name shouldBe "test-ir-reader"
        program.blocks.size shouldBe 2
        program.st.lookup("sys.wait.jiffies") shouldBe instanceOf<StStaticVariable>()
    }
})