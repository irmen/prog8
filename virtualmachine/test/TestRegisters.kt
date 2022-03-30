import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.vm.Registers


class TestRegisters: FunSpec({

    test("reset") {
        val regs = Registers()
        regs.setUB(1, 42u)
        regs.getUB(1) shouldBe 42u
        regs.reset()
        regs.getUB(1) shouldBe 0u
    }

    test("byte access") {
        val regs = Registers()
        regs.setUB(1000, 123u)
        regs.getUB(1000) shouldBe 123u
        regs.getSB(1000) shouldBe 123
        regs.setUB(1000, 234u)
        regs.getUB(1000) shouldBe 234u
        regs.getSB(1000) shouldBe -22
        regs.setSB(1000, -99)
        regs.getSB(1000) shouldBe -99
        regs.getUB(1000) shouldBe 157u
    }

    test("word access") {
        val regs = Registers()
        regs.setUW(1000, 12345u)
        regs.getUW(1000) shouldBe 12345u
        regs.getSW(1000) shouldBe 12345
        regs.setUW(1000, 55444u)
        regs.getUW(1000) shouldBe 55444u
        regs.getSW(1000) shouldBe -10092
        regs.setSW(1000, -23456)
        regs.getSW(1000) shouldBe -23456
        regs.getUW(1000) shouldBe 42080u
    }
})
