package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.DataType
import prog8.compiler.HeapValues
import prog8.stackvm.*
import kotlin.test.*


/***

@todo opcodes still to be unit-tested:

    SHL_MEM,
    SHL_MEM_W,
    SHL_VAR,
    SHL_VAR_W,
    SHR_MEM,
    SHR_MEM_W,
    SHR_VAR,
    SHR_VAR_W,
    ROL_MEM,
    ROL_MEM_W,
    ROL_VAR,
    ROL_VAR_W,
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

        val blockvars = mutableMapOf<String, MutableMap<String, Value>>()
        if(vars!=null) {
            for (blockvar in vars) {
                val blockname = blockvar.key.substringBefore('.')
                val variables = blockvars[blockname] ?: mutableMapOf()
                blockvars[blockname] = variables
                variables[blockvar.key] = blockvar.value
            }
        }
        val heap = HeapValues()
        return Program("test", ins, labels ?: mapOf(), blockvars, mem ?: mapOf(), heap)
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
        val ins = mutableListOf(Instruction(Opcode.PUSH, Value(DataType.WORD, 4299)))
        vm.load(makeProg(ins), null)
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testPush() {
        val ins = mutableListOf(Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 42.999)))
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
    }

    @Test
    fun testPushMem() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_MEM, Value(DataType.WORD, 0x2000)),
                Instruction(Opcode.PUSH_MEM_W, Value(DataType.WORD, 0x3000)),
                Instruction(Opcode.PUSH_MEM_F, Value(DataType.WORD, 0x4000))
        )
        val mem=mapOf(0x2000 to listOf(Value(DataType.WORD, 0x42ea)),
                0x3000 to listOf(Value(DataType.WORD, 0x42ea)),
                0x4000 to listOf(Value(DataType.FLOAT, 42.25)))
        vm.load(makeProg(ins, mem=mem), null)
        assertEquals(0xea, vm.mem.getByte(0x2000))
        assertEquals(0x42, vm.mem.getByte(0x2001))
        assertEquals(0xea, vm.mem.getByte(0x3000))
        assertEquals(0x42, vm.mem.getByte(0x3001))
        assertEquals(0x42ea, vm.mem.getWord(0x2000))
        assertEquals(0x42ea, vm.mem.getWord(0x3000))
        assertEquals(42.25, vm.mem.getFloat(0x4000))
        assertThat(vm.evalstack, empty())
        vm.step(3)
        assertEquals(3, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.25), vm.evalstack.pop())
        assertEquals(Value(DataType.WORD, 0x42ea), vm.evalstack.pop())
        assertEquals(Value(DataType.BYTE, 0xea), vm.evalstack.pop())
    }

    @Test
    fun testPushVar() {
        val ins = mutableListOf(Instruction(Opcode.PUSH_VAR_F, callLabel = "varname"))
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
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 42.999)),
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 3.1415)),
                Instruction(Opcode.DISCARD_F))
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
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 42.25)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0x42ea)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.POP_MEM, Value(DataType.WORD, 0x2000)),
                Instruction(Opcode.POP_MEM_W, Value(DataType.WORD, 0x3000)),
                Instruction(Opcode.POP_MEM_F, Value(DataType.WORD, 0x4000)))
        vm.load(makeProg(ins), null)
        assertEquals(0, vm.mem.getWord(0x2000))
        assertEquals(0, vm.mem.getWord(0x3000))
        assertEquals(0.0, vm.mem.getFloat(0x4000))
        assertThat(vm.evalstack, empty())
        vm.step(6)
        assertThat(vm.evalstack, empty())
        assertEquals(123, vm.mem.getByte(0x2000))
        assertEquals(0x42ea, vm.mem.getWord(0x3000))
        assertEquals(42.25, vm.mem.getFloat(0x4000))
    }


    @Test
    fun testPopVar() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 42.25)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0x42ea)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.POP_VAR, callLabel = "var1"),
                Instruction(Opcode.POP_VAR_W, callLabel = "var2"),
                Instruction(Opcode.POP_VAR_F, callLabel = "var3"))
        val vars = mapOf(
                "var1" to Value(DataType.BYTE, 0),
                "var2" to Value(DataType.WORD, 0),
                "var3" to Value(DataType.FLOAT, 0)
                )
        vm.load(makeProg(ins, vars), null)
        assertEquals(9, vm.variables.size)
        vm.step(6)
        assertEquals(Value(DataType.BYTE, 123), vm.variables["var1"])
        assertEquals(Value(DataType.WORD, 0x42ea), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, 42.25), vm.variables["var3"])

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0x42ea)),
                Instruction(Opcode.POP_VAR_W, callLabel = "var1"))
        val vars2 = mapOf(
                "var1" to Value(DataType.BYTE, 0)
        )
        vm.load(makeProg(ins2, vars2), null)
        assertEquals(7, vm.variables.size)
        assertFailsWith<VmExecutionException> {
            vm.step(2)
        }
    }

    @Test
    fun testAdd() {
        testBinaryOperator(Value(DataType.BYTE, 140), Opcode.ADD_B, Value(DataType.BYTE, 222), Value(DataType.BYTE, 106))
        testBinaryOperator(Value(DataType.BYTE, 40), Opcode.ADD_B, Value(DataType.BYTE, 122), Value(DataType.BYTE, 162))
        testBinaryOperator(Value(DataType.WORD, 4000), Opcode.ADD_W, Value(DataType.WORD, 40), Value(DataType.WORD, 4040))
        testBinaryOperator(Value(DataType.WORD, 24000), Opcode.ADD_W, Value(DataType.WORD, 55000), Value(DataType.WORD, 13464))
        testBinaryOperator(Value(DataType.FLOAT, 4000.0), Opcode.ADD_F, Value(DataType.FLOAT, 123.22), Value(DataType.FLOAT, 4123.22))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 4000 + 40), Opcode.ADD_W, Value(DataType.FLOAT, 42.25), Value(DataType.FLOAT, 42.25 + (4000 + 40)))
        }
    }

    @Test
    fun testSub() {
        testBinaryOperator(Value(DataType.BYTE, 250), Opcode.SUB_B, Value(DataType.BYTE, 70), Value(DataType.BYTE, 180))
        testBinaryOperator(Value(DataType.WORD, 4000), Opcode.SUB_W, Value(DataType.WORD, 123), Value(DataType.WORD, 4000-123))
        testBinaryOperator(Value(DataType.FLOAT, 123.44), Opcode.SUB_F, Value(DataType.FLOAT, 23.44), Value(DataType.FLOAT, 100.0))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 4000 - 40), Opcode.SUB_W, Value(DataType.FLOAT, 42.25), Value(DataType.FLOAT, 42.25 - (4000 - 40)))
        }
    }

    @Test
    fun testMul() {
        testBinaryOperator(Value(DataType.BYTE, 41), Opcode.MUL_B, Value(DataType.BYTE, 4), Value(DataType.BYTE, 164))
        testBinaryOperator(Value(DataType.WORD, 401), Opcode.MUL_W, Value(DataType.WORD, 4), Value(DataType.WORD, 401*4))
        testBinaryOperator(Value(DataType.FLOAT, 40.1), Opcode.MUL_F, Value(DataType.FLOAT, 2.4), Value(DataType.FLOAT, 96.24))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 401 * 4), Opcode.MUL_W, Value(DataType.FLOAT, 42.2533), Value(DataType.FLOAT, 42.2533 * (401 * 4)))
        }
    }

    @Test
    fun testDiv() {
        testBinaryOperator(Value(DataType.BYTE, 250), Opcode.DIV_B, Value(DataType.BYTE, 12), Value(DataType.BYTE, 20))
        testBinaryOperator(Value(DataType.WORD, 3999), Opcode.DIV_W, Value(DataType.WORD, 40), Value(DataType.WORD, 99))
        testBinaryOperator(Value(DataType.FLOAT, 42.25), Opcode.DIV_F, Value(DataType.FLOAT, 99.0), Value(DataType.FLOAT, 42.25/99.0))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 3333), Opcode.DIV_W, Value(DataType.FLOAT, 2.22), Value(DataType.FLOAT, 3333 / 2.22))
        }
    }

    @Test
    fun testFloorDiv() {
        testBinaryOperator(Value(DataType.BYTE, 244), Opcode.FLOORDIV_B, Value(DataType.BYTE, 33), Value(DataType.BYTE, 7))
        testBinaryOperator(Value(DataType.WORD, 3999), Opcode.FLOORDIV_W, Value(DataType.WORD, 99), Value(DataType.WORD, 40))
        testBinaryOperator(Value(DataType.FLOAT, 4000.25), Opcode.FLOORDIV_F, Value(DataType.FLOAT, 40.2), Value(DataType.FLOAT, 99.0))
    }

    @Test
    fun testPow() {
        testBinaryOperator(Value(DataType.BYTE, 3), Opcode.POW_B, Value(DataType.BYTE, 4), Value(DataType.BYTE, 81))
        testBinaryOperator(Value(DataType.WORD, 3), Opcode.POW_W, Value(DataType.WORD, 4), Value(DataType.WORD, 81))
        testBinaryOperator(Value(DataType.FLOAT, 1.1), Opcode.POW_F, Value(DataType.FLOAT, 81.0), Value(DataType.FLOAT, 2253.2402360440274))
    }

    @Test
    fun testRemainder() {
        testBinaryOperator(Value(DataType.BYTE, 250), Opcode.REMAINDER_B, Value(DataType.BYTE, 29), Value(DataType.BYTE, 18))
        testBinaryOperator(Value(DataType.WORD, 500), Opcode.REMAINDER_W, Value(DataType.WORD, 29), Value(DataType.WORD, 7))
        testBinaryOperator(Value(DataType.FLOAT, 2022.5), Opcode.REMAINDER_F, Value(DataType.FLOAT, 7.0), Value(DataType.FLOAT, 6.5))
    }

    @Test
    fun testBitand() {
        testBinaryOperator(Value(DataType.BYTE, 0b10011111), Opcode.BITAND, Value(DataType.BYTE, 0b11111101), Value(DataType.BYTE, 0b10011101))
        testBinaryOperator(Value(DataType.WORD, 0b0011001011110001), Opcode.BITAND_W, Value(DataType.WORD, 0b1110000010011101), Value(DataType.WORD, 0b0010000010010001))
    }

    @Test
    fun testBitor() {
        testBinaryOperator(Value(DataType.BYTE, 0b00011101), Opcode.BITOR, Value(DataType.BYTE, 0b10010001), Value(DataType.BYTE, 0b10011101))
        testBinaryOperator(Value(DataType.WORD, 0b0011001011100000), Opcode.BITOR_W, Value(DataType.WORD, 0b1000000010011101), Value(DataType.WORD, 0b1011001011111101))
    }

    @Test
    fun testBitxor() {
        testBinaryOperator(Value(DataType.BYTE, 0b00011101), Opcode.BITXOR, Value(DataType.BYTE, 0b10010001), Value(DataType.BYTE, 0b10001100))
        testBinaryOperator(Value(DataType.WORD, 0b0011001011100000), Opcode.BITXOR_W, Value(DataType.WORD, 0b1000000010001100), Value(DataType.WORD, 0b1011001001101100))
    }

    @Test
    fun testAnd() {
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.AND, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.AND, Value(DataType.BYTE, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.AND, Value(DataType.BYTE, 101), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.AND_W, Value(DataType.WORD, 13455), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.AND_W, Value(DataType.WORD, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 0), Opcode.AND_W, Value(DataType.WORD, 101), Value(DataType.BYTE, 0))
    }

    @Test
    fun testOr() {
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.OR, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.OR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.OR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.OR_W, Value(DataType.WORD, 13455), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.OR_W, Value(DataType.WORD, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 0), Opcode.OR_W, Value(DataType.WORD, 0), Value(DataType.BYTE, 0))
    }

    @Test
    fun testXor() {
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.XOR, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.XOR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.XOR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.XOR_W, Value(DataType.WORD, 13455), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.XOR_W, Value(DataType.WORD, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 0), Opcode.XOR_W, Value(DataType.WORD, 0), Value(DataType.BYTE, 0))
    }

    @Test
    fun testNot() {
        testUnaryOperator(Value(DataType.BYTE, 0), Opcode.NOT, Value(DataType.BYTE, 1))
        testUnaryOperator(Value(DataType.BYTE, 20), Opcode.NOT, Value(DataType.BYTE, 0))
        testUnaryOperator(Value(DataType.WORD, 0), Opcode.NOT_W, Value(DataType.BYTE, 1))
        testUnaryOperator(Value(DataType.WORD, 5000), Opcode.NOT_W, Value(DataType.BYTE, 0))
    }

    @Test
    fun testInc() {
        testUnaryOperator(Value(DataType.BYTE, 255), Opcode.INC, Value(DataType.BYTE, 0))
        testUnaryOperator(Value(DataType.BYTE, 99), Opcode.INC, Value(DataType.BYTE, 100))
        testUnaryOperator(Value(DataType.WORD, 65535), Opcode.INC_W, Value(DataType.WORD, 0))
        testUnaryOperator(Value(DataType.WORD, 999), Opcode.INC_W, Value(DataType.WORD, 1000))
        testUnaryOperator(Value(DataType.FLOAT, -1.0), Opcode.INC_F, Value(DataType.FLOAT, 0.0))
        testUnaryOperator(Value(DataType.FLOAT, 2022.5), Opcode.INC_F, Value(DataType.FLOAT, 2023.5))
    }

    @Test
    fun testDec() {
        testUnaryOperator(Value(DataType.BYTE, 100), Opcode.DEC, Value(DataType.BYTE, 99))
        testUnaryOperator(Value(DataType.BYTE, 0), Opcode.DEC, Value(DataType.BYTE, 255))
        testUnaryOperator(Value(DataType.WORD, 1000), Opcode.DEC_W, Value(DataType.WORD, 999))
        testUnaryOperator(Value(DataType.WORD, 0), Opcode.DEC_W, Value(DataType.WORD, 65535))
        testUnaryOperator(Value(DataType.FLOAT, 0.5), Opcode.DEC_F, Value(DataType.FLOAT, -0.5))
        testUnaryOperator(Value(DataType.FLOAT, 2022.5), Opcode.DEC_F, Value(DataType.FLOAT, 2021.5))
    }

    @Test
    fun testNeg() {
        testUnaryOperator(Value(DataType.BYTE, 12), Opcode.NEG_B, Value(DataType.BYTE, 244))
        testUnaryOperator(Value(DataType.WORD, 1234), Opcode.NEG_W, Value(DataType.WORD, 64302))
        testUnaryOperator(Value(DataType.FLOAT, 123.456), Opcode.NEG_F, Value(DataType.FLOAT, -123.456))
    }

    @Test
    fun testInv() {
        testUnaryOperator(Value(DataType.BYTE, 123), Opcode.INV, Value(DataType.BYTE, 0x84))
        testUnaryOperator(Value(DataType.WORD, 4044), Opcode.INV_W, Value(DataType.WORD, 0xf033))
    }

    @Test
    fun testLsb() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0x45)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.LSB),
                Instruction(Opcode.LSB)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.BYTE, 0x31), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testMsb() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0x45)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.MSB),
                Instruction(Opcode.MSB)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.BYTE, 0xea), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testB2Word() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0x45)),
                Instruction(Opcode.B2WORD),
                Instruction(Opcode.B2WORD)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 0x0045), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testMSB2Word() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0x45)),
                Instruction(Opcode.MSB2WORD),
                Instruction(Opcode.MSB2WORD)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 0x4500), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testB2Float() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.B2FLOAT),
                Instruction(Opcode.B2FLOAT)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.FLOAT, 123.0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testW2Float() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 11)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 12345)),
                Instruction(Opcode.W2FLOAT),
                Instruction(Opcode.W2FLOAT)
        )
        vm.load(makeProg(ins), null)
        vm.step(3)
        assertEquals(Value(DataType.FLOAT, 12345.0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testIncVar() {
        val ins = mutableListOf(
                Instruction(Opcode.INC_VAR_W, callLabel ="var1"),
                Instruction(Opcode.INC_VAR, callLabel ="var2"),
                Instruction(Opcode.INC_VAR_F, callLabel ="var3"),
                Instruction(Opcode.INC_VAR_W, callLabel ="var1"),
                Instruction(Opcode.INC_VAR, callLabel ="var2"),
                Instruction(Opcode.INC_VAR_F, callLabel ="var3")
                )
        val vars = mapOf("var1" to Value(DataType.WORD, 65534),
                "var2" to Value(DataType.BYTE, 254),
                "var3" to Value(DataType.FLOAT, -1.5)
                )
        vm.load(makeProg(ins, vars = vars), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 65535), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 255), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, -0.5), vm.variables["var3"])
        vm.step(3)
        assertEquals(Value(DataType.WORD, 0), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 0), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, 0.5), vm.variables["var3"])
    }

    @Test
    fun testDecVar() {
        val ins = mutableListOf(
                Instruction(Opcode.DEC_VAR_W, callLabel = "var1"),
                Instruction(Opcode.DEC_VAR, callLabel = "var2"),
                Instruction(Opcode.DEC_VAR_F, callLabel = "var3"),
                Instruction(Opcode.DEC_VAR_W, callLabel = "var1"),
                Instruction(Opcode.DEC_VAR, callLabel = "var2"),
                Instruction(Opcode.DEC_VAR_F, callLabel = "var3")
        )
        val vars = mapOf("var1" to Value(DataType.WORD,1),
                "var2" to Value(DataType.BYTE, 1),
                "var3" to Value(DataType.FLOAT, 1.5)
                )
        vm.load(makeProg(ins, vars = vars), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 0), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 0), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, 0.5), vm.variables["var3"])
        vm.step(3)
        assertEquals(Value(DataType.WORD, 65535), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 255), vm.variables["var2"])
        assertEquals(Value(DataType.FLOAT, -0.5), vm.variables["var3"])
    }

    @Test
    fun testSyscall() {
        val ins = mutableListOf(
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RNDF.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RNDW.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RND.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RND.callNr)),

                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 25544)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_SIN.callNr))
        )
        vm.load(makeProg(ins), null)
        vm.step(4)

        val rndb1 = vm.evalstack.pop()
        val rndb2 = vm.evalstack.pop()
        val rndw = vm.evalstack.pop()
        val rndf = vm.evalstack.pop()
        assertEquals(DataType.BYTE, rndb1.type)
        assertEquals(DataType.BYTE, rndb2.type)
        assertEquals(DataType.WORD, rndw.type)
        assertEquals(DataType.FLOAT, rndf.type)
        assertNotEquals(rndb1.integerValue(), rndb2.integerValue())
        assertTrue(rndf.numericValue().toDouble() > 0.0 && rndf.numericValue().toDouble() < 1.0)

        vm.step(2)
        assertEquals(Value(DataType.FLOAT, 0.28582414234140724), vm.evalstack.pop())
    }

    @Test
    fun testLess() {
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.LESS, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.LESS, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 2), Opcode.LESS_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 20), Opcode.LESS_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.LESS_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.LESS_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESS_F, Value(DataType.FLOAT, 21.0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESS_F, Value(DataType.FLOAT, 21.001), Value(DataType.BYTE, 1))
    }

    @Test
    fun testLessEq() {
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.LESSEQ, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.LESSEQ, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 2), Opcode.LESSEQ_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 20), Opcode.LESSEQ_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.LESSEQ_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.LESSEQ_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESSEQ_F, Value(DataType.FLOAT, 21.0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.LESSEQ_F, Value(DataType.FLOAT, 20.999), Value(DataType.BYTE, 0))
    }

    @Test
    fun testGreater() {
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.GREATER, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.GREATER, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 2), Opcode.GREATER_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 20), Opcode.GREATER_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.GREATER_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.GREATER_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATER_F, Value(DataType.FLOAT, 21.0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATER_F, Value(DataType.FLOAT, 20.999), Value(DataType.BYTE, 1))
    }

    @Test
    fun testGreaterEq() {
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.GREATEREQ, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.GREATEREQ, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 2), Opcode.GREATEREQ_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 20), Opcode.GREATEREQ_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.GREATEREQ_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.GREATEREQ_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATEREQ_F, Value(DataType.FLOAT, 21.0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.GREATEREQ_F, Value(DataType.FLOAT, 21.001), Value(DataType.BYTE, 0))
    }

    @Test
    fun testEqual() {
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.EQUAL, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.EQUAL, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 2), Opcode.EQUAL_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 20), Opcode.EQUAL_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.EQUAL_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.EQUAL_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.EQUAL_F, Value(DataType.FLOAT, 21.0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.EQUAL_F, Value(DataType.FLOAT, 21.001), Value(DataType.BYTE, 0))
    }

    @Test
    fun testNotEqual() {
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.NOTEQUAL, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 1), Opcode.NOTEQUAL, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 2), Opcode.NOTEQUAL_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 20), Opcode.NOTEQUAL_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.NOTEQUAL_W, Value(DataType.WORD, 20), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 21), Opcode.NOTEQUAL_W, Value(DataType.WORD, 21), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.NOTEQUAL_F, Value(DataType.FLOAT, 21.0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 21.0), Opcode.NOTEQUAL_F, Value(DataType.FLOAT, 21.001), Value(DataType.BYTE, 1))
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
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 1)),
                Instruction(Opcode.BZ, callLabel = "label"),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0)),
                Instruction(Opcode.BZ, callLabel = "label"),
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
    fun testBNZ() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0)),
                Instruction(Opcode.BNZ, callLabel = "label"),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 1)),
                Instruction(Opcode.BNZ, callLabel = "label"),
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
    fun testBNEG() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0)),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 1)),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, -99)),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.LINE, callLabel = "string1"),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, callLabel = "string2"))
        val labels = mapOf("label" to ins.last())   // points to the second LINE instruction
        vm.load(makeProg(ins, labels=labels), null)
        vm.step(2)
        assertEquals("", vm.sourceLine)
        vm.step(2)
        assertEquals("", vm.sourceLine)
        vm.step(3)
        assertEquals("string2", vm.sourceLine)
        assertEquals(0, vm.callstack.size)
        assertEquals(0, vm.evalstack.size)
    }

    @Test
    fun testBPOS() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, -99)),
                Instruction(Opcode.BPOS, callLabel = "label"),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0)),
                Instruction(Opcode.BPOS, callLabel = "label"),
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
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 9.99)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 3)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 61005)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 3)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 249)),
                Instruction(Opcode.SHR),        // 124
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHR),        // 1
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHR_W),        // 30502
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHR_W),        // 1
                Instruction(Opcode.SHR_W),        // 0
                Instruction(Opcode.SHR_W),        // 0
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHR)         // error on float
        )
        vm.load(makeProg(ins), null)
        vm.step(6)
        assertEquals(Value(DataType.BYTE, 124), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 1), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 0), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.WORD, 30502), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.WORD, 1), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.WORD, 0), vm.evalstack.peek())
        vm.step(1)
        assertEquals(Value(DataType.FLOAT, 9.99), vm.evalstack.peek())
        assertFailsWith<VmExecutionException> {
            vm.step(1)      // float shift error
        }
    }

    @Test
    fun testSHL() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH_F, Value(DataType.FLOAT, 9.99)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 3)),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 61005)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 3)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 249)),
                Instruction(Opcode.SHL),        // 242
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHL),        // 6
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHL_W),        // 56474
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHL_W),        // 6
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHL_W)         // error on float
        )
        vm.load(makeProg(ins), null)
        vm.step(6)
        assertEquals(Value(DataType.BYTE, 242), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 6), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.WORD, 56474), vm.evalstack.peek())
        vm.step(2)
        assertEquals(Value(DataType.WORD, 6), vm.evalstack.peek())
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
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0b10010011)),
                Instruction(Opcode.ROR),        // 0b01001001   c=1
                Instruction(Opcode.ROR),        // 0b10100100   c=1
                Instruction(Opcode.ROR),        // 0b11010010   c=0
                Instruction(Opcode.ROR),        // 0b01101001   c=0
                Instruction(Opcode.ROR),        // 0b00110100   c=1
                Instruction(Opcode.ROR),        // 0b10011010   c=0
                Instruction(Opcode.ROR),        // 0b01001101   c=0
                Instruction(Opcode.ROR),        // 0b00100110   c=1
                Instruction(Opcode.ROR)         // 0b10010011   c=0  (original value after 9 rors)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 0b01001001), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b10100100), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b11010010), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b01101001), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(5)
        assertEquals(Value(DataType.BYTE, 0b10010011), vm.evalstack.peek())
        assertFalse(vm.P_carry)

        val ins2 = mutableListOf(
                Instruction(Opcode.CLC),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0b1001001100001101)),
                Instruction(Opcode.ROR_W),        // 0b0100100110000110   c=1
                Instruction(Opcode.ROR_W),        // 0b1010010011000011   c=0
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W),
                Instruction(Opcode.ROR_W)         // 0b1001001100001101   c=0  (original value after 17 rors)
        )
        vm.load(makeProg(ins2), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 0b0100100110000110), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.WORD, 0b1010010011000011), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(15)
        assertEquals(Value(DataType.WORD, 0b1001001100001101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
    }

    @Test
    fun testROL() {
        // 9/17-bit rotation left (using carry)
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0b10010011)),
                Instruction(Opcode.ROL),        // 0b00100110   c=1
                Instruction(Opcode.ROL),        // 0b01001101   c=0
                Instruction(Opcode.ROL),        // 0b10011010   c=0
                Instruction(Opcode.ROL),        // 0b00110100   c=1
                Instruction(Opcode.ROL),        // 0b01101001   c=0
                Instruction(Opcode.ROL),        // 0b11010010   c=0
                Instruction(Opcode.ROL),        // 0b10100100   c=1
                Instruction(Opcode.ROL),        // 0b01001001   c=1
                Instruction(Opcode.ROL)         // 0b10010011   c=0  (original value after 9 rors)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 0b00100110), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b01001101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b10011010), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b00110100), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(5)
        assertEquals(Value(DataType.BYTE, 0b10010011), vm.evalstack.peek())
        assertFalse(vm.P_carry)

        val ins2 = mutableListOf(
                Instruction(Opcode.CLC),
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0b1001001100001101)),
                Instruction(Opcode.ROL_W),        // 0b0010011000011010   c=1
                Instruction(Opcode.ROL_W),        // 0b0100110000110101   c=0
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W),
                Instruction(Opcode.ROL_W)         // 0b1001001100001101   c=0  (original value after 17 rors)
        )
        vm.load(makeProg(ins2), null)
        vm.step(3)
        assertEquals(Value(DataType.WORD, 0b0010011000011010), vm.evalstack.peek())
        assertTrue(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.WORD, 0b0100110000110101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(15)
        assertEquals(Value(DataType.WORD, 0b1001001100001101), vm.evalstack.peek())
        assertFalse(vm.P_carry)
    }

    @Test
    fun testROR2() {
        // 8/16-bit rotation right
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0b10010011)),
                Instruction(Opcode.ROR2),        // 0b11001001
                Instruction(Opcode.ROR2),        // 0b11100100
                Instruction(Opcode.ROR2),        // 0b01110010
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2)         // 0b10010011  (original value after 8 rors)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 0b11001001), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b11100100), vm.evalstack.peek())
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b01110010), vm.evalstack.peek())
        vm.step(5)
        assertEquals(Value(DataType.BYTE, 0b10010011), vm.evalstack.peek())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0b1001001100001101)),
                Instruction(Opcode.ROR2_W),        // 0b1100100110000110
                Instruction(Opcode.ROR2_W),        // 0b0110010011000011
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W),
                Instruction(Opcode.ROR2_W)         // 0b1001001100001101  (original value after 16 rors)
        )
        vm.load(makeProg(ins2), null)
        vm.step(2)
        assertEquals(Value(DataType.WORD, 0b1100100110000110), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.WORD, 0b0110010011000011), vm.evalstack.peek())
        vm.step(14)
        assertEquals(Value(DataType.WORD, 0b1001001100001101), vm.evalstack.peek())
    }

    @Test
    fun testROL2() {
        // 8/16-bit rotation left
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0b10010011)),
                Instruction(Opcode.ROL2),        // 0b00100111
                Instruction(Opcode.ROL2),        // 0b01001110
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2)         // 0b10010011 (original value after 8 rols)
        )
        vm.load(makeProg(ins), null)
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 0b00100111), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0b01001110), vm.evalstack.peek())
        vm.step(6)
        assertEquals(Value(DataType.BYTE, 0b10010011), vm.evalstack.peek())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH_W, Value(DataType.WORD, 0b1001001100001101)),
                Instruction(Opcode.ROL2_W),        // 0b0010011000011011
                Instruction(Opcode.ROL2_W),        // 0b0100110000110110
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W),
                Instruction(Opcode.ROL2_W)         // 0b1001001100001101  (original value after 16 rols)
        )
        vm.load(makeProg(ins2), null)
        vm.step(2)
        assertEquals(Value(DataType.WORD, 0b0010011000011011), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(1)
        assertEquals(Value(DataType.WORD, 0b0100110000110110), vm.evalstack.peek())
        assertFalse(vm.P_carry)
        vm.step(14)
        assertEquals(Value(DataType.WORD, 0b1001001100001101), vm.evalstack.peek())
    }

    private fun pushOpcode(dt: DataType): Opcode {
        return when (dt) {
            DataType.BYTE -> Opcode.PUSH
            DataType.WORD -> Opcode.PUSH_W
            DataType.FLOAT -> Opcode.PUSH_F
            DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS,
            DataType.ARRAY, DataType.ARRAY_W, DataType.ARRAY_F, DataType.MATRIX -> Opcode.PUSH_W
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
