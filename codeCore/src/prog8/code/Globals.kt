package prog8.code

import java.io.IOException
import java.nio.file.Path
import kotlin.io.path.absolute


const val INTERNED_STRINGS_MODULENAME = "prog8_interned_strings"

val PROG8_CONTAINER_MODULES = arrayOf(INTERNED_STRINGS_MODULENAME)      // option to add more if needed one day

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


object SymbolNames {
    /**
     * Strips Prog8 symbol prefixes (p8b_, p8t_, p8s_, p8v_, p8l_, p8c_) from a scoped name.
     * Example: "p8b_plane.p8t_Point" -> "plane.Point"
     * Used to compare names across different contexts where prefixes may or may not be applied.
     * See https://github.com/irmen/prog8/issues/198
     */
    fun stripPrefixes(scopedName: String): String {
        return scopedName.split('.')
            .map { part ->
                if(part.length > 4 && part.startsWith("p8") && part[2].isLetter() && part[3] == '_')
                    part.drop(4)
                else part
            }
            .joinToString(".")
    }
}
