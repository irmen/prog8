package prog8.intermediate

import prog8.code.*
import prog8.code.core.*
import prog8.code.target.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.bufferedReader


class IRFileReader {

    fun read(irSourceCode: CharSequence): IRProgram {
        return parseProgram(irSourceCode.lineSequence().iterator())
    }

    fun read(irSourceFile: Path): IRProgram {
        println("Reading intermediate representation from $irSourceFile")
        irSourceFile.bufferedReader().use { reader ->
            return parseProgram(reader.lineSequence().iterator())
        }
    }

    private fun parseProgram(lines: Iterator<String>): IRProgram {
        val programPattern = Regex("<PROGRAM NAME=(.+)>")
        val line = lines.next()
        val match = programPattern.matchEntire(line) ?: throw IRParseException("invalid PROGRAM")
        val programName = match.groups[1]!!.value
        val options = parseOptions(lines)
        val variables = parseVariables(lines, options.dontReinitGlobals)
        val memorymapped = parseMemMapped(lines)
        val slabs = parseSlabs(lines)
        val initGlobals = parseInitGlobals(lines)
        val blocks = parseBlocksUntilProgramEnd(lines, variables)

        val st = IRSymbolTable(null)
        variables.forEach { st.add(it) }
        memorymapped.forEach { st.add(it) }
        slabs.forEach { st.add(it) }

        val program = IRProgram(programName, st, options, options.compTarget)
        program.addGlobalInits(initGlobals)
        blocks.forEach{ program.addBlock(it) }
        return program
    }

    private fun parseOptions(lines: Iterator<String>): CompilationOptions {
        var line = lines.next()
        while(line.isBlank())
            line = lines.next()
        var target: ICompilationTarget = VMTarget()
        var outputType = OutputType.PRG
        var launcher = CbmPrgLauncherType.NONE
        var zeropage = ZeropageType.FULL
        val zpReserved = mutableListOf<UIntRange>()
        var loadAddress = target.machine.PROGRAM_LOAD_ADDRESS
        var dontReinitGlobals = false
        var evalStackBaseAddress: UInt? = null
        var outputDir = Path("")
        if(line!="<OPTIONS>")
            throw IRParseException("invalid OPTIONS")
        while(true) {
            line = lines.next()
            if(line=="</OPTIONS>")
                break
            val (name, value) = line.split('=', limit=2)
            when(name) {
                "compTarget" -> {
                    target = when(value) {
                        VMTarget.NAME -> VMTarget()
                        C64Target.NAME -> C64Target()
                        C128Target.NAME -> C128Target()
                        AtariTarget.NAME -> AtariTarget()
                        Cx16Target.NAME -> Cx16Target()
                        else -> throw IRParseException("invalid target $value")
                    }
                }
                "output" -> outputType = OutputType.valueOf(value)
                "launcher" -> launcher = CbmPrgLauncherType.valueOf(value)
                "zeropage" -> zeropage = ZeropageType.valueOf(value)
                "loadAddress" -> loadAddress = value.toUInt()
                "dontReinitGlobals" -> dontReinitGlobals = value.toBoolean()
                "evalStackBaseAddress" -> evalStackBaseAddress = if(value=="null") null else value.toUInt()
                "zpReserved" -> {
                    val (start, end) = value.split(',')
                    zpReserved.add(UIntRange(start.toUInt(), end.toUInt()))
                }
                "outputDir" -> outputDir = Path(value)
                else -> throw IRParseException("illegal OPTION $name")
            }
        }

        return CompilationOptions(
            outputType,
            launcher,
            zeropage,
            zpReserved,
            false,
            false,
            target,
            loadAddress,
            dontReinitGlobals = dontReinitGlobals,
            evalStackBaseAddress = evalStackBaseAddress,
            outputDir = outputDir
        )
    }

