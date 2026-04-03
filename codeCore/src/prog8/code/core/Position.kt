package prog8.code.core

import prog8.code.sanitize
import prog8.code.source.SourceCode
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

/**
 * Source code position.
 */
data class Position(
    val file: String,
    val line: Int,           // line number (1-based)
    val startCol: Int,       // start column (1-based, tab-expanded)
    val endCol: Int          // end column (1-based, tab-expanded)
) {
    override fun toString(): String {
        return "[$file: line $line col ${startCol}-${endCol}]"
    }

    fun toClickableStr(): String {
        if(this===DUMMY)
            return ""
        if(SourceCode.isLibraryResource(file))
            return "$file:$line:$startCol:"
        return try {
            val path = Path(file).sanitize().toUri().toString()
            "$path:$line:$startCol:"
        } catch(_: InvalidPathException) {
            // this can occur on Windows when the source origin contains "invalid" characters such as ':'
            "file://$file:$line:$startCol:"
        }
    }

    companion object {
        val DUMMY = Position("~dummy~", 0, 0, 0)
    }
}
