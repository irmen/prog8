package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.DataType
import prog8.compiler.HeapValues
import prog8.compiler.intermediate.Instruction
import prog8.compiler.intermediate.Opcode
import prog8.compiler.intermediate.Value
import prog8.stackvm.*
import kotlin.test.*


/***

@todo opcodes still to be unit-tested:

    SHL_MEM_BYTE,
    SHL_MEM_WORD,
    SHL_VAR_BYTE,
    SHL_VAR_WORD,
    SHR_MEM_BYTE,
    SHR_MEM_WORD,
    SHR_VAR_BYTE,
    SHR_VAR_WORD,
    ROL_MEM_BYTE,
    ROL_MEM_WORD,
    ROL_VAR_BYTE,
    ROL_VAR_WORD,
    ROR_MEM,
    ROR_MEM_W,
    ROR_VAR,
    ROR_VAR_W,
    ROL2_MEM,
    ROL2_MEM_W,
    ROL2_VAR,
    ROL2_VAR_W,
    ROR2_MEM,
    ROR2_MEM_W,
    ROR2_VAR,
    ROR2_VAR_W

**/

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestStackVmOpcodes {

    private val vm = StackVm(null)

    fun makeProg(ins: MutableList<Instruction>,
                 vars: Map<String, Value>?=null,
                 labels: Map<String, Instruction>?=null,
                 mem: Map<Int, List<Value>>?=null) : Program {
        val heap = HeapValues()
        return Program("test", ins, vars ?: mapOf(), labels ?: mapOf(), mem ?: mapOf(), heap)
    }

    @Test
    fun testInitAndNop() {
        val ins = mutableListOf(Instruction(Opcode.NOP))
        vm.load(makeProg(ins), null)
        assertEquals(6, vm.variables.size)
        assertTrue(vm.variables.containsKey("XY"))
        assertTrue(vm.variables.containsKey("A"))
        vm.step(1)
        assertThat(vm.callstack, empty())
        assertThat(vm.evalstack, empty())
        assertFailsWith<VmTerminationException> {
            vm.step()
        }
    }

    @Test
    fun testBreakpoint() {
        val ins = mutableListOf(Instruction(Opcode.BREAKPOINT))
        vm.load(makeProg(ins), null)
        assertFailsWith<VmBreakpointException> {
            vm.step()
        }
        assertThat(vm.callstack, empty())
        assertThat(vm.evalstack, empty())
    }

    @Test
    fun testLine() {
        val ins = mutableListOf(Instruction(Opcode.LINE, callLabel = "line 99"))
        vm.load(makeProg(ins), null)
        assertEquals("", vm.sourceLine)
        vm.step(1)
        assertEquals("line 99", vm.sourceLine)
    }

    @Test
    fun testSECandSEIandCLCandCLI() {
        val ins = mutableListOf(Instruction(Opcode.SEC), Instruction(Opcode.SEI), Instruction(Opcode.CLC), Instruction(Opcode.CLI))
        vm.load(makeProg(ins), null)
        assertFalse(vm.P_carry)
        assertFalse(vm.P_irqd)
        assertFalse(vm.P_negative)
        assertFalse(vm.P_zero)
        vm.step(1)
        assertTrue(vm.P_carry)
        assertFalse(vm.P_irqd)
        vm.step(1)
        assertTrue(vm.P_carry)
        assertTrue(vm.P_irqd)
        vm.step(1)
        assertFalse(vm.P_carry)
        assertTrue(vm.P_irqd)
        vm.step(1)
        assertFalse(vm.P_carry)
        assertFalse(vm.P_irqd)
    }

    @Test
    fun testPushWrongDt() {
        val ins = mutableListOf(Instruction(Opcode.PUSH_BYTE, Value(DataType.UWORD, 4299)))
        vm.load(makeProg(ins), null)
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testPush() {
        val ins = mutableListOf(Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 42.999)))
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
    }

    @Test
    fun testPushMem() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_MEM_B, Value(DataType.UWORD, 0x2000)),
                Instruction(Opcode.PUSH_MEM_UB, Value(DataType.UWORD, 0x3000)),
                Instruction(Opcode.PUSH_MEM_W, Value(DataType.UWORD, 0x4000)),
                Instruction(Opcode.PUSH_MEM_UW, Value(DataType.UWORD, 0x5000)),
                Instruction(Opcode.PUSH_MEM_FLOAT, Value(DataType.UWORD, 0x6000))
        )
        val mem=mapOf(0x2000 to listOf(Value(DataType.UWORD, 0xc2ca)),
                0x3000 to listOf(Value(DataType.UWORD, 0xc2ca)),
                0x4000 to listOf(Value(DataType.UWORD, 0xc2ca)),
                0x5000 to listOf(Value(DataType.UWORD, 0xc2ca)),
                0x6000 to listOf(Value(DataType.FLOAT, 42.25)))
        vm.load(makeProg(ins, mem=mem), null)
        assertEquals(0xca, vm.mem.getUByte(0x2000))
        assertEquals(0xc2, vm.mem.getUByte(0x2001))
        assertEquals(0xca, vm.mem.getUByte(0x3000))
        assertEquals(0xc2, vm.mem.getUByte(0x3001))
        assertEquals(0xca, vm.mem.getUByte(0x4000))
        assertEquals(0xc2, vm.mem.getUByte(0x4001))
        assertEquals(0xca, vm.mem.getUByte(0x5000))
        assertEquals(0xc2, vm.mem.getUByte(0x5001))
        assertEquals(-54, vm.mem.getSByte(0x2000))
        assertEquals(-62, vm.mem.getSByte(0x2001))
        assertEquals(-54, vm.mem.getSByte(0x3000))
        assertEquals(-62, vm.mem.getSByte(0x3001))
        assertEquals(-54, vm.mem.getSByte(0x4000))
        assertEquals(-62, vm.mem.getSByte(0x4001))
        assertEquals(-54, vm.mem.getSByte(0x5000))
        assertEquals(-62, vm.mem.getSByte(0x5001))
        assertEquals(0xc2ca, vm.mem.getUWord(0x2000))
        assertEquals(0xc2ca, vm.mem.getUWord(0x3000))
        assertEquals(0xc2ca, vm.mem.getUWord(0x4000))
        assertEquals(0xc2ca, vm.mem.getUWord(0x5000))
        assertEquals(-15670, vm.mem.getSWord(0x2000))
        assertEquals(-15670, vm.mem.getSWord(0x3000))
        assertEquals(-15670, vm.mem.getSWord(0x4000))
        assertEquals(-15670, vm.mem.getSWord(0x5000))
        assertEquals(42.25, vm.mem.getFloat(0x6000))
        assertThat(vm.evalstack, empty())
        vm.step(5)
        assertEquals(5, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.25), vm.evalstack.pop())
        assertEquals(Value(DataType.UWORD, 0xc2ca), vm.evalstack.pop())
        assertEquals(Value(DataType.WORD, -15670), vm.evalstack.pop())
        assertEquals(Value(DataType.UBYTE, 0xca), vm.evalstack.pop())
        assertEquals(Value(DataType.BYTE, -54), vm.evalstack.pop())
    }

    @Test
    fun testPushVar() {
        val ins = mutableListOf(Instruction(Opcode.PUSH_VAR_FLOAT, callLabel = "varname"))
        vm.load(makeProg(ins, mapOf("varname" to Value(DataType.FLOAT, 42.999))), null)
        assertEquals(7, vm.variables.size)
        assertTrue(vm.variables.containsKey("varname"))
        assertTrue(vm.variables.containsKey("XY"))
        assertTrue(vm.variables.containsKey("A"))
        assertEquals(Value(DataType.FLOAT, 42.999), vm.variables["varname"])
        assertThat(vm.evalstack, empty())
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
        assertEquals(Value(DataType.FLOAT, 42.999), vm.variables["varname"])
    }

    @Test
    fun testDiscard() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 42.999)),
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 3.1415)),
                Instruction(Opcode.DISCARD_FLOAT))
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(2)
        assertEquals(2, vm.evalstack.size)
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
    }

    @Test
    fun testPopMem() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 42.25)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0xc2ca)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.WORD, -23456)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 177)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.BYTE, -55)),
                Instruction(Opcode.POP_MEM_BYTE, Value(DataType.UWORD, 0x2000)),
                Instruction(Opcode.POP_MEM_BYTE, Value(DataType.UWORD, 0x2001)),
                Instruction(Opcode.POP_MEM_WORD, Value(DataType.UWORD, 0x3000)),
                Instruction(Opcode.POP_MEM_WORD, Value(DataType.UWORD, 0x3002)),
                Instruction(Opcode.POP_MEM_FLOAT, Value(DataType.UWORD, 0x4000)))
        vm.load(makeProg(ins), null)
        assertEquals(0, vm.mem.getUWord(0x2000))
        assertEquals(0, vm.mem.getUWord(0x2001))
        assertEquals(0, vm.mem.getUWord(0x3000))
        assertEquals(0, vm.mem.getUWord(0x3002))
        assertEquals(0.0, vm.mem.getFloat(0x4000))
        assertThat(vm.evalstack, empty())
        vm.step(11)
        assertThat(vm.evalstack, empty())
        assertEquals(201, vm.mem.getUByte(0x2000))
        assertEquals(177, vm.mem.getUByte(0x2001))
        assertEquals(-55, vm.mem.getSByte(0x2000))
        assertEquals(-79, vm.mem.getSByte(0x2001))
        assertEquals(42080, vm.mem.getUWord(0x3000))
        assertEquals(0xc2ca, vm.mem.getUWord(0x3002))
        assertEquals(-23456, vm.mem.getSWord(0x3000))
        assertEquals(-15670, vm.mem.getSWord(0x3002))
        assertEquals(42.25, vm.mem.getFloat(0x4000))
    }

    @Test
    fun testPopVar() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 42.25)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0x42ea)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 123)),
                Instruction(Opcode.POP_VAR_BYTE, callLabel = "var1"),
                Instruction(Opcode.POP_VAR_WORD, callLabel = "var2"),
                Instruction(Opcode.POP_VAR_FLOAT, callLabel = "var3"))
        val vars = mapOf(
                "var1" to Value(DataType.UBYTE, 0),
                "var2" to Value(DataType.UWORD, 0),
                "var3" to Value(DataType.FLOAT, 0)
                )
        vm.load(makeProg(ins, vars), null)
        assertEquals(9, vm.variables.size)
        vm.step(6)
        assertEquals(Value(DataType.UBYTE, 123), vm.variables["var1"])
        assertEquals(Value(DataType.UWORD, 0x42ea), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, 42.25), vm.variables["var3"])

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0x42ea)),
                Instruction(Opcode.POP_VAR_WORD, callLabel = "var1"))
        val vars2 = mapOf(
                "var1" to Value(DataType.UBYTE, 0)
        )
        vm.load(makeProg(ins2, vars2), null)
        assertEquals(7, vm.variables.size)
        assertFailsWith<VmExecutionException> {
            vm.step(2)
        }
    }

    @Test
    fun testAdd() {
        testBinaryOperator(Value(DataType.UBYTE, 140), Opcode.ADD_UB, Value(DataType.UBYTE, 222), Value(DataType.UBYTE, 106))
        testBinaryOperator(Value(DataType.UBYTE, 40), Opcode.ADD_UB, Value(DataType.UBYTE, 122), Value(DataType.UBYTE, 162))
        testBinaryOperator(Value(DataType.UWORD, 4000), Opcode.ADD_UW, Value(DataType.UWORD, 40), Value(DataType.UWORD, 4040))
        testBinaryOperator(Value(DataType.UWORD, 24000), Opcode.ADD_UW, Value(DataType.UWORD, 55000), Value(DataType.UWORD, 13464))
        testBinaryOperator(Value(DataType.FLOAT, 4000.0), Opcode.ADD_F, Value(DataType.FLOAT, 123.22), Value(DataType.FLOAT, 4123.22))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.UWORD, 4000 + 40), Opcode.ADD_UW, Value(DataType.FLOAT, 42.25), Value(DataType.FLOAT, 42.25 + (4000 + 40)))
        }
    }

    @Test
    fun testSub() {
        testBinaryOperator(Value(DataType.UBYTE, 250), Opcode.SUB_UB, Value(DataType.UBYTE, 70), Value(DataType.UBYTE, 180))
        testBinaryOperator(Value(DataType.UWORD, 4000), Opcode.SUB_UW, Value(DataType.UWORD, 123), Value(DataType.UWORD, 4000 - 123))
        testBinaryOperator(Value(DataType.FLOAT, 123.44), Opcode.SUB_F, Value(DataType.FLOAT, 23.44), Value(DataType.FLOAT, 100.0))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.UWORD, 4000 - 40), Opcode.SUB_UW, Value(DataType.FLOAT, 42.25), Value(DataType.FLOAT, 42.25 - (4000 - 40)))
        }
    }

    @Test
    fun testMul() {
        testBinaryOperator(Value(DataType.UBYTE, 41), Opcode.MUL_UB, Value(DataType.UBYTE, 4), Value(DataType.UBYTE, 164))
        testBinaryOperator(Value(DataType.UWORD, 401), Opcode.MUL_UW, Value(DataType.UWORD, 4), Value(DataType.UWORD, 401 * 4))
        testBinaryOperator(Value(DataType.FLOAT, 40.1), Opcode.MUL_F, Value(DataType.FLOAT, 2.4), Value(DataType.FLOAT, 96.24))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.UWORD, 401 * 4), Opcode.MUL_UW, Value(DataType.FLOAT, 42.2533), Value(DataType.FLOAT, 42.2533 * (401 * 4)))
        }
    }

    @Test
    fun testDiv() {
        testBinaryOperator(Value(DataType.UBYTE, 250), Opcode.DIV_UB, Value(DataType.UBYTE, 12), Value(DataType.UBYTE, 20))
        testBinaryOperator(Value(DataType.UWORD, 3999), Opcode.DIV_UW, Value(DataType.UWORD, 40), Value(DataType.UWORD, 99))
        testBinaryOperator(Value(DataType.FLOAT, 42.25), Opcode.DIV_F, Value(DataType.FLOAT, 99.0), Value(DataType.FLOAT, 42.25 / 99.0))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.UWORD, 3333), Opcode.DIV_UW, Value(DataType.FLOAT, 2.22), Value(DataType.FLOAT, 3333 / 2.22))
        }
    }

    @Test
    fun testFloorDiv() {
        testBinaryOperator(Value(DataType.UBYTE, 244), Opcode.FLOORDIV_UB, Value(DataType.UBYTE, 33), Value(DataType.UBYTE, 7))
        testBinaryOperator(Value(DataType.UWORD, 3999), Opcode.FLOORDIV_UW, Value(DataType.UWORD, 99), Value(DataType.UWORD, 40))
        testBinaryOperator(Value(DataType.FLOAT, 4000.25), Opcode.FLOORDIV_F, Value(DataType.FLOAT, 40.2), Value(DataType.FLOAT, 99.0))
    }

    @Test
    fun testPow() {
        testBinaryOperator(Value(DataType.UBYTE, 3), Opcode.POW_UB, Value(DataType.UBYTE, 4), Value(DataType.UBYTE, 81))
        testBinaryOperator(Value(DataType.UWORD, 3), Opcode.POW_UW, Value(DataType.UWORD, 4), Value(DataType.UWORD, 81))
        testBinaryOperator(Value(DataType.FLOAT, 1.1), Opcode.POW_F, Value(DataType.FLOAT, 81.0), Value(DataType.FLOAT, 2253.2402360440274))
    }

    @Test
    fun testRemainder() {
        testBinaryOperator(Value(DataType.UBYTE, 250), Opcode.REMAINDER_UB, Value(DataType.UBYTE, 29), Value(DataType.UBYTE, 18))
        testBinaryOperator(Value(DataType.UWORD, 500), Opcode.REMAINDER_UW, Value(DataType.UWORD, 29), Value(DataType.UWORD, 7))
        testBinaryOperator(Value(DataType.FLOAT, 2022.5), Opcode.REMAINDER_F, Value(DataType.FLOAT, 7.0), Value(DataType.FLOAT, 6.5))
    }

    @Test
    fun testBitand() {
        testBinaryOperator(Value(DataType.UBYTE, 0b10011111), Opcode.BITAND_BYTE, Value(DataType.UBYTE, 0b11111101), Value(DataType.UBYTE, 0b10011101))
        testBinaryOperator(Value(DataType.UWORD, 0b0011001011110001), Opcode.BITAND_WORD, Value(DataType.UWORD, 0b1110000010011101), Value(DataType.UWORD, 0b0010000010010001))
    }

    @Test
    fun testBitor() {
        testBinaryOperator(Value(DataType.UBYTE, 0b00011101), Opcode.BITOR_BYTE, Value(DataType.UBYTE, 0b10010001), Value(DataType.UBYTE, 0b10011101))
        testBinaryOperator(Value(DataType.UWORD, 0b0011001011100000), Opcode.BITOR_WORD, Value(DataType.UWORD, 0b1000000010011101), Value(DataType.UWORD, 0b1011001011111101))
    }

    @Test
    fun testBitxor() {
        testBinaryOperator(Value(DataType.UBYTE, 0b00011101), Opcode.BITXOR_BYTE, Value(DataType.UBYTE, 0b10010001), Value(DataType.UBYTE, 0b10001100))
        testBinaryOperator(Value(DataType.UWORD, 0b0011001011100000), Opcode.BITXOR_WORD, Value(DataType.UWORD, 0b1000000010001100), Value(DataType.UWORD, 0b1011001001101100))
    }

    @Test
    fun testAnd() {
        testBinaryOperator(Value(DataType.UBYTE, 200), Opcode.AND_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 200), Opcode.AND_BYTE, Value(DataType.UBYTE, 0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.AND_BYTE, Value(DataType.UBYTE, 101), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 200), Opcode.AND_WORD, Value(DataType.UWORD, 13455), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 200), Opcode.AND_WORD, Value(DataType.UWORD, 0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 0), Opcode.AND_WORD, Value(DataType.UWORD, 101), Value(DataType.UBYTE, 0))
    }

    @Test
    fun testOr() {
        testBinaryOperator(Value(DataType.UBYTE, 200), Opcode.OR_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 200), Opcode.OR_BYTE, Value(DataType.UBYTE, 0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.OR_BYTE, Value(DataType.UBYTE, 0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 200), Opcode.OR_WORD, Value(DataType.UWORD, 13455), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 200), Opcode.OR_WORD, Value(DataType.UWORD, 0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 0), Opcode.OR_WORD, Value(DataType.UWORD, 0), Value(DataType.UBYTE, 0))
    }

    @Test
    fun testXor() {
        testBinaryOperator(Value(DataType.UBYTE, 200), Opcode.XOR_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UBYTE, 200), Opcode.XOR_BYTE, Value(DataType.UBYTE, 0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.XOR_BYTE, Value(DataType.UBYTE, 0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 200), Opcode.XOR_WORD, Value(DataType.UWORD, 13455), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 200), Opcode.XOR_WORD, Value(DataType.UWORD, 0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 0), Opcode.XOR_WORD, Value(DataType.UWORD, 0), Value(DataType.UBYTE, 0))
    }

    @Test
    fun testNot() {
        testUnaryOperator(Value(DataType.UBYTE, 0), Opcode.NOT_BYTE, Value(DataType.UBYTE, 1))
        testUnaryOperator(Value(DataType.UBYTE, 20), Opcode.NOT_BYTE, Value(DataType.UBYTE, 0))
        testUnaryOperator(Value(DataType.UWORD, 0), Opcode.NOT_WORD, Value(DataType.UBYTE, 1))
        testUnaryOperator(Value(DataType.UWORD, 5000), Opcode.NOT_WORD, Value(DataType.UBYTE, 0))
    }

    @Test
    fun testNeg() {
        testUnaryOperator(Value(DataType.BYTE, 12), Opcode.NEG_B, Value(DataType.BYTE, -12))
        testUnaryOperator(Value(DataType.WORD, 1234), Opcode.NEG_W, Value(DataType.WORD, -1234))
        testUnaryOperator(Value(DataType.FLOAT, 123.456), Opcode.NEG_F, Value(DataType.FLOAT, -123.456))
        assertFailsWith<VmExecutionException> {
            testUnaryOperator(Value(DataType.UBYTE, 12), Opcode.NEG_B, Value(DataType.UBYTE, 244))
        }
        assertFailsWith<VmExecutionException> {
            testUnaryOperator(Value(DataType.UWORD, 1234), Opcode.NEG_W, Value(DataType.UWORD, 64302))
        }
    }

    @Test
    fun testInv() {
        testUnaryOperator(Value(DataType.UBYTE, 123), Opcode.INV_BYTE, Value(DataType.UBYTE, 0x84))
        testUnaryOperator(Value(DataType.UWORD, 4044), Opcode.INV_WORD, Value(DataType.UWORD, 0xf033))
        assertFailsWith<VmExecutionException> {
            testUnaryOperator(Value(DataType.BYTE, 123), Opcode.INV_BYTE, Value(DataType.BYTE, 0x84))
        }
        assertFailsWith<VmExecutionException> {
            testUnaryOperator(Value(DataType.WORD, 4044), Opcode.INV_WORD, Value(DataType.WORD, 0xf033))
        }
    }

    @Test
    fun testLsb() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0x45)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0xea31)),
                Instruction(Opcode.LSB),
                Instruction(Opcode.LSB)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.UBYTE, 0x31), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testMsb() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0x45)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0xea31)),
                Instruction(Opcode.MSB),
                Instruction(Opcode.MSB)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.UBYTE, 0xea), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testB2Ub() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.BYTE, -88)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.BYTE, 127)),
                Instruction(Opcode.B2UB),
                Instruction(Opcode.B2UB)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.UBYTE, 127), vm.evalstack.pop())
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 168), vm.evalstack.pop())
    }

    @Test
    fun testUB2b() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 168)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 127)),
                Instruction(Opcode.UB2B),
                Instruction(Opcode.UB2B)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.BYTE, 127), vm.evalstack.pop())
        vm.step(1)
        assertEquals(Value(DataType.BYTE, -88), vm.evalstack.pop())
    }

    @Test
    fun testB2Word() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.WORD, 0x7a31)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.BYTE, 127)),
                Instruction(Opcode.B2WORD),
                Instruction(Opcode.B2WORD)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 127), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testUB2Uword() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0xea31)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0x45)),
                Instruction(Opcode.UB2UWORD),
                Instruction(Opcode.UB2UWORD)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 0x0045), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testMSB2Word() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0xea31)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0x45)),
                Instruction(Opcode.MSB2WORD),
                Instruction(Opcode.MSB2WORD)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 0x4500), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testB2Float() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.WORD, 0x7a31)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.BYTE, 127)),
                Instruction(Opcode.B2FLOAT),
                Instruction(Opcode.B2FLOAT)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.FLOAT, 127.0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testUB2Float() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0xea31)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 177)),
                Instruction(Opcode.UB2FLOAT),
                Instruction(Opcode.UB2FLOAT)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.FLOAT, 177.0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testW2Float() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 177)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 52345)),
                Instruction(Opcode.W2FLOAT),
                Instruction(Opcode.W2FLOAT)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.FLOAT, 52345.0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testUW2Float() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 177)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 52345)),
                Instruction(Opcode.UW2FLOAT),
                Instruction(Opcode.UW2FLOAT)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.FLOAT, 52345.0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testIncVar() {
        val ins = mutableListOf(
                Instruction(Opcode.INC_VAR_UW, callLabel = "var1"),
                Instruction(Opcode.INC_VAR_UB, callLabel = "var2"),
                Instruction(Opcode.INC_VAR_F, callLabel = "var3"),
                Instruction(Opcode.INC_VAR_UW, callLabel = "var1"),
                Instruction(Opcode.INC_VAR_UB, callLabel = "var2"),
                Instruction(Opcode.INC_VAR_F, callLabel = "var3")
                )
        val vars = mapOf("var1" to Value(DataType.UWORD, 65534),
                "var2" to Value(DataType.UBYTE, 254),
                "var3" to Value(DataType.FLOAT, -1.5)
                )
        vm.load(makeProg(ins, vars = vars), null)
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 65535), vm.variables["var1"])
        assertEquals(Value(DataType.UBYTE, 255), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, -0.5), vm.variables["var3"])
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 0), vm.variables["var1"])
        assertEquals(Value(DataType.UBYTE, 0), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, 0.5), vm.variables["var3"])
    }

    @Test
    fun testDecVar() {
        val ins = mutableListOf(
                Instruction(Opcode.DEC_VAR_UW, callLabel = "var1"),
                Instruction(Opcode.DEC_VAR_UB, callLabel = "var2"),
                Instruction(Opcode.DEC_VAR_F, callLabel = "var3"),
                Instruction(Opcode.DEC_VAR_UW, callLabel = "var1"),
                Instruction(Opcode.DEC_VAR_UB, callLabel = "var2"),
                Instruction(Opcode.DEC_VAR_F, callLabel = "var3")
        )
        val vars = mapOf("var1" to Value(DataType.UWORD, 1),
                "var2" to Value(DataType.UBYTE, 1),
                "var3" to Value(DataType.FLOAT, 1.5)
                )
        vm.load(makeProg(ins, vars = vars), null)
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 0), vm.variables["var1"])
        assertEquals(Value(DataType.UBYTE, 0), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, 0.5), vm.variables["var3"])
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 65535), vm.variables["var1"])
        assertEquals(Value(DataType.UBYTE, 255), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, -0.5), vm.variables["var3"])
    }

    @Test
    fun testSyscall() {
        val ins = mutableListOf(
                Instruction(Opcode.SYSCALL, Value(DataType.UBYTE, Syscall.FUNC_RNDF.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.UBYTE, Syscall.FUNC_RNDW.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.UBYTE, Syscall.FUNC_RND.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.UBYTE, Syscall.FUNC_RND.callNr)),

                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 25544)),
                Instruction(Opcode.SYSCALL, Value(DataType.UBYTE, Syscall.FUNC_SIN.callNr))
        )
        vm.load(makeProg(ins), null)
        vm.step(4)

        val rndb1 = vm.evalstack.pop()
        val rndb2 = vm.evalstack.pop()
        val rndw = vm.evalstack.pop()
        val rndf = vm.evalstack.pop()
        assertEquals(DataType.UBYTE, rndb1.type)
        assertEquals(DataType.UBYTE, rndb2.type)
        assertEquals(DataType.UWORD, rndw.type)
        assertEquals(DataType.FLOAT, rndf.type)
        assertNotEquals(rndb1.integerValue(), rndb2.integerValue())
        assertTrue(rndf.numericValue().toDouble() > 0.0 && rndf.numericValue().toDouble() < 1.0)

        vm.step(2)
        assertEquals(Value(DataType.FLOAT, 0.28582414234140724), vm.evalstack.pop())
    }

    @Test
    fun testLess() {
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.LESS_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.LESS_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.LESS_B, Value(DataType.BYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, -2), Opcode.LESS_B, Value(DataType.BYTE, 1), Value(DataType.UBYTE, 1))

        testBinaryOperator(Value(DataType.UWORD, 2), Opcode.LESS_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 20), Opcode.LESS_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.LESS_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.LESS_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.LESS_W, Value(DataType.WORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.WORD, -2), Opcode.LESS_W, Value(DataType.WORD, 1), Value(DataType.UBYTE, 1))

        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESS_F, Value(DataType.FLOAT, 21.0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESS_F, Value(DataType.FLOAT, 21.001), Value(DataType.UBYTE, 1))
    }

    @Test
    fun testLessEq() {
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.LESSEQ_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 1), Opcode.LESSEQ_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.LESSEQ_B, Value(DataType.BYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, -2), Opcode.LESSEQ_B, Value(DataType.BYTE, 1), Value(DataType.UBYTE, 1))

        testBinaryOperator(Value(DataType.UWORD, 2), Opcode.LESSEQ_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 20), Opcode.LESSEQ_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.LESSEQ_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.LESSEQ_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.LESSEQ_W, Value(DataType.WORD, 20), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.WORD, -2), Opcode.LESSEQ_W, Value(DataType.WORD, 1), Value(DataType.UBYTE, 1))

        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESSEQ_F, Value(DataType.FLOAT, 21.0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESSEQ_F, Value(DataType.FLOAT, 20.999), Value(DataType.UBYTE, 0))
    }

    @Test
    fun testGreater() {
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.GREATER_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UBYTE, 1), Opcode.GREATER_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.GREATER_B, Value(DataType.BYTE, -1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, -1), Opcode.GREATER_B, Value(DataType.BYTE, 1), Value(DataType.UBYTE, 0))

        testBinaryOperator(Value(DataType.UWORD, 2), Opcode.GREATER_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 20), Opcode.GREATER_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.GREATER_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.GREATER_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.GREATER_W, Value(DataType.WORD, -21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.WORD, -2), Opcode.GREATER_W, Value(DataType.WORD, 21), Value(DataType.UBYTE, 0))

        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATER_F, Value(DataType.FLOAT, 21.0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATER_F, Value(DataType.FLOAT, 20.999), Value(DataType.UBYTE, 1))
    }

    @Test
    fun testGreaterEq() {
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.GREATEREQ_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UBYTE, 1), Opcode.GREATEREQ_UB, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.GREATEREQ_B, Value(DataType.BYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, -11), Opcode.GREATEREQ_B, Value(DataType.BYTE, 11), Value(DataType.UBYTE, 0))

        testBinaryOperator(Value(DataType.UWORD, 2), Opcode.GREATEREQ_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 20), Opcode.GREATEREQ_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.GREATEREQ_UW, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.GREATEREQ_UW, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.GREATEREQ_W, Value(DataType.WORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.WORD, -21), Opcode.GREATEREQ_W, Value(DataType.WORD, 21), Value(DataType.UBYTE, 0))

        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATEREQ_F, Value(DataType.FLOAT, 21.0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATEREQ_F, Value(DataType.FLOAT, 21.001), Value(DataType.UBYTE, 0))
    }

    @Test
    fun testEqual() {
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.EQUAL_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UBYTE, 1), Opcode.EQUAL_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 2), Opcode.EQUAL_WORD, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 20), Opcode.EQUAL_WORD, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.EQUAL_WORD, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.EQUAL_WORD, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.EQUAL_F, Value(DataType.FLOAT, 21.0), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.EQUAL_F, Value(DataType.FLOAT, 21.001), Value(DataType.UBYTE, 0))
    }

    @Test
    fun testNotEqual() {
        testBinaryOperator(Value(DataType.UBYTE, 0), Opcode.NOTEQUAL_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UBYTE, 1), Opcode.NOTEQUAL_BYTE, Value(DataType.UBYTE, 1), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.UWORD, 2), Opcode.NOTEQUAL_WORD, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 20), Opcode.NOTEQUAL_WORD, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.NOTEQUAL_WORD, Value(DataType.UWORD, 20), Value(DataType.UBYTE, 1))
        testBinaryOperator(Value(DataType.UWORD, 21), Opcode.NOTEQUAL_WORD, Value(DataType.UWORD, 21), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.NOTEQUAL_F, Value(DataType.FLOAT, 21.0), Value(DataType.UBYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.NOTEQUAL_F, Value(DataType.FLOAT, 21.001), Value(DataType.UBYTE, 1))
    }

    @Test
    fun testBCC() {
        val ins = mutableListOf(
                Instruction(Opcode.SEC),
                Instruction(Opcode.BCC, callLabel = "label"),
                Instruction(Opcode.CLC),
                Instruction(Opcode.BCC, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(2)
        assertEquals("", vm.sourceLine)
        vm.step(3)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testBCS() {
        val ins = mutableListOf(
                Instruction(Opcode.CLC),
                Instruction(Opcode.BCS, callLabel = "label"),
                Instruction(Opcode.SEC),
                Instruction(Opcode.BCS, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        assertFalse(vm.P_carry)
        vm.step(2)
        assertEquals("", vm.sourceLine)
        vm.step(3)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testBZ() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 1)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BZ, callLabel = "label"),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BZ, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(3)
        assertEquals("", vm.sourceLine)
        vm.step(4)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testBNZ() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BNZ, callLabel = "label"),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 1)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BNZ, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(3)
        assertEquals("", vm.sourceLine)
        vm.step(4)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testBNEG() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 1)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, -99)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(3)
        assertEquals("", vm.sourceLine)
        vm.step(3)
        assertEquals("", vm.sourceLine)
        vm.step(4)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testBPOS() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, -99)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BPOS, callLabel = "label"),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0)),
                Instruction(Opcode.TEST),
                Instruction(Opcode.BPOS, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(3)
        assertEquals("", vm.sourceLine)
        vm.step(4)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testJump() {
        val ins = mutableListOf(
                Instruction(Opcode.JUMP, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(2)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testReturn() {
        // @todo this only tests return with zero return values for now.
        val ins = mutableListOf(
                Instruction(Opcode.RETURN),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string1")
        )
        vm.load(makeProg(ins), null)
        assertFailsWith<VmTerminationException> {
            vm.step(1)
        }

        vm.callstack.add(ins[2])        // set the LINE opcode as return instruction
        assertEquals("", vm.sourceLine)
        vm.step(2)
        assertEquals("string1", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testCall() {
        // @todo this only tests call with zero parameters for now.
        val ins = mutableListOf(
                Instruction(Opcode.CALL, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "returned"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "called"),
                Instruction(Opcode.RETURN)
        )
        val labels = mapOf("label" to ins[3])   // points to the LINE instruction
        vm.load(makeProg(ins, labels = labels), null)
        vm.step(1)
        assertEquals("", vm.sourceLine)
        assertEquals(1, vm.callstack.size)
        assertSame(ins[1], vm.callstack.peek())
        vm.step(1)
        assertEquals("called", vm.sourceLine)
        vm.step(1)
        assertEquals(0, vm.callstack.size)
        assertEquals("called", vm.sourceLine)
        vm.step(1)
        assertEquals("returned", vm.sourceLine)
    }

    @Test
    fun testSHR() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 9.99)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 3)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 61005)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 3)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 249)),
                Instruction(Opcode.SHR_BYTE),        // 124
                Instruction(Opcode.DISCARD_BYTE),
                Instruction(Opcode.SHR_BYTE),        // 1
                Instruction(Opcode.SHR_BYTE),        // 0
                Instruction(Opcode.SHR_BYTE),        // 0
                Instruction(Opcode.DISCARD_BYTE),
                Instruction(Opcode.SHR_WORD),        // 30502
                Instruction(Opcode.DISCARD_WORD),
                Instruction(Opcode.SHR_WORD),        // 1
                Instruction(Opcode.SHR_WORD),        // 0
                Instruction(Opcode.SHR_WORD),        // 0
                Instruction(Opcode.DISCARD_WORD),
                Instruction(Opcode.SHR_BYTE)         // error on float
        )
        vm.load(makeProg(ins), null)
        vm.step(6)
        assertEquals(Value(DataType.UBYTE, 124), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 1), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 0), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 30502), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 1), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 0), vm.evalstack.peek())
        vm.step(1)
        assertEquals(Value(DataType.FLOAT, 9.99), vm.evalstack.peek())
        assertFailsWith<VmExecutionException> {
            vm.step(1)      // float shift error
        }
    }

    @Test
    fun testSHL() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_FLOAT, Value(DataType.FLOAT, 9.99)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 3)),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 61005)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 3)),
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 249)),
                Instruction(Opcode.SHL_BYTE),        // 242
                Instruction(Opcode.DISCARD_BYTE),
                Instruction(Opcode.SHL_BYTE),        // 6
                Instruction(Opcode.DISCARD_BYTE),
                Instruction(Opcode.SHL_WORD),        // 56474
                Instruction(Opcode.DISCARD_WORD),
                Instruction(Opcode.SHL_WORD),        // 6
                Instruction(Opcode.DISCARD_WORD),
                Instruction(Opcode.SHL_WORD)         // error on float
        )
        vm.load(makeProg(ins), null)
        vm.step(6)
        assertEquals(Value(DataType.UBYTE, 242), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 6), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 56474), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 6), vm.evalstack.peek())
        vm.step(1)
        assertEquals(Value(DataType.FLOAT, 9.99), vm.evalstack.peek())
        assertFailsWith<VmExecutionException> {
            vm.step(1)      // float shift error
        }
    }

    @Test
    fun testROR() {
        // 9/17-bit rotation right (using carry)
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0b10010011)),
                Instruction(Opcode.ROR_BYTE),        // 0b01001001   c=1
                Instruction(Opcode.ROR_BYTE),        // 0b10100100   c=1
                Instruction(Opcode.ROR_BYTE),        // 0b11010010   c=0
                Instruction(Opcode.ROR_BYTE),        // 0b01101001   c=0
                Instruction(Opcode.ROR_BYTE),        // 0b00110100   c=1
                Instruction(Opcode.ROR_BYTE),        // 0b10011010   c=0
                Instruction(Opcode.ROR_BYTE),        // 0b01001101   c=0
                Instruction(Opcode.ROR_BYTE),        // 0b00100110   c=1
                Instruction(Opcode.ROR_BYTE)         // 0b10010011   c=0  (original value after 9 rors)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 0b01001001), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b10100100), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b11010010), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b01101001), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(5)
        assertEquals(Value(DataType.UBYTE, 0b10010011), vm.evalstack.peek())
        assertFalse(vm.P_carry)

        val ins2 = mutableListOf(
                Instruction(Opcode.CLC),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0b1001001100001101)),
                Instruction(Opcode.ROR_WORD),        // 0b0100100110000110   c=1
                Instruction(Opcode.ROR_WORD),        // 0b1010010011000011   c=0
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD),
                Instruction(Opcode.ROR_WORD)         // 0b1001001100001101   c=0  (original value after 17 rors)
        )
        vm.load(makeProg(ins2), null)
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 0b0100100110000110), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UWORD, 0b1010010011000011), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(15)
        assertEquals(Value(DataType.UWORD, 0b1001001100001101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
    }

    @Test
    fun testROL() {
        // 9/17-bit rotation left (using carry)
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0b10010011)),
                Instruction(Opcode.ROL_BYTE),        // 0b00100110   c=1
                Instruction(Opcode.ROL_BYTE),        // 0b01001101   c=0
                Instruction(Opcode.ROL_BYTE),        // 0b10011010   c=0
                Instruction(Opcode.ROL_BYTE),        // 0b00110100   c=1
                Instruction(Opcode.ROL_BYTE),        // 0b01101001   c=0
                Instruction(Opcode.ROL_BYTE),        // 0b11010010   c=0
                Instruction(Opcode.ROL_BYTE),        // 0b10100100   c=1
                Instruction(Opcode.ROL_BYTE),        // 0b01001001   c=1
                Instruction(Opcode.ROL_BYTE)         // 0b10010011   c=0  (original value after 9 rors)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 0b00100110), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b01001101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b10011010), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b00110100), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(5)
        assertEquals(Value(DataType.UBYTE, 0b10010011), vm.evalstack.peek())
        assertFalse(vm.P_carry)

        val ins2 = mutableListOf(
                Instruction(Opcode.CLC),
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0b1001001100001101)),
                Instruction(Opcode.ROL_WORD),        // 0b0010011000011010   c=1
                Instruction(Opcode.ROL_WORD),        // 0b0100110000110101   c=0
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD),
                Instruction(Opcode.ROL_WORD)         // 0b1001001100001101   c=0  (original value after 17 rors)
        )
        vm.load(makeProg(ins2), null)
        vm.step(3)
        assertEquals(Value(DataType.UWORD, 0b0010011000011010), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UWORD, 0b0100110000110101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(15)
        assertEquals(Value(DataType.UWORD, 0b1001001100001101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
    }

    @Test
    fun testROR2() {
        // 8/16-bit rotation right
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0b10010011)),
                Instruction(Opcode.ROR2_BYTE),        // 0b11001001
                Instruction(Opcode.ROR2_BYTE),        // 0b11100100
                Instruction(Opcode.ROR2_BYTE),        // 0b01110010
                Instruction(Opcode.ROR2_BYTE),
                Instruction(Opcode.ROR2_BYTE),
                Instruction(Opcode.ROR2_BYTE),
                Instruction(Opcode.ROR2_BYTE),
                Instruction(Opcode.ROR2_BYTE)         // 0b10010011  (original value after 8 rors)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 0b11001001), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b11100100), vm.evalstack.peek())
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b01110010), vm.evalstack.peek())
        vm.step(5)
        assertEquals(Value(DataType.UBYTE, 0b10010011), vm.evalstack.peek())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0b1001001100001101)),
                Instruction(Opcode.ROR2_WORD),        // 0b1100100110000110
                Instruction(Opcode.ROR2_WORD),        // 0b0110010011000011
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD),
                Instruction(Opcode.ROR2_WORD)         // 0b1001001100001101  (original value after 16 rors)
        )
        vm.load(makeProg(ins2), null)
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 0b1100100110000110), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UWORD, 0b0110010011000011), vm.evalstack.peek())
        vm.step(14)
        assertEquals(Value(DataType.UWORD, 0b1001001100001101), vm.evalstack.peek())
    }

    @Test
    fun testROL2() {
        // 8/16-bit rotation left
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_BYTE, Value(DataType.UBYTE, 0b10010011)),
                Instruction(Opcode.ROL2_BYTE),        // 0b00100111
                Instruction(Opcode.ROL2_BYTE),        // 0b01001110
                Instruction(Opcode.ROL2_BYTE),
                Instruction(Opcode.ROL2_BYTE),
                Instruction(Opcode.ROL2_BYTE),
                Instruction(Opcode.ROL2_BYTE),
                Instruction(Opcode.ROL2_BYTE),
                Instruction(Opcode.ROL2_BYTE)         // 0b10010011 (original value after 8 rols)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.UBYTE, 0b00100111), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UBYTE, 0b01001110), vm.evalstack.peek())
        vm.step(6)
        assertEquals(Value(DataType.UBYTE, 0b10010011), vm.evalstack.peek())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH_WORD, Value(DataType.UWORD, 0b1001001100001101)),
                Instruction(Opcode.ROL2_WORD),        // 0b0010011000011011
                Instruction(Opcode.ROL2_WORD),        // 0b0100110000110110
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD),
                Instruction(Opcode.ROL2_WORD)         // 0b1001001100001101  (original value after 16 rols)
        )
        vm.load(makeProg(ins2), null)
        vm.step(2)
        assertEquals(Value(DataType.UWORD, 0b0010011000011011), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.UWORD, 0b0100110000110110), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(14)
        assertEquals(Value(DataType.UWORD, 0b1001001100001101), vm.evalstack.peek())
    }

    private fun pushOpcode(dt: DataType): Opcode {
        return when (dt) {
            DataType.UBYTE, DataType.BYTE -> Opcode.PUSH_BYTE
            DataType.UWORD, DataType.WORD -> Opcode.PUSH_WORD
            DataType.FLOAT -> Opcode.PUSH_FLOAT
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY_UB, DataType.ARRAY_UW, DataType.ARRAY_F, DataType.MATRIX_UB,
            DataType.ARRAY_B, DataType.ARRAY_W, DataType.MATRIX_B -> Opcode.PUSH_WORD
        }
    }

    private fun testBinaryOperator(left: Value, operator: Opcode, right: Value, result: Value) {
        val program=makeProg(mutableListOf(
                Instruction(pushOpcode(left.type), left),
                Instruction(pushOpcode(right.type), right),
                Instruction(operator)
        ))
        vm.load(program, null)
        vm.step(3)
        assertEquals(1, vm.evalstack.size)
        assertEquals(result, vm.evalstack.pop())
    }

    private fun testUnaryOperator(value: Value, operator: Opcode, result: Value) {
        val program=makeProg(mutableListOf(
                Instruction(pushOpcode(value.type), value),
                Instruction(operator)
        ))
        vm.load(program, null)
        vm.step(2)
        assertEquals(1, vm.evalstack.size)
        assertEquals(result, vm.evalstack.pop())
    }

}
