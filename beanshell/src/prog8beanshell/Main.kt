package prog8beanshell

import bsh.FileReader
import bsh.Interpreter


class BeanshellInterpreter {

    fun run(symbols: Map<String, Any>) {
        val interpreter = Interpreter(CommandLineReader(FileReader(System.`in`)), System.out, System.err, true)
        interpreter.setExitOnEOF(false)
        symbols.forEach { (name, value) -> interpreter.set(name, value) }
        interpreter.run()
    }
}

fun main(args: Array<String>) {
    val i = BeanshellInterpreter()
    i.run(mapOf(
        "env" to System.getenv(),
        "args" to args
    ))
}
