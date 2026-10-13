package prog8.intermediate

/**
 * Shared format definitions for IR file reading and writing.
 * Keeps the text format synchronized between reader and writer.
 */
/**
 * The version of the .p8ir file format. Format 2 uses structured instruction operands and is
 * intentionally incompatible with the older positional format. Format 3 adds union struct definitions.
 * Format 4 replaces the ARRAY_POINTER base type with ARRAY+POINTER+pointeeSub.
 * Format 5 adds the frame:<offset> memory address base (stack frame slots) and the SUB FRAMESIZE attribute.
 * Format 6 adds the SUB VREGSLOTS attribute (per-activation virtual register slots).
 * Format 7 adds the shared=true attribute on static variables (the @shared tag).
 * Format 8 changes the incoming-argument area to word-packed slots (2-byte slots for
 * byte/word parameters); older files with one 4-byte slot per parameter are rejected
 * because their layout would silently mismatch the new convention.
 */
const val IR_FORMAT_VERSION = 8

/** lowest .p8ir format version this reader accepts; formats 5-7 only add syntax that older files never use */
const val IR_FORMAT_MIN_SUPPORTED = 8

object IRFormat {
    // Regex patterns for parsing IR text lines
    val VAR_NO_INIT = Regex("(?<type>.+?)(?<arrayspec>\\[.+?\\])? (?<name>.+) zp=(?<zp>.+?)\\s?(split=(?<split>.+?))?\\s?(align=(?<align>.+?))?\\s?(inBss=(?<inBss>.+?))?\\s?(shared=(?<shared>.+?))?\\s?(readonly=(?<readonly>.+?))?")
    val VAR_INIT = Regex("(?<type>.+?)(?<arrayspec>\\[.+?\\])? (?<name>.+)=(?<value>.*?) zp=(?<zp>.+?)\\s?(split=(?<split>.+?))?\\s?(align=(?<align>.+?))?\\s?(inBss=(?<inBss>.+?))?\\s?(shared=(?<shared>.+?))?\\s?(readonly=(?<readonly>.+?))?")
    val CONSTANT = Regex("(.+?) (.+)=(.*?)")
    val MEMORY_MAPPED = Regex("@(.+?)(\\[.+?\\])? (.+)=(.+)")
    val MEMORY_SLAB = Regex("(.+) (.+) (.+)")
    val POSITION_SINGLE = Regex("\\[(.+): line (.+) col (.+)-(.+)\\]")
}
