package prog8tests

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.DataType
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
                val variables = blockvars.getValue(blockname)
                variables[blockvar.key] = blockvar.value
            }
        }
        return Program("test", ins, labels ?: mapOf(), blockvars, mem ?: mapOf())
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
        val ins = mutableListOf(Instruction(Opcode.LINE, Value(DataType.STR, null, "line 99")))
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
    fun testPush() {
        val ins = mutableListOf(Instruction(Opcode.PUSH, Value(DataType.FLOAT, 42.999)))
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
        val ins = mutableListOf(Instruction(Opcode.PUSH_VAR, Value(DataType.STR, null, "varname")))
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
    fun testDup() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 42.999)),
                Instruction(Opcode.DUP))
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(2)
        assertEquals(2, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
    }

    @Test
    fun testSwap() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 9999)),
                Instruction(Opcode.SWAP)
        )
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(3)
        assertEquals(2, vm.evalstack.size)
        assertEquals(Value(DataType.BYTE, 123), vm.evalstack.pop())
        assertEquals(Value(DataType.WORD, 9999), vm.evalstack.pop())
    }

    @Test
    fun testDiscard() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 42.999)),
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 3.1415)),
                Instruction(Opcode.DISCARD))
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(2)
        assertEquals(2, vm.evalstack.size)
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 42.999), vm.evalstack.pop())
    }

    @Test
    fun testArray() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.WORD, 111)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 222)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 333)),
                Instruction(Opcode.ARRAY, Value(DataType.WORD, 2)))
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(4)
        assertEquals(2, vm.evalstack.size)
        var array = vm.evalstack.pop()
        assertEquals(DataType.ARRAY_W, array.type)
        assertThat(array.arrayvalue, equalTo(intArrayOf(222, 333)))
        assertEquals(Value(DataType.WORD, 111), vm.evalstack.pop())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 11)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 22)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 33)),
                Instruction(Opcode.ARRAY, Value(DataType.WORD, 2)))
        vm.load(makeProg(ins2), null)
        assertThat(vm.evalstack, empty())
        vm.step(4)
        assertEquals(2, vm.evalstack.size)
        array = vm.evalstack.pop()
        assertEquals(DataType.ARRAY, array.type)
        assertThat(array.arrayvalue, equalTo(intArrayOf(22, 33)))
        assertEquals(Value(DataType.BYTE, 11), vm.evalstack.pop())

        val ins3 = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 11)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 222)),
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 333.33)),
                Instruction(Opcode.ARRAY, Value(DataType.WORD, 2)))
        vm.load(makeProg(ins3), null)
        assertFailsWith<VmExecutionException> {
            vm.step(4)
        }
    }

    @Test
    fun testPopMem() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 42.25)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0x42ea)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.POP_MEM, Value(DataType.WORD, 0x2000)),
                Instruction(Opcode.POP_MEM, Value(DataType.WORD, 0x3000)),
                Instruction(Opcode.POP_MEM, Value(DataType.WORD, 0x4000)))
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
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 42.25)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0x42ea)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.POP_VAR, Value(DataType.STR, null, "var1")),
                Instruction(Opcode.POP_VAR, Value(DataType.STR, null, "var2")),
                Instruction(Opcode.POP_VAR, Value(DataType.STR, null, "var3")))
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0x42ea)),
                Instruction(Opcode.POP_VAR, Value(DataType.STR, null, "var1")))
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
        val values = listOf(
                Value(DataType.FLOAT, 42.25),
                Value(DataType.WORD, 4000),
                Value(DataType.BYTE, 40))
        val expected = listOf(
                Value(DataType.WORD, 4000+40),
                Value(DataType.FLOAT, 42.25+(4000+40)))
        val operator = Opcode.ADD

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testSub() {
        val values = listOf(
                Value(DataType.FLOAT, 42.25),
                Value(DataType.WORD, 4000),
                Value(DataType.BYTE, 40))
        val expected = listOf(
                Value(DataType.WORD, 4000-40),
                Value(DataType.FLOAT, 42.25-(4000-40)))
        val operator = Opcode.SUB

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testMul() {
        val values = listOf(
                Value(DataType.FLOAT, 42.2533),
                Value(DataType.WORD, 401),
                Value(DataType.BYTE, 4))
        val expected = listOf(
                Value(DataType.WORD, 401*4),
                Value(DataType.FLOAT, 42.2533*(401*4)))
        val operator = Opcode.MUL

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testDiv() {
        val values = listOf(
                Value(DataType.FLOAT, 42.25),
                Value(DataType.WORD, 3999),
                Value(DataType.BYTE, 40)
        )
        val expected = listOf(
                Value(DataType.WORD, 99),
                Value(DataType.FLOAT, 42.25/99))
        val operator = Opcode.DIV

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testFloorDiv() {
        val values = listOf(
                Value(DataType.FLOAT, 4000.25),
                Value(DataType.WORD, 3999),
                Value(DataType.BYTE, 40)
        )
        val expected = listOf(
                Value(DataType.WORD, 99),
                Value(DataType.FLOAT, 40.0))
        val operator = Opcode.FLOORDIV

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testPow() {
        val values = listOf(
                Value(DataType.FLOAT, 1.1),
                Value(DataType.WORD, 3),
                Value(DataType.BYTE, 4)
        )
        val expected = listOf(
                Value(DataType.WORD, 81),
                Value(DataType.FLOAT, 2253.2402360440274))
        val operator = Opcode.POW

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testRemainder() {
        val values = listOf(
                Value(DataType.FLOAT, 2022.5),
                Value(DataType.WORD, 500),
                Value(DataType.BYTE, 29)
        )
        val expected = listOf(
                Value(DataType.BYTE, 7),
                Value(DataType.FLOAT, 6.5))
        val operator = Opcode.REMAINDER

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testBitand() {
        val values = listOf(
                Value(DataType.WORD, 0b0011001011110001),
                Value(DataType.BYTE, 0b10011111),
                Value(DataType.BYTE, 0b11111101))
        val expected = listOf(
                Value(DataType.BYTE, 0b10011101),
                Value(DataType.WORD, 0b0000000010010001))
        val operator = Opcode.BITAND

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testBitor() {
        val values = listOf(
                Value(DataType.WORD, 0b0011001011100000),
                Value(DataType.BYTE, 0b00011101),
                Value(DataType.BYTE, 0b10010001))
        val expected = listOf(
                Value(DataType.BYTE, 0b10011101),
                Value(DataType.WORD, 0b0011001011111101))
        val operator = Opcode.BITOR

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testBitxor() {
        val values = listOf(
                Value(DataType.WORD, 0b0011001011100000),
                Value(DataType.BYTE, 0b00011101),
                Value(DataType.BYTE, 0b10010001))
        val expected = listOf(
                Value(DataType.BYTE, 0b10001100),
                Value(DataType.WORD, 0b0011001001101100))
        val operator = Opcode.BITXOR

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testAnd() {
        val values = listOf(
                Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)),
                Value(DataType.BYTE, 0),
                Value(DataType.WORD, 0),
                Value(DataType.STR, null, ""),
                Value(DataType.STR, null, "hello"),
                Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)),
                Value(DataType.FLOAT, 300.33),
                Value(DataType.WORD, 5000),
                Value(DataType.BYTE, 200),
                Value(DataType.BYTE, 1))
        val expected = listOf(
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 0))
        val operator = Opcode.AND

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testOr() {
        val values = listOf(
                Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)),
                Value(DataType.BYTE, 0),
                Value(DataType.WORD, 0),
                Value(DataType.STR, null, ""),
                Value(DataType.STR, null, "hello"),
                Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)),
                Value(DataType.FLOAT, 300.33),
                Value(DataType.WORD, 5000),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 0))
        val expected = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1))
        val operator = Opcode.OR

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testXor() {
        val values = listOf(
                Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)),
                Value(DataType.BYTE, 1),
                Value(DataType.WORD, 0),
                Value(DataType.STR, null, ""),
                Value(DataType.STR, null, "hello"),
                Value(DataType.ARRAY, null, arrayvalue = intArrayOf(1,2,3)),
                Value(DataType.FLOAT, 300.33),
                Value(DataType.WORD, 5000),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 20))
        val expected = listOf(
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1))
        val operator = Opcode.XOR

        testBinaryOperator(values, operator, expected)
    }

    @Test
    fun testNot() {
        val values = listOf(
                Value(DataType.STR, null, ""),
                Value(DataType.STR, null, "hello"),
                Value(DataType.FLOAT, 0.0),
                Value(DataType.FLOAT, 300.33),
                Value(DataType.WORD, 0),
                Value(DataType.WORD, 5000),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 20))
        val expected = listOf(
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1),
                Value(DataType.BYTE, 0),
                Value(DataType.BYTE, 1)
                )
        val operator = Opcode.NOT

        testUnaryOperator(values, operator, expected)
    }

    @Test
    fun testInc() {
        val values = listOf(
                Value(DataType.FLOAT, 2022.5),
                Value(DataType.WORD, 65535),
                Value(DataType.WORD, 999),
                Value(DataType.BYTE, 255),
                Value(DataType.BYTE, 99)
        )
        val expected = listOf(
                Value(DataType.BYTE, 100),
                Value(DataType.BYTE, 0),
                Value(DataType.WORD, 1000),
                Value(DataType.WORD, 0),
                Value(DataType.FLOAT, 2023.5)
        )
        val operator = Opcode.INC
        testUnaryOperator(values, operator, expected)
    }

    @Test
    fun testDec() {
        val values = listOf(
                Value(DataType.FLOAT, 0.5),
                Value(DataType.FLOAT, 123.456),
                Value(DataType.WORD, 1000),
                Value(DataType.WORD, 0),
                Value(DataType.BYTE, 100),
                Value(DataType.BYTE, 0)
        )
        val expected = listOf(
                Value(DataType.BYTE, 255),
                Value(DataType.BYTE, 99),
                Value(DataType.WORD, 65535),
                Value(DataType.WORD, 999),
                Value(DataType.FLOAT, 122.456),
                Value(DataType.FLOAT, -0.5)
        )
        val operator = Opcode.DEC
        testUnaryOperator(values, operator, expected)
    }

    @Test
    fun testNeg() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 123.456)),
                Instruction(Opcode.NEG),
                Instruction(Opcode.NEG)
        )
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(2)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, -123.456), vm.evalstack.peek())
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.FLOAT, 123.456), vm.evalstack.peek())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.WORD, 1234)),
                Instruction(Opcode.NEG)
        )
        vm.load(makeProg(ins2), null)
        vm.step(2)
        assertEquals(Value(DataType.WORD, 64302), vm.evalstack.pop())

        val ins3 = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 12)),
                Instruction(Opcode.NEG)
        )
        vm.load(makeProg(ins3), null)
        vm.step(2)
        assertEquals(Value(DataType.BYTE, 244), vm.evalstack.pop())
    }

    @Test
    fun testInv() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 123)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 4044)),
                Instruction(Opcode.INV),
                Instruction(Opcode.INV),
                Instruction(Opcode.INV)
        )
        vm.load(makeProg(ins), null)
        assertThat(vm.evalstack, empty())
        vm.step(3)
        assertEquals(2, vm.evalstack.size)
        assertEquals(Value(DataType.WORD, 0xf033), vm.evalstack.pop())
        vm.step(1)
        assertEquals(1, vm.evalstack.size)
        assertEquals(Value(DataType.BYTE, 0x84), vm.evalstack.pop())

        val ins2 = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 1234.33)),
                Instruction(Opcode.INV)
        )
        vm.load(makeProg(ins2), null)
        assertFailsWith<VmExecutionException> {
            vm.step(2)
        }
    }

    @Test
    fun testLsb() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 1.23)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0x45)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.LSB),
                Instruction(Opcode.LSB),
                Instruction(Opcode.LSB)
        )
        vm.load(makeProg(ins), null)
        vm.step(4)
        assertEquals(Value(DataType.BYTE, 0x31), vm.evalstack.pop())
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0x45), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testMsb() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 1.23)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 0x45)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0xea31)),
                Instruction(Opcode.MSB),
                Instruction(Opcode.MSB),
                Instruction(Opcode.MSB)
        )
        vm.load(makeProg(ins), null)
        vm.step(4)
        assertEquals(Value(DataType.BYTE, 0xea), vm.evalstack.pop())
        vm.step(1)
        assertEquals(Value(DataType.BYTE, 0), vm.evalstack.pop())
        assertFailsWith<VmExecutionException> {
            vm.step(1)
        }
    }

    @Test
    fun testB2Word() {
        val ins = mutableListOf(
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0xea31)),
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0xea31)),
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0xea31)),
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 12345)),
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
    fun testIncMemAndIncMemW() {
        val ins = mutableListOf(
                Instruction(Opcode.INC_MEM, Value(DataType.WORD, 0x2000)),
                Instruction(Opcode.INC_MEM, Value(DataType.WORD, 0x2001)),
                Instruction(Opcode.INC_MEM_W, Value(DataType.WORD, 0x3000)),
                Instruction(Opcode.INC_MEM_W, Value(DataType.WORD, 0x3002))
        )
        val mem=mapOf(0x2000 to listOf(Value(DataType.BYTE, 100), Value(DataType.BYTE, 255)),
                0x3000 to listOf(Value(DataType.WORD, 0x42ea), Value(DataType.WORD, 0xffff)))
        vm.load(makeProg(ins, mem=mem), null)
        vm.step(4)
        assertEquals(101, vm.mem.getByte(0x2000))
        assertEquals(0, vm.mem.getByte(0x2001))
        assertEquals(0x42eb, vm.mem.getWord(0x3000))
        assertEquals(0, vm.mem.getWord(0x3002))
    }

    @Test
    fun testIncVar() {
        val ins = mutableListOf(
                Instruction(Opcode.INC_VAR, Value(DataType.STR, null, "var1")),
                Instruction(Opcode.INC_VAR, Value(DataType.STR, null, "var2")),
                Instruction(Opcode.INC_VAR, Value(DataType.STR, null, "var1")),
                Instruction(Opcode.INC_VAR, Value(DataType.STR, null, "var2")))
        val vars = mapOf("var1" to Value(DataType.WORD, 65534),
                "var2" to Value(DataType.BYTE, 254))
        vm.load(makeProg(ins, vars = vars), null)
        vm.step(2)
        assertEquals(Value(DataType.WORD, 65535), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 255), vm.variables["var2"])
        vm.step(2)
        assertEquals(Value(DataType.WORD, 0), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 0), vm.variables["var2"])
    }

    @Test
    fun testDecVar() {
        val ins = mutableListOf(
                Instruction(Opcode.DEC_VAR, Value(DataType.STR, null, "var1")),
                Instruction(Opcode.DEC_VAR, Value(DataType.STR, null, "var2")),
                Instruction(Opcode.DEC_VAR, Value(DataType.STR, null, "var1")),
                Instruction(Opcode.DEC_VAR, Value(DataType.STR, null, "var2")))
        val vars = mapOf("var1" to Value(DataType.WORD,1),
                "var2" to Value(DataType.BYTE, 1))
        vm.load(makeProg(ins, vars = vars), null)
        vm.step(2)
        assertEquals(Value(DataType.WORD, 0), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 0), vm.variables["var2"])
        vm.step(2)
        assertEquals(Value(DataType.WORD, 65535), vm.variables["var1"])
        assertEquals(Value(DataType.BYTE, 255), vm.variables["var2"])
    }

    @Test
    fun testDecMemAndDecMemW() {
        val ins = mutableListOf(
                Instruction(Opcode.DEC_MEM, Value(DataType.WORD, 0x2000)),
                Instruction(Opcode.DEC_MEM, Value(DataType.WORD, 0x2001)),
                Instruction(Opcode.DEC_MEM_W, Value(DataType.WORD, 0x3000)),
                Instruction(Opcode.DEC_MEM_W, Value(DataType.WORD, 0x3002))
        )
        val mem=mapOf(0x2000 to listOf(Value(DataType.BYTE, 100), Value(DataType.BYTE, 0)),
                0x3000 to listOf(Value(DataType.WORD, 0x42ea), Value(DataType.WORD, 0)))
        vm.load(makeProg(ins, mem=mem), null)
        vm.step(4)
        assertEquals(99, vm.mem.getByte(0x2000))
        assertEquals(255, vm.mem.getByte(0x2001))
        assertEquals(0x42e9, vm.mem.getWord(0x3000))
        assertEquals(65535, vm.mem.getWord(0x3002))
    }

    @Test
    fun testSyscall() {
        val ins = mutableListOf(
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RNDF.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RNDW.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RND.callNr)),
                Instruction(Opcode.SYSCALL, Value(DataType.BYTE, Syscall.FUNC_RND.callNr)),

                Instruction(Opcode.PUSH, Value(DataType.WORD, 25544)),
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
                Value(DataType.STR, null, stringvalue = "hello"),
                Value(DataType.STR, null, stringvalue = "hello")
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.LESS)  // can't compare strings
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
                Value(DataType.STR, null, stringvalue = "hello"),
                Value(DataType.STR, null, stringvalue = "hello")
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.LESSEQ)  // can't compare strings
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
                Value(DataType.STR, null, stringvalue = "hello"),
                Value(DataType.STR, null, stringvalue = "hello")
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.GREATER)  // can't compare strings
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
                Value(DataType.STR, null, stringvalue = "hello"),
                Value(DataType.STR, null, stringvalue = "hello")
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.GREATEREQ)  // can't compare strings
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
                Value(DataType.STR, null, stringvalue = "hello"),
                Value(DataType.STR, null, stringvalue = "hello")
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.EQUAL)  // can't compare strings
        }
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
                Value(DataType.STR, null, stringvalue = "hello"),
                Value(DataType.STR, null, stringvalue = "hello")
        )
        assertFailsWith<VmExecutionException> {
            testComparisonOperator(valuesInvalid, listOf(0), Opcode.NOTEQUAL)  // can't compare strings
        }
    }

    @Test
    fun testBCC() {
        val ins = mutableListOf(
                Instruction(Opcode.SEC),
                Instruction(Opcode.BCC, callLabel = "label"),
                Instruction(Opcode.CLC),
                Instruction(Opcode.BCC, callLabel = "label"),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 1)),
                Instruction(Opcode.BZ, callLabel = "label"),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0)),
                Instruction(Opcode.BZ, callLabel = "label"),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0)),
                Instruction(Opcode.BNZ, callLabel = "label"),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 1)),
                Instruction(Opcode.BNZ, callLabel = "label"),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0)),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 1)),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, -99)),
                Instruction(Opcode.BNEG, callLabel = "label"),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, -99)),
                Instruction(Opcode.BPOS, callLabel = "label"),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0)),
                Instruction(Opcode.BPOS, callLabel = "label"),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string2")))
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
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "string1"))
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
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "returned")),
                Instruction(Opcode.TERMINATE),
                Instruction(Opcode.LINE, Value(DataType.STR, null, stringvalue = "called")),
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
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 9.99)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 3)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 61005)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 3)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 249)),
                Instruction(Opcode.SHR),        // 124
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHR),        // 1
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHR),        // 30502
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHR),        // 1
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.SHR),        // 0
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHR)         // error
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
                Instruction(Opcode.PUSH, Value(DataType.FLOAT, 9.99)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 3)),
                Instruction(Opcode.PUSH, Value(DataType.WORD, 61005)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 3)),
                Instruction(Opcode.PUSH, Value(DataType.BYTE, 249)),
                Instruction(Opcode.SHL),        // 242
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHL),        // 6
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHL),        // 56474
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHL),        // 6
                Instruction(Opcode.DISCARD),
                Instruction(Opcode.SHL)         // error
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0b1001001100001101)),
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0b1001001100001101)),
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0b1001001100001101)),
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
                Instruction(Opcode.PUSH, Value(DataType.WORD, 0b1001001100001101)),
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

    private fun testComparisonOperator(values: List<Value>, expected: List<Int>, operator: Opcode) {
        assertEquals(values.size, expected.size*2)
        val ins = mutableListOf<Instruction>()
        val vars = values.iterator()
        while(vars.hasNext()) {
            ins.add(Instruction(Opcode.PUSH, vars.next()))
            ins.add(Instruction(Opcode.PUSH, vars.next()))
            ins.add(Instruction(operator))
        }
        vm.load(makeProg(ins), null)
        for(expectedValue in expected) {
            vm.step(3)
            assertEquals(Value(DataType.BYTE, expectedValue), vm.evalstack.pop())
        }
    }

    private fun testBinaryOperator(values: List<Value>, operator: Opcode, expected: List<Value>) {
        assertEquals(values.size, expected.size+1)
        val ins = mutableListOf<Instruction>()
        for (value in values)
            ins.add(Instruction(Opcode.PUSH, value))
        for (i in 1 until values.size)
            ins.add(Instruction(operator))
        vm.load(makeProg(ins), null)
        vm.step(values.size)
        assertEquals(values.size, vm.evalstack.size)
        for (expectedVal in expected) {
            vm.step(1)
            assertEquals(expectedVal, vm.evalstack.peek())
        }
        assertFailsWith<VmTerminationException> {
            vm.step(1)
        }
    }

    private fun testUnaryOperator(values: List<Value>, operator: Opcode, expected: List<Value>) {
        assertEquals(values.size, expected.size)
        val ins = mutableListOf<Instruction>()
        for (value in values)
            ins.add(Instruction(Opcode.PUSH, value))
        for (i in 1..values.size) {
            ins.add(Instruction(operator))
            ins.add(Instruction(Opcode.DISCARD))
        }
        vm.load(makeProg(ins), null)
        vm.step(values.size)
        assertEquals(values.size, vm.evalstack.size)
        for (expectedVal in expected) {
            vm.step(1)
            assertEquals(expectedVal, vm.evalstack.peek())
            vm.step(1)
        }
        assertTrue(vm.evalstack.empty())
        assertFailsWith<VmTerminationException> {
            vm.step(1)
        }
    }
}
