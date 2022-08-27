package prog8.intermediate

import prog8.code.core.*
import java.io.BufferedWriter
import kotlin.io.path.bufferedWriter
import kotlin.io.path.div

class IRFileWriter(private val irProgram: IRProgram) {
    private val outfile = irProgram.options.outputDir / ("${irProgram.name}.p8ir")
    private val out = outfile.bufferedWriter()

    fun writeFile() {
        println("Writing intermediate representation to $outfile")
        out.write("<PROGRAM NAME=${irProgram.name}>\n")
        writeOptions()
        writeVariableAllocations()

        if(!irProgram.options.dontReinitGlobals) {
            // note: this a block of code that loads values and stores them into the global variables to reset their values.
            out.write("\n<INITGLOBALS>\n")
            irProgram.globalInits.forEach { out.writeLine(it) }
            out.write("</INITGLOBALS>\n")
        }
        writeBlocks()
        out.write("</PROGRAM>\n")
        out.close()
    }

    private fun writeBlocks() {
        irProgram.blocks.forEach { block ->
            out.write("\n<BLOCK NAME=${block.name} ADDRESS=${block.address} ALIGN=${block.alignment} POS=${block.position}>\n")
            block.inlineAssembly.forEach {
                out.write("<INLINEASM POS=${it.position}>\n")
                out.write(it.asm)
                if(!it.asm.endsWith('\n'))
                    out.write("\n")
                out.write("</INLINEASM>\n")
            }
            block.subroutines.forEach {
                out.write("<SUB SCOPEDNAME=${it.scopedName.joinToString(".")} RETURNTYPE=${it.returnType} POS=${it.position}>\n")
                it.lines.forEach { line -> out.writeLine(line) }
                out.write("</SUB>\n")
            }
            block.asmSubroutines.forEach {
                out.write("<ASMSUB SCOPEDNAME=${it.scopedName.joinToString(".")} ADDRESS=${it.address} POS=${it.position}>\n")
                it.lines.forEach { line -> out.writeLine(line) }
                out.write("</ASMSUB>\n")
            }
            out.write("</BLOCK>\n")
        }
    }

    private fun writeOptions() {
        out.write("<OPTIONS>\n")
        out.write("compTarget = ${irProgram.options.compTarget.name}\n")
        out.write("output = ${irProgram.options.output}\n")
        out.write("launcher = ${irProgram.options.launcher}\n")
        out.write("zeropage = ${irProgram.options.zeropage}\n")
        out.write("zpReserved = ${irProgram.options.zpReserved}\n")
        out.write("loadAddress = ${irProgram.options.loadAddress}\n")
        out.write("dontReinitGlobals = ${irProgram.options.dontReinitGlobals}\n")
        out.write("evalStackBaseAddress = ${irProgram.options.evalStackBaseAddress}\n")
        // other options not yet useful here?
        out.write("</OPTIONS>\n")
    }

    private fun writeVariableAllocations() {
        out.write("\n<VARIABLES>\n")
        for (variable in irProgram.st.allVariables) {
            val typeStr = when(variable.dt) {
                DataType.UBYTE, DataType.ARRAY_UB, DataType.STR -> "ubyte"
                DataType.BYTE, DataType.ARRAY_B -> "byte"
                DataType.UWORD, DataType.ARRAY_UW -> "uword"
                DataType.WORD, DataType.ARRAY_W -> "word"
                DataType.FLOAT, DataType.ARRAY_F -> "float"
                else -> throw InternalCompilerException("weird dt")
            }
            val value = when(variable.dt) {
                DataType.FLOAT -> (variable.onetimeInitializationNumericValue ?: 0.0).toString()
                in NumericDatatypes -> (variable.onetimeInitializationNumericValue ?: 0).toHex()
                DataType.STR -> {
                    val encoded = irProgram.encoding.encodeString(variable.onetimeInitializationStringValue!!.first, variable.onetimeInitializationStringValue!!.second) + listOf(0u)
                    encoded.joinToString(",") { it.toInt().toHex() }
                }
                DataType.ARRAY_F -> {
                    if(variable.onetimeInitializationArrayValue!=null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toString() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                in ArrayDatatypes -> {
                    if(variable.onetimeInitializationArrayValue!==null) {
                        variable.onetimeInitializationArrayValue!!.joinToString(",") { it.number!!.toHex() }
                    } else {
                        (1..variable.length!!).joinToString(",") { "0" }
                    }
                }
                else -> throw InternalCompilerException("weird dt")
            }
            // TODO have uninitialized variables? (BSS SECTION)
            out.write("VAR ${variable.scopedName.joinToString(".")} $typeStr = $value\n")
        }
        out.write("</VARIABLES>\n")

        out.write("\n<MEMORYMAPPEDVARIABLES>\n")
        for (variable in irProgram.st.allMemMappedVariables) {
            val typeStr = when(variable.dt) {
                DataType.UBYTE, DataType.ARRAY_UB, DataType.STR -> "ubyte"
                DataType.BYTE, DataType.ARRAY_B -> "byte"
                DataType.UWORD, DataType.ARRAY_UW -> "uword"
                DataType.WORD, DataType.ARRAY_W -> "word"
                DataType.FLOAT, DataType.ARRAY_F -> "float"
                else -> throw InternalCompilerException("weird dt")
            }
            out.write("MAP ${variable.scopedName.joinToString(".")} $typeStr ${variable.address}\n")
        }
        out.write("</MEMORYMAPPEDVARIABLES>\n")

        out.write("\n<MEMORYSLABS>\n")
        irProgram.st.allMemorySlabs.forEach{ slab -> out.write("SLAB _${slab.name} ${slab.size} ${slab.align}\n") }
        out.write("</MEMORYSLABS>\n")
    }

    private fun BufferedWriter.writeLine(line: IRCodeLine) {
        when(line) {
            is IRCodeComment -> write("; ${line.comment}\n")
            is IRCodeInstruction -> {
                write(line.ins.toString() + "\n")
            }
            is IRCodeLabel -> write("_" + line.name.joinToString(".") + ":\n")
            is IRCodeInlineBinary -> {
                write("incbin \"${line.file}\"")
                if(line.offset!=null)
                    write(",${line.offset}")
                if(line.length!=null)
                    write(",${line.length}")
                write("\n")
            }
            else -> throw AssemblyError("invalid vm code line")
        }
    }
}