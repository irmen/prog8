package il65.ast

import il65.ParsingFailedError


fun Module.checkImportValid() {
    val checker = ImportedAstChecker()
    this.process(checker)
    val result = checker.result()
    result.forEach {
        it.printError()
    }
    if(result.isNotEmpty())
        throw ParsingFailedError("There are ${result.size} errors in module '$name'.")
}


class ImportedAstChecker : IAstProcessor {
    private val checkResult: MutableList<SyntaxError> = mutableListOf()

    fun result(): List<SyntaxError> {
        return checkResult
    }

    override fun process(module: Module) {
        val newLines : MutableList<IStatement> = mutableListOf()
        module.lines.forEach {
            val stmt = it.process(this)
            if(stmt is Directive) {
                if(stmt.parent is Module) {
                    when(stmt.directive) {
                        "%output", "%launcher", "%zp", "%address" ->
                            println("${stmt.position} Warning: ignoring module directive because it was imported: ${stmt.directive}")
                        else ->
                            newLines.add(stmt)
                    }
                }
            }
            else newLines.add(stmt)
        }
        module.lines = newLines
    }
}