    private fun parseVariables(lines: Iterator<String>, dontReinitGlobals: Boolean): List<StStaticVariable> {
        var line = lines.next()
        while(line.isBlank())
            line = lines.next()
        if(line!="<VARIABLES>")
            throw IRParseException("invalid VARIABLES")
        val variables = mutableListOf<StStaticVariable>()
        val varPattern = Regex("(.+?)(\\[.+?\\])? (.+)=(.+?) (zp=(.+))?")
        while(true) {
            line = lines.next()
            if(line=="</VARIABLES>")
                break
            // examples:
            // uword main.start.qq2=0 zp=REQUIRE_ZP
            // ubyte[6] main.start.namestring=105,114,109,101,110,0
            val match = varPattern.matchEntire(line) ?: throw IRParseException("invalid VARIABLE $line")
            val (type, arrayspec, name, value, _, zpwish) = match.destructured
            val arraysize = if(arrayspec.isNotBlank()) arrayspec.substring(1, arrayspec.length-1).toInt() else null
            val dt: DataType = parseDatatype(type, arraysize!=null)
            val zp = if(zpwish.isBlank()) ZeropageWish.DONTCARE else ZeropageWish.valueOf(zpwish)
            var initNumeric: Double? = null
            var initArray: StArray? = null
            when(dt) {
                in NumericDatatypes -> {
                    if(dontReinitGlobals) {
                        // we need to specify a one time initialization value
                        initNumeric = value.toDouble()
                    }
                }
                in ArrayDatatypes -> {
                    initArray = value.split(',').map {
                        if(it.startsWith('&'))
                            StArrayElement(null, it.drop(1).split('.'))
                        else
                            StArrayElement(it.toDouble(), null)
                    }
                }
                DataType.STR -> throw IRParseException("STR should have been converted to byte array")
                else -> throw IRParseException("weird dt")
            }
            variables.add(StStaticVariable(name, dt, initNumeric, null, initArray, arraysize, zp, Position.DUMMY))
        }
        return variables
    }

    private fun parseMemMapped(lines: Iterator<String>): List<StMemVar> {
        var line = lines.next()
        while(line.isBlank())
            line = lines.next()
        if(line!="<MEMORYMAPPEDVARIABLES>")
            throw IRParseException("invalid MEMORYMAPPEDVARIABLES")
        val memvars = mutableListOf<StMemVar>()
        val mappedPattern = Regex("&(.+?)(\\[.+?\\])? (.+)=(.+)")
        while(true) {
            line = lines.next()
            if(line=="</MEMORYMAPPEDVARIABLES>")
                break
            // examples:
            // &uword main.start.mapped=49152
            // &ubyte[20] main.start.mappedarray=49408
            val match = mappedPattern.matchEntire(line) ?: throw IRParseException("invalid MEMORYMAPPEDVARIABLES $line")
            val (type, arrayspec, name, address) = match.destructured
            val arraysize = if(arrayspec.isNotBlank()) arrayspec.substring(1, arrayspec.length-1).toInt() else null
            val dt: DataType = parseDatatype(type, arraysize!=null)
            memvars.add(StMemVar(name, dt, address.toUInt(), arraysize, Position.DUMMY))
        }
        return memvars
    }

    private fun parseDatatype(type: String, isArray: Boolean): DataType {
        if(isArray) {
            return when(type) {
                "byte" -> DataType.ARRAY_B
                "ubyte", "str" -> DataType.ARRAY_UB
                "word" -> DataType.ARRAY_W
                "uword" -> DataType.ARRAY_UW
                "float" -> DataType.ARRAY_F
                "bool" -> DataType.ARRAY_B
                else -> throw IRParseException("invalid dt $type")
            }
        } else {
            return when(type) {
                "byte" -> DataType.BYTE
                "ubyte" -> DataType.UBYTE
                "word" -> DataType.WORD
                "uword" -> DataType.UWORD
                "float" -> DataType.FLOAT
                "bool" -> DataType.BOOL
                // note: 'str' should not occur anymore in IR. Should be 'uword'
                else -> throw IRParseException("invalid dt $type")
            }
        }
    }

    private fun parseSlabs(lines: Iterator<String>): List<StMemorySlab> {
        var line = lines.next()
        while(line.isBlank())
            line = lines.next()
        if(line!="<MEMORYSLABS>")
            throw IRParseException("invalid MEMORYSLABS")
        val slabs = mutableListOf<StMemorySlab>()
        val slabPattern = Regex("SLAB (.+) (.+) (.+)")
        while(true) {
            line = lines.next()
            if(line=="</MEMORYSLABS>")
                break
            // example: "SLAB slabname 4096 0"
            val match = slabPattern.matchEntire(line) ?: throw IRParseException("invalid SLAB $line")
            val (name, size, align) = match.destructured
            slabs.add(StMemorySlab(name, size.toUInt(), align.toUInt(), Position.DUMMY))
        }
        return slabs
    }

