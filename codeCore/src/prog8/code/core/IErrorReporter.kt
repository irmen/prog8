package prog8.code.core

interface IErrorReporter {
    fun err(msg: String, position: Position)
    fun warn(msg: String, position: Position)
    fun info(msg: String, position: Position)
    fun undefined(symbol: List<String>, suggestImport: Boolean=false, position: Position)
    fun noErrors(): Boolean
    fun report()
    fun finalizeNumErrors(numErrors: Int, numWarnings: Int, numInfos: Int) {
        if(numErrors>0)
            throw ErrorsReportedException("There are $numErrors errors, $numWarnings warnings, and $numInfos infos.")
    }

    fun noErrorForLine(position: Position): Boolean

    fun printSingleError(errormessage: String)
}
