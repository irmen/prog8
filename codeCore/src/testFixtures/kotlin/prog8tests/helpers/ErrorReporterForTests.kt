package prog8tests.helpers

import prog8.code.core.IErrorReporter
import prog8.code.core.Position


/**
 * Test implementation of IErrorReporter that collects errors/warnings/infos in memory.
 * 
 * @param throwExceptionAtReportIfErrors If true, report() will throw an exception when errors exist
 * @param keepMessagesAfterReporting If false, clear() is called after report()
 */
class ErrorReporterForTests(
    private val throwExceptionAtReportIfErrors: Boolean = true,
    private val keepMessagesAfterReporting: Boolean = false
) : IErrorReporter {

    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val infos = mutableListOf<String>()

    override fun err(msg: String, position: Position) {
        val text = "${position.toClickableStr()} $msg"
        if (text !in errors)
            errors.add(text)
    }

    override fun warn(msg: String, position: Position) {
        val text = "${position.toClickableStr()} $msg"
        if (text !in warnings)
            warnings.add(text)
    }

    override fun info(msg: String, position: Position) {
        val text = "${position.toClickableStr()} $msg"
        if (text !in infos)
            infos.add(text)
    }

    override fun undefined(symbol: List<String>, suggestImport: Boolean, position: Position) {
        err("undefined symbol: ${symbol.joinToString(".")}", position)
    }

    override fun noErrors(): Boolean = errors.isEmpty()
    
    override fun noErrorForLine(position: Position): Boolean =
        !errors.any { ":${position.line}:" in it }
    
    override fun printSingleError(errormessage: String) {
        // prints nothing in tests
    }

    override fun report() {
        infos.forEach { println("UNITTEST COMPILATION REPORT: INFO: $it") }
        warnings.forEach { println("UNITTEST COMPILATION REPORT: WARNING: $it") }
        errors.forEach { println("UNITTEST COMPILATION REPORT: ERROR: $it") }
        if (throwExceptionAtReportIfErrors)
            finalizeNumErrors(errors.size, warnings.size, infos.size)
        if (!keepMessagesAfterReporting) {
            clear()
        }
    }

    fun clear() {
        errors.clear()
        warnings.clear()
        infos.clear()
    }
}
