package prog8tests.codecore

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.BaseDataType
import prog8.code.target.ConfigFileTarget
import java.nio.file.Files
import kotlin.io.path.writeText

class TestConfigFileTarget: FunSpec({
    fun loadTarget(extraProperties: String = ""): ConfigFileTarget {
        val directory = Files.createTempDirectory("prog8-config-target")
        val library = Files.createDirectories(directory.resolve("library"))
        val config = directory.resolve("target.properties")
        config.writeText(
            """
            cpu = 65C02
            encoding = iso
            load_address = 2048
            memtop = 65535
            bss_highram_start = 0
            bss_highram_end = 0
            bss_goldenram_start = 0
            bss_goldenram_end = 0
            zp_scratch_b1 = 240
            zp_scratch_reg = 241
            zp_scratch_w1 = 242
            zp_scratch_w2 = 244
            zp_scratch_ptr = 246
            virtual_registers = 2
            zp_fullsafe = 34-239
            zp_kernalsafe = 34-239
            zp_basicsafe = 34-239
            io_regions =
            """.trimIndent() + "\nlibrary = $library\n$extraProperties"
        )
        return try {
            ConfigFileTarget.fromConfigFile(config)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    test("legacy config defaults to 2-byte pointers and 256 element arrays") {
        val target = loadTarget()

        target.POINTER_MEM_SIZE shouldBe 2u
        target.memorySize(BaseDataType.POINTER) shouldBe 2
        target.ARRAY_SIZE_LIMIT shouldBe 256u
    }

    test("config supports 4-byte pointers") {
        val target = loadTarget("pointer_size = 4")

        target.POINTER_MEM_SIZE shouldBe 4u
        target.memorySize(BaseDataType.POINTER) shouldBe 4
        target.ARRAY_SIZE_LIMIT shouldBe 256u
    }

    test("config rejects unsupported pointer sizes") {
        shouldThrow<IllegalArgumentException> {
            loadTarget("pointer_size = 3")
        }
    }
})
