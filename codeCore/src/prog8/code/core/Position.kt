package prog8.code.core

import prog8.code.core.SourceCode.Companion.libraryFilePrefix
import java.nio.file.InvalidPathException
import kotlin.io.path.Path
import kotlin.io.path.absolute

data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
    fun toClickableStr(): String {
        if(this===DUMMY)
            return ""
        if(file.startsWith(libraryFilePrefix))
            return "$file:$line:$startCol:"
        return try {
            val path = Path(file).absolute().normalize().toString()
            "file://$path:$line:$startCol:"
        } catch(x: InvalidPathException) {
            // this can occur on Windows when the source origin contains "invalid" characters such as ':'
            "file://$file:$line:$startCol:"
        }
    }

    companion object {
        val DUMMY = Position("~dummy~", 0, 0, 0)
    }
}