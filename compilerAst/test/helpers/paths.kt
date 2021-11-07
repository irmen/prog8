package prog8tests.ast.helpers

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue


val workingDir = assumeDirectory("").absolute()   // Note: "." does NOT work..!
val fixturesDir = assumeDirectory(workingDir,"test/fixtures")
val resourcesDir = assumeDirectory(workingDir,"res")
val outputDir: Path = createIfNotExists(workingDir, "build/tmp/test").also{ assumeDirectory(workingDir, "build/tmp/test") }

fun createIfNotExists(workingDir: Path, path: String): Path {
    val dir = workingDir / path
    if(!dir.toFile().isDirectory)
        Files.createDirectories(dir)
    return dir
}

fun assumeNotExists(path: Path): Path {
    assertFalse(path.exists(), "sanity check: should not exist: ${path.absolute()}")
    return path
}

fun assumeNotExists(pathStr: String): Path = assumeNotExists(Path(pathStr))
fun assumeNotExists(path: Path, other: String): Path = assumeNotExists(path / other)

fun assumeReadable(path: Path): Path {
    assertTrue(path.isReadable(), "sanity check: should be readable: ${path.absolute()}")
    return path
}

fun assumeReadableFile(path: Path): Path {
    assertTrue(path.isRegularFile(), "sanity check: should be normal file: ${path.absolute()}")
    return assumeReadable(path)
}

fun assumeReadableFile(pathStr: String): Path = assumeReadableFile(Path(pathStr))
fun assumeReadableFile(pathStr: String, other: Path): Path = assumeReadableFile(Path(pathStr), other)
fun assumeReadableFile(pathStr: String, other: String): Path = assumeReadableFile(Path(pathStr), other)
fun assumeReadableFile(path: Path, other: String): Path = assumeReadableFile(path / other)
fun assumeReadableFile(path: Path, other: Path): Path = assumeReadableFile(path / other)

fun assumeDirectory(path: Path): Path {
    assertTrue(path.isDirectory(), "sanity check; should be directory: $path")
    return path
}

fun assumeDirectory(pathStr: String): Path = assumeDirectory(Path(pathStr))
fun assumeDirectory(path: Path, other: String): Path = assumeDirectory(path / other)
fun assumeDirectory(pathStr: String, other: String): Path = assumeDirectory(Path(pathStr) / other)
fun assumeDirectory(pathStr: String, other: Path): Path = assumeDirectory(Path(pathStr) / other)


@Deprecated("Directories are checked automatically at init.",
    ReplaceWith("/* nothing */"))
@Suppress("UNUSED_PARAMETER")
fun sanityCheckDirectories(workingDirName: String? = null) {
}
