package prog8tests.helpers

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.*


val workingDir = assumeDirectory("").absolute()   // Note: "." does NOT work..!
val fixturesDir = assumeDirectory(workingDir, "test/fixtures")
val resourcesDir = assumeDirectory(workingDir, "res")

fun assumeNotExists(path: Path): Path {
    withClue("sanity check: should not exist: ${path.absolute()}") {
        path.exists() shouldBe false
    }
    return path
}

fun assumeNotExists(pathStr: String): Path = assumeNotExists(Path(pathStr))
fun assumeNotExists(path: Path, other: String): Path = assumeNotExists(path / other)

fun assumeReadable(path: Path): Path {
    withClue("sanity check: should be readable: ${path.absolute()}") {
        path.isReadable() shouldBe true
    }
    return path
}

fun assumeReadableFile(path: Path): Path {
    withClue("sanity check: should be normal file: ${path.absolute()}") {
        path.isRegularFile() shouldBe true
    }
    return assumeReadable(path)
}

fun assumeReadableFile(pathStr: String): Path = assumeReadableFile(Path(pathStr))
fun assumeReadableFile(pathStr: String, other: Path): Path = assumeReadableFile(Path(pathStr), other)
fun assumeReadableFile(pathStr: String, other: String): Path = assumeReadableFile(Path(pathStr), other)
fun assumeReadableFile(path: Path, other: String): Path = assumeReadableFile(path / other)
fun assumeReadableFile(path: Path, other: Path): Path = assumeReadableFile(path / other)

fun assumeDirectory(path: Path): Path {
    withClue("sanity check; should be directory: $path") {
        path.isDirectory() shouldBe true
    }
    return path
}

fun assumeDirectory(pathStr: String): Path = assumeDirectory(Path(pathStr))
fun assumeDirectory(path: Path, other: String): Path = assumeDirectory(path / other)
fun assumeDirectory(pathStr: String, other: String): Path = assumeDirectory(Path(pathStr) / other)
fun assumeDirectory(pathStr: String, other: Path): Path = assumeDirectory(Path(pathStr) / other)


@Deprecated(
    "Directories are checked automatically at init.",
    ReplaceWith("/* nothing */")
)
@Suppress("UNUSED_PARAMETER")
fun sanityCheckDirectories(workingDirName: String? = null) {
}
