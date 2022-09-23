package prog8tests

import io.kotest.core.spec.style.FunSpec
import prog8.code.target.VMTarget
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText


class TestLaunchEmu: FunSpec({

    test("test launch virtualmachine via target") {
        val target = VMTarget()
        val tmpfile = kotlin.io.path.createTempFile(suffix=".p8virt")
        tmpfile.writeText(";comment\n------PROGRAM------\n;comment\n")
        target.machine.launchEmulator(0, tmpfile)
        tmpfile.deleteExisting()
    }
})
