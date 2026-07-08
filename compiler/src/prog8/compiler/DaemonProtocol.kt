package prog8.compiler


internal data class DaemonRequest(
    val version: String,
    val filepath: String,
    val optimize: Boolean,
    val writeAssembly: Boolean,
    val warnSymbolShadowing: Boolean,
    val warnImplicitTypeCasts: Boolean,
    val quietAll: Boolean,
    val quietAssembler: Boolean,
    val showTimings: Boolean,
    val asmListfile: Boolean,
    val includeSourcelines: Boolean,
    val newCodegen: Boolean,
    val dumpVariables: Boolean,
    val dumpSymbols: Boolean,
    val varsHighBank: Int?,
    val varsGolden: Boolean,
    val slabsHighBank: Int?,
    val slabsGolden: Boolean,
    val compilationTarget: String,
    val breakpointCpuInstruction: String?,
    val printAst1: Boolean,
    val printAst2: Boolean,
    val ignoreFootguns: Boolean,
    val profilingInstrumentation: Boolean,
    val traceImports: Boolean,
    val symbolDefs: Map<String, String>,
    val sourceDirs: List<String>,
    val outputDir: String,
    val cwd: String
)

internal data class DaemonResponse(
    val ok: Boolean,
    val versionError: String?,
    val errors: List<DaemonError>,
    val stdout: String,
    val stderr: String,
    val t_ms: Long,
    val outputFiles: List<String>,
    val importedFiles: List<String>
)

internal data class DaemonError(
    val severity: String,
    val message: String,
    val file: String?,
    val line: Int,
    val startCol: Int,
    val endCol: Int
)


// Minimal JSON encoder/decoder for the daemon protocol
internal object DaemonProtocol {

    fun encodeRequest(req: DaemonRequest): String = buildString {
        append('{')
        append(prop("version", req.version))
        append(prop("filepath", req.filepath))
        append(prop("optimize", req.optimize))
        append(prop("writeAssembly", req.writeAssembly))
        append(prop("warnSymbolShadowing", req.warnSymbolShadowing))
        append(prop("warnImplicitTypeCasts", req.warnImplicitTypeCasts))
        append(prop("quietAll", req.quietAll))
        append(prop("quietAssembler", req.quietAssembler))
        append(prop("showTimings", req.showTimings))
        append(prop("asmListfile", req.asmListfile))
        append(prop("includeSourcelines", req.includeSourcelines))
        append(prop("dumpVariables", req.dumpVariables))
        append(prop("dumpSymbols", req.dumpSymbols))
        append(prop("varsHighBank", req.varsHighBank))
        append(prop("varsGolden", req.varsGolden))
        append(prop("slabsHighBank", req.slabsHighBank))
        append(prop("slabsGolden", req.slabsGolden))
        append(prop("compilationTarget", req.compilationTarget))
        append(propOpt("breakpointCpuInstruction", req.breakpointCpuInstruction))
        append(prop("printAst1", req.printAst1))
        append(prop("printAst2", req.printAst2))
        append(prop("ignoreFootguns", req.ignoreFootguns))
        append(prop("profilingInstrumentation", req.profilingInstrumentation))
        append(prop("traceImports", req.traceImports))
        append(prop("symbolDefs", req.symbolDefs))
        append(prop("sourceDirs", req.sourceDirs))
        append(prop("outputDir", req.outputDir))
        append(prop("cwd", req.cwd))
        append("null")  // placeholder, gets overwritten by trimEnd
        setLength(length - 5)
        append('}')
    }

    fun encodeResponse(resp: DaemonResponse): String = buildString {
        append('{')
        append(prop("ok", resp.ok))
        append(propOpt("versionError", resp.versionError))
        append("\"errors\":[")
        for ((i, e) in resp.errors.withIndex()) {
            if (i > 0) append(',')
            append('{')
            append(prop("severity", e.severity))
            append(prop("message", e.message))
            append(propOpt("file", e.file))
            append(prop("line", e.line))
            append(prop("startCol", e.startCol))
            append(prop("endCol", e.endCol))
            append("null")
            setLength(length - 4)
            append('}')
        }
        append("],")
        append(prop("stdout", resp.stdout))
        append(prop("stderr", resp.stderr))
        append(prop("t_ms", resp.t_ms))
        append(prop("outputFiles", resp.outputFiles))
        append(prop("importedFiles", resp.importedFiles))
        append("null")
        setLength(length - 5)
        append('}')
    }

