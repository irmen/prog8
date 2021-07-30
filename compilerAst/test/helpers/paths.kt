package prog8tests.helpers

import kotlin.test.*
import kotlin.io.path.*

import java.nio.file.Path


val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
val fixturesDir = workingDir.resolve("test/fixtures")
val resourcesDir = workingDir.resolve("res")
val outputDir = workingDir.resolve("build/tmp/test")

fun assumeReadable(path: Path) {
    assertTrue(path.isReadable(), "sanity check: should be readable: ${path.absolute()}")
}

fun assumeReadableFile(path: Path) {
    assumeReadable(path)
    assertTrue(path.isRegularFile(), "sanity check: should be normal file: ${path.absolute()}")
}

fun assumeDirectory(path: Path) {
    assertTrue(path.isDirectory(), "sanity check; should be directory: $path")
}

fun assumeNotExists(path: Path) {
    assertFalse(path.exists(), "sanity check: should not exist: ${path.absolute()}")
}

fun sanityCheckDirectories(workingDirName: String? = null) {
    if (workingDirName != null)
        assertEquals(workingDirName, workingDir.fileName.toString(), "name of current working dir")
    assumeDirectory(workingDir)
    assumeDirectory(fixturesDir)
    assumeDirectory(resourcesDir)
    assumeDirectory(outputDir)
}
