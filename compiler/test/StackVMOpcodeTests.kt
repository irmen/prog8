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
    SHR_MEM,
    SHR_MEM_W,
    SHR_VAR,
    ROL_MEM,
    ROL_MEM_W,
    ROL_VAR,
    ROR_MEM,
    ROR_MEM_W,
    ROR_VAR,
    ROL2_MEM,
    ROL2_MEM_W,
    ROL2_VAR,
    ROR2_MEM,
    ROR2_MEM_W,
    ROR2_VAR,

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
        testBinaryOperator(Value(DataType.WORD, 4000), Opcode.ADD, Value(DataType.BYTE, 40), Value(DataType.WORD, 4000+40))
        testBinaryOperator(Value(DataType.WORD, 4000+40), Opcode.ADD, Value(DataType.WORD, 123), Value(DataType.WORD, 4000+40+123))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 4000 + 40), Opcode.ADD, Value(DataType.FLOAT, 42.25), Value(DataType.FLOAT, 42.25 + (4000 + 40)))
        }
    }

    @Test
    fun testSub() {
        testBinaryOperator(Value(DataType.WORD, 4000), Opcode.SUB, Value(DataType.BYTE, 40), Value(DataType.WORD, 4000-40))
        testBinaryOperator(Value(DataType.WORD, 4000), Opcode.SUB, Value(DataType.WORD, 123), Value(DataType.WORD, 4000-123))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 4000 - 40), Opcode.SUB, Value(DataType.FLOAT, 42.25), Value(DataType.FLOAT, 42.25 - (4000 - 40)))
        }
    }

    @Test
    fun testMul() {
        testBinaryOperator(Value(DataType.WORD, 401), Opcode.MUL, Value(DataType.BYTE, 4), Value(DataType.WORD, 401*4))
        testBinaryOperator(Value(DataType.WORD, 401), Opcode.MUL, Value(DataType.WORD, 4), Value(DataType.WORD, 401*4))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 401 * 4), Opcode.MUL, Value(DataType.FLOAT, 42.2533), Value(DataType.FLOAT, 42.2533 * (401 * 4)))
        }
    }

    @Test
    fun testDiv() {
        testBinaryOperator(Value(DataType.WORD, 3999), Opcode.DIV, Value(DataType.BYTE, 40), Value(DataType.WORD, 99))
        testBinaryOperator(Value(DataType.WORD, 3999), Opcode.DIV, Value(DataType.WORD, 40), Value(DataType.WORD, 99))
        testBinaryOperator(Value(DataType.FLOAT, 42.25), Opcode.DIV, Value(DataType.WORD, 99), Value(DataType.FLOAT, 42.25/99))
        assertFailsWith<VmExecutionException> {
            testBinaryOperator(Value(DataType.WORD, 3333), Opcode.DIV, Value(DataType.FLOAT, 2.22), Value(DataType.FLOAT, 3333 / 2.22))
        }
    }

    @Test
    fun testFloorDiv() {
        testBinaryOperator(Value(DataType.WORD, 3999), Opcode.FLOORDIV, Value(DataType.BYTE, 99), Value(DataType.WORD, 40))
        testBinaryOperator(Value(DataType.WORD, 3999), Opcode.FLOORDIV, Value(DataType.WORD, 99), Value(DataType.WORD, 40))
        testBinaryOperator(Value(DataType.FLOAT, 4000.25), Opcode.FLOORDIV, Value(DataType.BYTE, 40), Value(DataType.FLOAT, 100.0))
    }

    @Test
    fun testPow() {
        testBinaryOperator(Value(DataType.WORD, 3), Opcode.POW, Value(DataType.BYTE, 4), Value(DataType.WORD, 81))
        testBinaryOperator(Value(DataType.WORD, 3), Opcode.POW, Value(DataType.WORD, 4), Value(DataType.WORD, 81))
        testBinaryOperator(Value(DataType.FLOAT, 1.1), Opcode.POW, Value(DataType.BYTE, 81), Value(DataType.FLOAT, 2253.2402360440274))
    }

    @Test
    fun testRemainder() {
        testBinaryOperator(Value(DataType.WORD, 500), Opcode.REMAINDER, Value(DataType.BYTE, 29), Value(DataType.BYTE, 7))
        testBinaryOperator(Value(DataType.WORD, 500), Opcode.REMAINDER, Value(DataType.WORD, 29), Value(DataType.BYTE, 7))
        testBinaryOperator(Value(DataType.FLOAT, 2022.5), Opcode.REMAINDER, Value(DataType.BYTE, 7), Value(DataType.FLOAT, 6.5))
    }

    @Test
    fun testBitand() {
        testBinaryOperator(Value(DataType.BYTE, 0b10011111), Opcode.BITAND, Value(DataType.BYTE, 0b11111101), Value(DataType.BYTE, 0b10011101))
        testBinaryOperator(Value(DataType.WORD, 0b0011001011110001), Opcode.BITAND, Value(DataType.BYTE, 0b10011101), Value(DataType.WORD, 0b0000000010010001))
    }

    @Test
    fun testBitor() {
        testBinaryOperator(Value(DataType.BYTE, 0b00011101), Opcode.BITOR, Value(DataType.BYTE, 0b10010001), Value(DataType.BYTE, 0b10011101))
        testBinaryOperator(Value(DataType.WORD, 0b0011001011100000), Opcode.BITOR, Value(DataType.BYTE, 0b10011101), Value(DataType.WORD, 0b0011001011111101))
    }

    @Test
    fun testBitxor() {
        testBinaryOperator(Value(DataType.BYTE, 0b00011101), Opcode.BITXOR, Value(DataType.BYTE, 0b10010001), Value(DataType.BYTE, 0b10001100))
        testBinaryOperator(Value(DataType.WORD, 0b0011001011100000), Opcode.BITXOR, Value(DataType.BYTE, 0b10001100), Value(DataType.WORD, 0b0011001001101100))
    }

    @Test
    fun testAnd() {
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.AND, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.AND, Value(DataType.BYTE, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.AND, Value(DataType.BYTE, 101), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.AND, Value(DataType.WORD, 13455), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.AND, Value(DataType.WORD, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 0), Opcode.AND, Value(DataType.WORD, 101), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 200.22), Opcode.AND, Value(DataType.FLOAT, 13455.55), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 200.22), Opcode.AND, Value(DataType.FLOAT, 0.0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 0.0), Opcode.AND, Value(DataType.FLOAT, 101.11), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.STR, 222), Opcode.AND, Value(DataType.STR, 333), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.ARRAY, 444), Opcode.AND, Value(DataType.ARRAY, 444), Value(DataType.BYTE, 1))
    }

    @Test
    fun testOr() {
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.OR, Value(DataType.BYTE, 1), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.OR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.OR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.OR, Value(DataType.WORD, 13455), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.OR, Value(DataType.WORD, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 0), Opcode.OR, Value(DataType.WORD, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 200.22), Opcode.OR, Value(DataType.FLOAT, 13455.55), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 200.22), Opcode.OR, Value(DataType.FLOAT, 0.0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 0.0), Opcode.OR, Value(DataType.FLOAT, 0.0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.STR, 222), Opcode.OR, Value(DataType.STR, 333), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.ARRAY, 444), Opcode.OR, Value(DataType.ARRAY, 444), Value(DataType.BYTE, 1))
    }

    @Test
    fun testXor() {
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.XOR, Value(DataType.BYTE, 1), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.BYTE, 200), Opcode.XOR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.BYTE, 0), Opcode.XOR, Value(DataType.BYTE, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.XOR, Value(DataType.WORD, 13455), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.WORD, 200), Opcode.XOR, Value(DataType.WORD, 0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.WORD, 0), Opcode.XOR, Value(DataType.WORD, 0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 200.22), Opcode.XOR, Value(DataType.FLOAT, 13455.55), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.FLOAT, 200.22), Opcode.XOR, Value(DataType.FLOAT, 0.0), Value(DataType.BYTE, 1))
        testBinaryOperator(Value(DataType.FLOAT, 0.0), Opcode.XOR, Value(DataType.FLOAT, 0.0), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.STR, 222), Opcode.XOR, Value(DataType.STR, 333), Value(DataType.BYTE, 0))
        testBinaryOperator(Value(DataType.ARRAY, 444), Opcode.XOR, Value(DataType.ARRAY, 444), Value(DataType.BYTE, 0))
    }

    @Test
    fun testNot() {
        testUnaryOperator(Value(DataType.BYTE, 0), Opcode.NOT, Value(DataType.BYTE, 1))
        testUnaryOperator(Value(DataType.BYTE, 20), Opcode.NOT, Value(DataType.BYTE, 0))
        testUnaryOperator(Value(DataType.WORD, 0), Opcode.NOT, Value(DataType.BYTE, 1))
        testUnaryOperator(Value(DataType.WORD, 5000), Opcode.NOT, Value(DataType.BYTE, 0))
        testUnaryOperator(Value(DataType.FLOAT, 0.0), Opcode.NOT, Value(DataType.BYTE, 1))
        testUnaryOperator(Value(DataType.FLOAT, 5000.0), Opcode.NOT, Value(DataType.BYTE, 0))
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
        testUnaryOperator(Value(DataType.BYTE, 12), Opcode.NEG, Value(DataType.BYTE, 244))
        testUnaryOperator(Value(DataType.WORD, 1234), Opcode.NEG, Value(DataType.WORD, 64302))
        testUnaryOperator(Value(DataType.FLOAT, 123.456), Opcode.NEG, Value(DataType.FLOAT, -123.456))
    }

    @Test
    fun testInv() {
        testUnaryOperator(Value(DataType.BYTE, 123), Opcode.INV, Value(DataType.BYTE, 0x84))
        testUnaryOperator(Value(DataType.WORD, 4044), Opcode.INV, Value(DataType.WORD, 0xf033))
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
        val values = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),        // 1
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),        // 0
                Value(DataType.BYTE, 2),
                Value(DataType.WORD, 20),       // 1
                Value(DataType.WORD, 20),
                Value(DataType.BYTE, 21),      // 1
                Value(DataType.WORD, 21),
                Value(DataType.BYTE, 21),      // 0
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21),      // 0
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21.0001)      // 1
        )
        val expected = listOf(1, 0, 1, 1, 0, 0, 1)
        testComparisonOperator(values, expected, Opcode.LESS)

        val valuesInvalid = listOf(
                Value(DataType.STR, 333),
                Value(DataType.STR, 333)
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.LESS)  // can't order strings
        }
    }

    @Test
    fun testLessEq() {
        val values = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),        // 1
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),        // 1
                Value(DataType.BYTE, 21),
                Value(DataType.WORD, 20),       // 0
                Value(DataType.WORD, 20),
                Value(DataType.BYTE, 21),      // 1
                Value(DataType.WORD, 21),
                Value(DataType.BYTE, 22),      // 1
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21),      // 1
                Value(DataType.BYTE, 22),
                Value(DataType.FLOAT, 21.999)      // 0
        )
        val expected = listOf(1,1,0,1,1,1,0)
        testComparisonOperator(values, expected, Opcode.LESSEQ)

        val valuesInvalid = listOf(
                Value(DataType.STR, 333),
                Value(DataType.STR, 333)
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.LESSEQ)  // can't order strings
        }
    }

    @Test
    fun testGreater() {
        val values = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),        // 0
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),        // 0
                Value(DataType.BYTE, 20),
                Value(DataType.WORD, 2),       // 1
                Value(DataType.WORD, 20),
                Value(DataType.BYTE, 21),      // 0
                Value(DataType.WORD, 21),
                Value(DataType.BYTE, 20),      // 1
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21),      // 0
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 20.9999)      // 1
        )
        val expected = listOf(0, 0, 1, 0, 1, 0, 1)
        testComparisonOperator(values, expected, Opcode.GREATER)

        val valuesInvalid = listOf(
                Value(DataType.STR, 333),
                Value(DataType.STR, 333)
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.GREATER)  // can't order strings
        }
    }

    @Test
    fun testGreaterEq() {
        val values = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),        // 0
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),        // 1
                Value(DataType.BYTE, 21),
                Value(DataType.WORD, 20),       // 1
                Value(DataType.WORD, 20),
                Value(DataType.BYTE, 21),      // 0
                Value(DataType.WORD, 21),
                Value(DataType.BYTE, 22),      // 0
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21),      // 1
                Value(DataType.BYTE, 22),
                Value(DataType.FLOAT, 21.999)      // 1
        )
        val expected = listOf(0,1,1,0,0,1,1)
        testComparisonOperator(values, expected, Opcode.GREATEREQ)

        val valuesInvalid = listOf(
                Value(DataType.STR, 333),
                Value(DataType.STR, 333)
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.GREATEREQ)  // can't order strings
        }
    }

    @Test
    fun testEqual() {
        val values = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),        // 0
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),        // 1
                Value(DataType.BYTE, 21),
                Value(DataType.WORD, 20),       // 0
                Value(DataType.WORD, 20),
                Value(DataType.BYTE, 21),      // 0
                Value(DataType.WORD, 21),
                Value(DataType.BYTE, 21),      // 1
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21),      // 1
                Value(DataType.BYTE, 22),
                Value(DataType.FLOAT, 21.999)      // 0
        )
        val expected = listOf(0,1,0,0,1,1,0)
        testComparisonOperator(values, expected, Opcode.EQUAL)

        val valuesInvalid = listOf(
                Value(DataType.STR, 111),
                Value(DataType.STR, 222),       // 0
                Value(DataType.STR, 333),
                Value(DataType.STR, 333)        // 1
        )
        testComparisonOperator(valuesInvalid, listOf(0, 1), Opcode.EQUAL)
    }

    @Test
    fun testNotEqual() {
        val values = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),        // 1
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),        // 0
                Value(DataType.BYTE, 21),
                Value(DataType.WORD, 20),       // 1
                Value(DataType.WORD, 20),
                Value(DataType.BYTE, 21),      // 1
                Value(DataType.WORD, 21),
                Value(DataType.BYTE, 21),      // 0
                Value(DataType.BYTE, 21),
                Value(DataType.FLOAT, 21),      // 0
                Value(DataType.BYTE, 22),
                Value(DataType.FLOAT, 21.999)      // 1
        )
        val expected = listOf(1,0,1,1,0,0,1)
        testComparisonOperator(values, expected, Opcode.NOTEQUAL)

        val valuesInvalid = listOf(
                Value(DataType.STR, 111),
                Value(DataType.STR, 222),       // 1
                Value(DataType.STR, 333),
                Value(DataType.STR, 333)        // 0
        )
        testComparisonOperator(valuesInvalid, listOf(1, 0), Opcode.NOTEQUAL)
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
                Instruction(Opcode.SHR),        // 30502
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHR),        // 1
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.SHR),        // 0
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
                Instruction(Opcode.SHL),        // 56474
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHL),        // 6
                Instruction(Opcode.DISCARD_W),
                Instruction(Opcode.SHL)         // error on float
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
                Instruction(Opcode.ROR),        // 0b0100100110000110   c=1
                Instruction(Opcode.ROR),        // 0b1010010011000011   c=0
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR),
                Instruction(Opcode.ROR)         // 0b1001001100001101   c=0  (original value after 17 rors)
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
                Instruction(Opcode.ROL),        // 0b0010011000011010   c=1
                Instruction(Opcode.ROL),        // 0b0100110000110101   c=0
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL),
                Instruction(Opcode.ROL)         // 0b1001001100001101   c=0  (original value after 17 rors)
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
                Instruction(Opcode.ROR2),        // 0b1100100110000110
                Instruction(Opcode.ROR2),        // 0b0110010011000011
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2),
                Instruction(Opcode.ROR2)         // 0b1001001100001101  (original value after 16 rors)
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
                Instruction(Opcode.ROL2),        // 0b0010011000011011
                Instruction(Opcode.ROL2),        // 0b0100110000110110
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2),
                Instruction(Opcode.ROL2)         // 0b1001001100001101  (original value after 16 rols)
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

    private fun testComparisonOperator(values: List<Value>, expected: List<Int>, operator: Opcode) {
        assertEquals(values.size, expected.size*2)
        val ins = mutableListOf<Instruction>()
        val vars = values.iterator()
        while(vars.hasNext()) {
            var nextvar = vars.next()
            ins.add(Instruction(pushOpcode(nextvar.type), nextvar))
            nextvar = vars.next()
            ins.add(Instruction(pushOpcode(nextvar.type), nextvar))
            ins.add(Instruction(operator))
        }
        vm.load(makeProg(ins), null)
        for(expectedValue in expected) {
            vm.step(3)
            assertEquals(Value(DataType.BYTE, expectedValue), vm.evalstack.pop())
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
