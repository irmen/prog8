package prog8.ast.base


enum class MessageSeverity {
    WARNING,
    ERROR
}


class CompilerMessage(val severity: MessageSeverity, val message: String, val position: Position?)


fun printWarning(message: String, position: Position? = null) {
    print("\u001b[93m")  // bright yellow
    val msg = "$position Warning: $message".trim()
    print("\n\u001b[0m")  // normal
}
