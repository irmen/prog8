package prog8tests.compiler

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.SpecExecutionOrder
import io.kotest.core.test.TestCaseOrder
import io.kotest.engine.concurrency.SpecExecutionMode

private val testParallelism = System.getProperty("kotest.framework.parallelism")?.toIntOrNull()
    ?.coerceAtLeast(1)
    ?: (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

object ProjectConfig : AbstractProjectConfig() {
    override val testCaseOrder = TestCaseOrder.Lexicographic
    override val specExecutionOrder = SpecExecutionOrder.Lexicographic
    override val specExecutionMode = SpecExecutionMode.LimitedConcurrency(testParallelism)
    // override fun listeners() = listOf(SystemOutToNullListener)
}

//object SystemOutToNullListener: TestListener {
//    override suspend fun beforeSpec(spec: Spec) = setup()
//
//    private fun setup() {
//        System.setOut(object: PrintStream(object: ByteArrayOutputStream(){
//            override fun write(p0: Int) {
//                // do nothing
//            }
//
//            override fun write(b: ByteArray, off: Int, len: Int) {
//                // do nothing
//            }
//
//            override fun write(b: ByteArray) {
//                // do nothing
//            }
//        }){}
//        )
//    }
//}
//
//object SystemErrToNullListener: TestListener {
//    override suspend fun beforeSpec(spec: Spec) = setup()
//
//    private fun setup() {
//        System.setErr(object: PrintStream(object: ByteArrayOutputStream(){
//            override fun write(p0: Int) {
//                // do nothing
//            }
//
//            override fun write(b: ByteArray, off: Int, len: Int) {
//                // do nothing
//            }
//
//            override fun write(b: ByteArray) {
//                // do nothing
//            }
//        }){}
//        )
//    }
//}
