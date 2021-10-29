package prog8.compilerinterface

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