    private fun parseInitGlobals(lines: Iterator<String>): IRCodeChunk {
        var line = lines.next()
        while(line.isBlank())
            line = lines.next()
        if(line!="<INITGLOBALS>")
            throw IRParseException("invalid INITGLOBALS")
        line = lines.next()
        var chunk = IRCodeChunk(Position.DUMMY)
        if(line=="<C>") {
            chunk = parseCodeChunk(line, lines)!!
            line = lines.next()
        }
        if(line!="</INITGLOBALS>")
            throw IRParseException("missing INITGLOBALS close tag")
        return chunk
    }

    private fun parseBlocksUntilProgramEnd(lines: Iterator<String>, variables: List<StStaticVariable>): List<IRBlock> {
        val blocks = mutableListOf<IRBlock>()
        while(true) {
            var line = lines.next()
            while (line.isBlank())
                line = lines.next()
            if (line == "</PROGRAM>")
                break
            blocks.add(parseBlock(line, lines, variables))
        }
        return blocks
    }

    private val blockPattern = Regex("<BLOCK NAME=(.+) ADDRESS=(.+) ALIGN=(.+) POS=(.+)>")
    private val inlineAsmPattern = Regex("<INLINEASM POS=(.+)>")
    private val asmsubPattern = Regex("<ASMSUB NAME=(.+) ADDRESS=(.+) CLOBBERS=(.*) RETURNS=(.*) POS=(.+)>")
    private val subPattern = Regex("<SUB NAME=(.+) RETURNTYPE=(.+) POS=(.+)>")
    private val posPattern = Regex("\\[(.+): line (.+) col (.+)-(.+)\\]")

    private fun parseBlock(startline: String, lines: Iterator<String>, variables: List<StStaticVariable>): IRBlock {
        var line = startline
        if(!line.startsWith("<BLOCK "))
            throw IRParseException("invalid BLOCK")
        val match = blockPattern.matchEntire(line) ?: throw IRParseException("invalid BLOCK")
        val (name, address, align, position) = match.destructured
        val addressNum = if(address=="null") null else address.toUInt()
        val block = IRBlock(name, addressNum, IRBlock.BlockAlignment.valueOf(align), parsePosition(position))
        while(true) {
            line = lines.next()
            if(line.isBlank())
                continue
            if(line=="</BLOCK>")
                return block
            if(line.startsWith("<SUB ")) {
                val sub = parseSubroutine(line, lines, variables)
                block += sub
            } else if(line.startsWith("<ASMSUB ")) {
                val sub = parseAsmSubroutine(line, lines)
                block += sub
            } else if(line.startsWith("<INLINEASM ")) {
                val asm = parseInlineAssembly(line, lines)
                block += asm
            } else
                throw IRParseException("invalid line in BLOCK")
        }
    }

    private fun parseInlineAssembly(startline: String, lines: Iterator<String>): IRInlineAsmChunk {
        // <INLINEASM POS=[examples/test.p8: line 8 col 6-9]>
        val match = inlineAsmPattern.matchEntire(startline) ?: throw IRParseException("invalid INLINEASM")
        val pos = parsePosition(match.groupValues[1])
        val asmlines = mutableListOf<String>()
        var line = lines.next()
        while(line!="</INLINEASM>") {
            asmlines.add(line)
            line = lines.next()
        }
        return IRInlineAsmChunk(asmlines.joinToString("\n"), pos)
    }

    private fun parseAsmSubroutine(startline: String, lines: Iterator<String>): IRAsmSubroutine {
        // <ASMSUB NAME=main.testasmsub ADDRESS=null CLOBBERS=A,Y POS=[examples/test.p8: line 14 col 6-11]>
        val match = asmsubPattern.matchEntire(startline) ?: throw IRParseException("invalid ASMSUB")
        val (scopedname, address, clobbers, returnSpec, pos) = match.destructured
        // parse PARAMS
        var line = lines.next()
        if(line!="<PARAMS>")
            throw IRParseException("missing PARAMS")
        val params = mutableListOf<Pair<DataType, RegisterOrStatusflag>>()
        while(true) {
            line = lines.next()
            if(line=="</PARAMS>")
                break
            val (datatype, regOrSf) = line.split(' ')
            val dt = parseDatatype(datatype, datatype.contains('['))
            val regsf = parseRegisterOrStatusflag(regOrSf)
            params += Pair(dt, regsf)
        }
        line = lines.next()
        val asm = parseInlineAssembly(line, lines)
        while(line!="</ASMSUB>")
            line = lines.next()
        val clobberRegs = if(clobbers.isBlank()) emptyList() else clobbers.split(',').map { CpuRegister.valueOf(it) }
        val returns = mutableListOf<Pair<DataType, RegisterOrStatusflag>>()
        returnSpec.split(',').forEach{ rs ->
            val (regstr, dtstr) = rs.split(':')
            val dt = parseDatatype(dtstr, false)
            val regsf = parseRegisterOrStatusflag(regstr)
            returns.add(Pair(dt, regsf))
        }
        return IRAsmSubroutine(scopedname,
            parsePosition(pos), if(address=="null") null else address.toUInt(),
            clobberRegs.toSet(),
            params,
            returns,
            asm.assembly)
    }

