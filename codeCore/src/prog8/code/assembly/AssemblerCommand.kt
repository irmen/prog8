package prog8.code.assembly

/** Characters that make a shell split or interpret an argument, so it has to be quoted. */
private const val SHELL_SPECIAL_CHARS = $$" \t\n'\"\\$&|;<>()*?[]{}~#!`"

/**
 * Renders [command] as a single shell command line, single-quoting every argument that a shell
 * would otherwise split or interpret, so the result can be copied and run as-is.
 */
fun shellCommandLine(command: List<String>): String = command.joinToString(" ") { arg ->
    if (arg.isEmpty() || arg.any { it in SHELL_SPECIAL_CHARS })
        "'" + arg.replace("'", "'\\''") + "'"
    else arg
}

/** The assembler header comment line holding the copyable [command]. */
fun assemblerCommandComment(command: List<String>): String =
    "; Assembler command: " + shellCommandLine(command)
