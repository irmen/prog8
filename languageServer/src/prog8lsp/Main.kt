package prog8lsp

import org.eclipse.lsp4j.launch.LSPLauncher
import prog8.buildversion.VERSION
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

fun main(args: Array<String>) {
    Logger.getLogger("").level = Level.INFO
    
    val inStream = System.`in`
    val outStream = System.out
    val server = Prog8LanguageServer()
    val threads = Executors.newCachedThreadPool { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, inStream, outStream, threads) { it }

    server.connect(launcher.remoteProxy)
    launcher.startListening()
    
    println("Prog8 Language Server started. Prog8 version: ${VERSION}")
}
