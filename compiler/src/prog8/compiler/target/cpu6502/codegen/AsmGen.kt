package prog8.compiler.target.cpu6502.codegen

import com.github.michaelbull.result.fold
import prog8.ast.*
import prog8.ast.antlr.escape
import prog8.ast.base.*
import prog8.ast.expressions.*
import prog8.ast.statements.*
import prog8.compiler.*
import prog8.compiler.functions.BuiltinFunctions
import prog8.compiler.functions.FSignature
import prog8.compiler.target.*
import prog8.compiler.target.cbm.AssemblyProgram
import prog8.compiler.target.cpu6502.codegen.assignment.AsmAssignment
import prog8.compiler.target.cpu6502.codegen.assignment.AssignmentAsmGen
import prog8.optimizer.CallGraph
import prog8.parser.SourceCode
import java.nio.file.Path
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.MutableSet
import kotlin.collections.any
import kotlin.collections.chunked
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.contains
import kotlin.collections.drop
import kotlin.collections.filter
import kotlin.collections.filterIsInstance
import kotlin.collections.first
import kotlin.collections.firstOrNull
import kotlin.collections.forEach
import kotlin.collections.getValue
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.joinToString
import kotlin.collections.last
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.collections.partition
import kotlin.collections.plus
import kotlin.collections.removeLast
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.single
import kotlin.collections.sortedBy
import kotlin.collections.toList
import kotlin.collections.toMutableList
import kotlin.collections.zip
import kotlin.io.path.Path
import kotlin.math.absoluteValue


