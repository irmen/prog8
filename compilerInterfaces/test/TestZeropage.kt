package prog8tests.interfaces

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import prog8.ast.base.DataType
import prog8.compilerinterface.*
import kotlin.test.assertEquals


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestZeropage {

    @Test
    fun testAbstractZeropage() {
        val compTarget = DummyCompilationTarget()
        val zp = DummyZeropage(
            CompilationOptions(
                OutputType.RAW,
                LauncherType.NONE,
                ZeropageType.FULL,
                listOf((0x50..0x5f)),
                false,
                false,
                compTarget
            )
        )
        assertEquals(256-4-16, zp.free.size)
    }

    class DummyCompilationTarget: ICompilationTarget {
        override val name: String
            get() = TODO("Not yet implemented")
        override val machine: IMachineDefinition
            get() = TODO("Not yet implemented")

        override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
            TODO("Not yet implemented")
        }

        override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
            TODO("Not yet implemented")
        }

        override fun memorySize(dt: DataType): Int {
            TODO("Not yet implemented")
        }

    }

    class DummyZeropage(options: CompilationOptions) : Zeropage(options) {
        override val SCRATCH_B1: Int = 0x10
        override val SCRATCH_REG: Int = 0x11
        override val SCRATCH_W1: Int= 0x12
        override val SCRATCH_W2: Int = 0x13

        init {
            free.addAll(0..255)

            // TODO should be in class:
            free.remove(SCRATCH_B1)
            free.remove(SCRATCH_REG)
            free.remove(SCRATCH_W1)
            free.remove(SCRATCH_W2)

            // TODO should be in class:
            for (reserved in options.zpReserved)
                reserve(reserved)
        }
    }

}
