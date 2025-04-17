package prog8.code

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolute


// the automatically generated module where all string literals are interned to:
const val INTERNED_STRINGS_MODULENAME = "prog8_interned_strings"

// all automatically generated labels everywhere need to have the same label name prefix:
const val GENERATED_LABEL_PREFIX = "p8_label_gen_"


/**
 * Returns the absolute path of the given path,
 * where links are replaced by the actual directories,
 * and containing no redundant path elements.
 * If the path doesn't refer to an existing directory or file on the file system,
 * it is returned unchanged.
 */
fun Path.sanitize(): Path {
    return try {
        this.toRealPath().normalize()
    } catch (_: java.nio.file.NoSuchFileException) {
        this.absolute().normalize()
        //throw NoSuchFileException(this.toFile(), null, nx.reason).also { it.initCause(nx) }
    } catch (iox: IOException) {
        throw FileSystemException(this.toFile()).also { it.initCause(iox) }
    }
}