    private fun parseSubroutine(startline: String, lines: Iterator<String>, variables: List<StStaticVariable>): IRSubroutine {
        // <SUB NAME=main.start.nested.nested2 RETURNTYPE=null POS=[examples/test.p8: line 54 col 14-16]>
        val match = subPattern.matchEntire(startline) ?: throw IRParseException("invalid SUB")
        val (name, returntype, pos) = match.destructured
        val sub = IRSubroutine(name,
            parseParameters(lines, variables),
            if(returntype=="null") null else parseDatatype(returntype, false),
            parsePosition(pos))
        while(true) {
            val line = lines.next()
            if(line=="</SUB>")
                return sub
            val chunk = if(line=="<C>")
                parseCodeChunk(line, lines)
            else if(line.startsWith("<INLINEASM "))
                parseInlineAssembly(line, lines)
            else
                throw IRParseException("invalid sub child node")

            if (chunk == null)
                break
            else
                sub += chunk
        }
        val line = lines.next()
        if(line=="</SUB>")
            throw IRParseException("missing SUB close tag")
        return sub
    }

    private fun parseParameters(lines: Iterator<String>, variables: List<StStaticVariable>): List<IRSubroutine.IRParam> {
        var line = lines.next()
        if(line!="<PARAMS>")
            throw IRParseException("missing PARAMS")
        val params = mutableListOf<IRSubroutine.IRParam>()
        while(true) {
            line = lines.next()
            if(line=="</PARAMS>")
                return params
            val (datatype, name) = line.split(' ')
            val dt = parseDatatype(datatype, datatype.contains('['))
            val orig = variables.single { it.dt==dt && it.name==name}
            params.add(IRSubroutine.IRParam(name, dt, orig))
        }
    }

    private fun parseCodeChunk(firstline: String, lines: Iterator<String>): IRCodeChunk? {
        if(firstline!="<C>") {
            if(firstline=="</SUB>")
                return null
            else
                throw IRParseException("invalid or empty <C>ODE chunk")
        }
        val chunk = IRCodeChunk(Position.DUMMY)
        while(true) {
            var line = lines.next()
            if (line == "</C>")
                return chunk
            if (line.isBlank() || line.startsWith(';'))
                continue
            if(line=="<BYTES>") {
                val bytes = mutableListOf<UByte>()
                line = lines.next()
                while(line!="</BYTES>") {
                    line.trimEnd().windowed(size=2, step=2) {
                        bytes.add(it.toString().toUByte(16))
                    }
                    line = lines.next()
                }
                chunk += IRCodeInlineBinary(bytes)
            } else {
                chunk += parseIRCodeLine(line, 0, mutableMapOf())
            }
        }
    }

    private fun parseRegisterOrStatusflag(regs: String): RegisterOrStatusflag {
        var reg: RegisterOrPair? = null
        var sf: Statusflag? = null
        try {
            reg = RegisterOrPair.valueOf(regs)
        } catch (x: IllegalArgumentException) {
            sf = Statusflag.valueOf(regs)
        }
        return RegisterOrStatusflag(reg, sf)
    }

    private fun parsePosition(strpos: String): Position {
        // example: "[library:/prog8lib/virtual/textio.p8: line 5 col 2-4]"
        val match = posPattern.matchEntire(strpos) ?: throw IRParseException("invalid Position")
        val (file, line, startCol, endCol) = match.destructured
        return Position(file, line.toInt(), startCol.toInt(), endCol.toInt())
    }

}
