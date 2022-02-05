package prog8tests

import io.kotest.core.config.AbstractProjectConfig

object ProjectConfig : AbstractProjectConfig() {
    override val parallelism = 2 // max(2, Runtime.getRuntime().availableProcessors() / 2)
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