    fun decodeResponse(json: String): DaemonResponse {
        val map = parseJsonObject(json)
        val errorsJson = map["errors"] as? List<*> ?: emptyList<Any>()
        val errors = errorsJson.map { e ->
            val em = e as Map<*, *>
            DaemonError(
                severity = em["severity"] as String,
                message = em["message"] as String,
                file = em["file"] as? String,
                line = (em["line"] as Number).toInt(),
                startCol = (em["startCol"] as Number).toInt(),
                endCol = (em["endCol"] as Number).toInt()
            )
        }
        val outputFiles = (map["outputFiles"] as? List<*>)?.map { it as String } ?: emptyList()
        val importedFiles = (map["importedFiles"] as? List<*>)?.map { it as String } ?: emptyList()
        return DaemonResponse(
            ok = map["ok"] as Boolean,
            versionError = map["versionError"] as? String,
            errors = errors,
            stdout = map["stdout"] as String,
            stderr = map["stderr"] as String,
            t_ms = (map["t_ms"] as Number).toLong(),
            outputFiles = outputFiles,
            importedFiles = importedFiles
        )
    }

    fun decodeRequest(json: String): DaemonRequest {
        val map = parseJsonObject(json)
        return DaemonRequest(
            version = map["version"] as String,
            filepath = map["filepath"] as String,
            optimize = map["optimize"] as Boolean,
            writeAssembly = map["writeAssembly"] as Boolean,
            warnSymbolShadowing = map["warnSymbolShadowing"] as Boolean,
            warnImplicitTypeCasts = map["warnImplicitTypeCasts"] as Boolean,
            quietAll = map["quietAll"] as Boolean,
            quietAssembler = map["quietAssembler"] as Boolean,
            showTimings = map["showTimings"] as Boolean,
            asmListfile = map["asmListfile"] as Boolean,
            includeSourcelines = map["includeSourcelines"] as Boolean,
            newCodegen = map["newCodegen"] as Boolean,
            dumpVariables = map["dumpVariables"] as Boolean,
            dumpSymbols = map["dumpSymbols"] as Boolean,
            varsHighBank = map["varsHighBank"] as? Int,
            varsGolden = map["varsGolden"] as Boolean,
            slabsHighBank = map["slabsHighBank"] as? Int,
            slabsGolden = map["slabsGolden"] as Boolean,
            compilationTarget = map["compilationTarget"] as String,
            breakpointCpuInstruction = map["breakpointCpuInstruction"] as? String,
            printAst1 = map["printAst1"] as Boolean,
            printAst2 = map["printAst2"] as Boolean,
            ignoreFootguns = map["ignoreFootguns"] as Boolean,
            profilingInstrumentation = map["profilingInstrumentation"] as Boolean,
            traceImports = map["traceImports"] as Boolean,
            symbolDefs = (map["symbolDefs"] as? Map<*, *>)?.mapKeys { it.key as String }?.mapValues { it.value as String } ?: emptyMap(),
            sourceDirs = (map["sourceDirs"] as? List<*>)?.map { it as String } ?: emptyList(),
            outputDir = map["outputDir"] as String,
            cwd = map["cwd"] as String
        )
    }

