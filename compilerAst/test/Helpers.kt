package prog8tests.helpers

import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.io.path.*

import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.IStringEncoding
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import java.nio.file.Path


val workingDir = Path("").absolute()    // Note: Path(".") does NOT work..!
val fixturesDir = workingDir.resolve("test/fixtures")
val resourcesDir = workingDir.resolve("res")
val outputDir = workingDir.resolve("build/tmp/test")

fun assumeReadable(path: Path) {
    assertTrue(path.isReadable(), "sanity check: should be readable: ${path.absolute()}")
}

fun assumeReadableFile(path: Path) {
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


 val DummyEncoding = object : IStringEncoding {
    override fun encodeString(str: String, altEncoding: Boolean): List<Short> {
        TODO("Not yet implemented")
    }

    override fun decodeString(bytes: List<Short>, altEncoding: Boolean): String {
        TODO("Not yet implemented")
    }
}

val DummyFunctions = object : IBuiltinFunctions {
    override val names: Set<String> = emptySet()
    override val purefunctionNames: Set<String> = emptySet()
    override fun constValue(
        name: String,
        args: List<Expression>,
        position: Position,
        memsizer: IMemSizer
    ): NumericLiteralValue? = null

    override fun returnType(name: String, args: MutableList<Expression>) = InferredTypes.InferredType.unknown()
}

val DummyMemsizer = object : IMemSizer {
    override fun memorySize(dt: DataType): Int = 0
}

