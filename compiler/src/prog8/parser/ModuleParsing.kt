package prog8.parser

import org.antlr.v4.runtime.*
import prog8.ast.*
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths


class ParsingFailedError(override var message: String) : Exception(message)


private class LexerErrorListener: BaseErrorListener() {
    var  numberOfErrors: Int = 0
    override fun syntaxError(p0: Recognizer<*, *>?, p1: Any?, p2: Int, p3: Int, p4: String?, p5: RecognitionException?) {
        numberOfErrors++
    }
}


internal class CustomLexer(val modulePath: Path, input: CharStream?) : prog8Lexer(input)


fun importModule(program: Program, filePath: Path): Module {
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

    val input = CharStreams.fromPath(filePath)
    val module = importModule(program, input, filePath, filePath.parent==null)
    return module
}

fun importLibraryModule(program: Program, name: String): Module? {
    val import = Directive("%import", listOf(
            DirectiveArg("", name, 42, position = Position("<<<implicit-import>>>", 0, 0 ,0))
    ), Position("<<<implicit-import>>>", 0, 0 ,0))
    return executeImportDirective(program, import, Paths.get(""))
}

private fun importModule(program: Program, stream: CharStream, modulePath: Path, isLibrary: Boolean): Module {
    val moduleName = modulePath.fileName
    val lexer = CustomLexer(modulePath, stream)
    val lexerErrors = LexerErrorListener()
    lexer.addErrorListener(lexerErrors)
    val tokens = CommentHandlingTokenStream(lexer)
    val parser = prog8Parser(tokens)
    val parseTree = parser.module()
    val numberOfErrors = parser.numberOfSyntaxErrors + lexerErrors.numberOfErrors
    if(numberOfErrors > 0)
        throw ParsingFailedError("There are $numberOfErrors errors in '$moduleName'.")

    // You can do something with the parsed comments:
    // tokens.commentTokens().forEach { println(it) }

    // convert to Ast
    val moduleAst = parseTree.toAst(moduleName.toString(), isLibrary, modulePath)
    moduleAst.program = program
    moduleAst.linkParents()
    program.modules.add(moduleAst)

    // process imports
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
    val locations = mutableListOf(Paths.get(source.parent.toString()))

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

private fun executeImportDirective(program: Program, import: Directive, source: Path): Module? {
    if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
        throw SyntaxError("invalid import directive", import.position)
    val moduleName = import.args[0].name!!
    if("$moduleName.p8" == import.position.file)
        throw SyntaxError("cannot import self", import.position)

    val existing = program.modules.singleOrNull { it.name == moduleName }
    if(existing!=null)
        return null

    val resource = tryGetEmbeddedResource(moduleName+".p8")
    val importedModule =
        if(resource!=null) {
            // load the module from the embedded resource
            resource.use {
                if(import.args[0].int==42)
                    print("automatically ")
                println("importing '$moduleName' (embedded library)")
                importModule(program, CharStreams.fromStream(it), Paths.get("@embedded@/$moduleName"), true)
            }
        } else {
            val modulePath = discoverImportedModuleFile(moduleName, source, import.position)
            importModule(program, modulePath)
        }

    importedModule.checkImportedValid()
    return importedModule
}

fun tryGetEmbeddedResource(name: String): InputStream? {
    return object{}.javaClass.getResourceAsStream("/prog8lib/$name")
}
