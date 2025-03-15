package prog8.code.core

import prog8.code.sanitize
import prog8.code.source.SourceCode
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
    fun toClickableStr(): String {
        if(this===DUMMY)
            return ""
        if(SourceCode.isLibraryResource(file))
            return "$file:$line:$startCol:"
        return try {
            val path = Path(file).sanitize().toString()
            "file://$path:$line:$startCol:"
        } catch(_: InvalidPathException) {
            // this can occur on Windows when the source origin contains "invalid" characters such as ':'
            "file://$file:$line:$startCol:"
        }
    }

    companion object {
        val DUMMY = Position("~dummy~", 0, 0, 0)
    }
}