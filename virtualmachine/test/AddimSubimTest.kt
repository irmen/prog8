package prog8.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.intermediate.IRFileReader

class AddimSubimTest : FunSpec({

    fun runProgram(): VirtualMachine {
        val source = $$"""
<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="addim_subim_test" COMPILERVERSION="12.3-SNAPSHOT">
<OPTIONS>
compTarget=virtual
output=PRG
launcher=BASIC
zeropage=BASICSAFE
zpAllowed=0,255
loadAddress=0
memtop=$ffff
optimize=true
romable=false
noSysInit=false
outputDir=/tmp
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
<CHUNK><REGS><![CDATA[]]></REGS><CODE>
</CODE></CHUNK>
</INITGLOBALS>

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" POS="[addim_subim_test.p8ir: line 1 col 1-1]">
<SUB NAME="main.start" RETURNS="" POS="[addim_subim_test.p8ir: line 1 col 1-1]">
<PARAMS>
</PARAMS>
<CHUNK LABEL="main.start"><REGS><![CDATA[]]></REGS><CODE>
load.b r1, #100
storem.b r1, $2000
addim.b #20, $2000
subim.b #30, $2000
load.b r2, #200
storem.b r2, $2001
addim.b #100, $2001
load.w r3, #1000
storem.w r3, $2002
addim.w #200, $2002
subim.w #300, $2002
load.w r4, #100
storem.w r4, $2004
subim.w #200, $2004
load.l r5, #100000
storem.l r5, $2006
addim.l #20000, $2006
subim.l #30000, $2006
load.l r6, #100
storem.l r6, $200A
subim.l #200, $200A
load.f fr7, #1.5
storem.f fr7, $200E
addim.f #2.0, $200E
subim.f #0.5, $200E
return
</CODE></CHUNK>
</SUB>
</BLOCK>

</PROGRAM>
""".trimIndent()
        val irProgram = IRFileReader().read(source)
        val vm = VirtualMachine(irProgram)
        vm.run(true)
        return vm
    }

    test("ADDIM/SUBIM produce correct results for all datatypes") {
        val vm = runProgram()

        // BYTE: 100 + 20 - 30 = 90
        vm.memory.getUB(0x2000u) shouldBe 90u
        // BYTE wrap: 200 + 100 = 300 -> 44 (uByte)
        vm.memory.getUB(0x2001u) shouldBe 44u

        // WORD: 1000 + 200 - 300 = 900
        vm.memory.getUW(0x2002u) shouldBe 900u
        // WORD wrap (unsigned): 100 - 200 = -100 -> 65436
        vm.memory.getUW(0x2004u) shouldBe 65436u

        // LONG (signed): 100000 + 20000 - 30000 = 90000
        vm.memory.getSL(0x2006u) shouldBe 90000
        // LONG (signed): 100 - 200 = -100
        vm.memory.getSL(0x200Au) shouldBe -100

        // FLOAT: 1.5 + 2.0 - 0.5 = 3.0
        vm.memory.getFloat(0x200Eu) shouldBe 3.0
    }
})
