package prog8lsp

import org.eclipse.lsp4j.launch.LSPLauncher
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

fun main(args: Array<String>) {
    Logger.getLogger("").level = Level.INFO

    val inStream = System.`in`
    val outStream = System.out
    val server = Prog8LanguageServer()
    val threads = Executors.newSingleThreadExecutor { Thread(it, "client") }
    val launcher = LSPLauncher.createServerLauncher(server, inStream, outStream, threads) { it }

    server.connect(launcher.remoteProxy)
    launcher.startListening()
}
