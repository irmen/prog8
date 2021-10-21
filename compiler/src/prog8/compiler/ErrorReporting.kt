package prog8.compiler

import prog8.ast.base.Position
import prog8.parser.ParsingFailedError


interface IErrorReporter {
    fun err(msg: String, position: Position)
    fun warn(msg: String, position: Position)
    fun noErrors(): Boolean
    fun report()
    fun finalizeNumErrors(numErrors: Int, numWarnings: Int) {
        if(numErrors>0)
            throw ParsingFailedError("There are $numErrors errors and $numWarnings warnings.")
    }
}


internal class ErrorReporter: IErrorReporter {
    private enum class MessageSeverity {
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

    override fun report() {
        var numErrors = 0
        var numWarnings = 0
        messages.forEach {
            when(it.severity) {
                MessageSeverity.ERROR -> System.err.print("\u001b[91m")  // bright red
                MessageSeverity.WARNING -> System.err.print("\u001b[93m")  // bright yellow
            }
            val msg = "${it.position.toClickableStr()} ${it.severity} ${it.message}".trim()
            if(msg !in alreadyReportedMessages) {
                System.err.println(msg)
                alreadyReportedMessages.add(msg)
                when(it.severity) {
                    MessageSeverity.WARNING -> numWarnings++
                    MessageSeverity.ERROR -> numErrors++
                }
            }
            System.err.print("\u001b[0m")  // reset color
        }
        messages.clear()
        finalizeNumErrors(numErrors, numWarnings)
    }

    override fun noErrors() = messages.none { it.severity==MessageSeverity.ERROR }
}
