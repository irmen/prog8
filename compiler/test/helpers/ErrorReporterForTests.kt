package prog8tests.helpers

import prog8.ast.base.Position
import prog8.compilerinterface.IErrorReporter

internal class ErrorReporterForTests(private val throwExceptionAtReportIfErrors: Boolean=true): IErrorReporter {


    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    override fun err(msg: String, position: Position) {
        errors.add("${position.toClickableStr()} $msg")
    }

    override fun warn(msg: String, position: Position) {
        warnings.add("${position.toClickableStr()} $msg")
    }

    override fun noErrors(): Boolean  = errors.isEmpty()

    override fun report() {
        warnings.forEach { println("UNITTEST COMPILATION REPORT: WARNING: $it") }
        errors.forEach { println("UNITTEST COMPILATION REPORT: ERROR: $it") }
        if(throwExceptionAtReportIfErrors)
            finalizeNumErrors(errors.size, warnings.size)
        errors.clear()
        warnings.clear()
    }
}
