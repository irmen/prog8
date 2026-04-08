import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import prog8.vm.Memory


class TestMemory: FunSpec({

    test("reset") {
        val mem = Memory()
        mem.setUB(1000u, 42u)
        mem.getUB(1000u) shouldBe 42u
        mem.reset()
        mem.getUB(1000u) shouldBe 0u
    }

    test("byte access") {
        val mem = Memory()
        mem.setUB(1000u, 123u)
        mem.getUB(1000u) shouldBe 123u
        mem.getSB(1000u) shouldBe 123
        mem.setUB(1000u, 234u)
        mem.getUB(1000u) shouldBe 234u
        mem.getSB(1000u) shouldBe -22
        mem.setSB(1000u, -99)
        mem.getSB(1000u) shouldBe -99
        mem.getUB(1000u) shouldBe 157u
    }

    test("word access") {
        val mem = Memory()
        mem.setUW(1000u, 12345u)
        mem.getUW(1000u) shouldBe 12345u
        mem.getSW(1000u) shouldBe 12345
        mem.setUW(1000u, 55444u)
        mem.getUW(1000u) shouldBe 55444u
        mem.getSW(1000u) shouldBe -10092
        mem.setSW(1000u, -23456)
        mem.getSW(1000u) shouldBe -23456
        mem.getUW(1000u) shouldBe 42080u

        mem.setUW(1000u, 0xea31u)
        mem.getUB(1000u) shouldBe 0x31u
        mem.getUB(1001u) shouldBe 0xeau
    }

    test("long access") {
        val mem = Memory()
        mem.setSL(1000u, 12345678)
        mem.getSL(1000u) shouldBe 12345678
        mem.setSL(1000u, -888888)
        mem.getSL(1000u) shouldBe -888888
        mem.setSL(1000u, 0)
        mem.getSL(1000u) shouldBe 0
    }

    test("32 bits float access") {
        val mem = Memory()
        mem.getFloat(1000u) shouldNotBe 0.0
        mem.setFloat(1000u, 0.0)
        mem.getFloat(1000u) shouldBe 0.0
        mem.setFloat(1000u, -9.876543)
        mem.getFloat(1000u) shouldBe -9.876543
    }

    test("setstring and getstring") {
        val mem = Memory()
        mem.setString(1000u, "******************", false)
        mem.setString(1000u, "Hello world!", true)
        mem.getString(1000u) shouldBe "Hello world!"
        mem.getUB(1012u) shouldBe 0u
        mem.getUB(1013u) shouldBe 42u
        mem.setString(1000u, "Goodbye", false)
        mem.getString(1000u) shouldBe "Goodbyeorld!"
    }

    test("illegal address") {
        val mem = Memory()
        shouldThrow<ArrayIndexOutOfBoundsException> {
            mem.getUB(9999999u)
        }
        shouldThrow<ArrayIndexOutOfBoundsException> {
            mem.setUB(9999999u, 0u)
        }
    }
})
