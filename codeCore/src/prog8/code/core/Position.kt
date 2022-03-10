package prog8.code.core

import kotlin.io.path.Path
import kotlin.io.path.absolute

data class Position(val file: String, val line: Int, val startCol: Int, val endCol: Int) {
    override fun toString(): String = "[$file: line $line col ${startCol+1}-${endCol+1}]"
    fun toClickableStr(): String {
        val path = Path(file).absolute().normalize()
        return "file://$path:$line:$startCol:"
    }

    companion object {
        val DUMMY = Position("<dummy>", 0, 0, 0)
    }
}