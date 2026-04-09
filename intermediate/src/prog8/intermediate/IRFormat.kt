package prog8.intermediate

/**
 * Shared format definitions for IR file reading and writing.
 * Keeps the text format synchronized between reader and writer.
 */
object IRFormat {
    // Regex patterns for parsing IR text lines
    val VAR_NO_INIT = Regex("(?<type>.+?)(?<arrayspec>\\[.+?\\])? (?<name>.+) zp=(?<zp>.+?)\\s?(split=(?<split>.+?))?\\s?(align=(?<align>.+?))?")
    val VAR_INIT = Regex("(?<type>.+?)(?<arrayspec>\\[.+?\\])? (?<name>.+)=(?<value>.*?) zp=(?<zp>.+?)\\s?(split=(?<split>.+?))?\\s?(align=(?<align>.+?))?")
    val CONSTANT = Regex("(.+?) (.+)=(.*?)")
    val MEMORY_MAPPED = Regex("@(.+?)(\\[.+?\\])? (.+)=(.+)")
    val MEMORY_SLAB = Regex("(.+) (.+) (.+)")
    val POSITION_SINGLE = Regex("\\[(.+): line (.+) col (.+)-(.+)\\]")
}
