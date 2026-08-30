import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import prog8.code.core.*
import prog8.code.target.Amiga500Target
import prog8.code.target.Cx16Target
import prog8.intermediate.*
import kotlin.io.path.*

class TestIRFileInOut: FunSpec({
    test("test IR writer") {
        val target = Cx16Target()
        val tempdir = Path(System.getProperty("java.io.tmpdir"))
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .loadAddress(target.PROGRAM_LOAD_ADDRESS)
            .memtopAddress(0xffffu)
            .outputDir(tempdir)
            .build()
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

    test("test IR reader with struct containing pointer fields") {
        val source="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test-struct-pointer" COMPILERVERSION="99.99">
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
uword main.start zp=DONTCARE
</NOINITCLEAN>

<NOINITDIRTY>
</NOINITDIRTY>

<INIT>
</INIT>

<STRUCTINSTANCESNOINIT>
</STRUCTINSTANCESNOINIT>
<STRUCTINSTANCES>
re.State testinst size=8 values=uword:$0100,^^re.State:0,^^re.State:0,uword:0
</STRUCTINSTANCES>

<CONSTANTS>
</CONSTANTS>

<MEMORYMAPPED>
</MEMORYMAPPED>

<MEMORYSLABS>
</MEMORYSLABS>
</VARS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" FORCEOUTPUT="false" NOPREFIXING="false" VERAFXMULS="false" ALIGN="NONE" POS="[test.p8: line 1 col 1-2]">
<SUB NAME="main.start" RETURNS="" POS="[test.p8: line 1 col 1-2]">
<PARAMS>
</PARAMS>
<CHUNK LABEL="main.start"><REGS>dummy</REGS><CODE>
load.b r1,#0
</CODE></CHUNK>
</SUB>
</BLOCK>
</PROGRAM>
"""
        val tempfile = createTempFile(suffix = ".p8ir")
        tempfile.writeText(source)
        val program = IRFileReader().read(tempfile)
        tempfile.deleteExisting()
        program.name shouldBe "test-struct-pointer"
        val struct = program.st.allStructInstances().first()
        struct.name shouldBe "testinst"
        struct.structName shouldBe "re.State"
    }

    test("test IR writer preserves decimal precision in float struct fields") {
        // regression test: struct field floats used to be serialized via .toInt().toHex()
        // which truncated the decimal portion (e.g. 1463.87 became 1463.0 in the loaded VM memory)
        val target = Cx16Target()
        val tempdir = Path(System.getProperty("java.io.tmpdir"))
        val options = CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .loadAddress(target.PROGRAM_LOAD_ADDRESS)
            .memtopAddress(0xffffu)
            .outputDir(tempdir)
            .build()
        val program = IRProgram("unittest-float-struct", IRSymbolTable(), options, target)
        val structName = "main.Country"
        val fields = listOf(
            IRStStructField(DataType.forDt(BaseDataType.UWORD), "name"),
            IRStStructField(DataType.forDt(BaseDataType.FLOAT), "population"),
            IRStStructField(DataType.forDt(BaseDataType.UWORD), "area")
        )
        program.st.add(IRStStructDef(structName, fields, 8u))
        val instanceName = "main.india_instance"
        val population = 1463.87
        val area = 3287
        program.st.add(IRStStructInstance(
            instanceName,
            structName,
            listOf(
                IRStructInitValue(BaseDataType.UWORD, IRStSymbolicReference.Numeric(0.0)),
                IRStructInitValue(BaseDataType.FLOAT, IRStSymbolicReference.Numeric(population)),
                IRStructInitValue(BaseDataType.UWORD, IRStSymbolicReference.Numeric(area.toDouble()))
            ),
            8u
        ))
        val writer = IRFileWriter(program, null)
        val generatedFile = writer.write()
        val program2 = IRFileReader().read(generatedFile)
        generatedFile.deleteExisting()
        val instance = program2.st.allStructInstances().single { it.name == instanceName }
        instance.values.size shouldBe 3
        val floatValue = instance.values[1].value as IRStSymbolicReference.Numeric
        floatValue.value shouldBe population
        val intValue = instance.values[2].value as IRStSymbolicReference.Numeric
        intValue.value shouldBe area.toDouble()
    }

    test("test IR reader parses loadhr/storehr sN immediate encoding") {
        val source="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test-sn-immediate" COMPILERVERSION="99.99">
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
</NOINITCLEAN>
<NOINITDIRTY>
</NOINITDIRTY>
<INIT>
</INIT>
<STRUCTINSTANCESNOINIT>
</STRUCTINSTANCESNOINIT>
<STRUCTINSTANCES>
</STRUCTINSTANCES>
<CONSTANTS>
</CONSTANTS>
<MEMORYMAPPED>
</MEMORYMAPPED>
<MEMORYSLABS>
</MEMORYSLABS>
</VARS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" FORCEOUTPUT="false" NOPREFIXING="false" VERAFXMULS="false" ALIGN="NONE" POS="[test.p8: line 1 col 1-2]">
<SUB NAME="main.start" RETURNS="" POS="[test.p8: line 1 col 1-2]">
<PARAMS>
</PARAMS>
<CHUNK LABEL="main.start"><REGS>dummy</REGS><CODE>
loadhr.b r1,s0
storehr.b r1,s2
</CODE></CHUNK>
</SUB>
</BLOCK>
</PROGRAM>
"""
        val tempfile = createTempFile(suffix = ".p8ir")
        tempfile.writeText(source)
        val program = IRFileReader().read(tempfile)
        tempfile.deleteExisting()
        val sub = program.blocks.single().children.single() as IRSubroutine
        val instructions = sub.chunks.flatMap { it.instructions }
        instructions.size shouldBe 2
        instructions[0].opcode shouldBe Opcode.LOADHR
        instructions[0].type shouldBe IRDataType.BYTE
        instructions[0].reg1 shouldBe 1
        instructions[0].immediate shouldBe 0
        instructions[1].opcode shouldBe Opcode.STOREHR
        instructions[1].type shouldBe IRDataType.BYTE
        instructions[1].reg1 shouldBe 1
        instructions[1].immediate shouldBe 2
    }

    test("test IR callfar round-trip with negative amiga LVO address") {
        val target = Amiga500Target()
        val tempdir = Path(System.getProperty("java.io.tmpdir"))
        val options = CompilationOptions.builder(target)
            .zeropage(ZeropageType.DONTUSE)
            .noSysInit(true)
            .compilerVersion("99.99")
            .loadAddress(target.PROGRAM_LOAD_ADDRESS)
            .memtopAddress(target.PROGRAM_MEMTOP_ADDRESS)
            .outputDir(tempdir)
            .quiet(true)
            .build()
        val program = IRProgram("unittest-callfar-lvo", IRSymbolTable(), options, target)
        val block = IRBlock("main", library=false, IRBlock.Options(), Position("unittest", 1, 1, 1))
        val sub = IRSubroutine("main.start", emptyList(), emptyList(), Position("unittest", 1, 1, 1))
        val chunk = IRCodeChunk("main.start", null)
        chunk.instructions += IRInstruction(
            Opcode.CALLFAR,
            immediate = 1,
            address = MemoryAddress(0xfffffdd8u),
            fcallArgs = FunctionCallArgs(emptyList(), emptyList())
        )
        sub += chunk
        block += sub
        program.addBlock(block)

        val generatedFile = IRFileWriter(program, null).write()
        val readProgram = IRFileReader().read(generatedFile)
        generatedFile.deleteExisting()

        val readSub = readProgram.blocks.single().children.single() as IRSubroutine
        val instr = readSub.chunks.flatMap { it.instructions }.single()
        instr.opcode shouldBe Opcode.CALLFAR
        instr.immediate shouldBe 1
        instr.address shouldBe MemoryAddress(0xfffffdd8u)
    }

    test("test IR reader parses block-level CHUNK (label and align)") {
        val source="""<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="test-block-level-chunk" COMPILERVERSION="99.99">
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
</NOINITCLEAN>
<NOINITDIRTY>
</NOINITDIRTY>
<INIT>
</INIT>
<STRUCTINSTANCESNOINIT>
</STRUCTINSTANCESNOINIT>
<STRUCTINSTANCES>
</STRUCTINSTANCES>
<CONSTANTS>
</CONSTANTS>
<MEMORYMAPPED>
</MEMORYMAPPED>
<MEMORYSLABS>
</MEMORYSLABS>
</VARS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" POS="[test.p8: line 1 col 1-2]">
<CHUNK><REGS><![CDATA[]]></REGS><CODE>
align #$100
</CODE></CHUNK>
<CHUNK LABEL="main.mylabel"><REGS><![CDATA[]]></REGS><CODE>
</CODE></CHUNK>
<SUB NAME="main.start" RETURNS="" POS="[test.p8: line 2 col 2-4]">
<PARAMS>
</PARAMS>
<CHUNK LABEL="main.start"><REGS><![CDATA[]]></REGS><CODE>
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
        val block = program.blocks.single()
        // 1 align chunk + 1 label chunk + 1 sub
        block.children.size shouldBe 3
        val alignChunk = block.children[0] as IRCodeChunk
        alignChunk.label shouldBe null
        alignChunk.instructions.size shouldBe 1
        alignChunk.instructions[0].opcode shouldBe Opcode.ALIGN
        alignChunk.instructions[0].immediate shouldBe 256
        val labelChunk = block.children[1] as IRCodeChunk
        labelChunk.label shouldBe "main.mylabel"
    }
})
