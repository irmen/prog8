package prog8tests.asmgen

import io.kotest.core.config.AbstractProjectConfig
import kotlin.math.max

object ProjectConfig : AbstractProjectConfig() {
    override val parallelism = max(2, Runtime.getRuntime().availableProcessors() / 2)
}
