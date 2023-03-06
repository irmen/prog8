import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.StStaticVariable
import prog8.code.core.CbmPrgLauncherType
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.ZeropageType
import prog8.code.target.Cx16Target
import prog8.intermediate.IRFileReader
import prog8.intermediate.IRFileWriter
import prog8.intermediate.IRProgram
import prog8.intermediate.IRSymbolTable
import kotlin.io.path.*

class TestIRFileInOut: FunSpec({
    test("test IR writer") {
        val target = Cx16Target()
        val tempdir = Path(System.getProperty("java.io.tmpdir"))
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
        val program = IRProgram("unittest-irwriter", IRSymbolTable(null), options, target)
        val writer = IRFileWriter(program, null)
        val generatedFile = writer.write()
        val lines = generatedFile.readLines()
        lines[0] shouldBe "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        lines[1] shouldBe "<PROGRAM NAME=\"unittest-irwriter\">"
        lines.last() shouldBe "</PROGRAM>"
        generatedFile.deleteExisting()
        lines.size shouldBeGreaterThan 20
    }

    test("test IR reader") {
        val source="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test-ir-reader">
<OPTIONS>
compTarget=virtual
output=PRG
launcher=BASIC
zeropage=KERNALSAFE
loadAddress=$0000
evalStackBaseAddress=
</OPTIONS>

<ASMSYMBOLS>
</ASMSYMBOLS>

<VARIABLESNOINIT>
uword sys.bssvar zp=DONTCARE
</VARIABLESNOINIT>
<VARIABLESWITHINIT>
uword sys.wait.jiffies=10 zp=DONTCARE
ubyte[3] sys.emptystring=0,0,0 zp=DONTCARE
</VARIABLESWITHINIT>

<MEMORYMAPPEDVARIABLES>
@uword cx16.r0=65282
</MEMORYMAPPEDVARIABLES>

<MEMORYSLABS>
</MEMORYSLABS>

<INITGLOBALS>
<CODE>
load.b r1,42
</CODE>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" ALIGN="NONE" POS="[examples/test.p8: line 2 col 2-5]">
<SUB NAME="main.start" RETURNTYPE="" POS="[examples/test.p8: line 4 col 6-8]">
<PARAMS>
</PARAMS>
<CODE LABEL="main.start">
return
</CODE>
</SUB>
</BLOCK>

<BLOCK NAME="sys" ADDRESS="" ALIGN="NONE" POS="[library:/prog8lib/virtual/syslib.p8: line 3 col 2-4]">
<SUB NAME="sys.wait" RETURNTYPE="" POS="[library:/prog8lib/virtual/syslib.p8: line 15 col 6-8]">
<PARAMS>
uword sys.wait.jiffies
</PARAMS>
<INLINEASM LABEL="sys.wait" IR="true" POS="[library:/prog8lib/virtual/syslib.p8: line 17 col 10-13]">
            loadm.w r0,sys.wait.jiffies
            syscall 13
</INLINEASM>
<CODE>
return
</CODE>
</SUB>
</BLOCK>
</PROGRAM>
"""
        val tempfile = createTempFile(suffix = ".p8ir")
        tempfile.writeText(source)
        val program = IRFileReader().read(tempfile)
        tempfile.deleteExisting()
        program.name shouldBe "test-ir-reader"
        program.blocks.size shouldBe 2
        program.st.allVariables().count() shouldBe 3
        val var1 = program.st.lookup("sys.wait.jiffies") as StStaticVariable
        val var2 = program.st.lookup("sys.bssvar") as StStaticVariable
        val var3 = program.st.lookup("sys.emptystring") as StStaticVariable
        var1.uninitialized shouldBe false
        var2.uninitialized shouldBe true
        var3.uninitialized shouldBe true
    }
})