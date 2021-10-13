package prog8.compiler

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.base.SyntaxError
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import prog8.parser.Prog8Parser
import prog8.parser.SourceCode
import java.nio.file.Path
import kotlin.io.path.*


class ModuleImporter(private val program: Program,
                     private val compilationTargetName: String,
                     val errors: IErrorReporter,
                     sourceDirs: List<String>) {

    private val libpaths: List<Path> = sourceDirs.map { Path(it) }

    fun importModule(filePath: Path): Result<Module, NoSuchFileException> {
        val currentDir = Path("").absolute()
        val searchIn = listOf(currentDir) + libpaths
        val candidates = searchIn
            .map { it.absolute().div(filePath).normalize().absolute() }
            .filter { it.exists() }
            .map { currentDir.relativize(it) }
            .map { if (it.isAbsolute) it else Path(".", "$it") }

        val srcPath = when (candidates.size) {
            0 -> return Err(NoSuchFileException(
                    file = filePath.normalize().toFile(),
                    reason = "searched in $searchIn"))
            1 -> candidates.first()
            else -> candidates.first()  // TODO: report error if more than 1 candidate?
        }

        val logMsg = "importing '${filePath.nameWithoutExtension}' (from file $srcPath)"
        println(logMsg)

        return Ok(importModule(SourceCode.File(srcPath)))
    }

    fun importLibraryModule(name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg("", name, 42, position = Position("<<<implicit-import>>>", 0, 0, 0))
        ), Position("<<<implicit-import>>>", 0, 0, 0))
        return executeImportDirective(import, null)
    }

    //private fun importModule(stream: CharStream, modulePath: Path, isLibrary: Boolean): Module {
    private fun importModule(src: SourceCode) : Module {
        val moduleAst = Prog8Parser.parseModule(src)
        program.addModule(moduleAst)

        // accept additional imports
        val lines = moduleAst.statements.toMutableList()
        lines.asSequence()
                .mapIndexed { i, it -> i to it }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(it.second as Directive, moduleAst) }

        moduleAst.statements = lines
        return moduleAst
    }

    private fun executeImportDirective(import: Directive, importingModule: Module?): Module? {
        if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
            throw SyntaxError("invalid import directive", import.position)
        val moduleName = import.args[0].name!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name == moduleName }
        if (existing!=null)
            return null // TODO: why return null instead of Module instance?

        var srcCode = tryGetModuleFromResource("$moduleName.p8", compilationTargetName)
        val importedModule =
            if (srcCode != null) {
                println("importing '$moduleName' (from internal ${srcCode.origin})")
                importModule(srcCode)
            } else {
                srcCode = tryGetModuleFromFile(moduleName, importingModule)
                if (srcCode == null) {
                    errors.err("imported file not found: $moduleName.p8", import.position)
                    return null
                    //throw NoSuchFileException(File("$moduleName.p8"))
                }
                importModule(srcCode)
            }

        removeDirectivesFromImportedModule(importedModule)
        return importedModule
    }

    private fun removeDirectivesFromImportedModule(importedModule: Module) {
        // Most global directives don't apply for imported modules, so remove them
        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%address", "%target")
        var directives = importedModule.statements.filterIsInstance<Directive>()
        importedModule.statements.removeAll(directives)
        directives = directives.filter{ it.directive !in moduleLevelDirectives }
        importedModule.statements.addAll(0, directives)
    }

    private fun tryGetModuleFromResource(name: String, compilationTargetName: String): SourceCode? {
        // try target speficic first
        try {
            return SourceCode.Resource("/prog8lib/$compilationTargetName/$name")
        } catch (e: FileSystemException) {
        }
        try {
            return SourceCode.Resource("/prog8lib/$name")
        } catch (e: FileSystemException) {
        }
        return null
    }

    private fun tryGetModuleFromFile(name: String, importingModule: Module?): SourceCode? {
        val fileName = "$name.p8"
        val locations =
            if (importingModule == null) { // <=> imported from library module
                libpaths
            } else {
                libpaths.drop(1) +  // TODO: why drop the first?
                // FIXME: won't work until Prog8Parser is fixed s.t. it fully initialzes the modules it returns
                listOf(Path(importingModule.position.file).parent ?: Path("")) +
                listOf(Path(".", "prog8lib"))
            }

        locations.forEach {
            try {
                return SourceCode.File(it.resolve(fileName))
            } catch (e: NoSuchFileException) {
            }
        }

        //throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: embedded libs and $locations)")
        return null
    }
}
