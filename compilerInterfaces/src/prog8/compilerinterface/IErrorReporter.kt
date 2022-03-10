package prog8.compilerinterface

interface IErrorReporter {
    fun err(msg: String, position: Position)
    fun warn(msg: String, position: Position)
    fun noErrors(): Boolean
    fun report()
    fun finalizeNumErrors(numErrors: Int, numWarnings: Int) {
        if(numErrors>0)
            throw AbortCompilation("There are $numErrors errors and $numWarnings warnings.")
    }
}

