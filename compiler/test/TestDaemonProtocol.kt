package prog8tests.compiler

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.compiler.DaemonProtocol
import prog8.compiler.DaemonRequest

class TestDaemonProtocol : FunSpec({

    test("DaemonRequest roundtrip encode/decode preserves all fields") {
        val request = DaemonRequest(
            version = "12.3-SNAPSHOT",
            filepath = "/path/to/test.p8",
            optimize = true,
            writeAssembly = false,
            warnSymbolShadowing = true,
            warnImplicitTypeCasts = false,
            quietAll = true,
            quietAssembler = false,
            showTimings = true,
            asmListfile = false,
            includeSourcelines = true,
            newCodegen = true,
            dumpVariables = false,
            dumpSymbols = true,
            varsHighBank = 10,
            varsGolden = true,
            slabsHighBank = 20,
            slabsGolden = false,
            compilationTarget = "cx16",
            breakpointCpuInstruction = "NOP",
            printAst1 = true,
            printAst2 = false,
            ignoreFootguns = true,
            profilingInstrumentation = false,
            traceImports = true,
            symbolDefs = mapOf("FOO" to "123", "BAR" to "456"),
            sourceDirs = listOf("/src1", "/src2"),
            outputDir = "/out",
            cwd = "/home/user/project"
        )

        val encoded = DaemonProtocol.encodeRequest(request)
        val decoded = DaemonProtocol.decodeRequest(encoded)

        decoded.version shouldBe request.version
        decoded.filepath shouldBe request.filepath
        decoded.optimize shouldBe request.optimize
        decoded.writeAssembly shouldBe request.writeAssembly
        decoded.warnSymbolShadowing shouldBe request.warnSymbolShadowing
        decoded.warnImplicitTypeCasts shouldBe request.warnImplicitTypeCasts
        decoded.quietAll shouldBe request.quietAll
        decoded.quietAssembler shouldBe request.quietAssembler
        decoded.showTimings shouldBe request.showTimings
        decoded.asmListfile shouldBe request.asmListfile
        decoded.includeSourcelines shouldBe request.includeSourcelines
        decoded.newCodegen shouldBe request.newCodegen
        decoded.dumpVariables shouldBe request.dumpVariables
        decoded.dumpSymbols shouldBe request.dumpSymbols
        decoded.varsHighBank shouldBe request.varsHighBank
        decoded.varsGolden shouldBe request.varsGolden
        decoded.slabsHighBank shouldBe request.slabsHighBank
        decoded.slabsGolden shouldBe request.slabsGolden
        decoded.compilationTarget shouldBe request.compilationTarget
        decoded.breakpointCpuInstruction shouldBe request.breakpointCpuInstruction
        decoded.printAst1 shouldBe request.printAst1
        decoded.printAst2 shouldBe request.printAst2
        decoded.ignoreFootguns shouldBe request.ignoreFootguns
        decoded.profilingInstrumentation shouldBe request.profilingInstrumentation
        decoded.traceImports shouldBe request.traceImports
        decoded.symbolDefs shouldBe request.symbolDefs
        decoded.sourceDirs shouldBe request.sourceDirs
        decoded.outputDir shouldBe request.outputDir
        decoded.cwd shouldBe request.cwd
    }

    test("DaemonRequest roundtrip with null optional fields") {
        val request = DaemonRequest(
            version = "1.0",
            filepath = "test.p8",
            optimize = false,
            writeAssembly = false,
            warnSymbolShadowing = false,
            warnImplicitTypeCasts = false,
            quietAll = false,
            quietAssembler = false,
            showTimings = false,
            asmListfile = false,
            includeSourcelines = false,
            newCodegen = false,
            dumpVariables = false,
            dumpSymbols = false,
            varsHighBank = null,
            varsGolden = false,
            slabsHighBank = null,
            slabsGolden = false,
            compilationTarget = "c64",
            breakpointCpuInstruction = null,
            printAst1 = false,
            printAst2 = false,
            ignoreFootguns = false,
            profilingInstrumentation = false,
            traceImports = false,
            symbolDefs = emptyMap(),
            sourceDirs = emptyList(),
            outputDir = "",
            cwd = "."
        )

        val encoded = DaemonProtocol.encodeRequest(request)
        val decoded = DaemonProtocol.decodeRequest(encoded)

        decoded.varsHighBank shouldBe null
        decoded.slabsHighBank shouldBe null
        decoded.breakpointCpuInstruction shouldBe null
        decoded.newCodegen shouldBe false
    }

    test("Decode fails gracefully with missing field") {
        val brokenJson = """{"version":"1.0","filepath":"test.p8","optimize":true}"""
        shouldThrowAny {
            DaemonProtocol.decodeRequest(brokenJson)
        }
    }
})
