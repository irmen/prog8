package prog8.parser

import java.io.File
import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.readText

/**
 * Encapsulates - and ties together - actual source code (=text) and its [origin].
 */
sealed class SourceCode {

    /**
     * Whether this [SourceCode] instance was created as a [Resource]
     */
    abstract val isFromResources: Boolean

    /**
     * Whether this [SourceCode] instance was created as a [File]
     */
    abstract val isFromFilesystem: Boolean

    /**
     * The logical name of the source code unit. Usually the module's name.
     */
    abstract val name: String

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
    abstract val text: String

    /**
     * Printable representation, deliberately does NOT return the actual text.
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
    class Text(override val text: String): SourceCode() {
        override val isFromResources = false
        override val isFromFilesystem = false
        override val origin = "$stringSourcePrefix${System.identityHashCode(text).toString(16)}>"
        override val name = "<unnamed-text>"
    }

    /**
     * Get [SourceCode] from the file represented by the specified Path.
     * This immediately reads the file fully into memory.
     *
     * [origin] will be the given path in absolute and normalized form.
     * @throws NoSuchFileException if the file does not exist
     * @throws FileSystemException if the file cannot be read
     */
    class File(path: Path): SourceCode() {
        override val text: String
        override val origin: String
        override val name: String
        override val isFromResources = false
        override val isFromFilesystem = true

        init {
            val normalized = path.normalize()
            origin = relative(normalized).toString()
            try {
                text = normalized.readText()
                name = normalized.toFile().nameWithoutExtension
            } catch (nfx: java.nio.file.NoSuchFileException) {
                throw NoSuchFileException(normalized.toFile()).also { it.initCause(nfx) }
            } catch (iox: IOException) {
                throw FileSystemException(normalized.toFile()).also { it.initCause(iox) }
            }
        }
    }

    /**
     * [origin]: `library:/x/y/z.p8` for a given `pathString` of "x/y/z.p8"
     */
    class Resource(pathString: String): SourceCode() {
        private val normalized = "/" + Path.of(pathString).normalize().toMutableList().joinToString("/")

        override val isFromResources = true
        override val isFromFilesystem = false
        override val origin = "$libraryFilePrefix$normalized"
        override val text: String
        override val name: String

        init {
            val rscURL = object {}.javaClass.getResource(normalized)
            if (rscURL == null) {
                val rscRoot = object {}.javaClass.getResource("/")
                throw NoSuchFileException(
                    File(normalized),
                    reason = "looked in resources rooted at $rscRoot"
                )
            }
            val stream = object {}.javaClass.getResourceAsStream(normalized)
            text = stream!!.reader().use { it.readText() }
            name = Path.of(pathString).toFile().nameWithoutExtension
        }
    }

    /**
     * SourceCode for internally generated nodes (usually Modules)
     */
    class Generated(name: String) : SourceCode() {
        override val isFromResources: Boolean = false
        override val isFromFilesystem: Boolean = false
        override val origin: String = name
        override val name: String = name
        override val text: String = "<generated code node, no text representation>"
    }
}
