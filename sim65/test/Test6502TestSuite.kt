import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import sim65.components.Bus
import sim65.components.Cpu6502
import sim65.components.InstructionError
import sim65.components.Ram

@TestInstance(TestInstance.Lifecycle.PER_METHOD)
class Test6502TestSuite {

    val cpu: Cpu6502 = Cpu6502(stopOnBrk = false)
    val ram = Ram(0, 0xffff)
    val bus = Bus()
    val kernalStubs = C64KernalStubs(ram)

    init {
        cpu.tracing = false
        cpu.breakpoint(0xffd2) { cpu, pc -> kernalStubs.handleBreakpoint(cpu, pc) }
        cpu.breakpoint(0xffe4) { cpu, pc -> kernalStubs.handleBreakpoint(cpu, pc) }
        cpu.breakpoint(0xe16f) { cpu, pc -> kernalStubs.handleBreakpoint(cpu, pc) }

        // create the system bus and add device to it.
        // note that the order is relevant w.r.t. where reads and writes are going.
        ram[0x02] = 0
        ram[0xa002] = 0
        ram[0xa003] = 0x80
        ram[Cpu6502.IRQ_vector] = 0x48
        ram[Cpu6502.IRQ_vector + 1] = 0xff
        ram[Cpu6502.RESET_vector] = 0x01
        ram[Cpu6502.RESET_vector + 1] = 0x08
        ram[0x01fe] = 0xff
        ram[0x01ff] = 0x7f
        ram[0x8000] = 2
        ram[0xa474] = 2

        // setup the irq/brk routine
        for(b in listOf(0x48, 0x8A, 0x48, 0x98, 0x48, 0xBA, 0xBD, 0x04,
                0x01, 0x29, 0x10, 0xF0, 0x03, 0x6C, 0x16, 0x03,
                0x6C, 0x14, 0x03).withIndex()) {
            ram[0xff48+b.index] = b.value.toShort()
        }
        bus.add(cpu)
        bus.add(ram)
    }

    private fun runTest(testprogram: String) {
        ram.loadPrg("test/6502testsuite/$testprogram")
        bus.reset()
        cpu.SP = 0xfd
        cpu.Status.fromByte(0b00100100)
        try {
            while (cpu.totalCycles < 50000000L) {
                bus.clock()
            }
            fail("test hangs")
        } catch (e: InstructionError) {
            println(">>> INSTRUCTION ERROR: ${e.message}")
        } catch (le: LoadNextPart) {
            return  // test ok
        } catch (ie: InputRequired) {
            fail("test failed")
        }
        fail("test failed")
    }

    @Test
    fun test0start() {
        runTest("0start")
    }

    @Test
    fun testAdca() {
        runTest("adca")
    }

    @Test
    fun testAdcax() {
        runTest("adcax")
    }

    @Test
    fun testAdcay() {
        runTest("adcay")
    }

    @Test
    fun testAdcb() {
        runTest("adcb")
    }

    @Test
    fun testAdcix() {
        runTest("adcix")
    }

    @Test
    fun testAdciy() {
        runTest("adciy")
    }

    @Test
    fun testAdcz() {
        runTest("adcz")
    }

