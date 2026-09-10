package prog8.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.intermediate.IRFileReader

class AddimSubimTest : FunSpec({

    fun runProgram(): VirtualMachine {
        val source = $$"""
<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="addim_subim_test" COMPILERVERSION="12.3-SNAPSHOT" IRFORMAT="2">
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
load.b r1.b,#100.b
storem.b r1.b,[$2000]
addim.b #20.b,[$2000]
subim.b #30.b,[$2000]
load.b r2.b,#200.b
storem.b r2.b,[$2001]
addim.b #100.b,[$2001]
load.w r3.w,#1000.w
storem.w r3.w,[$2002]
addim.w #200.w,[$2002]
subim.w #300.w,[$2002]
load.w r4.w,#100.w
storem.w r4.w,[$2004]
subim.w #200.w,[$2004]
load.l r5.l,#100000.l
storem.l r5.l,[$2006]
addim.l #20000.l,[$2006]
subim.l #30000.l,[$2006]
load.l r6.l,#100.l
storem.l r6.l,[$200A]
subim.l #200.l,[$200A]
load.f fr7.f,#1.5.f
storem.f fr7.f,[$200E]
addim.f #2.0.f,[$200E]
subim.f #0.5.f,[$200E]
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
