package prog8tests.helpers

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import prog8.compiler.CompilationResult
import razorvine.ksim65.testing.CpuType
import razorvine.ksim65.testing.IHostSerialAndPowerIO
import razorvine.ksim65.testing.TestMachine
import java.nio.file.Path
import kotlin.io.path.exists

class SimulatorResetException : Exception()
class SimulatorPoweroffException : Exception()

class CapturingSerialIO : IHostSerialAndPowerIO {
    private val buffer = StringBuilder()
    private val inputQueue = ArrayDeque<UByte>()

    override fun write(byte: UByte) {
        buffer.append(byte.toInt().toChar())
    }

    override fun read(): UByte = inputQueue.removeFirstOrNull() ?: 0.toUByte()

    override fun reset() {
        buffer.clear()
        inputQueue.clear()
        throw SimulatorResetException()
    }

    override fun poweroff() {
        throw SimulatorPoweroffException()
    }

    fun provideInput(data: String) {
        data.forEach { inputQueue.add(it.code.toUByte()) }
    }

    fun provideInput(data: ByteArray) {
        data.forEach { inputQueue.add(it.toUByte()) }
    }

    fun getOutput(): String = buffer.toString()

    fun assertOutput(expected: String) {
        buffer.toString() shouldBe expected
    }

    fun assertOutputContains(expected: String) {
        buffer.toString() shouldContain expected
    }
}

class Cpu6502SimulatorHelper(val result: CompilationResult) {
    val prgFile: Path = result.compilationOptions.outputDir.resolve(result.compilerAst.name + ".prg")

    init {
        if (!prgFile.exists()) {
            throw IllegalStateException("Output file not found: $prgFile")
        }
    }

    fun run(maxCycles: Long = 10000, serialAndPower: IHostSerialAndPowerIO? = null, execute: Boolean = true): TestMachine {
        val bytes = prgFile.toFile().readBytes()
        val loadAddr = (bytes[0].toInt() and 0xff) or ((bytes[1].toInt() and 0xff) shl 8)

        val cpuType = result.compilationOptions.compTarget.cpu
        val actualCpuType: CpuType = when(cpuType) {
            prog8.code.core.CpuType.CPU6502 -> CpuType.CPU6502
            prog8.code.core.CpuType.CPU65C02 -> CpuType.CPU65C02
            prog8.code.core.CpuType.VIRTUAL -> throw IllegalStateException("Virtual CPU cannot be simulated here")
        }

        val ioHandler = serialAndPower ?: CapturingSerialIO()
        val machine = TestMachine(cpuType = actualCpuType, resetAddress = loadAddr, serialAndPower = ioHandler)
        machine.loadFileInRam(prgFile.toFile(), null)
        // PC is already set by TestMachine's init -> bus.reset()

        if (execute) {
            var finished = false
            while (machine.cpu.totalCycles < maxCycles) {
                try {
                    machine.step()
                } catch (_: SimulatorPoweroffException) {
                    finished = true
                    break
                } catch (_: SimulatorResetException) {
                    machine.reset()
                    continue
                }

                // Basic exit condition: BRK instruction (opcode 00)
                if (machine.cpu.currentOpcode == 0x00) {
                    finished = true
                    break
                }
                // Or if it's looping on itself (JMP *)
                if (machine.cpu.isLooping) {
                    finished = true
                    break
                }
            }

            if (!finished) {
                throw AssertionError("Simulation timed out after ${machine.cpu.totalCycles} cycles")
            }
        }

        return machine
    }
}

/**
 * Simulates the execution of the compiled program represented by the current CompilationResult.
 *
 * @param maxCycles The maximum number of CPU cycles to simulate. Defaults to 10,000.
 * @param execute Whether to actually run the simulation. Defaults to true.
 * @return A TestMachine instance that represents the state of the simulated machine after execution.
 */
fun CompilationResult.simulate(maxCycles: Long = 10000, serialAndPower: IHostSerialAndPowerIO? = null, execute: Boolean = true): TestMachine {
    return Cpu6502SimulatorHelper(this).run(maxCycles, serialAndPower, execute)
}
