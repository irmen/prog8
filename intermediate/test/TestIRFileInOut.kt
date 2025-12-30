import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.core.CbmPrgLauncherType
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.ZeropageType
import prog8.code.target.Cx16Target
import prog8.intermediate.*
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
            CompilationOptions.AllZeropageAllowed,
            floats = false,
            noSysInit = true,
            romable = false,
            compTarget = target,
            compilerVersion = "99.99",
            loadAddress = target.PROGRAM_LOAD_ADDRESS,
            memtopAddress = 0xffffu,
            outputDir = tempdir
        )
        val program = IRProgram("unittest-irwriter", IRSymbolTable(), options, target)
        val writer = IRFileWriter(program, null)
        val generatedFile = writer.write()
        val lines = generatedFile.readLines()
        lines[0] shouldBe "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
        lines[1] shouldBe "<PROGRAM NAME=\"unittest-irwriter\" COMPILERVERSION=\"99.99\">"
        lines.last() shouldBe "</PROGRAM>"
        generatedFile.deleteExisting()
        lines.size shouldBeGreaterThan 20
    }

    test("test IR reader") {
        val source="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test-ir-reader" COMPILERVERSION="99.99">
<OPTIONS>
compTarget=virtual
output=PRG
launcher=BASIC
zeropage=KERNALSAFE
loadAddress=$0000
</OPTIONS>

<ASMSYMBOLS>
</ASMSYMBOLS>

<VARS>

<NOINITCLEAN>
uword sys.bssvar zp=DONTCARE align=0
</NOINITCLEAN>
<NOINITDIRTY>
</NOINITDIRTY>
<INIT>
uword sys.wait.jiffies=10 zp=DONTCARE align=0
ubyte[3] sys.emptystring=0,0,0 zp=DONTCARE align=0
</INIT>

<STRUCTINSTANCESNOINIT>
</STRUCTINSTANCESNOINIT>
<STRUCTINSTANCES>
</STRUCTINSTANCES>

<CONSTANTS>
ubyte main.thing=42
</CONSTANTS>

<MEMORYMAPPED>
@uword cx16.r0=65282
</MEMORYMAPPED>

<MEMORYSLABS>
</MEMORYSLABS>
</VARS>

<INITGLOBALS>
<CHUNK><REGS>dummy</REGS><CODE>
load.b r1,#42
</CODE></CHUNK>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" FORCEOUTPUT="false" NOPREFIXING="false" VERAFXMULS="false" ALIGN="NONE" POS="[examples/test.p8: line 2 col 2-5]">
<SUB NAME="main.start" RETURNS="" POS="[examples/test.p8: line 4 col 6-8]">
<PARAMS>
</PARAMS>
<CHUNK LABEL="main.start"><REGS>dummy</REGS><CODE>
return
</CODE></CHUNK>
</SUB>
</BLOCK>

<BLOCK NAME="sys" ADDRESS="" LIBRARY="false" FORCEOUTPUT="false" ALIGN="NONE" POS="[library:/prog8lib/virtual/syslib.p8: line 3 col 2-4]">
<SUB NAME="sys.wait" RETURNS="" POS="[library:/prog8lib/virtual/syslib.p8: line 15 col 6-8]">
<PARAMS>
uword sys.wait.jiffies
</PARAMS>
<ASM LABEL="sys.wait" IR="true" POS="[library:/prog8lib/virtual/syslib.p8: line 17 col 10-13]">
            loadm.w r0,sys.wait.jiffies
</ASM>
<CHUNK><REGS>dummy</REGS><CODE>
return
</CODE></CHUNK>
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
        program.st.allConstants().count() shouldBe 1
        val var1 = program.st.lookup("sys.wait.jiffies") as IRStStaticVariable
        val var2 = program.st.lookup("sys.bssvar") as IRStStaticVariable
        val var3 = program.st.lookup("sys.emptystring") as IRStStaticVariable
        var1.uninitialized shouldBe false
        var2.uninitialized shouldBe true
        var3.uninitialized shouldBe true
    }
})