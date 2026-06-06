package prog8.compiler

import prog8.buildversion.BUILD_UNIX_TIME
import prog8.code.core.Position
import java.io.*
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.time.DurationUnit
import kotlin.time.measureTime


internal class CompilerDaemon(private val socketPath: Path) {
    companion object {
        private const val IDLE_TIMEOUT_MS = 5 * 60_000L

        fun getDefaultSocketPath(): Path {
            val baseDir = System.getenv("XDG_RUNTIME_DIR")
                ?.let { Path.of(it) }
                ?: Path.of(System.getProperty("user.home"), ".cache")
            return baseDir.resolve("prog8c-daemon.sock")
        }

        private fun myPid(): String? = try {
            ProcessHandle.current().pid().toString()
        } catch (_: Exception) {
            null
        }
    }

    private var shutdownRequested = false

    fun run() {
        socketPath.deleteIfExists()
        socketPath.parent?.let { Files.createDirectories(it) }

        val address = UnixDomainSocketAddress.of(socketPath)
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX).bind(address)
        server.configureBlocking(false)
        val selector = Selector.open()
        server.register(selector, SelectionKey.OP_ACCEPT)

        val pid = myPid()
        System.err.println(buildString {
            append("prog8c daemon")
            if (pid != null) append(" (pid $pid)")
            append(" running at ${socketPath}")
        })

        var lastRequestTime = System.currentTimeMillis()

        try {
            while (true) {
                val idleTime = System.currentTimeMillis() - lastRequestTime
                if (idleTime > IDLE_TIMEOUT_MS && lastRequestTime > 0) {
                    // daemon idle timeout reached, shutting down
                    break
                }
                val timeout = maxOf(1L, IDLE_TIMEOUT_MS - idleTime)
                if (selector.select(timeout) == 0)
                    continue

                val selected = selector.selectedKeys()
                val it = selected.iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                    val client = server.accept()
                    if (client != null) {
                        handleClient(client)
                        client.close()
                        lastRequestTime = System.currentTimeMillis()
                        if (shutdownRequested) break
                    }
                }
            }
        } finally {
            selector.close()
            server.close()
            socketPath.deleteIfExists()
        }
    }

    private fun handleClient(clientChannel: SocketChannel) {
        try {
            val reader = BufferedReader(InputStreamReader(Channels.newInputStream(clientChannel), Charsets.UTF_8))
            val writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(clientChannel), Charsets.UTF_8))

            val reqJson = reader.readLine() ?: return
            val request: DaemonRequest = try {
                DaemonProtocol.decodeRequest(reqJson)
            } catch (e: Exception) {
                System.err.println("daemon: failed to decode request: ${e.message}")
                return
            }

            // Version check (build timestamp must match)
            if (request.version != BUILD_UNIX_TIME.toString()) {
                val resp = DaemonResponse(
                    ok = false,
                    versionError = "daemon version mismatch: build time differs",
                    errors = emptyList(),
                    output = "",
                    t_ms = 0,
                    outputFiles = emptyList(),
                    importedFiles = emptyList()
                )
                writer.write(DaemonProtocol.encodeResponse(resp))
                writer.newLine()
                writer.flush()
                System.err.println("daemon: version mismatch, shutting down")
                shutdownRequested = true
                return
            }

            // Capture stdout/stderr
            val outBytes = ByteArrayOutputStream()
            val errBytes = ByteArrayOutputStream()
            val oldOut = System.out
            val oldErr = System.err
            System.setOut(PrintStream(outBytes, true, Charsets.UTF_8.name()))
            System.setErr(PrintStream(errBytes, true, Charsets.UTF_8.name()))

            val daemonErr = DaemonErrorReporter()
            var result: CompilationResult? = null
            val t_ms = try {
                measureTime {
                    val compilerArgs = request.toCompilerArguments(daemonErr)
                    result = compileProgram(compilerArgs)
                }.toLong(DurationUnit.MILLISECONDS)
            } catch (e: Exception) {
                System.err.println("daemon: compilation crash: ${e.message}")
                daemonErr.err("internal daemon error: ${e.message}", Position.DUMMY)
                0L
            } finally {
                System.out.flush()
                System.err.flush()
                System.setOut(oldOut)
                System.setErr(oldErr)
            }

            val outputFiles = if (result != null)
                listOfNotNull(
                    request.outputDir.let { d ->
                        val name = Path.of(request.filepath).fileName.toString().substringBeforeLast('.')
                        val dir = if (d == ".") "" else "$d/"
                        listOf(
                            "${dir}${name}.prg".takeIf { request.writeAssembly && request.compilationTarget != "virtual" },
                            "${dir}${name}.asm".takeIf { request.writeAssembly && request.compilationTarget != "virtual" },
                            "${dir}${name}.p8ir".takeIf { !request.writeAssembly || request.compilationTarget == "virtual" }
                        ).filterNotNull()
                    }
                ).flatten()
            else
                emptyList()

            val importedFiles = result?.importedFiles?.map { it.toString() } ?: emptyList()

            val combinedOutput = outBytes.toString(Charsets.UTF_8.name()) + errBytes.toString(Charsets.UTF_8.name())

            val resp = DaemonResponse(
                ok = result != null,
                versionError = null,
                errors = daemonErr.getMessages().map { msg ->
                    DaemonError(
                        severity = msg.severity,
                        message = msg.message,
                        file = msg.position?.file,
                        line = msg.position?.line ?: 1,
                        col = msg.position?.startCol ?: 1
                    )
                },
                output = combinedOutput,
                t_ms = t_ms,
                outputFiles = outputFiles,
                importedFiles = importedFiles
            )

            val respJson = DaemonProtocol.encodeResponse(resp)
            writer.write(respJson)
            writer.newLine()
            writer.flush()
        } catch (e: IOException) {
            System.err.println("daemon: client I/O error: ${e.message}")
        }
    }

    private fun DaemonRequest.toCompilerArguments(daemonErr: DaemonErrorReporter): CompilerArguments {
        return CompilerArguments(
            filepath = Path.of(filepath),
            optimize = optimize,
            writeAssembly = writeAssembly,
            warnSymbolShadowing = warnSymbolShadowing,
            warnImplicitTypeCasts = warnImplicitTypeCasts,
            quietAll = quietAll,
            quietAssembler = quietAssembler,
            showTimings = showTimings,
            asmListfile = asmListfile,
            includeSourcelines = includeSourcelines,
            experimentalCodegen = experimentalCodegen,
            dumpVariables = dumpVariables,
            dumpSymbols = dumpSymbols,
            varsHighBank = varsHighBank,
            varsGolden = varsGolden,
            slabsHighBank = slabsHighBank,
            slabsGolden = slabsGolden,
            compilationTarget = compilationTarget,
            breakpointCpuInstruction = breakpointCpuInstruction,
            printAst1 = printAst1,
            printAst2 = printAst2,
            ignoreFootguns = ignoreFootguns,
            profilingInstrumentation = profilingInstrumentation,
            nostdlib = nostdlib,
            symbolDefs = symbolDefs,
            sourceDirs = sourceDirs,
            outputDir = Path.of(outputDir),
            errors = daemonErr
        )
    }
}