    private fun encodeJsonString(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun prop(name: String, value: String): String = "\"$name\":${encodeJsonString(value)},"
    private fun prop(name: String, value: Boolean): String = "\"$name\":$value,"
    private fun prop(name: String, value: Int): String = "\"$name\":$value,"
    private fun prop(name: String, value: Long): String = "\"$name\":$value,"
    private fun prop(name: String, value: Int?): String = value?.let { "\"$name\":$it," } ?: "\"$name\":null,"
    private fun prop(name: String, value: Map<String, String>): String {
        val entries = value.entries.joinToString(",") { "${encodeJsonString(it.key)}:${encodeJsonString(it.value)}" }
        return "\"$name\":{$entries},"
    }
    private fun prop(name: String, value: List<String>): String {
        val items = value.joinToString(",") { encodeJsonString(it) }
        return "\"$name\":[$items],"
    }
    private fun propOpt(name: String, value: String?): String = value?.let { "\"$name\":${encodeJsonString(it)}," } ?: ""

    private fun parseJsonObject(json: String): Map<String, Any?> {
        val trimmed = json.trim()
        if (!trimmed.startsWith('{') || !trimmed.endsWith('}'))
            throw IllegalArgumentException("not a JSON object: $json")
        val inner = trimmed.substring(1, trimmed.length - 1)
        return parsePairs(inner)
    }

    private fun parsePairs(s: String): Map<String, Any?> {
        val result = LinkedHashMap<String, Any?>()
        var pos = 0
        while (pos < s.length) {
            skipWhitespace(s, pos).let { pos = it }
            if (pos >= s.length) break
            // key
            check(s[pos] == '"') { "expected '\"' at pos $pos in: $s" }
            pos++
            val keyEnd = s.indexOf('"', pos)
            check(keyEnd > 0) { "unterminated string at pos $pos" }
            val key = unescapeJson(s.substring(pos, keyEnd))
            pos = keyEnd + 1
            skipWhitespace(s, pos).let { pos = it }
            check(s[pos] == ':') { "expected ':' at pos $pos" }
            pos++
            skipWhitespace(s, pos).let { pos = it }
            // value
            val (value, newPos) = parseValue(s, pos)
            result[key] = value
            pos = newPos
            skipWhitespace(s, pos).let { pos = it }
            if (pos < s.length && s[pos] == ',') pos++
        }
        return result
    }

    private fun parseValue(s: String, start: Int): Pair<Any?, Int> {
        var pos = start
        skipWhitespace(s, pos).let { pos = it }
        return when {
            pos >= s.length -> null to pos
            s[pos] == '"' -> parseStringValue(s, pos)
            s[pos] == '{' -> parseMapValue(s, pos)
            s[pos] == '[' -> parseListValue(s, pos)
            s.startsWith("null", pos) -> null to (pos + 4)
            s.startsWith("true", pos) -> true to (pos + 4)
            s.startsWith("false", pos) -> false to (pos + 5)
            else -> parseNumberValue(s, pos)
        }
    }

    private fun parseStringValue(s: String, start: Int): Pair<String, Int> {
        check(s[start] == '"')
        pos@ for (pos in (start + 1) until s.length) {
            when (s[pos]) {
                '\\' -> continue@pos  // skip next char
                '"' -> return unescapeJson(s.substring(start + 1, pos)) to (pos + 1)
            }
        }
        error("unterminated string in JSON")
    }

    private fun parseMapValue(s: String, start: Int): Pair<Map<String, Any?>, Int> {
        check(s[start] == '{')
        var depth = 1
        var idx = start + 1
        while (idx < s.length) {
            when (s[idx]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0)
                        return parsePairs(s.substring(start + 1, idx)) to (idx + 1)
                }
                '"' -> {
                    // skip string content
                    var p = idx + 1
                    while (p < s.length) {
                        when (s[p]) {
                            '\\' -> p += 2
                            '"' -> { idx = p; break }
                            else -> p++
                        }
                    }
                }
            }
            idx++
        }
        error("unterminated JSON object")
    }

    private fun parseListValue(s: String, start: Int): Pair<List<Any?>, Int> {
        check(s[start] == '[')
        val items = mutableListOf<Any?>()
        var pos = start + 1
        while (pos < s.length) {
            skipWhitespace(s, pos).let { pos = it }
            if (s[pos] == ']') return items to (pos + 1)
            val (value, newPos) = parseValue(s, pos)
            items.add(value)
            pos = newPos
            skipWhitespace(s, pos).let { pos = it }
            if (pos < s.length && s[pos] == ',') pos++
        }
        error("unterminated JSON array")
    }

    private fun parseNumberValue(s: String, start: Int): Pair<Number, Int> {
        var end = start
        while (end < s.length && (s[end].isDigit() || s[end] == '-' || s[end] == '+' || s[end] == '.' || s[end] == 'e' || s[end] == 'E'))
            end++
        val num = s.substring(start, end)
        return if (num.contains('.') || num.contains('e') || num.contains('E'))
            num.toDouble() to end
        else
            num.toLong() to end
    }

    private fun skipWhitespace(s: String, pos: Int): Int {
        var p = pos
        while (p < s.length && s[p].isWhitespace()) p++
        return p
    }

    private fun unescapeJson(s: String): String {
        if ('\\' !in s) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        val hex = s.substring(i + 2, i + 6)
                        sb.append(hex.toInt(16).toChar())
                        i += 6
                    }
                    else -> { sb.append(s[i]); i++ }
                }
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun check(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) throw IllegalArgumentException(lazyMessage())
    }

    private fun error(message: String): Nothing = throw IllegalArgumentException(message)
}
