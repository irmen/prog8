package prog8.http

import org.takes.Request
import org.takes.Response
import org.takes.Take
import org.takes.facets.fork.FkMethods
import org.takes.facets.fork.FkRegex
import org.takes.facets.fork.TkFork
import org.takes.http.Exit
import org.takes.http.FtBasic
import org.takes.rq.form.RqFormBase
import org.takes.rs.RsJson
import org.takes.tk.TkSlf4j
import prog8.compiler.CompilerArguments
import prog8.compiler.compileProgram
import javax.json.Json
import kotlin.io.path.Path


class Jsonding: RsJson.Source {
    override fun toJson(): javax.json.JsonStructure {
        return Json.createObjectBuilder()
            .add("name", "irmen")
            .build()
    }
}

class RequestParser : Take {
    override fun act(request: Request): Response {
        val form = RqFormBase(request)
        // val names = form.names()
        val a = form.param("a").single()
        val args = CompilerArguments(
            Path(a),
            optimize = true,
            writeAssembly = true,
            warnSymbolShadowing = false,
            compilationTarget = "c64",
            symbolDefs = emptyMap(),
            quietAssembler = false,
            includeSourcelines = false,
            asmListfile = false,
            experimentalCodegen = false,
            splitWordArrays = false,
            breakpointCpuInstruction = false,
            varsHighBank = null,
        )
        compileProgram(args)
        return RsJson(Jsonding())
    }
}

fun main() {
    FtBasic(
        TkSlf4j(
            TkFork(
                FkRegex("/", "hello, world!"),
                FkRegex("/json",
                    TkFork(
                        FkMethods("GET", RsJson(Jsonding())),
                        FkMethods("POST", RequestParser())
                    )
                ),
            )
        ),
        8080
    ).start(Exit.NEVER)
}
