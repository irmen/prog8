package prog8.parser

import prog8.ast.IStringEncoding

/**
 * TODO: remove once [IStringEncoding] has been moved to compiler module
 */
object ThrowTodoEncoding: IStringEncoding {
    override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
        TODO("move StringEncoding out of compilerAst")
    }

    override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
        TODO("move StringEncoding out of compilerAst")
    }
}
