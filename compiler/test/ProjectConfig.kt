package prog8tests

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.listeners.Listener
import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.Spec
import io.kotest.extensions.system.NoSystemErrListener
import io.kotest.extensions.system.NoSystemOutListener
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.math.max

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