internal class AsmGen(private val program: Program,
                      val errors: IErrorReporter,
                      val zeropage: Zeropage,
                      val options: CompilationOptions,
                      private val compTarget: ICompilationTarget,
                      private val outputDir: Path): IAssemblyGenerator {

    // for expressions and augmented assignments:
    val optimizedByteMultiplications = setOf(3,5,6,7,9,10,11,12,13,14,15,20,25,40,50,80,100)
    val optimizedWordMultiplications = setOf(3,5,6,7,9,10,12,15,20,25,40,50,80,100,320,640)
    private val callGraph = CallGraph(program)

    private val assemblyLines = mutableListOf<String>()
    private val globalFloatConsts = mutableMapOf<Double, String>()     // all float values in the entire program (value -> varname)
    private val allocatedZeropageVariables = mutableMapOf<String, Pair<Int, DataType>>()
    private val breakpointLabels = mutableListOf<String>()
    private val forloopsAsmGen = ForLoopsAsmGen(program, this)
    private val postincrdecrAsmGen = PostIncrDecrAsmGen(program, this)
    private val functioncallAsmGen = FunctionCallAsmGen(program, this)
    private val expressionsAsmGen = ExpressionsAsmGen(program, this)
    private val assignmentAsmGen = AssignmentAsmGen(program, this, expressionsAsmGen)
    private val builtinFunctionsAsmGen = BuiltinFunctionsAsmGen(program, this, assignmentAsmGen)
    internal val loopEndLabels = ArrayDeque<String>()
    private val blockLevelVarInits = mutableMapOf<Block, MutableSet<VarDecl>>()
    internal val slabs = mutableMapOf<String, Int>()
    internal val removals = mutableListOf<Pair<Statement, IStatementContainer>>()

    override fun compileToAssembly(): IAssemblyProgram {
        assemblyLines.clear()
        loopEndLabels.clear()

        println("Generating assembly code... ")

        header()
        val allBlocks = program.allBlocks
        if(allBlocks.first().name != "main")
            throw AssemblyError("first block should be 'main'")
        for(b in program.allBlocks)
            block2asm(b)

        for(removal in removals.toList()) {
            removal.second.remove(removal.first)
            removals.remove(removal)
        }

        slaballocations()
        footer()

        val outputFile = outputDir.resolve("${program.name}.asm").toFile()
        outputFile.printWriter().use {
            for (line in assemblyLines) { it.println(line) }
        }

        if(options.optimize) {
            assemblyLines.clear()
            assemblyLines.addAll(outputFile.readLines())
            var optimizationsDone = 1
            while (optimizationsDone > 0) {
                optimizationsDone = optimizeAssembly(assemblyLines)
            }
            outputFile.printWriter().use {
                for (line in assemblyLines) { it.println(line) }
            }
        }

        return if(errors.noErrors())
            AssemblyProgram(true, program.name, outputDir, compTarget.name)
        else {
            AssemblyProgram(false, "<error>", outputDir, compTarget.name)
        }
    }

    internal fun isTargetCpu(cpu: CpuType) = compTarget.machine.cpu == cpu
    internal fun haveFPWR() = compTarget is Cx16Target

    private fun header() {
        val ourName = this.javaClass.name
        val cpu = when(compTarget.machine.cpu) {
            CpuType.CPU6502 -> "6502"
            CpuType.CPU65c02 -> "w65c02"
            else -> "unsupported"
        }

        out("; $cpu assembly code for '${program.name}'")
        out("; generated by $ourName on ${LocalDateTime.now().withNano(0)}")
        out("; assembler syntax is for the 64tasm cross-assembler")
        out("; output options: output=${options.output} launcher=${options.launcher} zp=${options.zeropage}")
        out("\n.cpu  '$cpu'\n.enc  'none'\n")

        program.actualLoadAddress = program.definedLoadAddress
        if (program.actualLoadAddress == 0)   // fix load address
            program.actualLoadAddress = if (options.launcher == LauncherType.BASIC)
                compTarget.machine.BASIC_LOAD_ADDRESS else compTarget.machine.RAW_LOAD_ADDRESS

        // the global prog8 variables needed
        val zp = compTarget.machine.zeropage
        out("P8ZP_SCRATCH_B1 = ${zp.SCRATCH_B1}")
        out("P8ZP_SCRATCH_REG = ${zp.SCRATCH_REG}")
        out("P8ZP_SCRATCH_W1 = ${zp.SCRATCH_W1}    ; word")
        out("P8ZP_SCRATCH_W2 = ${zp.SCRATCH_W2}    ; word")
        out("P8ESTACK_LO = ${compTarget.machine.ESTACK_LO.toHex()}")
        out("P8ESTACK_HI = ${compTarget.machine.ESTACK_HI.toHex()}")

        when {
            options.launcher == LauncherType.BASIC -> {
                if (program.actualLoadAddress != 0x0801)
                    throw AssemblyError("BASIC output must have load address $0801")
                out("; ---- basic program with sys call ----")
                out("* = ${program.actualLoadAddress.toHex()}")
                val year = LocalDate.now().year
                out("  .word  (+), $year")
                out("  .null  $9e, format(' %d ', _prog8_entrypoint), $3a, $8f, ' prog8'")
                out("+\t.word  0")
                out("_prog8_entrypoint\t; assembly code starts here\n")
                if(!options.noSysInit)
                    out("  jsr  ${compTarget.name}.init_system")
                out("  jsr  ${compTarget.name}.init_system_phase2")
            }
            options.output == OutputType.PRG -> {
                out("; ---- program without basic sys call ----")
                out("* = ${program.actualLoadAddress.toHex()}\n")
                if(!options.noSysInit)
                    out("  jsr  ${compTarget.name}.init_system")
                out("  jsr  ${compTarget.name}.init_system_phase2")
            }
            options.output == OutputType.RAW -> {
                out("; ---- raw assembler program ----")
                out("* = ${program.actualLoadAddress.toHex()}\n")
            }
        }

        if(options.zeropage !in arrayOf(ZeropageType.BASICSAFE, ZeropageType.DONTUSE)) {
            out("""
                ; zeropage is clobbered so we need to reset the machine at exit
                lda  #>sys.reset_system
                pha
                lda  #<sys.reset_system
                pha""")
        }

        // make sure that on the cx16 and c64, basic rom is banked in again when we exit the program
        when(compTarget.name) {
            Cx16Target.name -> {
                if(options.floats)
                    out("  lda  #4 |  sta  $01")    // to use floats, make sure Basic rom is banked in
                out("  jsr  main.start |  lda  #4 |  sta  $01 |  rts")
            }
            C64Target.name -> out("  jsr  main.start |  lda  #31 |  sta  $01 |  rts")
            else -> jmp("main.start")
        }
    }

    private fun slaballocations() {
        out("; memory slabs")
        out("prog8_slabs\t.block")
        for((name, size) in slabs)
            out("$name\t.fill  $size")
        out("\t.bend")
    }

    private fun footer() {
        // the global list of all floating point constants for the whole program
        out("; global float constants")
        for (flt in globalFloatConsts) {
            val floatFill = compTarget.machine.getFloat(flt.key).makeFloatFillAsm()
            val floatvalue = flt.key
            out("${flt.value}\t.byte  $floatFill  ; float $floatvalue")
        }
        out("prog8_program_end\t; end of program label for progend()")
    }

    private fun block2asm(block: Block) {
        out("\n\n; ---- block: '${block.name}' ----")
        if(block.address!=null)
            out("* = ${block.address!!.toHex()}")
        else {
            if("align_word" in block.options())
                out("\t.align 2")
            else if("align_page" in block.options())
                out("\t.align $100")
        }

        out("${block.name}\t" + (if("force_output" in block.options()) ".block\n" else ".proc\n"))

        outputSourceLine(block)
        zeropagevars2asm(block.statements)
        memdefs2asm(block.statements)
        vardecls2asm(block.statements)
        out("\n; subroutines in this block")

        // first translate regular statements, and then put the subroutines at the end.
        val (subroutine, stmts) = block.statements.partition { it is Subroutine }
        stmts.forEach { translate(it) }
        subroutine.forEach { translateSubroutine(it as Subroutine) }

        // if any global vars need to be initialized, generate a subroutine that does this
        // it will be called from program init.
        if(block in blockLevelVarInits) {
            out("prog8_init_vars\t.proc\n")
            blockLevelVarInits.getValue(block).forEach { decl ->
                val scopedFullName = decl.makeScopedName(decl.name).split('.')
                require(scopedFullName.first()==block.name)
                assignInitialValueToVar(decl, scopedFullName.drop(1))
            }
            out("  rts\n  .pend")
        }

        out(if("force_output" in block.options()) "\n\t.bend\n" else "\n\t.pend\n")
    }

    private fun assignInitialValueToVar(decl: VarDecl, variableName: List<String>) {
        val asmName = asmVariableName(variableName)
        assignmentAsmGen.assignExpressionToVariable(decl.value!!, asmName, decl.datatype, decl.definingSubroutine)
    }

    private var generatedLabelSequenceNumber: Int = 0

    internal fun makeLabel(postfix: String): String {
        generatedLabelSequenceNumber++
        return "${generatedLabelPrefix}${generatedLabelSequenceNumber}_$postfix"
    }

    private fun outputSourceLine(node: Node) {
        out(" ;\tsrc line: ${node.position.file}:${node.position.line}")
    }

    internal fun out(str: String, splitlines: Boolean = true) {
        val fragment = (if(" | " in str) str.replace("|", "\n") else str).trim('\n')

        if (splitlines) {
            for (line in fragment.split('\n')) {
                val trimmed = if (line.startsWith(' ')) "\t" + line.trim() else line.trim()
                // trimmed = trimmed.replace(Regex("^\\+\\s+"), "+\t")  // sanitize local label indentation
                assemblyLines.add(trimmed)
            }
        } else assemblyLines.add(fragment)
    }

    private fun zeropagevars2asm(statements: List<Statement>) {
        out("; vars allocated on zeropage")
        val variables = statements.filterIsInstance<VarDecl>().filter { it.type==VarDeclType.VAR }
        for(variable in variables) {
            val fullName = variable.makeScopedName(variable.name)
            val zpVar = allocatedZeropageVariables[fullName]
            if(zpVar==null) {
                // This var is not on the ZP yet. Attempt to move it there (if it's not a float, those take up too much space)
                if(variable.zeropage != ZeropageWish.NOT_IN_ZEROPAGE &&
                        variable.datatype in zeropage.allowedDatatypes
                        && variable.datatype != DataType.FLOAT
                        && options.zeropage != ZeropageType.DONTUSE) {
                    try {
                        val errors = ErrorReporter()
                        val address = zeropage.allocate(fullName, variable.datatype, null, errors)
                        errors.report()
                        out("${variable.name} = $address\t; auto zp ${variable.datatype}")
                        // make sure we add the var to the set of zpvars for this block
                        allocatedZeropageVariables[fullName] = address to variable.datatype
                    } catch (x: ZeropageDepletedError) {
                        // leave it as it is.
                    }
                }
            }
        }
    }

    private fun vardecl2asm(decl: VarDecl) {
        val name = decl.name
        when (decl.datatype) {
            DataType.UBYTE -> out("$name\t.byte  0")
            DataType.BYTE -> out("$name\t.char  0")
            DataType.UWORD -> out("$name\t.word  0")
            DataType.WORD -> out("$name\t.sint  0")
            DataType.FLOAT -> out("$name\t.byte  0,0,0,0,0  ; float")
            DataType.STR -> {
                val str = decl.value as StringLiteralValue
                outputStringvar(decl, compTarget.encodeString(str.value, str.altEncoding).plus(0))
            }
            DataType.ARRAY_UB -> {
                val data = makeArrayFillDataUnsigned(decl)
                if (data.size <= 16)
                    out("$name\t.byte  ${data.joinToString()}")
                else {
                    out(name)
                    for (chunk in data.chunked(16))
                        out("  .byte  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_B -> {
                val data = makeArrayFillDataSigned(decl)
                if (data.size <= 16)
                    out("$name\t.char  ${data.joinToString()}")
                else {
                    out(name)
                    for (chunk in data.chunked(16))
                        out("  .char  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_UW -> {
                val data = makeArrayFillDataUnsigned(decl)
                if (data.size <= 16)
                    out("$name\t.word  ${data.joinToString()}")
                else {
                    out(name)
                    for (chunk in data.chunked(16))
                        out("  .word  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_W -> {
                val data = makeArrayFillDataSigned(decl)
                if (data.size <= 16)
                    out("$name\t.sint  ${data.joinToString()}")
                else {
                    out(name)
                    for (chunk in data.chunked(16))
                        out("  .sint  " + chunk.joinToString())
                }
            }
            DataType.ARRAY_F -> {
                val array =
                        if(decl.value!=null)
                            (decl.value as ArrayLiteralValue).value
                        else {
                            // no init value, use zeros
                            val zero = decl.zeroElementValue()
                            Array(decl.arraysize!!.constIndex()!!) { zero }
                        }
                val floatFills = array.map {
                    val number = (it as NumericLiteralValue).number
                    compTarget.machine.getFloat(number).makeFloatFillAsm()
                }
                out(name)
                for (f in array.zip(floatFills))
                    out("  .byte  ${f.second}  ; float ${f.first}")
            }
            else -> {
                throw AssemblyError("weird dt")
            }
        }
    }

    private fun memdefs2asm(statements: List<Statement>) {
        out("\n; memdefs and kernal subroutines")
        val memvars = statements.filterIsInstance<VarDecl>().filter { it.type==VarDeclType.MEMORY || it.type==VarDeclType.CONST }
        for(m in memvars) {
            if(m.value is NumericLiteralValue)
                out("  ${m.name} = ${(m.value as NumericLiteralValue).number.toHex()}")
            else
                out("  ${m.name} = ${asmVariableName((m.value as AddressOf).identifier)}")
        }
        val asmSubs = statements.filterIsInstance<Subroutine>().filter { it.isAsmSubroutine }
        for(sub in asmSubs) {
            val addr = sub.asmAddress
            if(addr!=null) {
                if(sub.statements.isNotEmpty())
                    throw AssemblyError("kernal subroutine cannot have statements")
                out("  ${sub.name} = ${addr.toHex()}")
            }
        }
    }

    private fun vardecls2asm(statements: List<Statement>) {
        out("\n; non-zeropage variables")
        val vars = statements.filterIsInstance<VarDecl>().filter { it.type==VarDeclType.VAR }

        val encodedstringVars = vars
                .filter {it.datatype == DataType.STR }
                .map {
                    val str = it.value as StringLiteralValue
                    it to compTarget.encodeString(str.value, str.altEncoding).plus(0)
                }
        for((decl, variables) in encodedstringVars) {
            outputStringvar(decl, variables)
        }

        // non-string variables
        vars.filter{ it.datatype != DataType.STR }.sortedBy { it.datatype }.forEach {
            if(it.makeScopedName(it.name) !in allocatedZeropageVariables)
                vardecl2asm(it)
        }
    }

    private fun outputStringvar(strdecl: VarDecl, bytes: List<Short>) {
        val sv = strdecl.value as StringLiteralValue
        val altEncoding = if(sv.altEncoding) "@" else ""
        out("${strdecl.name}\t; ${strdecl.datatype} $altEncoding\"${escape(sv.value).replace("\u0000", "<NULL>")}\"")
        val outputBytes = bytes.map { "$" + it.toString(16).padStart(2, '0') }
        for (chunk in outputBytes.chunked(16))
            out("  .byte  " + chunk.joinToString())
    }

    private fun makeArrayFillDataUnsigned(decl: VarDecl): List<String> {
        val array =
                if(decl.value!=null)
                    (decl.value as ArrayLiteralValue).value
                else {
                    // no array init value specified, use a list of zeros
                    val zero = decl.zeroElementValue()
                    Array(decl.arraysize!!.constIndex()!!) { zero }
                }
        return when (decl.datatype) {
            DataType.ARRAY_UB ->
                // byte array can never contain pointer-to types, so treat values as all integers
                array.map {
                    val number = (it as NumericLiteralValue).number.toInt()
                    "$"+number.toString(16).padStart(2, '0')
                }
            DataType.ARRAY_UW -> array.map {
                when (it) {
                    is NumericLiteralValue -> {
                        "$" + it.number.toInt().toString(16).padStart(4, '0')
                    }
                    is AddressOf -> {
                        asmSymbolName(it.identifier)
                    }
                    is IdentifierReference -> {
                        asmSymbolName(it)
                    }
                    else -> throw AssemblyError("weird array elt dt")
                }
            }
            else -> throw AssemblyError("invalid arraysize type")
        }
    }

    private fun makeArrayFillDataSigned(decl: VarDecl): List<String> {
        val array =
                if(decl.value!=null)
                    (decl.value as ArrayLiteralValue).value
                else {
                    // no array init value specified, use a list of zeros
                    val zero = decl.zeroElementValue()
                    Array(decl.arraysize!!.constIndex()!!) { zero }
                }
        return when (decl.datatype) {
            DataType.ARRAY_UB ->
                // byte array can never contain pointer-to types, so treat values as all integers
                array.map {
                    val number = (it as NumericLiteralValue).number.toInt()
                    "$"+number.toString(16).padStart(2, '0')
                }
            DataType.ARRAY_B ->
                // byte array can never contain pointer-to types, so treat values as all integers
                array.map {
                    val number = (it as NumericLiteralValue).number.toInt()
                    val hexnum = number.absoluteValue.toString(16).padStart(2, '0')
                    if(number>=0)
                        "$$hexnum"
                    else
                        "-$$hexnum"
                }
            DataType.ARRAY_UW -> array.map {
                val number = (it as NumericLiteralValue).number.toInt()
                "$" + number.toString(16).padStart(4, '0')
            }
            DataType.ARRAY_W -> array.map {
                val number = (it as NumericLiteralValue).number.toInt()
                val hexnum = number.absoluteValue.toString(16).padStart(4, '0')
                if(number>=0)
                    "$$hexnum"
                else
                    "-$$hexnum"
            }
            else -> throw AssemblyError("invalid arraysize type ${decl.datatype}")
        }
    }

    internal fun getFloatAsmConst(number: Double): String {
        val asmName = globalFloatConsts[number]
        if(asmName!=null)
            return asmName

        val newName = "prog8_float_const_${globalFloatConsts.size}"
        globalFloatConsts[number] = newName
        return newName
    }

    internal fun asmSymbolName(identifier: IdentifierReference): String {
        if(identifier.nameInSource.size==2 && identifier.nameInSource[0]=="prog8_slabs")
            return identifier.nameInSource.joinToString(".")

        val tgt2 = identifier.targetStatement(program)
        if(tgt2==null && (identifier.nameInSource[0].startsWith("_prog8") || identifier.nameInSource[0].startsWith("prog8")))
            return identifier.nameInSource.joinToString(".")

        val target = identifier.targetStatement(program)!!
        val targetScope = target.definingSubroutine
        val identScope = identifier.definingSubroutine
        return if(targetScope !== identScope) {
            val scopedName = getScopedSymbolNameForTarget(identifier.nameInSource.last(), target)
            if(target is Label) {
                // make labels locally scoped in the asm. Is slightly problematic, see github issue #62
                val last = scopedName.removeLast()
                scopedName.add("_$last")
            }
            fixNameSymbols(scopedName.joinToString("."))
        } else {
            if(target is Label) {
                // make labels locally scoped in the asm. Is slightly problematic, see github issue #62
                val scopedName = identifier.nameInSource.toMutableList()
                val last = scopedName.removeLast()
                scopedName.add("_$last")
                fixNameSymbols(scopedName.joinToString("."))
            }
            else fixNameSymbols(identifier.nameInSource.joinToString("."))
        }
    }

    internal fun asmVariableName(identifier: IdentifierReference) =
        fixNameSymbols(identifier.nameInSource.joinToString("."))

    private fun getScopedSymbolNameForTarget(actualName: String, target: Statement): MutableList<String> {
        val scopedName = mutableListOf(actualName)
        var node: Node = target
        while (node !is Block) {
            node = node.parent
            if(node is INameScope) {
                scopedName.add(0, node.name)
            }
        }
        return scopedName
    }

    internal fun asmSymbolName(regs: RegisterOrPair): String =
        if (regs in Cx16VirtualRegisters)
            "cx16." + regs.toString().lowercase()
        else
            throw AssemblyError("no symbol name for register $regs")

    internal fun asmSymbolName(name: String) = fixNameSymbols(name)
    internal fun asmVariableName(name: String) = fixNameSymbols(name)
    internal fun asmSymbolName(name: Iterable<String>) = fixNameSymbols(name.joinToString("."))
    internal fun asmVariableName(name: Iterable<String>) = fixNameSymbols(name.joinToString("."))


    internal fun loadByteFromPointerIntoA(pointervar: IdentifierReference): String {
        // returns the source name of the zero page pointervar if it's already in the ZP,
        // otherwise returns "P8ZP_SCRATCH_W1" which is the intermediary
        when (val target = pointervar.targetStatement(program)) {
            is Label -> {
                val sourceName = asmSymbolName(pointervar)
                out("  lda  $sourceName")
                return sourceName
            }
            is VarDecl -> {
                val sourceName = asmVariableName(pointervar)
                val scopedName = target.makeScopedName(target.name)
                if (isTargetCpu(CpuType.CPU65c02)) {
                    return if (isZpVar(scopedName)) {
                        // pointervar is already in the zero page, no need to copy
                        out("  lda  ($sourceName)")
                        sourceName
                    } else {
                        out("""
                            lda  $sourceName
                            ldy  $sourceName+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            lda  (P8ZP_SCRATCH_W1)""")
                        "P8ZP_SCRATCH_W1"
                    }
                } else {
                    return if (isZpVar(scopedName)) {
                        // pointervar is already in the zero page, no need to copy
                        out("  ldy  #0 |  lda  ($sourceName),y")
                        sourceName
                    } else {
                        out("""
                            lda  $sourceName
                            ldy  $sourceName+1
                            sta  P8ZP_SCRATCH_W1
                            sty  P8ZP_SCRATCH_W1+1
                            ldy  #0
                            lda  (P8ZP_SCRATCH_W1),y""")
                        "P8ZP_SCRATCH_W1"
                    }
                }
            }
            else -> throw AssemblyError("invalid pointervar")
        }
    }

    private  fun fixNameSymbols(name: String) = name.replace("<", "prog8_").replace(">", "")     // take care of the autogenerated invalid (anon) label names

    internal fun saveRegisterLocal(register: CpuRegister, scope: Subroutine) {
        if (isTargetCpu(CpuType.CPU65c02)) {
            // just use the cpu's stack for all registers, shorter code
            when (register) {
                CpuRegister.A -> out("  pha")
                CpuRegister.X -> out("  phx")
                CpuRegister.Y -> out("  phy")
            }
        } else {
            when (register) {
                CpuRegister.A -> {
                    // just use the stack, only for A
                    out("  pha")
                }
                CpuRegister.X -> {
                    out("  stx  _prog8_regsaveX")
                    scope.asmGenInfo.usedRegsaveX = true
                }
                CpuRegister.Y -> {
                    out("  sty  _prog8_regsaveY")
                    scope.asmGenInfo.usedRegsaveY = true
                }
            }
        }
    }

    internal fun saveRegisterStack(register: CpuRegister, keepA: Boolean) {
        when (register) {
            CpuRegister.A -> out("  pha")
            CpuRegister.X -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  phx")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  txa |  pha  |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  txa |  pha")
                }
            }
            CpuRegister.Y -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  phy")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  tya |  pha  |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  tya |  pha")
                }
            }
        }
    }

    internal fun restoreRegisterLocal(register: CpuRegister) {
        if (isTargetCpu(CpuType.CPU65c02)) {
            when (register) {
                // this just used the stack, for all registers. Shorter code.
                CpuRegister.A -> out("  pla")
                CpuRegister.X -> out("  plx")
                CpuRegister.Y -> out("  ply")
            }

        } else {
            when (register) {
                CpuRegister.A -> out("  pla")   // this just used the stack but only for A
                CpuRegister.X -> out("  ldx  _prog8_regsaveX")
                CpuRegister.Y -> out("  ldy  _prog8_regsaveY")
            }
        }
    }

    internal fun restoreRegisterStack(register: CpuRegister, keepA: Boolean) {
        when (register) {
            CpuRegister.A -> {
                if(keepA)
                    throw AssemblyError("can't set keepA if A is restored")
                out("  pla")
            }
            CpuRegister.X -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  plx")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  pla |  tax |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  pla |  tax")
                }
            }
            CpuRegister.Y -> {
                if (isTargetCpu(CpuType.CPU65c02)) out("  ply")
                else {
                    if(keepA)
                        out("  sta  P8ZP_SCRATCH_REG |  pla |  tay |  lda  P8ZP_SCRATCH_REG")
                    else
                        out("  pla |  tay")
                }
            }
        }
    }

    internal fun translate(stmt: Statement) {
        outputSourceLine(stmt)
        when(stmt) {
            is ParameterVarDecl -> { /* subroutine parameter vardecls don't get any special treatment here */ }
            is VarDecl -> translate(stmt)
            is NopStatement -> {}
            is Directive -> translate(stmt)
            is Return -> translate(stmt)
            is Subroutine -> translateSubroutine(stmt)
            is InlineAssembly -> translate(stmt)
            is FunctionCallStatement -> {
                val functionName = stmt.target.nameInSource.last()
                val builtinFunc = BuiltinFunctions[functionName]
                if (builtinFunc != null) {
                    builtinFunctionsAsmGen.translateFunctioncallStatement(stmt, builtinFunc)
                } else {
                    functioncallAsmGen.translateFunctionCallStatement(stmt)
                }
            }
            is Assignment -> assignmentAsmGen.translate(stmt)
            is Jump -> translate(stmt)
            is PostIncrDecr -> postincrdecrAsmGen.translate(stmt)
            is Label -> translate(stmt)
            is BranchStatement -> translate(stmt)
            is IfStatement -> translate(stmt)
            is ForLoop -> forloopsAsmGen.translate(stmt)
            is Break -> {
                if(loopEndLabels.isEmpty())
                    throw AssemblyError("break statement out of context  ${stmt.position}")
                jmp(loopEndLabels.peek())
            }
            is WhileLoop -> translate(stmt)
            is RepeatLoop -> translate(stmt)
            is UntilLoop -> translate(stmt)
            is WhenStatement -> translate(stmt)
            is BuiltinFunctionStatementPlaceholder -> throw AssemblyError("builtin function should not have placeholder anymore?")
            is AnonymousScope -> translate(stmt)
            is Block -> throw AssemblyError("block should have been handled elsewhere")
            else -> throw AssemblyError("missing asm translation for $stmt")
        }
    }

    internal fun loadScaledArrayIndexIntoRegister(
        expr: ArrayIndexedExpression,
        elementDt: DataType,
        register: CpuRegister,
        addOneExtra: Boolean = false
    ) {
        val reg = register.toString().lowercase()
        val indexnum = expr.indexer.constIndex()
        if (indexnum != null) {
            val indexValue = indexnum * compTarget.memorySize(elementDt) + if (addOneExtra) 1 else 0
            out("  ld$reg  #$indexValue")
            return
        }

        val indexVar = expr.indexer.indexExpr as? IdentifierReference
            ?: throw AssemblyError("array indexer should have been replaced with a temp var @ ${expr.indexer.position}")

        val indexName = asmVariableName(indexVar)
        if (addOneExtra) {
            // add 1 to the result
            when (elementDt) {
                in ByteDatatypes -> {
                    out("  ldy  $indexName |  iny")
                    when (register) {
                        CpuRegister.A -> out(" tya")
                        CpuRegister.X -> out(" tyx")
                        CpuRegister.Y -> {
                        }
                    }
                }
                in WordDatatypes -> {
                    out("  lda  $indexName |  sec |  rol a")
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                DataType.FLOAT -> {
                    require(compTarget.memorySize(DataType.FLOAT) == 5)
                    out(
                        """
                                lda  $indexName
                                asl  a
                                asl  a
                                sec
                                adc  $indexName"""
                    )
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                else -> throw AssemblyError("weird dt")
            }
        } else {
            when (elementDt) {
                in ByteDatatypes -> out("  ld$reg  $indexName")
                in WordDatatypes -> {
                    out("  lda  $indexName |  asl a")
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                DataType.FLOAT -> {
                    require(compTarget.memorySize(DataType.FLOAT) == 5)
                    out(
                        """
                                lda  $indexName
                                asl  a
                                asl  a
                                clc
                                adc  $indexName"""
                    )
                    when (register) {
                        CpuRegister.A -> {
                        }
                        CpuRegister.X -> out(" tax")
                        CpuRegister.Y -> out(" tay")
                    }
                }
                else -> throw AssemblyError("weird dt")
            }
        }
    }

    internal fun translateExpression(expression: Expression) =
            expressionsAsmGen.translateExpression(expression)

    internal fun translateExpression(indexer: ArrayIndex) =
            expressionsAsmGen.translateExpression(indexer)

    internal fun translateBuiltinFunctionCallExpression(functionCall: FunctionCall, signature: FSignature, resultToStack: Boolean, resultRegister: RegisterOrPair?) =
            builtinFunctionsAsmGen.translateFunctioncallExpression(functionCall, signature, resultToStack, resultRegister)

    internal fun translateFunctionCall(functionCall: FunctionCall) =
            functioncallAsmGen.translateFunctionCall(functionCall)

    internal fun saveXbeforeCall(functionCall: IFunctionCall)  =
            functioncallAsmGen.saveXbeforeCall(functionCall)

    internal fun restoreXafterCall(functionCall: IFunctionCall) =
            functioncallAsmGen.restoreXafterCall(functionCall)

    internal fun translateNormalAssignment(assign: AsmAssignment) =
            assignmentAsmGen.translateNormalAssignment(assign)

    internal fun assignExpressionToRegister(expr: Expression, register: RegisterOrPair) =
            assignmentAsmGen.assignExpressionToRegister(expr, register)

    internal fun assignExpressionToVariable(expr: Expression, asmVarName: String, dt: DataType, scope: Subroutine?) =
            assignmentAsmGen.assignExpressionToVariable(expr, asmVarName, dt, scope)

    internal fun assignVariableToRegister(asmVarName: String, register: RegisterOrPair) =
            assignmentAsmGen.assignVariableToRegister(asmVarName, register)


    private fun translateSubroutine(sub: Subroutine) {
        var onlyVariables = false

        if(sub.inline) {
            if(options.optimize) {
                if(sub.isAsmSubroutine || callGraph.unused(sub))
                    return

                // from an inlined subroutine only the local variables are generated,
                // all other code statements are omitted in the subroutine itself
                // (they've been inlined at the call site, remember?)
                onlyVariables = true
            }
            else if(sub.amountOfRtsInAsm()==0) {
                // make sure the NOT INLINED subroutine actually does a rts at the end
                sub.statements.add(Return(null, Position.DUMMY))
            }
        }

        out("")
        outputSourceLine(sub)


        if(sub.isAsmSubroutine) {
            if(sub.asmAddress!=null)
                return  // already done at the memvars section

            // asmsub with most likely just an inline asm in it
            out("${sub.name}\t.proc")
            sub.statements.forEach { translate(it) }
            out("  .pend\n")
        } else {
            // regular subroutine
            out("${sub.name}\t.proc")
            zeropagevars2asm(sub.statements)
            memdefs2asm(sub.statements)

            // the main.start subroutine is the program's entrypoint and should perform some initialization logic
            if(sub.name=="start" && sub.definingBlock.name=="main") {
                out("; program startup initialization")
                out("  cld")
                program.allBlocks.forEach {
                    if(it.statements.filterIsInstance<VarDecl>().any { vd->vd.value!=null && vd.type==VarDeclType.VAR && vd.datatype in NumericDatatypes})
                        out("  jsr  ${it.name}.prog8_init_vars")
                }
                out("""
                    tsx
                    stx  prog8_lib.orig_stackpointer    ; required for sys.exit()                    
                    ldx  #255       ; init estack ptr
                    clv
                    clc""")
            }

            if(!onlyVariables) {
                out("; statements")
                sub.statements.forEach { translate(it) }
            }

            for(removal in removals.toList()) {
                if(removal.second==sub) {
                    removal.second.remove(removal.first)
                    removals.remove(removal)
                }
            }

            out("; variables")
            for((dt, name, addr) in sub.asmGenInfo.extraVars) {
                if(addr!=null)
                    out("$name = $addr")
                else when(dt) {
                    DataType.UBYTE -> out("$name    .byte  0")
                    DataType.UWORD -> out("$name    .word  0")
                    else -> throw AssemblyError("weird dt")
                }
            }
            if(sub.asmGenInfo.usedRegsaveA)
                out("_prog8_regsaveA     .byte  0")
            if(sub.asmGenInfo.usedRegsaveX)
                out("_prog8_regsaveX     .byte  0")
            if(sub.asmGenInfo.usedRegsaveY)
                out("_prog8_regsaveY     .byte  0")
            if(sub.asmGenInfo.usedFloatEvalResultVar1)
                out("$subroutineFloatEvalResultVar1    .byte  0,0,0,0,0")
            if(sub.asmGenInfo.usedFloatEvalResultVar2)
                out("$subroutineFloatEvalResultVar2    .byte  0,0,0,0,0")
            vardecls2asm(sub.statements)
            out("  .pend\n")
        }
    }

    private fun branchInstruction(condition: BranchCondition, complement: Boolean) =
            if(complement) {
                when (condition) {
                    BranchCondition.CS -> "bcc"
                    BranchCondition.CC -> "bcs"
                    BranchCondition.EQ, BranchCondition.Z -> "bne"
                    BranchCondition.NE, BranchCondition.NZ -> "beq"
                    BranchCondition.VS -> "bvc"
                    BranchCondition.VC -> "bvs"
                    BranchCondition.MI, BranchCondition.NEG -> "bpl"
                    BranchCondition.PL, BranchCondition.POS -> "bmi"
                }
            } else {
                when (condition) {
                    BranchCondition.CS -> "bcs"
                    BranchCondition.CC -> "bcc"
                    BranchCondition.EQ, BranchCondition.Z -> "beq"
                    BranchCondition.NE, BranchCondition.NZ -> "bne"
                    BranchCondition.VS -> "bvs"
                    BranchCondition.VC -> "bvc"
                    BranchCondition.MI, BranchCondition.NEG -> "bmi"
                    BranchCondition.PL, BranchCondition.POS -> "bpl"
                }
            }

    private fun translate(stmt: IfStatement) {
        checkBooleanExpression(stmt.condition)  // we require the condition to be of the form  'x <comparison> <value>'
        val booleanCondition = stmt.condition as BinaryExpression

        // DISABLED FOR NOW:
//        if(!booleanCondition.left.isSimple || !booleanCondition.right.isSimple)
//            throw AssemblyError("both operands for if comparison expression should have been simplified")

        if (stmt.elsepart.isEmpty()) {
            val endLabel = makeLabel("if_end")
            expressionsAsmGen.translateComparisonExpressionWithJumpIfFalse(booleanCondition, endLabel)
            translate(stmt.truepart)
            out(endLabel)
        }
        else {
            // both true and else parts
            val elseLabel = makeLabel("if_else")
            val endLabel = makeLabel("if_end")
            expressionsAsmGen.translateComparisonExpressionWithJumpIfFalse(booleanCondition, elseLabel)
            translate(stmt.truepart)
            jmp(endLabel)
            out(elseLabel)
            translate(stmt.elsepart)
            out(endLabel)
        }
    }

    private fun checkBooleanExpression(condition: Expression) {
        if(condition !is BinaryExpression || condition.operator !in comparisonOperators)
            throw AssemblyError("expected boolean expression $condition")
    }

    private fun translate(stmt: RepeatLoop) {
        val repeatLabel = makeLabel("repeat")
        val endLabel = makeLabel("repeatend")
        loopEndLabels.push(endLabel)

        when (stmt.iterations) {
            null -> {
                // endless loop
                out(repeatLabel)
                translate(stmt.body)
                jmp(repeatLabel)
                out(endLabel)
            }
            is NumericLiteralValue -> {
                val iterations = (stmt.iterations as NumericLiteralValue).number.toInt()
                if(iterations<0 || iterations > 65535)
                    throw AssemblyError("invalid number of iterations")
                when {
                    iterations == 0 -> {}
                    iterations <= 256 -> {
                        out("  lda  #${iterations and 255}")
                        repeatByteCountInA(iterations, repeatLabel, endLabel, stmt)
                    }
                    else -> {
                        out("  lda  #<${iterations} |  ldy  #>${iterations}")
                        repeatWordCountInAY(iterations, repeatLabel, endLabel, stmt)
                    }
                }
            }
            is IdentifierReference -> {
                val vardecl = (stmt.iterations as IdentifierReference).targetStatement(program) as VarDecl
                val name = asmVariableName(stmt.iterations as IdentifierReference)
                when(vardecl.datatype) {
                    DataType.UBYTE, DataType.BYTE -> {
                        assignVariableToRegister(name, RegisterOrPair.A)
                        repeatByteCountInA(null, repeatLabel, endLabel, stmt)
                    }
                    DataType.UWORD, DataType.WORD -> {
                        assignVariableToRegister(name, RegisterOrPair.AY)
                        repeatWordCountInAY(null, repeatLabel, endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop variable datatype $vardecl")
                }
            }
            else -> {
                val dt = stmt.iterations!!.inferType(program)
                if(!dt.isKnown)
                    throw AssemblyError("unknown dt")
                when (dt.getOr(DataType.UNDEFINED)) {
                    in ByteDatatypes -> {
                        assignExpressionToRegister(stmt.iterations!!, RegisterOrPair.A)
                        repeatByteCountInA(null, repeatLabel, endLabel, stmt)
                    }
                    in WordDatatypes -> {
                        assignExpressionToRegister(stmt.iterations!!, RegisterOrPair.AY)
                        repeatWordCountInAY(null, repeatLabel, endLabel, stmt)
                    }
                    else -> throw AssemblyError("invalid loop expression datatype $dt")
                }
            }
        }

        loopEndLabels.pop()
    }

    private fun repeatWordCountInAY(constIterations: Int?, repeatLabel: String, endLabel: String, stmt: RepeatLoop) {
        // note: A/Y must have been loaded with the number of iterations!
        if(constIterations==0)
            return
        // no need to explicitly test for 0 iterations as this is done in the countdown logic below

        val counterVar: String = createRepeatCounterVar(DataType.UWORD, constIterations, stmt)
        out("""
                sta  $counterVar
                sty  $counterVar+1
$repeatLabel    lda  $counterVar
                bne  +
                lda  $counterVar+1
                beq  $endLabel
                lda  $counterVar
                bne  +
                dec  $counterVar+1
+               dec  $counterVar
""")
        translate(stmt.body)
        jmp(repeatLabel)
        out(endLabel)
    }

    private fun repeatByteCountInA(constIterations: Int?, repeatLabel: String, endLabel: String, stmt: RepeatLoop) {
        // note: A must be loaded with the number of iterations!
        if(constIterations==0)
            return

        if(constIterations==null)
            out("  beq  $endLabel   ; skip loop if zero iters")
        val counterVar = createRepeatCounterVar(DataType.UBYTE, constIterations, stmt)
        out("  sta  $counterVar")
        out(repeatLabel)
        translate(stmt.body)
        out("  dec  $counterVar |  bne  $repeatLabel")
        if(constIterations==null)
            out(endLabel)
    }

    private fun createRepeatCounterVar(dt: DataType, constIterations: Int?, stmt: RepeatLoop): String {
        val asmInfo = stmt.definingSubroutine!!.asmGenInfo
        var parent = stmt.parent
        while(parent !is ParentSentinel) {
            if(parent is RepeatLoop)
                break
            parent = parent.parent
        }
        val isNested = parent is RepeatLoop

        if(!isNested) {
            // we can re-use a counter var from the subroutine if it already has one for that datatype
            val existingVar = asmInfo.extraVars.firstOrNull { it.first==dt }
            if(existingVar!=null)
                return existingVar.second
        }

        val counterVar = makeLabel("repeatcounter")
        when(dt) {
            DataType.UBYTE -> {
                if(constIterations!=null && constIterations>=16 && zeropage.hasByteAvailable()) {
                    // allocate count var on ZP
                    val zpAddr = zeropage.allocate(counterVar, DataType.UBYTE, stmt.position, errors)
                    asmInfo.extraVars.add(Triple(DataType.UBYTE, counterVar, zpAddr))
                } else {
                    asmInfo.extraVars.add(Triple(DataType.UBYTE, counterVar, null))
                }
            }
            DataType.UWORD -> {
                if(constIterations!=null && constIterations>=16 && zeropage.hasWordAvailable()) {
                    // allocate count var on ZP
                    val zpAddr = zeropage.allocate(counterVar, DataType.UWORD, stmt.position, errors)
                    asmInfo.extraVars.add(Triple(DataType.UWORD, counterVar, zpAddr))
                } else {
                    asmInfo.extraVars.add(Triple(DataType.UWORD, counterVar, null))
                }
            }
            else -> throw AssemblyError("invalidt dt")
        }
        return counterVar
    }

    private fun translate(stmt: WhileLoop) {
        checkBooleanExpression(stmt.condition)  // we require the condition to be of the form  'x <comparison> <value>'
        val booleanCondition = stmt.condition as BinaryExpression
        val whileLabel = makeLabel("while")
        val endLabel = makeLabel("whileend")
        loopEndLabels.push(endLabel)
        out(whileLabel)
        expressionsAsmGen.translateComparisonExpressionWithJumpIfFalse(booleanCondition, endLabel)
        translate(stmt.body)
        jmp(whileLabel)
        out(endLabel)
        loopEndLabels.pop()
    }

    private fun translate(stmt: UntilLoop) {
        checkBooleanExpression(stmt.condition)  // we require the condition to be of the form  'x <comparison> <value>'
        val booleanCondition = stmt.condition as BinaryExpression
        val repeatLabel = makeLabel("repeat")
        val endLabel = makeLabel("repeatend")
        loopEndLabels.push(endLabel)
        out(repeatLabel)
        translate(stmt.body)
        expressionsAsmGen.translateComparisonExpressionWithJumpIfFalse(booleanCondition, repeatLabel)
        out(endLabel)
        loopEndLabels.pop()
    }

    private fun translate(stmt: WhenStatement) {
        val endLabel = makeLabel("choice_end")
        val choiceBlocks = mutableListOf<Pair<String, AnonymousScope>>()
        val conditionDt = stmt.condition.inferType(program)
        if(!conditionDt.isKnown)
            throw AssemblyError("unknown condition dt")
        if(conditionDt.getOr(DataType.BYTE) in ByteDatatypes)
            assignExpressionToRegister(stmt.condition, RegisterOrPair.A)
        else
            assignExpressionToRegister(stmt.condition, RegisterOrPair.AY)

        for(choice in stmt.choices) {
            val choiceLabel = makeLabel("choice")
            if(choice.values==null) {
                // the else choice
                translate(choice.statements)
                jmp(endLabel)
            } else {
                choiceBlocks.add(choiceLabel to choice.statements)
                for (cv in choice.values!!) {
                    val value = (cv as NumericLiteralValue).number.toInt()
                    if(conditionDt.getOr(DataType.BYTE) in ByteDatatypes) {
                        out("  cmp  #${value.toHex()} |  beq  $choiceLabel")
                    } else {
                        out("""
                            cmp  #<${value.toHex()}
                            bne  +
                            cpy  #>${value.toHex()}
                            beq  $choiceLabel
+
                            """)
                    }
                }
            }
        }
        jmp(endLabel)
        for(choiceBlock in choiceBlocks) {
            out(choiceBlock.first)
            translate(choiceBlock.second)
            jmp(endLabel)
        }
        out(endLabel)
    }

    private fun translate(stmt: Label) {
        // underscore prefix to make sure it's a local label. Is slightly problematic, see github issue #62
        out("_${stmt.name}")
    }

    private fun translate(scope: AnonymousScope) {
        // note: the variables defined in an anonymous scope have been moved to their defining subroutine's scope
        scope.statements.forEach{ translate(it) }
    }

    private fun translate(stmt: BranchStatement) {
        if(stmt.truepart.isEmpty() && stmt.elsepart.isNotEmpty())
            throw AssemblyError("only else part contains code, shoud have been switched already")

        val jump = stmt.truepart.statements.first() as? Jump
        if(jump!=null) {
            // branch with only a jump (goto)
            val instruction = branchInstruction(stmt.condition, false)
            out("  $instruction  ${getJumpTarget(jump)}")
            translate(stmt.elsepart)
        } else {
            val truePartIsJustBreak = stmt.truepart.statements.firstOrNull() is Break
            val elsePartIsJustBreak = stmt.elsepart.statements.firstOrNull() is Break
            if(stmt.elsepart.isEmpty()) {
                if(truePartIsJustBreak) {
                    // branch with just a break (jump out of loop)
                    val instruction = branchInstruction(stmt.condition, false)
                    val loopEndLabel = loopEndLabels.peek()
                    out("  $instruction  $loopEndLabel")
                } else {
                    val instruction = branchInstruction(stmt.condition, true)
                    val elseLabel = makeLabel("branch_else")
                    out("  $instruction  $elseLabel")
                    translate(stmt.truepart)
                    out(elseLabel)
                }
            }
            else if(truePartIsJustBreak) {
                // branch with just a break (jump out of loop)
                val instruction = branchInstruction(stmt.condition, false)
                val loopEndLabel = loopEndLabels.peek()
                out("  $instruction  $loopEndLabel")
                translate(stmt.elsepart)
            } else if(elsePartIsJustBreak) {
                // branch with just a break (jump out of loop) but true/false inverted
                val instruction = branchInstruction(stmt.condition, true)
                val loopEndLabel = loopEndLabels.peek()
                out("  $instruction  $loopEndLabel")
                translate(stmt.truepart)
            } else {
                val instruction = branchInstruction(stmt.condition, true)
                val elseLabel = makeLabel("branch_else")
                val endLabel = makeLabel("branch_end")
                out("  $instruction  $elseLabel")
                translate(stmt.truepart)
                jmp(endLabel)
                out(elseLabel)
                translate(stmt.elsepart)
                out(endLabel)
            }
        }
    }

    private fun translate(stmt: VarDecl) {
        if(stmt.value!=null && stmt.type==VarDeclType.VAR && stmt.datatype in NumericDatatypes) {
            // generate an assignment statement to (re)initialize the variable's value.
            // if the vardecl is not in a subroutine however, we have to initialize it globally.
            if(stmt.definingSubroutine ==null) {
                val block = stmt.definingBlock
                var inits = blockLevelVarInits[block]
                if(inits==null) {
                    inits = mutableSetOf()
                    blockLevelVarInits[block] = inits
                }
                inits.add(stmt)
            } else {
                val next = (stmt.parent as IStatementContainer).nextSibling(stmt)
                if (next !is ForLoop || next.loopVar.nameInSource.single() != stmt.name) {
                    assignInitialValueToVar(stmt, listOf(stmt.name))
                }
            }
        }
    }

    /**
     * TODO: %asminclude and %asmbinary should be done earlier than code gen (-> put content into AST) ... (describe why?)
     */
    private fun translate(stmt: Directive) {
        when(stmt.directive) {
            "%asminclude" -> {
                // TODO: handle %asminclude with SourceCode
                val includedName = stmt.args[0].str!!
                if(stmt.definingModule.source is SourceCode.Generated)
                    TODO("%asminclude inside non-library, non-filesystem module")
                loadAsmIncludeFile(includedName, stmt.definingModule.source).fold(
                    success = { assemblyLines.add(it.trimEnd().trimStart('\n')) },
                    failure = { errors.err(it.toString(), stmt.position) }
                )
            }
            "%asmbinary" -> {
                val includedName = stmt.args[0].str!!
                val offset = if(stmt.args.size>1) ", ${stmt.args[1].int}" else ""
                val length = if(stmt.args.size>2) ", ${stmt.args[2].int}" else ""
                if(stmt.definingModule.source is SourceCode.Generated)
                    TODO("%asmbinary inside non-library, non-filesystem module")
                val sourcePath = Path(stmt.definingModule.source.origin)
                val includedPath = sourcePath.resolveSibling(includedName)
                val pathForAssembler = outputDir // #54: 64tass needs the path *relative to the .asm file*
                    .toAbsolutePath()
                    .relativize(includedPath.toAbsolutePath())
                    .normalize() // avoid assembler warnings (-Wportable; only some, not all)
                    .toString().replace('\\', '/')
                out("  .binary \"$pathForAssembler\" $offset $length")
            }
            "%breakpoint" -> {
                val label = "_prog8_breakpoint_${breakpointLabels.size+1}"
                breakpointLabels.add(label)
                out("""
                    nop
$label              nop""")
            }
        }
    }

    private fun translate(jump: Jump) = jmp(getJumpTarget(jump))

    private fun getJumpTarget(jump: Jump): String {
        val ident = jump.identifier
        val label = jump.generatedLabel
        val addr = jump.address
        return when {
            ident!=null -> asmSymbolName(ident)
            label!=null -> label
            addr!=null -> addr.toHex()
            else -> "????"
        }
    }

    internal fun translate(ret: Return, withRts: Boolean=true) {
        ret.value?.let { returnvalue ->
            val sub = ret.definingSubroutine!!
            val returnType = sub.returntypes.single()
            val returnReg = sub.asmReturnvaluesRegisters.single()
            if(returnReg.registerOrPair==null)
                throw AssemblyError("normal subroutines can't return value in status register directly")

            when (returnType) {
                in NumericDatatypes -> {
                    assignExpressionToRegister(returnvalue, returnReg.registerOrPair!!)
                }
                else -> {
                    // all else take its address and assign that also to AY register pair
                    val addrofValue = AddressOf(returnvalue as IdentifierReference, returnvalue.position)
                    assignmentAsmGen.assignExpressionToRegister(addrofValue, returnReg.registerOrPair!!)
                }
            }
        }

        if(withRts)
            out("  rts")
    }

    private fun translate(asm: InlineAssembly) {
        val assembly = asm.assembly.trimEnd().trimStart('\n')
        assemblyLines.add(assembly)
    }

    internal fun signExtendAYlsb(valueDt: DataType) {
        // sign extend signed byte in A to full word in AY
        when(valueDt) {
            DataType.UBYTE -> out("  ldy  #0")
            DataType.BYTE -> out("  jsr  prog8_lib.sign_extend_AY_byte")
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun signExtendStackLsb(valueDt: DataType) {
        // sign extend signed byte on stack to signed word on stack
        when(valueDt) {
            DataType.UBYTE -> {
                if(isTargetCpu(CpuType.CPU65c02))
                    out("  stz  P8ESTACK_HI+1,x")
                else
                    out("  lda  #0 |  sta  P8ESTACK_HI+1,x")
            }
            DataType.BYTE -> out("  jsr  prog8_lib.sign_extend_stack_byte")
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun signExtendVariableLsb(asmvar: String, valueDt: DataType) {
        // sign extend signed byte in a var to a full word in that variable
        when(valueDt) {
            DataType.UBYTE -> {
                if(isTargetCpu(CpuType.CPU65c02))
                    out("  stz  $asmvar+1")
                else
                    out("  lda  #0 |  sta  $asmvar+1")
            }
            DataType.BYTE -> {
                out("""
                    lda  $asmvar
                    ora  #$7f
                    bmi  +
                    lda  #0
+                   sta  $asmvar+1""")
            }
            else -> throw AssemblyError("need byte type")
        }
    }

    internal fun isZpVar(scopedName: String): Boolean = scopedName in allocatedZeropageVariables

    internal fun isZpVar(variable: IdentifierReference): Boolean {
        val vardecl = variable.targetVarDecl(program)!!
        return vardecl.makeScopedName(vardecl.name) in allocatedZeropageVariables
    }

    internal fun jmp(asmLabel: String) {
        if(isTargetCpu(CpuType.CPU65c02))
            out("  bra  $asmLabel")     // note: 64tass will convert this automatically to a jmp if the relative distance is too large
        else
            out("  jmp  $asmLabel")
    }

    internal fun pointerViaIndexRegisterPossible(pointerOffsetExpr: Expression): Pair<Expression, Expression>? {
        if(pointerOffsetExpr is BinaryExpression && pointerOffsetExpr.operator=="+") {
            val leftDt = pointerOffsetExpr.left.inferType(program)
            val rightDt = pointerOffsetExpr.left.inferType(program)
            if(leftDt istype DataType.UWORD && rightDt istype DataType.UBYTE)
                return Pair(pointerOffsetExpr.left, pointerOffsetExpr.right)
            if(leftDt istype DataType.UBYTE && rightDt istype DataType.UWORD)
                return Pair(pointerOffsetExpr.right, pointerOffsetExpr.left)
            if(leftDt istype DataType.UWORD && rightDt istype DataType.UWORD) {
                // could be that the index was a constant numeric byte but converted to word, check that
                val constIdx = pointerOffsetExpr.right.constValue(program)
                if(constIdx!=null && constIdx.number.toInt()>=0 && constIdx.number.toInt()<=255) {
                    return Pair(pointerOffsetExpr.left, NumericLiteralValue(DataType.UBYTE, constIdx.number, constIdx.position))
                }
                // could be that the index was typecasted into uword, check that
                val rightTc = pointerOffsetExpr.right as? TypecastExpression
                if(rightTc!=null && rightTc.expression.inferType(program) istype DataType.UBYTE)
                    return Pair(pointerOffsetExpr.left, rightTc.expression)
                val leftTc = pointerOffsetExpr.left as? TypecastExpression
                if(leftTc!=null && leftTc.expression.inferType(program) istype DataType.UBYTE)
                    return Pair(pointerOffsetExpr.right, leftTc.expression)
            }

        }
        return null
    }

    internal fun tryOptimizedPointerAccessWithA(expr: BinaryExpression, write: Boolean): Boolean {
        // optimize pointer,indexregister if possible

        fun evalBytevalueWillClobberA(expr: Expression): Boolean {
            val dt = expr.inferType(program)
            if(dt isnot DataType.UBYTE && dt isnot DataType.BYTE)
                return true
            return when(expr) {
                is IdentifierReference -> false
                is NumericLiteralValue -> false
                is DirectMemoryRead -> expr.addressExpression !is IdentifierReference && expr.addressExpression !is NumericLiteralValue
                is TypecastExpression -> evalBytevalueWillClobberA(expr.expression)
                else -> true
            }
        }


        if(expr.operator=="+") {
            val ptrAndIndex = pointerViaIndexRegisterPossible(expr)
            if(ptrAndIndex!=null) {
                val pointervar = ptrAndIndex.first as? IdentifierReference
                when(pointervar?.targetStatement(program)) {
                    is Label -> {
                        assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                        out("  lda  ${asmSymbolName(pointervar)},y")
                        return true
                    }
                    is VarDecl, null -> {
                        if(write) {
                            if(pointervar!=null && isZpVar(pointervar)) {
                                val saveA = evalBytevalueWillClobberA(ptrAndIndex.second)
                                if(saveA)
                                    out("  pha")
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                if(saveA)
                                    out("  pla")
                                out("  sta  (${asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                val saveA = evalBytevalueWillClobberA(ptrAndIndex.first) || evalBytevalueWillClobberA(ptrAndIndex.second)
                                if(saveA)
                                    out("  pha")
                                assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                if(saveA)
                                    out("  pla")
                                out("  sta  (P8ZP_SCRATCH_W2),y")
                            }
                        } else {
                            if(pointervar!=null && isZpVar(pointervar)) {
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                out("  lda  (${asmSymbolName(pointervar)}),y")
                            } else {
                                // copy the pointer var to zp first
                                assignExpressionToVariable(ptrAndIndex.first, "P8ZP_SCRATCH_W2", DataType.UWORD, null)
                                assignExpressionToRegister(ptrAndIndex.second, RegisterOrPair.Y)
                                out("  lda  (P8ZP_SCRATCH_W2),y")
                            }
                        }
                        return true
                    }
                    else -> throw AssemblyError("invalid pointervar")
                }
            }
        }
        return false
    }
}
