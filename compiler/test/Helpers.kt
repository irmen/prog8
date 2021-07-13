package prog8tests.helpers

import kotlin.test.*
import kotlin.io.path.*
import java.nio.file.Path

import prog8.ast.IBuiltinFunctions
import prog8.ast.IMemSizer
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.expressions.Expression
import prog8.ast.expressions.InferredTypes
import prog8.ast.expressions.NumericLiteralValue
import prog8.compiler.CompilationResult
import prog8.compiler.compileProgram
import prog8.compiler.target.ICompilationTarget

// TODO: find a way to share with compilerAst/test/Helpers.kt, while still being able to amend it (-> compileFile(..))

internal fun CompilationResult.assertSuccess(description: String = ""): CompilationResult {
    assertTrue(success, "expected successful compilation but failed $description")
    return this
}

internal fun CompilationResult.assertFailure(description: String = ""): CompilationResult {
    assertFalse(success, "expected failure to compile but succeeded $description")
    return this
}

/**
 * @see CompilationResult.assertSuccess
 * @see CompilationResult.assertFailure
 */
internal fun compileFile(
    platform: ICompilationTarget,
    optimize: Boolean,
    fileDir: Path,
    fileName: String,
    outputDir: Path = prog8tests.helpers.outputDir
) : CompilationResult {
    val filepath = fileDir.resolve(fileName)
    assumeReadableFile(filepath)
    return compileProgram(
        filepath,
        optimize,
        writeAssembly = true,
        slowCodegenWarnings = false,
        platform.name,
        libdirs = listOf(),
        outputDir
    )
}

/**
 * Takes a [sourceText] as a String, writes it to a temporary
 * file and then runs the compiler on that.
 * @see compileFile
 */
internal fun compileText(
    platform: ICompilationTarget,
    optimize: Boolean,
    sourceText: String
) : CompilationResult {
    val filePath = outputDir.resolve("on_the_fly_test_" + sourceText.hashCode().toUInt().toString(16) + ".p8")
    // we don't assumeNotExists(filePath) - should be ok to just overwrite it
    filePath.toFile().writeText(sourceText)
    return compileFile(platform, optimize, filePath.parent, filePath.name)
}



val workingDir : Path = Path("").absolute()    // Note: Path(".") does NOT work..!
val fixturesDir : Path = workingDir.resolve("test/fixtures")
val resourcesDir : Path = workingDir.resolve("res")
val outputDir : Path = workingDir.resolve("build/tmp/test")

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


fun <A, B, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, combine2: (A, B) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                yield(combine2(a, b))
    }.toList()

fun <A, B, C, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, dim3: Iterable<C>, combine3: (A, B, C) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                for (c in dim3)
                    yield(combine3(a, b, c))
    }.toList()

fun <A, B, C, D, R> mapCombinations(dim1: Iterable<A>, dim2: Iterable<B>, dim3: Iterable<C>, dim4: Iterable<D>, combine4: (A, B, C, D) -> R) =
    sequence {
        for (a in dim1)
            for (b in dim2)
                for (c in dim3)
                    for (d in dim4)
                        yield(combine4(a, b, c, d))
    }.toList()


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

