package prog8.parser

import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import java.io.File
import java.io.IOException
import java.nio.channels.Channels
import java.nio.charset.CodingErrorAction
import java.nio.file.Path
import kotlin.io.path.*

/**
 * Encapsulates - and ties together - actual source code (=text)
 * and its [origin].
 */
sealed class SourceCode {

    /**
     * To be used *only* by the parser (as input to a TokenStream).
     * DO NOT mess around with!
     */
    internal abstract fun getCharStream(): CharStream

    /**
     * Whether this [SourceCode] instance was created as a [Resource]
     */
    abstract val isFromResources: Boolean

    /**
     * Whether this [SourceCode] instance was created as a [File]
     */
    abstract val isFromFilesystem: Boolean

    /**
     * Where this [SourceCode] instance came from.
     * This can be one of the following:
     * * a normal string representation of a [java.nio.file.Path], if it originates from a file (see [File])
     * * `$stringSourcePrefix44c56085>` if was created via [String]
     * * `library:/x/y/z.ext` if it is a library file that was loaded from resources (see [Resource])
     */
    abstract val origin: String

    /**
     * The source code as plain string.
     */
    abstract fun readText(): String

    /**
     * Deliberately does NOT return the actual text.
     * For this - if at all - use [getCharStream].
     */
    final override fun toString() = "${this.javaClass.name}[${this.origin}]"

    companion object {

        /**
         * filename prefix to designate library files that will be retreived from internal resources rather than disk
         */
        const val libraryFilePrefix = "library:"
        const val stringSourcePrefix = "<String@"
        val curdir: Path = Path(".").toAbsolutePath()
        fun relative(path: Path): Path = curdir.relativize(path.toAbsolutePath())
        fun isRegularFilesystemPath(pathString: String) =
            !(pathString.startsWith(libraryFilePrefix) || pathString.startsWith(stringSourcePrefix))
    }

    /**
     * Turn a plain String into a [SourceCode] object.
     * [origin] will be something like `$stringSourcePrefix44c56085>`.
     */
    class Text(val text: String): SourceCode() {
        override val isFromResources = false
        override val isFromFilesystem = false
        override val origin = "$stringSourcePrefix${System.identityHashCode(text).toString(16)}>"
        public override fun getCharStream(): CharStream = CharStreams.fromString(text, origin)
        override fun readText() = text
    }

    /**
     * Get [SourceCode] from the file represented by the specified Path.
     * This does not actually *access* the file, but it does check
     * whether it
     * * exists
     * * is a regular file (ie: not a directory)
     * * and is actually readable
     *
     * [origin] will be the given path in absolute and normalized form.
     * @throws NoSuchFileException if the file does not exist
     * @throws AccessDeniedException if the given path points to a directory or the file is non-readable for some other reason
     */
    class File(path: Path): SourceCode() {
        private val normalized = path.normalize()
        init {
            val file = normalized.toFile()
            if (!path.exists())
                throw NoSuchFileException(file)
            if (path.isDirectory())
                throw AccessDeniedException(file, reason = "Not a file but a directory")
            if (!path.isReadable())
                throw AccessDeniedException(file, reason = "Is not readable")
        }

        override val isFromResources = false
        override val isFromFilesystem = true
        override val origin = relative(normalized).toString()
        override fun getCharStream(): CharStream = CharStreams.fromPath(normalized)
        override fun readText() = normalized.readText()
    }

    /**
     * [origin]: `library:/x/y/z.p8` for a given `pathString` of "x/y/z.p8"
     */
    class Resource(pathString: String): SourceCode() {
        private val normalized = "/" + Path.of(pathString).normalize().toMutableList().joinToString("/")

        init {
            val rscURL = object {}.javaClass.getResource(normalized)
            if (rscURL == null) {
                val rscRoot = object {}.javaClass.getResource("/")
                throw NoSuchFileException(
                    File(normalized),
                    reason = "looked in resources rooted at $rscRoot"
                )
            }
        }

        override val isFromResources = true
        override val isFromFilesystem = false
        override val origin = "$libraryFilePrefix$normalized"
        public override fun getCharStream(): CharStream {
            val inpStr = object {}.javaClass.getResourceAsStream(normalized)!!
            // CharStreams.fromStream() doesn't allow us to set the stream name properly, so we use a lower level api
            val channel = Channels.newChannel(inpStr)
            return CharStreams.fromChannel(channel, Charsets.UTF_8, 4096, CodingErrorAction.REPLACE, origin, -1)
        }

        override fun readText(): String {
            val stream = object {}.javaClass.getResourceAsStream(normalized)
            return stream!!.bufferedReader().use { r -> r.readText() }
        }
    }

    /**
     * SourceCode for internally generated nodes (usually Modules)
     */
    class Generated(name: String) : SourceCode() {
        override fun getCharStream(): CharStream = throw IOException("generated code nodes doesn't have a stream to read")
        override val isFromResources: Boolean = false
        override val isFromFilesystem: Boolean = false
        override val origin: String = name
        override fun readText() = throw IOException("generated code nodes don't have a text representation")
    }
}
