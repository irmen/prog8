package il65

import il65.ast.*
import il65.parser.il65Lexer
import il65.parser.il65Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.collections.HashMap


class MyTokenStream(lexer: Lexer) : CommonTokenStream(lexer) {

    data class Comment(val type: String, val line: Int, val comment: String)

    fun commentTokens() : List<Comment> {
        // extract the comments
        val commentTokenChannel = il65Lexer.channelNames.indexOf("HIDDEN")
        val theLexer = tokenSource as Lexer
        return  get(0, size())
                .filter { it.channel == commentTokenChannel }
                .map {
                    Comment(theLexer.vocabulary.getSymbolicName(it.type),
                            it.line, it.text.substringAfter(';').trim())
                }
    }
}

class ParsingFailedError(override var message: String) : Exception(message)


private val importedModules : HashMap<String, Module> = hashMapOf()



fun loadModule(filePath: Path) : Module {
    println("importing '${filePath.fileName}'  (from ${filePath.parent})...")
    if(!Files.isReadable(filePath))
        throw ParsingFailedError("No such file: $filePath")

    val moduleName = fileNameWithoutSuffix(filePath)
    val input = CharStreams.fromPath(filePath)
    val lexer = il65Lexer(input)
    val tokens = MyTokenStream(lexer)
    val parser = il65Parser(tokens)
    val parseTree = parser.module()
    if(parser.numberOfSyntaxErrors > 0)
        throw ParsingFailedError("There are ${parser.numberOfSyntaxErrors} syntax errors in '${filePath.fileName}'.")

    // TODO the comments:
    // tokens.commentTokens().forEach { println(it) }

    // convert to Ast and optimize
    val moduleAst = parseTree.toAst(moduleName,true)
    moduleAst.optimize()
    moduleAst.linkParents()
    moduleAst.checkValid()
    importedModules[moduleAst.name] = moduleAst

    // process imports
    val lines = moduleAst.lines.toMutableList()
    val imports = lines
            .mapIndexed { i, it -> Pair(i, it) }
            .filter { (it.second as? Directive)?.directive == "%import" }
            .map { Pair(it.first, executeImportDirective(it.second as Directive, filePath)) }

    imports.reversed().forEach {
        if(it.second==null) {
            // this import was already satisfied. just remove this line.
            lines.removeAt(it.first)
        } else {
            // merge imported lines at this spot
            lines.addAll(it.first, it.second!!.lines)
        }
    }

    moduleAst.lines = lines
    return moduleAst
}


fun fileNameWithoutSuffix(filePath: Path) =
        filePath.fileName.toString().substringBeforeLast('.')


fun discoverImportedModule(name: String, importedFrom: Path): Path {
    val tryName = Paths.get(importedFrom.parent.toString(), name + ".ill")
    if(Files.exists(tryName))
        return tryName
    else
        throw ParsingFailedError("No such module source file: $tryName")
}


fun executeImportDirective(import: Directive, importedFrom: Path): Module? {
    if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
        throw SyntaxError("invalid import directive", import.position)
    val moduleName = import.args[0].name!!
    if(importedModules.containsKey(moduleName))
        return null

    val modulePath = discoverImportedModule(moduleName, importedFrom)
    val importedModule = loadModule(modulePath)
    importedModule.checkImportValid()

    return importedModule
}


fun main(args: Array<String>) {
    try {
        val filepath = Paths.get(args[0]).normalize()
        val moduleAst = loadModule(filepath)
        moduleAst.optimize()        // one final global optimization
        moduleAst.linkParents()     // re-link parents in final configuration
        moduleAst.checkValid()      // check if final tree is valid

        // todo compile to asm...
        moduleAst.lines.forEach {
            println(it)
        }
    } catch(sx: SyntaxError) {
        sx.printError()
    } catch (px: ParsingFailedError) {
        System.err.println(px.message)
    }
}


