package prog8.codegen.new6502

import prog8.code.core.IErrorReporter
import prog8.code.core.Position

internal class StderrErrorReporter : IErrorReporter {
    override fun err(msg: String, position: Position) { System.err.println("ERROR: $msg") }
    override fun warn(msg: String, position: Position) { System.err.println("WARNING: $msg") }
    override fun info(msg: String, position: Position) {}
    override fun undefined(symbol: List<String>, suggestImport: Boolean, position: Position) {}
    override fun noErrors(): Boolean = true
    override fun report() {}
    override fun noErrorForLine(position: Position): Boolean = true
    override fun printSingleError(errormessage: String) { System.err.println(errormessage) }
}
