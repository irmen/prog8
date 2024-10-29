package prog8beanshell

import bsh.FileReader
import bsh.Interpreter


class BeanshellInterpreter {

    fun run(symbols: Map<String, Any>) {
        val interpreter = Interpreter(CommandLineReader(FileReader(System.`in`)), System.out, System.err, true)
        interpreter.setExitOnEOF(false)
        interpreter.set("env", System.getenv())
        symbols.forEach { (name, value) -> interpreter.set(name, value) }
        interpreter.run()
    }
}

fun main(args: Array<String>) {
    val i = BeanshellInterpreter()
    i.run(mapOf(
        "irmen" to 50
    ))
}
