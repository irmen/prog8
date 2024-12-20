package prog8.code.source

import prog8.code.core.Position
import kotlin.io.path.Path

object SourceLineCache {
    private val cache = mutableMapOf<String, List<String>>()

    private fun getCachedFile(file: String): List<String> {
        val existing = cache[file]
        if(existing!=null)
            return existing
        if (SourceCode.isRegularFilesystemPath(file)) {
            val source = SourceCode.File(Path(file))
            cache[file] = source.text.split('\n', '\r').map { it.trim() }
            return cache.getValue(file)
        } else if(SourceCode.isLibraryResource(file)) {
            val source = SourceCode.Resource(SourceCode.withoutPrefix(file))
            cache[file] = source.text.split('\n', '\r').map { it.trim()}
            return cache.getValue(file)
        }
        return emptyList()
    }

    fun retrieveLine(position: Position): String? {
        if (position.line>0) {
            val lines = getCachedFile(position.file)
            if(lines.isNotEmpty())
                return lines[position.line-1]
        }
        return null
    }
}