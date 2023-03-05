package prog8tests.codegencpu6502

import prog8.code.core.*


internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType) = when(dt) {
        in ByteDatatypes -> 1
        DataType.FLOAT -> 5
        else -> 2
    }
    override fun memorySize(arrayDt: DataType, numElements: Int) = when(arrayDt) {
        DataType.ARRAY_UW -> numElements*2
        DataType.ARRAY_W -> numElements*2
        DataType.ARRAY_F -> numElements*5
        else -> numElements
    }
}

internal object DummyStringEncoder : IStringEncoding {
    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String {
        return ""
    }
}

internal class ErrorReporterForTests(private val throwExceptionAtReportIfErrors: Boolean=true, private val keepMessagesAfterReporting: Boolean=false):
    IErrorReporter {

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
        if(!keepMessagesAfterReporting) {
            clear()
        }
    }

    fun clear() {
        errors.clear()
        warnings.clear()
    }
}