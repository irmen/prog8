package prog8.compiler

import com.github.michaelbull.result.*
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.SyntaxError
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import prog8.code.core.IErrorReporter
import prog8.code.core.Position
import prog8.code.sanitize
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
import prog8.parser.Prog8Parser
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists


class ModuleImporter(private val program: Program,
                     private val compilationTargetName: String,
                     val errors: IErrorReporter,
                     sourceDirs: List<String>,
                     libraryDirs: List<String>,
                     val cwd: Path,
                     val quiet: Boolean,
                     val nostdlib: Boolean = false,
                     val traceImports: Boolean = false) {

    private val sourcePaths: List<Path> = sourceDirs.map { Path(it).sanitize() }.distinct()
    private val libraryPaths: List<Path> = libraryDirs.map { Path(it).sanitize() }.distinct()

    private fun trace(message: String) {
        if (traceImports) {
            println("[import-trace] $message")
        }
    }

    fun importMainModule(filePath: Path): Result<Module, NoSuchFileException> {
        val searchIn = (sourcePaths + listOf(Path("").absolute())).distinct()
        val normalizedFilePath = filePath.normalize()
        
        if (normalizedFilePath.exists()) {
            printCompileInfo(normalizedFilePath.toAbsolutePath())
            return Ok(importModule(ImportFileSystem.getFile(normalizedFilePath)))
        }

        for(path in searchIn) {
            val programPath = path.resolve(normalizedFilePath)
            if(programPath.exists()) {
                printCompileInfo(programPath)
                trace("Importing main module '$normalizedFilePath' from file: $programPath")
                val source = ImportFileSystem.getFile(programPath)
                return Ok(importModule(source))
            }
        }
        return Err(NoSuchFileException(
            file = normalizedFilePath.toFile(),
            reason = "Searched in $searchIn"))
    }

    private fun printCompileInfo(programPath: Path) {
        if(!quiet) {
            println("Compiling program ${cwd.toAbsolutePath().relativize(programPath)}")
            println("Compiler target: $compilationTargetName")
        }
    }

    fun importImplicitLibraryModule(name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg(name, 42u, position = Position("~implicit-import~", 0, 0, 0))
        ), Position("~implicit-import~", 0, 0, 0))
        return executeImportDirective(import, null)
    }

    private fun importModule(src: SourceCode) : Module {
        val moduleAst = Prog8Parser.parseModule(src)

        // Check if module already loaded (e.g., via symlink from different name)
        val existing = program.modules.firstOrNull { it.name == moduleAst.name }
        if (existing != null)
            return existing

        program.addModule(moduleAst)

        // accept additional imports
        try {
            val lines = moduleAst.statements.toMutableList()
            lines.asSequence()
                .mapIndexed { i, it -> i to it }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(it.second as Directive, moduleAst) }
            moduleAst.statements.clear()
            moduleAst.statements.addAll(lines)
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
        val moduleName = import.args[0].string!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name.equals(moduleName, ignoreCase = true) }
        if (existing!=null) {
            if(existing.name != moduleName) {
                errors.err("module import name '$moduleName' differs in case only from already known name '${existing.name}'", import.position)
                return null
            }
            return existing
        }

        // try filesystem first
        var importedModule = getModuleFromFilesystem(moduleName, importingModule, import.position, true)

        // try internal library (unless --nostdlib is active)
        if(importedModule == null && !nostdlib) {
            val moduleResourceSrc = getModuleFromResource("$moduleName.p8", compilationTargetName, importingModule?.name ?: "~implicit~")
            moduleResourceSrc.onOk {
                importedModule = importModule(it)
            }
        }

        // if still not found, report error
        if (importedModule == null)
            getModuleFromFilesystem(moduleName, importingModule, import.position, false)

        if(importedModule != null)
            removeDirectivesFromImportedModule(importedModule)
        return importedModule
    }

    private fun getModuleFromFilesystem(moduleName: String, importingModule: Module?, errorPosition: Position, suppressError: Boolean): Module? {
        val (moduleSrc, searchedPaths) = getModuleFromFile(moduleName, importingModule)
        return moduleSrc.fold(
            success = { importModule(it) },
            failure = {
                if (!suppressError) {
                    val requestedBy = importingModule?.name ?: "~implicit~"
                    val searchPaths = if(nostdlib) "$searchedPaths (internal libraries disabled)" else "$searchedPaths (and internal libraries)"
                    errors.err("no module found with name $moduleName (imported by '$requestedBy'). Searched in: $searchPaths", errorPosition)
                }
                null
            }
        )
    }

    private fun removeDirectivesFromImportedModule(importedModule: Module) {
        // Most global directives don't apply for imported modules, so remove them
        val moduleLevelDirectives = listOf("%output", "%launcher", "%zeropage", "%zpreserved", "%zpallowed", "%address", "%memtop")
        var directives = importedModule.statements.filterIsInstance<Directive>()
        importedModule.statements.removeAll(directives)
        directives = directives.filter{ it.directive !in moduleLevelDirectives }
        importedModule.statements.addAll(0, directives)
    }

    private fun getModuleFromResource(name: String, compilationTargetName: String, requestedBy: String): Result<SourceCode, NoSuchFileException> {
        val targetPath = "/prog8lib/$compilationTargetName/$name"
        val generalPath = "/prog8lib/$name"

        val result = runCatching { ImportFileSystem.getResource(targetPath) }
            .onOk { trace("Importing module '${name.removeSuffix(".p8")}' from resource: $targetPath (target-specific library) - imported by '$requestedBy'") }
            .orElse {
                runCatching { ImportFileSystem.getResource(generalPath) }
                .onOk { trace("Importing module '${name.removeSuffix(".p8")}' from resource: $generalPath (general library) - imported by '$requestedBy'") }
            }

        return result.mapError { NoSuchFileException(File(name)) }
    }

    private fun getModuleFromFile(name: String, importingModule: Module?): Pair<Result<SourceCode, NoSuchFileException>, List<Path>> {
        val fileName = "$name.p8"
        val requestedBy = importingModule?.name ?: "~implicit~"

        val normalLocations =
            if (importingModule == null) {
                (sourcePaths + listOf(Path("").absolute())).distinct()
            } else {
                val pathFromImportingModule = (Path(importingModule.position.file).parent ?: Path("")).sanitize()
                val cwd = Path("").absolute()
                (sourcePaths + listOf(pathFromImportingModule, cwd)).distinct()
            }

        val libraryPathsSet = libraryPaths.toSet()
        val searched = mutableListOf<Path>()
        normalLocations.forEach {
            searched.add(it)
            try {
                val file = it.resolve(fileName)
                val isLib = file.parent in libraryPathsSet   // a file may be found here before reaching the libraryPaths loop, but still resides in a library dir (customtargets)
                val source = ImportFileSystem.getFile(file, isLib)
                val origin = when {
                    it == Path("").absolute() -> "CWD"
                    it in sourcePaths -> "srcdirs"
                    else -> "neighboring directory"
                }
                trace("Importing module '$name' from file: $file (found in $origin) - imported by '$requestedBy'")
                return Ok(source) to searched
            } catch (_: NoSuchFileException) {
            }
        }

        libraryPaths.forEach {
            searched.add(it)
            try {
                val file = it.resolve(fileName)
                val source = ImportFileSystem.getFile(file, true)
                trace("Importing module '$name' from file: $file (found in libdirs) - imported by '$requestedBy'")
                return Ok(source) to searched
            } catch (_: NoSuchFileException) {
            }
        }

        return Err(NoSuchFileException(File(name))) to searched
    }
}
