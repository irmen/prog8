package prog8.ast.base

import prog8.parser.ParsingFailedError


fun printErrors(errors: List<Any>, moduleName: String) {
    val reportedMessages = mutableSetOf<String>()
    print("\u001b[91m")  // bright red
    errors.forEach {
        val msg = it.toString()
        if(msg !in reportedMessages) {
            System.err.println(msg)
            reportedMessages.add(msg)
        }
    }
    print("\u001b[0m")  // reset color
    if(reportedMessages.isNotEmpty())
        throw ParsingFailedError("There are ${reportedMessages.size} errors in module '$moduleName'.")
}


fun printWarning(msg: String, position: Position, detailInfo: String?=null) {
    print("\u001b[93m")  // bright yellow
    print("$position Warning: $msg")
    if(detailInfo==null)
        print("\n")
    else
        println(": $detailInfo\n")
    print("\u001b[0m")  // normal
}


fun printWarning(msg: String) {
    print("\u001b[93m")  // bright yellow
    print("Warning: $msg")
    print("\u001b[0m\n")  // normal
}
