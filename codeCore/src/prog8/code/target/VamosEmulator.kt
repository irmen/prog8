package prog8.code.target

import java.nio.file.Path

object VamosEmulator {
    fun launch(programFile: Path, quiet: Boolean, cpu: String) {
        val cmd = listOf("vamos", "--cpu", cpu, programFile.toString())
        if (!quiet) println("Launching Amitools's Vamos...")
        val pb = ProcessBuilder(cmd).inheritIO()
        try {
            pb.start().waitFor()
        } catch (_: Exception) {
            System.err.println("Cannot find 'vamos' (Amitools's Amiga emulator). Install it from: https://github.com/cnvogelg/amitools")
        }
    }
}
