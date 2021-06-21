package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.IStringEncoding
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.base.Position
import prog8.ast.base.SyntaxError
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import kotlin.io.FileSystemException
import java.net.URL
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path   // TODO: use kotlin.io.paths.Path instead
import java.nio.file.Paths  // TODO: use kotlin.io.paths.Path instead


fun moduleName(fileName: Path) = fileName.toString().substringBeforeLast('.')

internal fun pathFrom(stringPath: String, vararg rest: String): Path  = FileSystems.getDefault().getPath(stringPath, *rest)


class ModuleImporter(private val program: Program,
                     private val encoder: IStringEncoding,
                     private val compilationTargetName: String,
                     private val libdirs: List<String>) {

    fun importModule(filePath: Path): Module {
        print("importing '${moduleName(filePath.fileName)}'")
        if(filePath.parent!=null) {
            var importloc = filePath.toString()
            val curdir = Paths.get("").toAbsolutePath().toString()
            if(importloc.startsWith(curdir))
                importloc = "." + importloc.substring(curdir.length)
            println(" (from '$importloc')")
        }
        else
            println("")

        val module = Prog8Parser.parseModule(SourceCode.fromPath(filePath))

        module.program = program
        module.linkParents(program.namespace)
        program.modules.add(module)

        // accept additional imports
        val lines = module.statements.toMutableList()
        lines.asSequence()
            .mapIndexed { i, it -> i to it }
            .filter { (it.second as? Directive)?.directive == "%import" }
            .forEach { executeImportDirective(it.second as Directive, filePath) }

        module.statements = lines
        return module
    }

    fun importLibraryModule(name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg("", name, 42, position = Position("<<<implicit-import>>>", 0, 0, 0))
        ), Position("<<<implicit-import>>>", 0, 0, 0))
        return executeImportDirective(import, Paths.get(""))
    }

    private fun importModule(stream: CharStream, modulePath: Path): Module {
        val parser = Prog8Parser
        val sourceText = stream.toString()
        val moduleAst = parser.parseModule(SourceCode.of(sourceText))
        moduleAst.program = program
        moduleAst.linkParents(program.namespace)
        program.modules.add(moduleAst)

        // accept additional imports
        val lines = moduleAst.statements.toMutableList()
        lines.asSequence()
                .mapIndexed { i, it -> i to it }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(it.second as Directive, modulePath) }

        moduleAst.statements = lines
        return moduleAst
    }

    private fun executeImportDirective(import: Directive, source: Path): Module? {
        if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
            throw SyntaxError("invalid import directive", import.position)
        val moduleName = import.args[0].name!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name == moduleName }
        if(existing!=null)
            return null

        val srcCode = tryGetModuleFromResource("$moduleName.p8", compilationTargetName)
        val importedModule =
            if (srcCode != null) { // found in resources
                    // load the module from the embedded resource
                println("importing '$moduleName' (library): ${srcCode.origin}")
                val path = Path.of(URL(srcCode.origin).file)
                importModule(srcCode.getCharStream(), path)
                } else {
                    val modulePath = tryGetModuleFromFile(moduleName, source, import.position)
                    importModule(modulePath)
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
            return SourceCode.fromResources("/prog8lib/$compilationTargetName/$name")
        } catch (e: FileSystemException) {
        }
        try {
            return SourceCode.fromResources("/prog8lib/$name")
        } catch (e: FileSystemException) {
        }
        return null
    }

    private fun tryGetModuleFromFile(name: String, source: Path, position: Position?): Path {
        val fileName = "$name.p8"
        val libpaths = libdirs.map {Path.of(it)}
        val locations =
            (if(source.toString().isEmpty()) libpaths else libpaths.drop(1) + listOf(source.parent ?: Path.of("."))) +
                listOf(Paths.get(Paths.get("").toAbsolutePath().toString(), "prog8lib"))

        locations.forEach {
            val file = pathFrom(it.toString(), fileName)
            if (Files.isReadable(file)) return file
        }

        throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: embedded libs and $locations)")
    }
}
