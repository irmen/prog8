package prog8.code.target

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

object FsUaeEmulator {
    fun launch(
        programFile: Path,
        quiet: Boolean,
        model: String,
        preferredVersion: String,
        romHint: String,
        sourceHdName: String,
        cmdDir: String,
        libsDir: String,
        commands: List<String>,
        libs: List<String>,
        extraFiles: List<Pair<String, String>>,
        startupSequence: String
    ) {
        val home = System.getProperty("user.home")
        val ksDir = File("$home/Documents/FS-UAE/Kickstarts")
        val candidates = ksDir.listFiles()?.filter { it.name.endsWith(".rom", ignoreCase = true) && it.name.contains(model, ignoreCase = true) } ?: emptyList()
        val rom = candidates.firstOrNull { it.name.contains(preferredVersion, ignoreCase = true) && it.name.contains("[!", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains(preferredVersion, ignoreCase = true) && !it.name.contains("[b", ignoreCase = true) && !it.name.contains("[h", ignoreCase = true) }
            ?: candidates.firstOrNull { it.name.contains(preferredVersion, ignoreCase = true) }
            ?: candidates.firstOrNull { !it.name.contains("[b", ignoreCase = true) && !it.name.contains("[h", ignoreCase = true) }
            ?: candidates.firstOrNull()
            ?: ksDir.listFiles()?.firstOrNull { it.name.contains(romHint, ignoreCase = true) && it.name.endsWith(".rom", ignoreCase = true) }
        if (rom == null) {
            System.err.println("Cannot find a Kickstart ROM for $model in ${ksDir}")
            return
        }
        val tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "prog8-fsuae-$model")
        tempDir.createDirectories()
        val hdDir = tempDir.resolve("hd")
        hdDir.resolve("S").createDirectories()
        hdDir.resolve("C").createDirectories()
        hdDir.resolve("L").createDirectories()
        hdDir.resolve("Libs").createDirectories()
        hdDir.resolve("Devs").createDirectories()
        val progName = programFile.fileName.toString()
        programFile.copyTo(hdDir.resolve(progName), overwrite = true)
        val srcHd = File("$home/Documents/FS-UAE/Hard Drives/$sourceHdName")
        for (cmd in commands) {
            val src = File(srcHd, "$cmdDir/$cmd")
            if (src.exists()) src.toPath().copyTo(hdDir.resolve("C/$cmd"), overwrite = true)
        }
        for (lib in libs) {
            val src = File(srcHd, "$libsDir/$lib")
            if (src.exists()) src.toPath().copyTo(hdDir.resolve("Libs/$lib"), overwrite = true)
        }
        for ((srcRel, dstRel) in extraFiles) {
            val src = File(srcHd, srcRel)
            if (src.exists()) {
                val dst = hdDir.resolve(dstRel)
                dst.parent.createDirectories()
                src.toPath().copyTo(dst, overwrite = true)
            }
        }
        hdDir.resolve("S/Startup-Sequence").writeText(startupSequence.replace("\$progName", progName))
        val memoryArgs = if (model == "A500") listOf("--slow_memory=512") else listOf("--fast_memory=2048")
        val cmd = listOf(
            "fs-uae",
            "--amiga_model=$model",
            "--kickstart_file=${rom.absolutePath}",
            "--hard_drive_0=${hdDir}",
            "--automatic_input_grab=0",
            "--window_width=1280",
            "--window_height=1024",
            "--floppy_drive_volume=8",
            "--floppy_drive_volume_empty=8"
        ) + memoryArgs
        if (!quiet) println("Launching FS-UAE...")
        val pb = ProcessBuilder(cmd).inheritIO()
        try {
            pb.start().waitFor()
        } catch (_: Exception) {
            System.err.println("Cannot find 'fs-uae'. Install it from https://fs-uae.net/")
        }
    }
}
