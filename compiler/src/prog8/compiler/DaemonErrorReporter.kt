package prog8.compiler

import prog8.code.core.ErrorsReportedException
import prog8.code.core.IErrorReporter
import prog8.code.core.Position


internal class DaemonErrorReporter : IErrorReporter {
    data class CompilerMessage(val severity: String, val message: String, val position: Position?)

    private val messages = mutableListOf<CompilerMessage>()

    override fun err(msg: String, position: Position) {
        messages.add(CompilerMessage("ERROR", msg, position))
    }

    override fun warn(msg: String, position: Position) {
        messages.add(CompilerMessage("WARNING", msg, position))
    }

    override fun info(msg: String, position: Position) {
        messages.add(CompilerMessage("INFO", msg, position))
    }

    override fun undefined(symbol: List<String>, suggestImport: Boolean, position: Position) {
        if (suggestImport && symbol.size > 1)
            err("undefined symbol: ${symbol.joinToString(".")} (maybe you forgot to import a module that defines ${symbol.first()}?)", position)
        else
            err("undefined symbol: ${symbol.joinToString(".")}", position)
    }

    override fun noErrors() = messages.none { it.severity == "ERROR" }

    override fun report() {
        // Don't print anything, but throw so compileProgram() stops and returns null
        if (!noErrors())
            throw ErrorsReportedException("")
    }

    override fun noErrorForLine(position: Position) =
        !messages.any { it.position?.line == position.line && it.severity != "INFO" }

    override fun printSingleError(errormessage: String) {
        messages.add(CompilerMessage("ERROR", errormessage, null))
    }

    fun getMessages() = messages.toList()
}
