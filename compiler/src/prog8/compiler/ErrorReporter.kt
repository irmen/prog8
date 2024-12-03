package prog8.compiler

import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import java.io.PrintStream

internal class ErrorReporter(val colors: IConsoleColors): IErrorReporter {
    private enum class MessageSeverity {
        INFO,
        WARNING,
        ERROR
    }
    private class CompilerMessage(val severity: MessageSeverity, val message: String, val position: Position)

    private val messages = mutableListOf<CompilerMessage>()
    private val alreadyReportedMessages = mutableSetOf<String>()

    override fun err(msg: String, position: Position) {
        messages.add(CompilerMessage(MessageSeverity.ERROR, msg, position))
    }
    override fun warn(msg: String, position: Position) {
        messages.add(CompilerMessage(MessageSeverity.WARNING, msg, position))
    }
    override fun info(msg: String, position: Position) {
        messages.add(CompilerMessage(MessageSeverity.INFO, msg, position))
    }

    override fun undefined(symbol: List<String>, position: Position) {
        err("undefined symbol: ${symbol.joinToString(".")}", position)
    }

    override fun report() {
        var numErrors = 0
        var numWarnings = 0
        var numInfos = 0
        messages.sortedWith(compareBy({it.position.file}, {it.position.line}, {it.severity})).forEach {
            val printer = when(it.severity) {
                MessageSeverity.INFO -> System.out
                MessageSeverity.WARNING -> System.out
                MessageSeverity.ERROR -> System.err
            }
            val msg = "${it.position.toClickableStr()} ${it.message}".trim()
            if(msg !in alreadyReportedMessages) {
                when(it.severity) {
                    MessageSeverity.ERROR -> {
                        System.out.flush()
                        colors.error(printer)
                        printer.print("ERROR ")
                        colors.normal(printer)
                        numErrors++
                    }
                    MessageSeverity.WARNING -> {
                        colors.warning(printer)
                        printer.print("WARN  ")
                        colors.normal(printer)
                        numWarnings++
                    }
                    MessageSeverity.INFO -> {
                        colors.info(printer)
                        printer.print("INFO  ")
                        colors.normal(printer)
                        numInfos++
                    }
                }
                val filtered = colors.filtered(msg)
                printer.println(filtered)
                alreadyReportedMessages.add(filtered)
            }
        }
        System.out.flush()
        System.err.flush()
        messages.clear()
        finalizeNumErrors(numErrors, numWarnings, numInfos)
    }

    override fun noErrors() = messages.none { it.severity==MessageSeverity.ERROR }
    override fun noErrorForLine(position: Position) = !messages.any { it.position.line==position.line && it.severity!=MessageSeverity.INFO }


    interface IConsoleColors {
        fun error(printer: PrintStream)
        fun warning(printer: PrintStream)
        fun info(printer: PrintStream)
        fun normal(printer: PrintStream)
        fun filtered(msg: String): String
    }

    object AnsiColors: IConsoleColors {
        override fun error(printer: PrintStream) = printer.print("\u001b[91m")      // red
        override fun warning(printer: PrintStream) = printer.print("\u001b[93m")    // yellow
        override fun info(printer: PrintStream) = printer.print("\u001b[92m")       // green
        override fun normal(printer: PrintStream) = printer.print("\u001B[0m")
        override fun filtered(msg: String): String = msg
    }

    object PlainText: IConsoleColors {
        override fun error(printer: PrintStream) {}
        override fun warning(printer: PrintStream) {}
        override fun info(printer: PrintStream) {}
        override fun normal(printer: PrintStream) {}
        override fun filtered(msg: String): String = msg.filter { !it.isSurrogate() }
    }
}
