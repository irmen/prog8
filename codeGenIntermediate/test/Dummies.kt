import prog8.code.core.*


internal object DummyMemsizer : IMemSizer {
    override fun memorySize(dt: DataType, numElements: Int?): Int {
        if(dt.isPointerArray)
            return 2 * numElements!!
        else if(dt.isArray || dt.isSplitWordArray) {
            require(numElements!=null)
            return when(dt.sub) {
                BaseDataType.BOOL, BaseDataType.BYTE, BaseDataType.UBYTE -> numElements
                BaseDataType.UWORD, BaseDataType.WORD -> numElements*2
                BaseDataType.LONG -> numElements*4
                BaseDataType.FLOAT -> numElements*5
                else -> throw IllegalArgumentException("invalid sub type")
            }
        }
        return when {
            dt.isByteOrBool -> 1 * (numElements ?: 1)
            dt.isLong -> 4 * (numElements ?: 1)
            dt.isFloat -> 5 * (numElements ?: 1)
            else -> 2 * (numElements ?: 1)
        }
    }

    override fun memorySize(dt: BaseDataType): Int {
        return memorySize(DataType.forDt(dt), null)
    }
}

internal object DummyStringEncoder : IStringEncoding {
    override val defaultEncoding: Encoding = Encoding.ISO

    override fun encodeString(str: String, encoding: Encoding): List<UByte> {
        return emptyList()
    }

    override fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String {
        return ""
    }
}

internal class ErrorReporterForTests(private val throwExceptionAtReportIfErrors: Boolean=true, private val keepMessagesAfterReporting: Boolean=false): IErrorReporter {

    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val infos = mutableListOf<String>()

    override fun err(msg: String, position: Position) {
        val text = "${position.toClickableStr()} $msg"
        if(text !in errors)
            errors.add(text)
    }

    override fun warn(msg: String, position: Position) {
        val text = "${position.toClickableStr()} $msg"
        if(text !in warnings)
            warnings.add(text)
    }

    override fun info(msg: String, position: Position) {
        val text = "${position.toClickableStr()} $msg"
        if(text !in infos)
            infos.add(text)
    }

    override fun undefined(symbol: List<String>, suggestImport: Boolean, position: Position) {
        err("undefined symbol: ${symbol.joinToString(".")}", position)
    }

    override fun noErrors(): Boolean  = errors.isEmpty()
    override fun noErrorForLine(position: Position) = !errors.any { ":${position.line}:" in it }
    override fun printSingleError(errormessage: String) { /* prints nothing in tests */ }

    override fun report() {
        infos.forEach { println("UNITTEST COMPILATION REPORT: INFO: $it") }
        warnings.forEach { println("UNITTEST COMPILATION REPORT: WARNING: $it") }
        errors.forEach { println("UNITTEST COMPILATION REPORT: ERROR: $it") }
        if(throwExceptionAtReportIfErrors)
            finalizeNumErrors(errors.size, warnings.size, infos.size)
        if(!keepMessagesAfterReporting) {
            clear()
        }
    }

    fun clear() {
        errors.clear()
        warnings.clear()
        infos.clear()
    }
}
