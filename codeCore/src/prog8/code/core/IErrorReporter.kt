package prog8.code.core

interface IErrorReporter {
    fun err(msg: String, position: Position)
    fun warn(msg: String, position: Position)
    fun undefined(symbol: List<String>, position: Position)
    fun noErrors(): Boolean
    fun report()
    fun finalizeNumErrors(numErrors: Int, numWarnings: Int) {
        if(numErrors>0)
            throw ErrorsReportedException("There are $numErrors errors and $numWarnings warnings.")
    }
}
