package prog8simulatortests

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.ast.base.DataType
import prog8.ast.base.Position
import prog8.ast.statements.AssignmentOrigin
import prog8.compilerinterface.Encoding
import prog8.compilerinterface.SymbolTable
import prog8.compilerinterface.intermediate.*
import prog8.sim.MemSizer
import prog8.sim.Simulator
import prog8.sim.StringEncoding

class TestSim : FunSpec({
    test("simple program simulation") {
        val program = PtProgram("test", MemSizer, StringEncoding)
        val module = PtModule("test", 0u, false, Position.DUMMY)
        val block = PtBlock("main", null, false, Position.DUMMY)
        val sub = PtSub("start", emptyList(), emptyList(), false, Position.DUMMY)
        block.add(sub)
        module.add(block)
        program.add(module)
        val fcall = PtBuiltinFunctionCall("print", Position.DUMMY).also {
            it.add(PtString("Hello, world! From the program.\n", Encoding.DEFAULT, Position.DUMMY))
        }
        sub.add(fcall)
        val memwrite = PtAssignment(false, AssignmentOrigin.USERCODE, Position.DUMMY).also { assign ->
            assign.add(PtAssignTarget(Position.DUMMY).also { tgt ->
                tgt.add(PtMemoryByte(Position.DUMMY).also { mb ->
                    mb.add(PtNumber(DataType.UWORD, 1000.0, Position.DUMMY))
                })
            })
            assign.add(PtNumber(DataType.UBYTE, 99.0, Position.DUMMY))
        }
        sub.add(memwrite)
        sub.add(PtReturn(Position.DUMMY))

        val symboltable = SymbolTable()

        val sim = Simulator(program, symboltable)
        sim.memory[1000u] shouldBe 0u
        sim.run()
        sim.memory[1000u] shouldBe 99u
    }
})