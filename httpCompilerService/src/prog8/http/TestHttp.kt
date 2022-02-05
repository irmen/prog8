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
import java.nio.file.Path
import javax.json.Json


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
        val names = form.names()
        val a = form.param("a").single()
        val args = CompilerArguments(
            Path.of(a),
            optimize = true,
            optimizeFloatExpressions = false,
            dontReinitGlobals = false,
            writeAssembly = true,
            slowCodegenWarnings = true,
            compilationTarget = "c64",
            quietAssembler = false,
            asmListfile = false,
            experimentalCodegen = false
        )
        val compilationResult = compileProgram(args)
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
