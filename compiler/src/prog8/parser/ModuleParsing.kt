package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.Module
import prog8.ast.Program
import prog8.ast.antlr.toAst
import prog8.ast.base.Position
import prog8.ast.base.SyntaxError
import prog8.ast.base.checkImportedValid
import prog8.ast.statements.Directive
import prog8.ast.statements.DirectiveArg
import prog8.compiler.target.CompilationTarget
import prog8.pathFrom
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


internal class ParsingFailedError(override var message: String) : Exception(message)


internal class CustomLexer(val modulePath: Path, input: CharStream?) : prog8Lexer(input)


internal fun moduleName(fileName: Path) = fileName.toString().substringBeforeLast('.')


internal class ModuleImporter {

    internal fun importModule(program: Program, filePath: Path): Module {
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
        if(!Files.isReadable(filePath))
            throw ParsingFailedError("No such file: $filePath")

        val input = CharStreams.fromPath(filePath)
        return importModule(program, input, filePath, false)
    }

    internal fun importLibraryModule(program: Program, name: String): Module? {
        val import = Directive("%import", listOf(
                DirectiveArg("", name, 42, position = Position("<<<implicit-import>>>", 0, 0, 0))
        ), Position("<<<implicit-import>>>", 0, 0, 0))
        return executeImportDirective(program, import, Paths.get(""))
    }

    private class MyErrorListener: ConsoleErrorListener() {
        var  numberOfErrors: Int = 0
        override fun syntaxError(recognizer: Recognizer<*, *>?, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
            numberOfErrors++
            when (recognizer) {
                is CustomLexer -> System.err.println("${recognizer.modulePath}:$line:$charPositionInLine: $msg")
                is prog8Parser -> System.err.println("${recognizer.inputStream.sourceName}:$line:$charPositionInLine: $msg")
                else -> System.err.println("$line:$charPositionInLine $msg")
            }
        }
    }

    private fun importModule(program: Program, stream: CharStream, modulePath: Path, isLibrary: Boolean): Module {
        val moduleName = moduleName(modulePath.fileName)
        val lexer = CustomLexer(modulePath, stream)
        lexer.removeErrorListeners()
        val lexerErrors = MyErrorListener()
        lexer.addErrorListener(lexerErrors)
        val tokens = CommentHandlingTokenStream(lexer)
        val parser = prog8Parser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(MyErrorListener())
        val parseTree = parser.module()
        val numberOfErrors = parser.numberOfSyntaxErrors + lexerErrors.numberOfErrors
        if(numberOfErrors > 0)
            throw ParsingFailedError("There are $numberOfErrors errors in '$moduleName'.")

        // You can do something with the parsed comments:
        // tokens.commentTokens().forEach { println(it) }

        // convert to Ast
        val moduleAst = parseTree.toAst(moduleName, isLibrary, modulePath)
        moduleAst.program = program
        moduleAst.linkParents(program.namespace)
        program.modules.add(moduleAst)

        // accept additional imports
        val lines = moduleAst.statements.toMutableList()
        lines.asSequence()
                .mapIndexed { i, it -> Pair(i, it) }
                .filter { (it.second as? Directive)?.directive == "%import" }
                .forEach { executeImportDirective(program, it.second as Directive, modulePath) }

        moduleAst.statements = lines
        return moduleAst
    }

    private fun discoverImportedModuleFile(name: String, source: Path, position: Position?): Path {
        val fileName = "$name.p8"
        val locations = if(source.toString().isEmpty()) mutableListOf<Path>() else mutableListOf(source.parent)

        val propPath = System.getProperty("prog8.libdir")
        if(propPath!=null)
            locations.add(pathFrom(propPath))
        val envPath = System.getenv("PROG8_LIBDIR")
        if(envPath!=null)
            locations.add(pathFrom(envPath))
        locations.add(Paths.get(Paths.get("").toAbsolutePath().toString(), "prog8lib"))

        locations.forEach {
            val file = pathFrom(it.toString(), fileName)
            if (Files.isReadable(file)) return file
        }

        throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: embedded libs and $locations)")
    }

    private fun executeImportDirective(program: Program, import: Directive, source: Path): Module? {
        if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
            throw SyntaxError("invalid import directive", import.position)
        val moduleName = import.args[0].name!!
        if("$moduleName.p8" == import.position.file)
            throw SyntaxError("cannot import self", import.position)

        val existing = program.modules.singleOrNull { it.name == moduleName }
        if(existing!=null)
            return null

        val resource = tryGetEmbeddedResource("$moduleName.p8")
        val importedModule =
                if(resource!=null) {
                    // load the module from the embedded resource
                    resource.use {
                        println("importing '$moduleName' (library)")
                        importModule(program, CharStreams.fromStream(it), Paths.get("@embedded@/$moduleName"), true)
                    }
                } else {
                    val modulePath = discoverImportedModuleFile(moduleName, source, import.position)
                    importModule(program, modulePath)
                }

        importedModule.checkImportedValid()
        return importedModule
    }

    private fun tryGetEmbeddedResource(name: String): InputStream? {
        val target = CompilationTarget.instance.name
        val targetSpecific = object{}.javaClass.getResourceAsStream("/prog8lib/$target/$name")
        if(targetSpecific!=null)
            return targetSpecific
        return object{}.javaClass.getResourceAsStream("/prog8lib/$name")
    }
}
