package prog8tests

import io.kotest.core.spec.style.FunSpec
import prog8.code.target.VMTarget
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText


class TestLaunchEmu: FunSpec({

    test("test launch virtualmachine via target") {
        val target = VMTarget()
        val tmpfile = kotlin.io.path.createTempFile(suffix=".p8ir")
        tmpfile.writeText("""<PROGRAM NAME=test>
<OPTIONS>
</OPTIONS>

<VARIABLES>
</VARIABLES>

<MEMORYMAPPEDVARIABLES>
</MEMORYMAPPEDVARIABLES>

<MEMORYSLABS>
</MEMORYSLABS>

<INITGLOBALS>
</INITGLOBALS>

<BLOCK NAME=main ADDRESS=null ALIGN=NONE POS=[unittest: line 42 col 1-9]>
</BLOCK>
</PROGRAM>
""")
        target.machine.launchEmulator(0, tmpfile)
        tmpfile.deleteExisting()
    }
})
