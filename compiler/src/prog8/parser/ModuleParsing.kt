package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.*
import prog8.compiler.LauncherType
import prog8.compiler.OutputType
import prog8.determineCompilationOptions
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*


class ParsingFailedError(override var message: String) : Exception(message)


private val importedModules : HashMap<String, Module> = hashMapOf()


private class LexerErrorListener: BaseErrorListener() {
    var  numberOfErrors: Int = 0
    override fun syntaxError(p0: Recognizer<*, *>?, p1: Any?, p2: Int, p3: Int, p4: String?, p5: RecognitionException?) {
        numberOfErrors++
    }
}


fun importModule(stream: CharStream, moduleName: String, isLibrary: Boolean): Module {
    val lexer = prog8Lexer(stream)
    val lexerErrors = LexerErrorListener()
    lexer.addErrorListener(lexerErrors)
    val tokens = CommentHandlingTokenStream(lexer)
    val parser = prog8Parser(tokens)
    val parseTree = parser.module()
    val numberOfErrors = parser.numberOfSyntaxErrors + lexerErrors.numberOfErrors
    if(numberOfErrors > 0)
        throw ParsingFailedError("There are $numberOfErrors errors in '$moduleName.p8'.")

    // You can do something with the parsed comments:
    // tokens.commentTokens().forEach { println(it) }

    // convert to Ast
    val moduleAst = parseTree.toAst(moduleName, isLibrary, Paths.get(stream.sourceName))
    importedModules[moduleAst.name] = moduleAst

    // process imports
    val lines = moduleAst.statements.toMutableList()
    if(!moduleAst.position.file.startsWith("c64utils.") && !moduleAst.isLibraryModule) {
        // if the output is a PRG or BASIC program, include the c64utils library
        val compilerOptions = determineCompilationOptions(moduleAst)
        if(compilerOptions.launcher==LauncherType.BASIC || compilerOptions.output==OutputType.PRG) {
            lines.add(0, Directive("%import", listOf(DirectiveArg(null, "c64utils", null, moduleAst.position)), moduleAst.position))
        }
    }
    // always import the prog8lib and math compiler libraries
    if(!moduleAst.position.file.startsWith("math."))
        lines.add(0, Directive("%import", listOf(DirectiveArg(null, "math", null, moduleAst.position)), moduleAst.position))
    if(!moduleAst.position.file.startsWith("prog8lib."))
        lines.add(0, Directive("%import", listOf(DirectiveArg(null, "prog8lib", null, moduleAst.position)), moduleAst.position))

    val imports = lines
            .asSequence()
            .mapIndexed { i, it -> Pair(i, it) }
            .filter { (it.second as? Directive)?.directive == "%import" }
            .map { Pair(it.first, executeImportDirective(it.second as Directive, Paths.get("$moduleName.p8"))) }
            .toList()

    imports.reversed().forEach {
        if(it.second==null) {
            // this import was already satisfied. just remove this line.
            lines.removeAt(it.first)
        } else {
            // merge imported lines at this spot
            lines.addAll(it.first, it.second!!.statements)
        }
    }

    moduleAst.statements = lines
    return moduleAst
}


fun importModule(filePath: Path) : Module {
    print("importing '${filePath.fileName}'")
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

    val moduleName = filePath.fileName.toString().substringBeforeLast('.')
    val input = CharStreams.fromPath(filePath)
    return importModule(input, moduleName, filePath.parent==null)
}


private fun discoverImportedModuleFile(name: String, importedFrom: Path, position: Position?): Path {
    val fileName = "$name.p8"
    val locations = mutableListOf(Paths.get(importedFrom.parent.toString()))

    val propPath = System.getProperty("prog8.libdir")
    if(propPath!=null)
        locations.add(Paths.get(propPath))
    val envPath = System.getenv("PROG8_LIBDIR")
    if(envPath!=null)
        locations.add(Paths.get(envPath))
    locations.add(Paths.get(Paths.get("").toAbsolutePath().toString(), "prog8lib"))

    locations.forEach {
        val file = Paths.get(it.toString(), fileName)
        if (Files.isReadable(file)) return file
    }

    throw ParsingFailedError("$position Import: no module source file '$fileName' found  (I've looked in: $locations)")
}

private fun executeImportDirective(import: Directive, importedFrom: Path): Module? {
    if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
        throw SyntaxError("invalid import directive", import.position)
    val moduleName = import.args[0].name!!
    if("$moduleName.p8" == import.position.file)
        throw SyntaxError("cannot import self", import.position)
    if(importedModules.containsKey(moduleName))
        return null

    val resource = tryGetEmbeddedResource(moduleName+".p8")
    val importedModule =
        if(resource!=null) {
            // load the module from the embedded resource
            resource.use {
                println("importing '$moduleName' (embedded library)")
                importModule(CharStreams.fromStream(it), moduleName, true)
            }
        } else {
            val modulePath = discoverImportedModuleFile(moduleName, importedFrom, import.position)
            importModule(modulePath)
        }

    importedModule.checkImportedValid()
    return importedModule
}

fun tryGetEmbeddedResource(name: String): InputStream? {
    return object{}.javaClass.getResourceAsStream("/prog8lib/$name")
}
