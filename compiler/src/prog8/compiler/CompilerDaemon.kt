package prog8.compiler

import prog8.buildversion.BUILD_UNIX_TIME
import prog8.code.core.Position
import prog8.code.source.ImportFileSystem
import prog8.code.source.SourceCode
import prog8.code.target.VMTarget
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
                ?: ImportFileSystem.userHome.resolve(".cache")
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
                    stdout = "",
                    stderr = "",
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
            val combinedBytes = ByteArrayOutputStream()
            val combinedStream = PrintStream(combinedBytes, true, Charsets.UTF_8.name())
            val oldOut = System.out
            val oldErr = System.err
            System.setOut(combinedStream)
            System.setErr(combinedStream)

            val daemonErr = DaemonErrorReporter()
            var result: CompilationResult? = null
            ImportFileSystem.clearCaches()
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
                            "${dir}${name}.prg".takeIf { request.writeAssembly && request.compilationTarget != VMTarget.NAME },
                            "${dir}${name}.asm".takeIf { request.writeAssembly && request.compilationTarget != VMTarget.NAME },
                            "${dir}${name}.p8ir".takeIf { !request.writeAssembly || request.compilationTarget == VMTarget.NAME }
                        ).filterNotNull()
                    }
                ).flatten()
            else
                emptyList()

            val importedFiles = result?.importedFiles?.map { it.toString() } ?: emptyList()

            val stdout = combinedBytes.toString(Charsets.UTF_8.name())
            val stderr = ""

            val resp = DaemonResponse(
                ok = result != null,
                versionError = null,
                errors = daemonErr.getMessages().map { msg ->
                    // Strip "ERROR ", "WARNING ", "INFO " prefixes
                    var message = if (msg.message.startsWith("${msg.severity} ")) {
                        msg.message.substring(msg.severity.length + 1)
                    } else {
                        msg.message
                    }
                    
                    // Strip path prefix if it exists (e.g. file:///...:line:col: )
                    // The path prefix usually ends with a colon followed by a space
                    val pathRegex = Regex("^file:///[^:]+:[0-9]+:[0-9]+: ")
                    message = message.replace(pathRegex, "")

                    DaemonError(
                        severity = msg.severity,
                        message = message,
                        file = msg.position?.let {
                            val origin = it.file
                            if (SourceCode.isLibraryResource(origin)) {
                                origin
                            } else {
                                ImportFileSystem.userHome.resolve(origin).normalize().toString()
                            }
                        },
                        line = msg.position?.line ?: 1,
                        startCol = msg.position?.startCol ?: 1,
                        endCol = msg.position?.endCol ?: 1
                    )
                },
                stdout = stdout,
                stderr = stderr,
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
        // Resolve the output directory path: if relative, make it absolute by resolving against
        // the client's working directory (cwd) to avoid writing files in the daemon's CWD.
        val clientCwd = Path.of(cwd)
        val resolvedOutputDir = if (Path.of(outputDir).isAbsolute) {
            Path.of(outputDir)
        } else {
            clientCwd.resolve(outputDir)
        }
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
            newCodegen = newCodegen,
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
            traceImports = traceImports,
            symbolDefs = symbolDefs,
            sourceDirs = sourceDirs,
            outputDir = resolvedOutputDir,
            cwd = clientCwd,
            errors = daemonErr
        )
    }
}
