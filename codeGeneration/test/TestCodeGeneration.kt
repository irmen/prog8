package prog8tests.asmgen

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.fail


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestCodeGeneration {

    @Test
    @Disabled("for future implementation")
    fun dummy() {
        fail("dummy")
    }

}
