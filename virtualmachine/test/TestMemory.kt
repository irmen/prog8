import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.vm.Memory


class TestMemory: FunSpec({

    test("reset") {
        val mem = Memory()
        mem.setUB(1000, 42u)
        mem.getUB(1000) shouldBe 42u
        mem.reset()
        mem.getUB(1000) shouldBe 0u
    }

    test("byte access") {
        val mem = Memory()
        mem.setUB(1000, 123u)
        mem.getUB(1000) shouldBe 123u
        mem.getSB(1000) shouldBe 123
        mem.setUB(1000, 234u)
        mem.getUB(1000) shouldBe 234u
        mem.getSB(1000) shouldBe -22
        mem.setSB(1000, -99)
        mem.getSB(1000) shouldBe -99
        mem.getUB(1000) shouldBe 157u
    }

    test("word access") {
        val mem = Memory()
        mem.setUW(1000, 12345u)
        mem.getUW(1000) shouldBe 12345u
        mem.getSW(1000) shouldBe 12345
        mem.setUW(1000, 55444u)
        mem.getUW(1000) shouldBe 55444u
        mem.getSW(1000) shouldBe -10092
        mem.setSW(1000, -23456)
        mem.getSW(1000) shouldBe -23456
        mem.getUW(1000) shouldBe 42080u

        mem.setUW(1000, 0xea31u)
        mem.getUB(1000) shouldBe 0x31u
        mem.getUB(1001) shouldBe 0xeau
    }

    test("32 bits float access") {
        val mem = Memory()
        mem.getFloat(1000) shouldBe 0.0
        mem.setFloat(1000, -9.876543f)
        mem.getFloat(1000) shouldBe -9.876543f
    }

    test("setstring and getstring") {
        val mem = Memory()
        mem.setString(1000, "******************", false)
        mem.setString(1000, "Hello world!", true)
        mem.getString(1000) shouldBe "Hello world!"
        mem.getUB(1012) shouldBe 0u
        mem.getUB(1013) shouldBe 42u
        mem.setString(1000, "Goodbye", false)
        mem.getString(1000) shouldBe "Goodbyeorld!"
    }

    test("illegal address") {
        val mem = Memory()
        shouldThrow<ArrayIndexOutOfBoundsException> {
            mem.getUB(9999999)
        }
        shouldThrow<ArrayIndexOutOfBoundsException> {
            mem.setUB(9999999, 0u)
        }
    }
})
