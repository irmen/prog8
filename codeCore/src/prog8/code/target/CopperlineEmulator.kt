package prog8.code.target

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

object CopperlineEmulator {
    fun launch(programFile: Path, quiet: Boolean, model: String, cpu: String, memoryArgs: List<String>) {
        val home = System.getProperty("user.home")
        val sourceHdName = if (model == "A500") "Harddisk500" else "Harddisk1200"
        val cmdDir = if (model == "A500") "c" else "C"
        val libsDir = if (model == "A500") "libs" else "Libs"
        val libs = listOf("diskfont.library", "iffparse.library", "lowlevel.library", "rexxsyslib.library", "asyncio.library", "icon.library")

        val tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "prog8-copperline-$model")
        tempDir.createDirectories()
        val progName = programFile.fileName.toString()
        val stagedProg = tempDir.resolve(progName)
        programFile.copyTo(stagedProg, overwrite = true)

        val srcHd = File("$home/Documents/FS-UAE/Hard Drives/$sourceHdName")
        if (!srcHd.isDirectory) System.err.println("Warning: source hard disk folder not found: ${srcHd}; skipping file staging.")
        for (lib in libs) {
            val src = File(srcHd, "$libsDir/$lib")
            if (src.exists()) src.toPath().copyTo(tempDir.resolve(lib), overwrite = true)
        }
        val commands = if (model == "A500") listOf("Dir","List","Makedir","Assign","SetPatch","Mount","CPU","Echo","Avail") else listOf("Dir","List","MakeDir","Assign","SetPatch","Mount","CPU","Echo","Avail")
        for (cmd in commands) {
            val src = File(srcHd, "$cmdDir/$cmd")
            if (src.exists()) src.toPath().copyTo(tempDir.resolve(cmd), overwrite = true)
        }
        val ramHandlerSrc = File(srcHd, "l/Ram-Handler")
        if (model == "A500" && ramHandlerSrc.exists()) ramHandlerSrc.toPath().copyTo(tempDir.resolve("Ram-Handler"), overwrite = true)
        val sysCfgSrc = File(srcHd, "Devs/system-configuration")
        if (sysCfgSrc.exists()) sysCfgSrc.toPath().copyTo(tempDir.resolve("system-configuration"), overwrite = true)

        val ksDirs = listOf(
            File("${System.getProperty("user.home")}/Documents/FS-UAE/Kickstarts"),
            File("${System.getProperty("user.home")}/Documents/Kickstarts")
        )
        val ksDir = ksDirs.firstOrNull { it.isDirectory } ?: ksDirs.first()
        val candidates = ksDirs.filter { it.isDirectory }.flatMap { d -> d.listFiles()?.filter { it.name.endsWith(".rom", ignoreCase = true) && it.name.contains(model, ignoreCase = true) } ?: emptyList() }
        val preferredVersion = if (model == "A500") "v1.3" else "v3.1"
        val rom = candidates.firstOrNull { it.name.contains(preferredVersion, ignoreCase = true) && it.name.contains("[!", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains(preferredVersion, ignoreCase = true) && !it.name.contains("[b", ignoreCase = true) && !it.name.contains("[h", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains(preferredVersion, ignoreCase = true) }
        if (rom == null) {
            System.err.println("Warning: no Kickstart ROM for $model found in ${ksDir}; using bundled AROS.")
        }
        val cmd = mutableListOf(
            "copperline",
            "--factory",
            "--model", model,
            "--cpu", cpu
        )
        cmd.addAll(memoryArgs)
        if (rom != null) cmd.add(rom.absolutePath)
        cmd.addAll(listOf(
            "--run", stagedProg.toString()
        ))
        if (!quiet) println("Launching Copperline...")
        val pb = ProcessBuilder(cmd).inheritIO()
        try {
            pb.start().waitFor()
        } catch (_: Exception) {
            System.err.println("Cannot find 'copperline'. Install it from https://copperline.dev/")
        }
    }
}
