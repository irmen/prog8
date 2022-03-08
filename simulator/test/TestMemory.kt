package prog8simulatortests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.sim.Memory

class TestMemory : FunSpec({

    val mem = Memory()

    test("unsigned byte") {
        mem.clear()
        mem[100u] shouldBe 0u
        mem[100u] = 99u
        mem[100u] shouldBe 99u
        mem[100u] = 254u
        mem[100u] shouldBe 254u
        mem.getSByte(100u) shouldBe -2
    }

    test("signed byte") {
        mem.clear()
        mem.getSByte(100u) shouldBe 0
        mem.setSByte(100u, 99)
        mem.getSByte(100u) shouldBe 99
        mem.setSByte(100u, -2)
        mem.getSByte(100u) shouldBe -2
        mem[100u] shouldBe 254u
    }

    test("unsigned word") {
        mem.clear()
        mem.getWord(100u) shouldBe 0u
        mem.setWord(100u, 12345u)
        mem.getWord(100u) shouldBe 12345u
        mem.getSWord(100u) shouldBe 12345
        mem.setWord(100u, 54321u)
        mem.getWord(100u) shouldBe 54321u
        mem.getSWord(100u) shouldBe -11215
        mem[100u] shouldBe 0x31u
        mem[101u] shouldBe 0xd4u
    }

    test("signed word") {
        mem.clear()
        mem.getSWord(100u) shouldBe 0
        mem.setSWord(100u, 12345)
        mem.getSWord(100u) shouldBe 12345
        mem.getWord(100u) shouldBe 12345u
        mem.setSWord(100u, -12345)
        mem.getSWord(100u) shouldBe -12345
        mem.getWord(100u) shouldBe 53191u
        mem[100u] shouldBe 0xc7u
        mem[101u] shouldBe 0xcfu
    }

    test("string") {
        mem.clear()
        mem[105u] = 1u
        mem.setString(100u, "Hello")
        mem[100u] shouldBe 'H'.code.toUByte()
        mem[104u] shouldBe 'o'.code.toUByte()
        mem[105u] shouldBe 0u

        mem.getString(100u) shouldBe "Hello"
    }
})