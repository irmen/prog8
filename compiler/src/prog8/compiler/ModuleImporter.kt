package prog8.compiler

import com.github.michaelbull.result.*
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.SyntaxError
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.parser.Prog8Parser
import prog8.code.core.SourceCode
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*


class ModuleImporter(private val program: Program,
                     private val compilationTargetName: String,
                     val errors: IErrorReporter,
                     sourceDirs: List<String>) {

    private val sourcePaths: List<Path> = sourceDirs.map { Path(it) }

    fun importModule(filePath: Path): Result<Module, NoSuchFileException> {
        val currentDir = Path("").absolute()
        val searchIn = listOf(currentDir) + sourcePaths
        val candidates = searchIn
            .map { it.absolute().div(filePath).normalize().absolute() }
            .filter { it.exists() }
            .map { currentDir.relativize(it) }
            .map { if (it.isAbsolute) it else Path(".", "$it") }

        val srcPath = when (candidates.size) {
            0 -> return Err(NoSuchFileException(
                    file = filePath.normalize().toFile(),
                    reason = "Searched in $searchIn"))
            1 -> candidates.first()
            else -> candidates.first()  // when more candiates, pick the one from the first location
        }

        val source = SourceCode.File(srcPath)
        return Ok(importModule(source))
    }

    fun importLibraryModule(name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg("", name, 42u, position = Position("<<<implicit-import>>>", 0, 0, 0))
        ), Position("<<<implicit-import>>>", 0, 0, 0))
        return executeImportDirective(import, null)
    }

    private fun importModule(src: SourceCode) : Module {
        printImportingMessage(src.name, src.origin)
        val moduleAst = Prog8Parser.parseModule(src)
        program.addModule(moduleAst)

        // accept additional imports
        try {
            val lines = moduleAst.statements.toMutableList()
            lines.asSequence()
                .mapIndexed { i, it -> i to it }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(it.second as Directive, moduleAst) }
            moduleAst.statements = lines
            return moduleAst
        } catch (x: Exception) {
            // in case of error, make sure the module we're importing is no longer in the Ast
            program.removeModule(moduleAst)
            throw x
        }
    }

    private fun executeImportDirective(import: Directive, importingModule: Module?): Module? {
        if(import.directive!="%import" || import.args.size!=1)
            throw SyntaxError("invalid import directive", import.position)
        if(!import.args[0].str.isNullOrEmpty() || import.args[0].name==null)
            throw SyntaxError("%import requires unquoted module name", import.position)
        val moduleName = import.args[0].name!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name == moduleName }
        if (existing!=null)
            return existing

        // try internal library first
        val moduleResourceSrc = getModuleFromResource("$moduleName.p8", compilationTargetName)
        val importedModule =
            moduleResourceSrc.fold(
                success = {
                    importModule(it)
                },
                failure = {
                    // try filesystem next
                    val moduleSrc = getModuleFromFile(moduleName, importingModule)
                    moduleSrc.fold(
                        success = {
                            importModule(it)
                        },
                        failure = {
                            errors.err("no module found with name $moduleName. Searched in: $sourcePaths (and internal libraries)", import.position)
                            return null
                        }
                    )
                }
            )

        removeDirectivesFromImportedModule(importedModule)
        return importedModule
    }

    private fun removeDirectivesFromImportedModule(importedModule: Module) {
        // Most global directives don't apply for imported modules, so remove them
        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address")
        var directives = importedModule.statements.filterIsInstance<Directive>()
        importedModule.statements.removeAll(directives)
        directives = directives.filter{ it.directive !in moduleLevelDirectives }
        importedModule.statements.addAll(0, directives)
    }

    private fun getModuleFromResource(name: String, compilationTargetName: String): Result<SourceCode, NoSuchFileException> {
        val result =
            runCatching { SourceCode.Resource("/prog8lib/$compilationTargetName/$name") }
            .orElse { runCatching { SourceCode.Resource("/prog8lib/$name") }  }

        return result.mapError { NoSuchFileException(File(name)) }
    }

    private fun getModuleFromFile(name: String, importingModule: Module?): Result<SourceCode, NoSuchFileException> {
        val fileName = "$name.p8"
        val locations =
            if (importingModule == null) { // <=> imported from library module
                sourcePaths
            } else {
                val dropCurDir = if(sourcePaths.isNotEmpty() && sourcePaths[0].name == ".") 1 else 0
                sourcePaths.drop(dropCurDir) +
                listOf(Path(importingModule.position.file).parent ?: Path("")) +
                listOf(Path(".", "prog8lib"))
            }

        locations.forEach {
            try {
                return Ok(SourceCode.File(it.resolve(fileName)))
            } catch (_: NoSuchFileException) {
            }
        }

        return Err(NoSuchFileException(File("name")))
    }

    fun printImportingMessage(module: String, origin: String) {
        print(" importing '$module'  (from ${origin})")
        ansiEraseRestOfLine(false)
        print("\r")
    }

    companion object {
        fun ansiEraseRestOfLine(newline: Boolean) {
            print("\u001b[0K")
            if(newline)
                println()
        }
    }
}