    @Test
    fun testAdczx() {
        runTest("adczx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAlrb() {
        runTest("alrb")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAncb() {
        runTest("ancb")
    }

    @Test
    fun testAnda() {
        runTest("anda")
    }

    @Test
    fun testAndax() {
        runTest("andax")
    }

    @Test
    fun testAnday() {
        runTest("anday")
    }

    @Test
    fun testAndb() {
        runTest("andb")
    }

    @Test
    fun testAndix() {
        runTest("andix")
    }

    @Test
    fun testAndiy() {
        runTest("andiy")
    }

    @Test
    fun testAndz() {
        runTest("andz")
    }

    @Test
    fun testAndzx() {
        runTest("andzx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAneb() {
        runTest("aneb")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testArrb() {
        runTest("arrb")
    }

    @Test
    fun testAsla() {
        runTest("asla")
    }

    @Test
    fun testAslax() {
        runTest("aslax")
    }

    @Test
    fun testAsln() {
        runTest("asln")
    }

    @Test
    fun testAslz() {
        runTest("aslz")
    }

    @Test
    fun testAslzx() {
        runTest("aslzx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsoa() {
        runTest("asoa")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsoax() {
        runTest("asoax")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsoay() {
        runTest("asoay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsoix() {
        runTest("asoix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsoiy() {
        runTest("asoiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsoz() {
        runTest("asoz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAsozx() {
        runTest("asozx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAxsa() {
        runTest("axsa")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAxsix() {
        runTest("axsix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAxsz() {
        runTest("axsz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testAxszy() {
        runTest("axszy")
    }

    @Test
    fun testBccr() {
        runTest("bccr")
    }

    @Test
    fun testBcsr() {
        runTest("bcsr")
    }

    @Test
    fun testBeqr() {
        runTest("beqr")
    }

    @Test
    fun testBita() {
        runTest("bita")
    }

    @Test
    fun testBitz() {
        runTest("bitz")
    }

    @Test
    fun testBmir() {
        runTest("bmir")
    }

    @Test
    fun testBner() {
        runTest("bner")
    }

    @Test
    fun testBplr() {
        runTest("bplr")
    }

    @Test
    fun testBranchwrap() {
        runTest("branchwrap")
    }

    @Test
    fun testBrkn() {
        runTest("brkn")
    }

    @Test
    fun testBvcr() {
        runTest("bvcr")
    }

    @Test
    fun testBvsr() {
        runTest("bvsr")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia1pb6() {
        runTest("cia1pb6")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia1pb7() {
        runTest("cia1pb7")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia1ta() {
        runTest("cia1ta")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia1tab() {
        runTest("cia1tab")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia1tb() {
        runTest("cia1tb")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia1tb123() {
        runTest("cia1tb123")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia2pb6() {
        runTest("cia2pb6")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia2pb7() {
        runTest("cia2pb7")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia2ta() {
        runTest("cia2ta")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia2tb() {
        runTest("cia2tb")
    }

    @Test
    @Disabled("c64 specific component")
    fun testCia2tb123() {
        runTest("cia2tb123")
    }

    @Test
    fun testClcn() {
        runTest("clcn")
    }

    @Test
    fun testCldn() {
        runTest("cldn")
    }

    @Test
    fun testClin() {
        runTest("clin")
    }

    @Test
    fun testClvn() {
        runTest("clvn")
    }

    @Test
    fun testCmpa() {
        runTest("cmpa")
    }

    @Test
    fun testCmpax() {
        runTest("cmpax")
    }

    @Test
    fun testCmpay() {
        runTest("cmpay")
    }

    @Test
    fun testCmpb() {
        runTest("cmpb")
    }

    @Test
    fun testCmpix() {
        runTest("cmpix")
    }

    @Test
    fun testCmpiy() {
        runTest("cmpiy")
    }

    @Test
    fun testCmpz() {
        runTest("cmpz")
    }

    @Test
    fun testCmpzx() {
        runTest("cmpzx")
    }

    @Test
    @Disabled("c64 6510 specific component")
    fun testCntdef() {
        runTest("cntdef")
    }

    @Test
    @Disabled("c64 6510 specific component")
    fun testCnto2() {
        runTest("cnto2")
    }

    @Test
    @Disabled("c64 6510 specific component")
    fun testCpuport() {
        runTest("cpuport")
    }

    @Test
    @Disabled("todo: get all cycle times right")
    fun testCputiming() {
        runTest("cputiming")
    }

    @Test
    fun testCpxa() {
        runTest("cpxa")
    }

    @Test
    fun testCpxb() {
        runTest("cpxb")
    }

    @Test
    fun testCpxz() {
        runTest("cpxz")
    }

    @Test
    fun testCpya() {
        runTest("cpya")
    }

    @Test
    fun testCpyb() {
        runTest("cpyb")
    }

    @Test
    fun testCpyz() {
        runTest("cpyz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcma() {
        runTest("dcma")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcmax() {
        runTest("dcmax")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcmay() {
        runTest("dcmay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcmix() {
        runTest("dcmix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcmiy() {
        runTest("dcmiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcmz() {
        runTest("dcmz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testDcmzx() {
        runTest("dcmzx")
    }

    @Test
    fun testDeca() {
        runTest("deca")
    }

    @Test
    fun testDecax() {
        runTest("decax")
    }

    @Test
    fun testDecz() {
        runTest("decz")
    }

    @Test
    fun testDeczx() {
        runTest("deczx")
    }

    @Test
    fun testDexn() {
        runTest("dexn")
    }

    @Test
    fun testDeyn() {
        runTest("deyn")
    }

    @Test
    fun testEora() {
        runTest("eora")
    }

    @Test
    fun testEorax() {
        runTest("eorax")
    }

    @Test
    fun testEoray() {
        runTest("eoray")
    }

    @Test
    fun testEorb() {
        runTest("eorb")
    }

    @Test
    fun testEorix() {
        runTest("eorix")
    }

    @Test
    fun testEoriy() {
        runTest("eoriy")
    }

    @Test
    fun testEorz() {
        runTest("eorz")
    }

    @Test
    fun testEorzx() {
        runTest("eorzx")
    }

    @Test
    @Disabled("c64 specific component")
    fun testFlipos() {
        runTest("flipos")
    }

    @Test
    @Disabled("c64 specific component")
    fun testIcr01() {
        runTest("icr01")
    }

    @Test
    @Disabled("c64 specific component")
    fun testImr() {
        runTest("imr")
    }

    @Test
    fun testInca() {
        runTest("inca")
    }

    @Test
    fun testIncax() {
        runTest("incax")
    }

    @Test
    fun testIncz() {
        runTest("incz")
    }

    @Test
    fun testInczx() {
        runTest("inczx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInsa() {
        runTest("insa")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInsax() {
        runTest("insax")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInsay() {
        runTest("insay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInsix() {
        runTest("insix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInsiy() {
        runTest("insiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInsz() {
        runTest("insz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testInszx() {
        runTest("inszx")
    }

    @Test
    fun testInxn() {
        runTest("inxn")
    }

    @Test
    fun testInyn() {
        runTest("inyn")
    }

    @Test
    @Disabled("c64 specific component")
    fun testIrq() {
        runTest("irq")
    }

    @Test
    fun testJmpi() {
        runTest("jmpi")
    }

    @Test
    fun testJmpw() {
        runTest("jmpw")
    }

    @Test
    fun testJsrw() {
        runTest("jsrw")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLasay() {
        runTest("lasay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLaxa() {
        runTest("laxa")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLaxay() {
        runTest("laxay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLaxix() {
        runTest("laxix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLaxiy() {
        runTest("laxiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLaxz() {
        runTest("laxz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLaxzy() {
        runTest("laxzy")
    }

    @Test
    fun testLdaa() {
        runTest("ldaa")
    }

    @Test
    fun testLdaax() {
        runTest("ldaax")
    }

    @Test
    fun testLdaay() {
        runTest("ldaay")
    }

    @Test
    fun testLdab() {
        runTest("ldab")
    }

    @Test
    fun testLdaix() {
        runTest("ldaix")
    }

    @Test
    fun testLdaiy() {
        runTest("ldaiy")
    }

    @Test
    fun testLdaz() {
        runTest("ldaz")
    }

    @Test
    fun testLdazx() {
        runTest("ldazx")
    }

    @Test
    fun testLdxa() {
        runTest("ldxa")
    }

    @Test
    fun testLdxay() {
        runTest("ldxay")
    }

    @Test
    fun testLdxb() {
        runTest("ldxb")
    }

    @Test
    fun testLdxz() {
        runTest("ldxz")
    }

    @Test
    fun testLdxzy() {
        runTest("ldxzy")
    }

    @Test
    fun testLdya() {
        runTest("ldya")
    }

    @Test
    fun testLdyax() {
        runTest("ldyax")
    }

    @Test
    fun testLdyb() {
        runTest("ldyb")
    }

    @Test
    fun testLdyz() {
        runTest("ldyz")
    }

    @Test
    fun testLdyzx() {
        runTest("ldyzx")
    }

    @Test
    @Disabled("c64 specific component")
    fun testLoadth() {
        runTest("loadth")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLsea() {
        runTest("lsea")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLseax() {
        runTest("lseax")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLseay() {
        runTest("lseay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLseix() {
        runTest("lseix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLseiy() {
        runTest("lseiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLsez() {
        runTest("lsez")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLsezx() {
        runTest("lsezx")
    }

    @Test
    fun testLsra() {
        runTest("lsra")
    }

    @Test
    fun testLsrax() {
        runTest("lsrax")
    }

    @Test
    fun testLsrn() {
        runTest("lsrn")
    }

    @Test
    fun testLsrz() {
        runTest("lsrz")
    }

    @Test
    fun testLsrzx() {
        runTest("lsrzx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testLxab() {
        runTest("lxab")
    }

    @Test
    @Disabled("c64 6510 specific component")
    fun testMmu() {
        runTest("mmu")
    }

    @Test
    @Disabled("c64 6510 specific component")
    fun testMmufetch() {
        runTest("mmufetch")
    }

    @Test
    @Disabled("c64 specific component")
    fun testNmi() {
        runTest("nmi")
    }

    @Test
    fun testNopa() {
        runTest("nopa")
    }

    @Test
    fun testNopax() {
        runTest("nopax")
    }

    @Test
    fun testNopb() {
        runTest("nopb")
    }

    @Test
    fun testNopn() {
        runTest("nopn")
    }

    @Test
    fun testNopz() {
        runTest("nopz")
    }

    @Test
    fun testNopzx() {
        runTest("nopzx")
    }

    @Test
    @Disabled("c64 specific component")
    fun testOneshot() {
        runTest("oneshot")
    }

    @Test
    fun testOraa() {
        runTest("oraa")
    }

    @Test
    fun testOraax() {
        runTest("oraax")
    }

    @Test
    fun testOraay() {
        runTest("oraay")
    }

    @Test
    fun testOrab() {
        runTest("orab")
    }

    @Test
    fun testOraix() {
        runTest("oraix")
    }

    @Test
    fun testOraiy() {
        runTest("oraiy")
    }

    @Test
    fun testOraz() {
        runTest("oraz")
    }

    @Test
    fun testOrazx() {
        runTest("orazx")
    }

    @Test
    fun testPhan() {
        runTest("phan")
    }

    @Test
    fun testPhpn() {
        runTest("phpn")
    }

    @Test
    fun testPlan() {
        runTest("plan")
    }

    @Test
    fun testPlpn() {
        runTest("plpn")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlaa() {
        runTest("rlaa")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlaax() {
        runTest("rlaax")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlaay() {
        runTest("rlaay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlaix() {
        runTest("rlaix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlaiy() {
        runTest("rlaiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlaz() {
        runTest("rlaz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRlazx() {
        runTest("rlazx")
    }

    @Test
    fun testRola() {
        runTest("rola")
    }

    @Test
    fun testRolax() {
        runTest("rolax")
    }

    @Test
    fun testRoln() {
        runTest("roln")
    }

    @Test
    fun testRolz() {
        runTest("rolz")
    }

    @Test
    fun testRolzx() {
        runTest("rolzx")
    }

    @Test
    fun testRora() {
        runTest("rora")
    }

    @Test
    fun testRorax() {
        runTest("rorax")
    }

    @Test
    fun testRorn() {
        runTest("rorn")
    }

    @Test
    fun testRorz() {
        runTest("rorz")
    }

    @Test
    fun testRorzx() {
        runTest("rorzx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRraa() {
        runTest("rraa")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRraax() {
        runTest("rraax")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRraay() {
        runTest("rraay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRraix() {
        runTest("rraix")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRraiy() {
        runTest("rraiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRraz() {
        runTest("rraz")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testRrazx() {
        runTest("rrazx")
    }

    @Test
    fun testRtin() {
        runTest("rtin")
    }

    @Test
    fun testRtsn() {
        runTest("rtsn")
    }

    @Test
    fun testSbca() {
        runTest("sbca")
    }

    @Test
    fun testSbcax() {
        runTest("sbcax")
    }

    @Test
    fun testSbcay() {
        runTest("sbcay")
    }

    @Test
    fun testSbcb() {
        runTest("sbcb")
    }

    @Test
    fun testSbcb_eb() {
        runTest("sbcb(eb)")
    }

    @Test
    fun testSbcix() {
        runTest("sbcix")
    }

    @Test
    fun testSbciy() {
        runTest("sbciy")
    }

    @Test
    fun testSbcz() {
        runTest("sbcz")
    }

    @Test
    fun testSbczx() {
        runTest("sbczx")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testSbxb() {
        runTest("sbxb")
    }

    @Test
    fun testSecn() {
        runTest("secn")
    }

    @Test
    fun testSedn() {
        runTest("sedn")
    }

    @Test
    fun testSein() {
        runTest("sein")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction sha/ahx")
    fun testShaay() {
        runTest("shaay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction sha/ahx")
    fun testShaiy() {
        runTest("shaiy")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction shs/tas")
    fun testShsay() {
        runTest("shsay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testShxay() {
        runTest("shxay")
    }

    @Test
    @Disabled("not yet implemented- illegal instruction")
    fun testShyax() {
        runTest("shyax")
    }

    @Test
    fun testStaa() {
        runTest("staa")
    }

    @Test
    fun testStaax() {
        runTest("staax")
    }

    @Test
    fun testStaay() {
        runTest("staay")
    }

    @Test
    fun testStaix() {
        runTest("staix")
    }

    @Test
    fun testStaiy() {
        runTest("staiy")
    }

    @Test
    fun testStaz() {
        runTest("staz")
    }

    @Test
    fun testStazx() {
        runTest("stazx")
    }

    @Test
    fun testStxa() {
        runTest("stxa")
    }

    @Test
    fun testStxz() {
        runTest("stxz")
    }

    @Test
    fun testStxzy() {
        runTest("stxzy")
    }

    @Test
    fun testStya() {
        runTest("stya")
    }

    @Test
    fun testStyz() {
        runTest("styz")
    }

    @Test
    fun testStyzx() {
        runTest("styzx")
    }

    @Test
    fun testTaxn() {
        runTest("taxn")
    }

    @Test
    fun testTayn() {
        runTest("tayn")
    }

    @Test
    fun testTsxn() {
        runTest("tsxn")
    }

    @Test
    fun testTxan() {
        runTest("txan")
    }

    @Test
    fun testTxsn() {
        runTest("txsn")
    }

    @Test
    fun testTyan() {
        runTest("tyan")
    }

}
