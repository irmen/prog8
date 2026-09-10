package prog8.vm

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.intermediate.IRFileReader

/**
 * End-to-end check of the structured IR operands as the VM consumes them:
 * indexed and indirect memory references, a call with an argument and a captured result,
 * hardware register slots, an explicit status-flag comparison plus branch, and a syscall.
 */
class TestVmStructuredOperands : FunSpec({

    fun runProgram(): VirtualMachine {
        val source = $$"""
<?xml version="1.0" encoding="utf-8"?>
<PROGRAM NAME="structured_operands_test" COMPILERVERSION="12.3-SNAPSHOT" IRFORMAT="2">
<OPTIONS>
compTarget=virtual
output=PRG
launcher=BASIC
zeropage=BASICSAFE
zpAllowed=0,255
loadAddress=0
memtop=$ffff
optimize=false
romable=false
noSysInit=false
</OPTIONS>

<ASMSYMBOLS>
</ASMSYMBOLS>
<VARS>
<NOINITCLEAN>
</NOINITCLEAN>
<NOINITDIRTY>
</NOINITDIRTY>
<INIT>
uword[4] main.arr=11,22,33,44 zp=DONTCARE align=0
uword main.result=0 zp=DONTCARE align=0
uword main.flag=0 zp=DONTCARE align=0
ubyte main.hwval=0 zp=DONTCARE align=0
uword main.cmp=0 zp=DONTCARE align=0
uword main.doubled.value=0 zp=DONTCARE align=0
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

<BLOCK NAME="main" ADDRESS="" LIBRARY="false" POS="[structured.p8ir: line 1 col 1-1]">
<SUB NAME="main.start" RETURNS="" POS="[structured.p8ir: line 1 col 1-1]">
<PARAMS>
</PARAMS>
<CHUNK LABEL="main.start"><REGS><![CDATA[]]></REGS><CODE>
load.w r10.w,#2.w
loadx.w r1.w,[main.arr+r10.w*2]
call main.doubled(value=r1.w):r2.w
storem.w r2.w,[main.result]
load.p r3.p,#main.arr
load.w r4.w,#99.w
storei.w r4.w,[r3.p+4]
load.b r6.b,#$5a.b
storehr.b r6.b,s0.b
loadhr.b r7.b,s0.b
storem.b r7.b,[main.hwval]
cmpi.w r2.w,#66.w
bsteq main.ok
</CODE></CHUNK>
<CHUNK><REGS><![CDATA[]]></REGS><CODE>
load.w r8.w,#0.w
jump main.end
</CODE></CHUNK>
<CHUNK LABEL="main.ok"><REGS><![CDATA[]]></REGS><CODE>
load.w r8.w,#1.w
</CODE></CHUNK>
<CHUNK LABEL="main.end"><REGS><![CDATA[]]></REGS><CODE>
storem.w r8.w,[main.flag]
load.l r11.l,#main.arr
load.l r12.l,#main.arr
load.w r13.w,#8.w
syscall $2f(r11.l,r12.l,r13.w):r14.w
storem.w r14.w,[main.cmp]
load.f fr15.f,#main.arr
return
</CODE></CHUNK>
</SUB>
<SUB NAME="main.doubled" RETURNS="uword" POS="[structured.p8ir: line 1 col 1-1]">
<PARAMS>
uword main.doubled.value
</PARAMS>
<CHUNK LABEL="main.doubled"><REGS><![CDATA[]]></REGS><CODE>
loadm.w r20.w,[main.doubled.value]
mul.w r20.w,#2.w
returnr.w r20.w
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

    test("structured operands execute correctly") {
        val vm = runProgram()
        val arrAddress = vm.registers.getUL(3)

        // indexed memory read arr[2]==33, passed to a subroutine that doubles it and returns it
        vm.registers.getUW(1) shouldBe 33u
        vm.registers.getUW(2) shouldBe 66u
        vm.memory.getUW(arrAddress + 4u) shouldBe 99u       // indirect store through pointer register + displacement
        vm.memory.getUW(arrAddress) shouldBe 11u            // untouched array element

        // hardware register slot roundtrip (slot 0 == cpu register A)
        vm.registers.getUB(7) shouldBe 0x5au
        vm.hardwareRegisterA shouldBe 0x5au

        // the CMPI status flags drove the branch to main.ok
        vm.registers.getUW(8) shouldBe 1u
        vm.statusZero shouldBe true

        // syscall MEMCMP of the array against itself returns 0
        vm.registers.getUW(14) shouldBe 0u

        // an address-of a symbol keeps the data type the instruction prescribes
        vm.registers.getSL(11) shouldBe arrAddress.toInt()
        vm.registers.getFloat(15) shouldBe arrAddress.toDouble()
    }
})
