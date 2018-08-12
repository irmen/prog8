package il65

import il65.ast.Directive
import il65.ast.Module
import il65.ast.SyntaxError
import il65.ast.toAst
import il65.parser.il65Lexer
import il65.parser.il65Parser
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Lexer
import java.nio.file.Paths


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


fun loadModule(filename: String) : Module {

    val filePath = Paths.get(filename).normalize()
    val fileLocation = filePath.parent
    val fileName = filePath.fileName

    println("importing '$fileName'  (from $fileLocation)...")
    val input = CharStreams.fromPath(filePath)
    val lexer = il65Lexer(input)
    val tokens = MyTokenStream(lexer)
    val parser = il65Parser(tokens)
    val parseTree = parser.module()
    if(parser.numberOfSyntaxErrors > 0)
        throw ParsingFailedError("There are ${parser.numberOfSyntaxErrors} syntax errors in '$fileName'.")

    // TODO the comments:
    // tokens.commentTokens().forEach { println(it) }

    // convert to Ast (optimizing this is done as a final step)
    var moduleAst = parseTree.toAst(fileName.toString(),true)
    val checkResult = moduleAst.checkValid()
    checkResult.forEach { it.printError() }
    if(checkResult.isNotEmpty())
        throw ParsingFailedError("There are ${checkResult.size} syntax errors in '$fileName'.")

    // process imports
    val lines = moduleAst.lines.toMutableList()
    val imports = lines
            .mapIndexed { i, it -> Pair(i, it) }
            .filter { (it.second as? Directive)?.directive == "%import" }
            .map { Pair(it.first, executeImportDirective(it.second as Directive)) }

    imports.reversed().forEach {
            println("IMPORT [in ${moduleAst.name}]: $it")
    }

    moduleAst.lines = lines
    return moduleAst
}


fun executeImportDirective(import: Directive): Module {
    if(import.directive!="%import" || import.args.size!=1 || import.args[0].name==null)
        throw SyntaxError("invalid import directive", import)

    return Module("???", emptyList(), null)   // TODO
}


fun main(args: Array<String>) {
    println("Reading source file: ${args[0]}")
    try {
        val moduleAst = loadModule(args[0]).optimized()

        moduleAst.lines.map {
            println(it)
        }
    } catch(sx: SyntaxError) {
        sx.printError()
    } catch (px: ParsingFailedError) {
        System.err.println(px.message)
    }
}


