package prog8.code.source

import prog8.code.core.Position
import prog8.code.sanitize
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path


// Resource caching "filesystem".
// Note that it leaves the decision to load a resource or an actual disk file to the caller.
// Thread-safe: uses ConcurrentHashMap for all caches.

object ImportFileSystem {

    fun expandTilde(path: String): String = if (path.startsWith("~")) {
        val userHome = System.getProperty("user.home")
        userHome + path.drop(1)
    } else {
        path
    }

    fun expandTilde(path: Path): Path = Path(expandTilde(path.toString()))

    fun getFile(path: Path, isLibrary: Boolean=false): SourceCode {
        val normalized = path.sanitize()
        return cache.computeIfAbsent(normalized.toString().lowercase()) {
            SourceCode.File(normalized, isLibrary)
        }
    }

    fun getResource(name: String): SourceCode {
        return cache.computeIfAbsent(name.lowercase()) {
            SourceCode.Resource(name)
        }
    }

    fun retrieveSourceLine(position: Position): String {
        if(SourceCode.isLibraryResource(position.file)) {
            val key = SourceCode.withoutPrefix(position.file)
            val cached = cache[key.lowercase()]
                ?: runCatching { getResource(key) }.getOrNull()
            if(cached != null)
                return getLine(cached, position.line)
        }
        val cached = cache[position.file.lowercase()]
        if(cached != null)
            return getLine(cached, position.line)
        val path = Path(position.file).sanitize()
        val cached2 = cache[path.toString().lowercase()]
        if(cached2 != null)
            return getLine(cached2, position.line)
        throw NoSuchElementException("cannot get source line $position, with path $path")
    }

    private fun getLine(code: SourceCode, lineIndex: Int): String {
        val spans = lineSpanCache.computeIfAbsent(code) {
            val lineSpans = Regex("^", RegexOption.MULTILINE).findAll(code.text).map { it.range.first }
            val ends = lineSpans.drop(1) + code.text.length
            lineSpans.zip(ends).map { (start, end) -> LineSpan(start, end) }.toList().toTypedArray()
        }
        val span = spans[lineIndex - 1]
        return code.text.substring(span.start, span.end).trim()
    }

    private class LineSpan(val start: Int, val end: Int)

    private val cache = ConcurrentHashMap<String, SourceCode>()
    private val lineSpanCache = ConcurrentHashMap<SourceCode, Array<LineSpan>>()
}
