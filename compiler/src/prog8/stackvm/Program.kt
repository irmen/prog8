package prog8.stackvm

import prog8.ast.DataType
import prog8.compiler.HeapValues
import prog8.compiler.unescape
import java.io.File
import java.io.PrintStream
import java.util.*
import java.util.regex.Pattern

class Program (val name: String,
               prog: MutableList<Instruction>,
               val labels: Map<String, Instruction>,
               val variables: Map<String, Map<String, Value>>,
               val memory: Map<Int, List<Value>>,
               val heap: HeapValues)
{
    companion object {
        fun load(filename: String): Program {
            val lines = File(filename).readLines().withIndex().iterator()
            val memory = mutableMapOf<Int, List<Value>>()
            val vars = mutableMapOf<String, MutableMap<String, Value>>()
            var instructions = mutableListOf<Instruction>()
            var labels = mapOf<String, Instruction>()
            val heap = HeapValues()
            while(lines.hasNext()) {
                val (lineNr, line) = lines.next()
                if(line.startsWith(';') || line.isEmpty())
                    continue
                else if(line=="%memory")
                    loadMemory(lines, memory)
                else if(line=="%heap")
                    loadHeap(lines, heap)
                else if(line=="%variables")
                    loadVars(lines, vars)
                else if(line=="%instructions") {
                    val (insResult, labelResult) = loadInstructions(lines, heap)
                    instructions = insResult
                    labels = labelResult
                }
                else throw VmExecutionException("syntax error at line ${lineNr + 1}")
            }
            return Program(filename, instructions, labels, vars, memory, heap)
        }

        private fun loadHeap(lines: Iterator<IndexedValue<String>>, heap: HeapValues) {
            val splitpattern = Pattern.compile("\\s+")
            val heapvalues = mutableListOf<Triple<Int, DataType, String>>()
            while(true) {
                val (lineNr, line) = lines.next()
                if (line == "%end_heap")
                    break
                val parts = line.split(splitpattern, limit=3)
                val value = Triple(parts[0].toInt(), DataType.valueOf(parts[1].toUpperCase()), parts[2])
                heapvalues.add(value)
            }
            heapvalues.sortedBy { it.first }.forEach {
                when(it.second) {
                    DataType.STR,
                    DataType.STR_P,
                    DataType.STR_S,
                    DataType.STR_PS -> heap.add(it.second, it.third.substring(1, it.third.length-1).unescape())
                    DataType.ARRAY,
                    DataType.ARRAY_W,
                    DataType.MATRIX -> {
                        val numbers = it.third.substring(1, it.third.length-1).split(',')
                        val intarray = numbers.map{number->number.trim().toInt()}.toIntArray()
                        heap.add(it.second, intarray)
                    }
                    else -> throw VmExecutionException("invalid heap value type $it.second")
                }
            }
        }

        private fun loadInstructions(lines: Iterator<IndexedValue<String>>, heap: HeapValues): Pair<MutableList<Instruction>, Map<String, Instruction>> {
            val instructions = mutableListOf<Instruction>()
            val labels = mutableMapOf<String, Instruction>()
            val splitpattern = Pattern.compile("\\s+")
            val nextInstructionLabels = Stack<String>()     // more than one label can occur on the same line

            while(true) {
                val (lineNr, line) = lines.next()
                if(line=="%end_instructions")
                    return Pair(instructions, labels)
                if(!line.startsWith(' ') && line.endsWith(':')) {
                    nextInstructionLabels.push(line.substring(0, line.length-1))
                } else if(line.startsWith(' ')) {
                    val parts = line.trimStart().split(splitpattern, limit = 2)
                    val opcodeStr = parts[0].toUpperCase()
                    val opcode= Opcode.valueOf(if(opcodeStr.startsWith('_')) opcodeStr.substring(1) else opcodeStr)
                    val args = if(parts.size==2) parts[1] else null
                    val instruction = when(opcode) {
                        Opcode.LINE -> Instruction(opcode, null, callLabel = args)
                        Opcode.JUMP, Opcode.CALL, Opcode.BNEG, Opcode.BPOS,
                        Opcode.BZ, Opcode.BNZ, Opcode.BCS, Opcode.BCC -> {
                            if(args!!.startsWith('$')) {
                                Instruction(opcode, Value(DataType.WORD, args.substring(1).toInt(16)))
                            } else {
                                Instruction(opcode, callLabel = args)
                            }
                        }
                        Opcode.INC_VAR, Opcode.DEC_VAR,
                        Opcode.SHR_VAR, Opcode.SHL_VAR, Opcode.ROL_VAR, Opcode.ROR_VAR,
                        Opcode.ROL2_VAR, Opcode.ROR2_VAR, Opcode.POP_VAR, Opcode.PUSH_VAR,
                        Opcode.PUSH_INDEXED_VAR -> {
                            val withoutQuotes =
                                    if(args!!.startsWith('"') && args.endsWith('"'))
                                        args.substring(1, args.length-1) else args

                            Instruction(opcode, callLabel = withoutQuotes)
                        }
                        Opcode.SYSCALL -> {
                            val call = Syscall.valueOf(args!!)
                            Instruction(opcode, Value(DataType.BYTE, call.callNr))
                        }
                        else -> {
                            Instruction(opcode, getArgValue(args, heap))
                        }
                    }
                    instructions.add(instruction)
                    while(nextInstructionLabels.isNotEmpty()) {
                        val label = nextInstructionLabels.pop()
                        labels[label] = instruction
                    }
                } else throw VmExecutionException("syntax error at line ${lineNr + 1}")
            }
        }

        private fun getArgValue(args: String?, heap: HeapValues): Value? {
            if(args==null)
                return null
            if(args[0]=='"' && args[args.length-1]=='"') {
                throw VmExecutionException("encountered a string arg value, but all strings should already have been moved into the heap")
            }
            val (type, valueStr) = args.split(':')
            return when(type) {
                "b" -> Value(DataType.BYTE, valueStr.toShort(16))
                "w" -> Value(DataType.WORD, valueStr.toInt(16))
                "f" -> Value(DataType.FLOAT, valueStr.toDouble())
                "heap" -> {
                    val heapId = valueStr.toInt()
                    Value(heap.get(heapId).type, heapId)
                }
                else -> throw VmExecutionException("invalid datatype $type")
            }
        }

        private fun loadVars(lines: Iterator<IndexedValue<String>>,
                             vars: MutableMap<String, MutableMap<String, Value>>): Map<String, Map<String, Value>> {
            val splitpattern = Pattern.compile("\\s+")
            while(true) {
                val (lineNr, line) = lines.next()
                if(line=="%end_variables")
                    return vars
                val (name, typeStr, valueStr) = line.split(splitpattern, limit = 3)
                if(valueStr[0] !='"' && ':' !in valueStr)
                    throw VmExecutionException("missing value type character")
                val type = DataType.valueOf(typeStr.toUpperCase())
                val value = when(type) {
                    DataType.BYTE -> Value(DataType.BYTE, valueStr.substring(2).toShort(16))
                    DataType.WORD -> Value(DataType.WORD, valueStr.substring(2).toInt(16))
                    DataType.FLOAT -> Value(DataType.FLOAT, valueStr.substring(2).toDouble())
                    DataType.STR, DataType.STR_P, DataType.STR_S, DataType.STR_PS -> {
                        if(valueStr.startsWith('"') && valueStr.endsWith('"'))
                            throw VmExecutionException("encountered a var with a string value, but all string values should already have been moved into the heap")
                        else if(!valueStr.startsWith("heap:"))
                            throw VmExecutionException("invalid string value, should be a heap reference")
                        else {
                            val heapId = valueStr.substring(5).toInt()
                            Value(type, heapId)
                        }
                    }
                    DataType.ARRAY,
                    DataType.ARRAY_W,
                    DataType.MATRIX -> {
                        if(!valueStr.startsWith("heap:"))
                            throw VmExecutionException("invalid array/matrix value, should be a heap reference")
                        else {
                            val heapId = valueStr.substring(5).toInt()
                            Value(type, heapId)
                        }
                    }
                }
                val blockname = name.substringBefore('.')
                val blockvars = vars[blockname] ?: mutableMapOf()
                vars[blockname] = blockvars
                blockvars[name] = value
            }
        }

        private fun loadMemory(lines: Iterator<IndexedValue<String>>, memory: MutableMap<Int, List<Value>>): Map<Int, List<Value>> {
            while(true) {
                val (lineNr, line) = lines.next()
                if(line=="%end_memory")
                    return memory
                val address = line.substringBefore(' ').toInt(16)
                val rest = line.substringAfter(' ').trim()
                if(rest.startsWith('"')) {
                    TODO("memory init with char/string")
                } else {
                    val valueStrings = rest.split(' ')
                    val values = mutableListOf<Value>()
                    valueStrings.forEach {
                        when(it.length) {
                            2 -> values.add(Value(DataType.BYTE, it.toShort(16)))
                            4 -> values.add(Value(DataType.WORD, it.toInt(16)))
                            else -> throw VmExecutionException("invalid value at line $lineNr+1")
                        }
                    }
                    memory[address] = values
                }
            }
        }
    }

    val program: List<Instruction>

    init {
        prog.add(Instruction(Opcode.TERMINATE))
        prog.add(Instruction(Opcode.NOP))
        program = prog
        connect()
    }

    private fun connect() {
        val it1 = program.iterator()
        val it2 = program.iterator()
        it2.next()

        while(it1.hasNext() && it2.hasNext()) {
            val instr = it1.next()
            val nextInstr = it2.next()
            when(instr.opcode) {
                Opcode.TERMINATE -> instr.next = instr          // won't ever execute a next instruction
                Opcode.RETURN -> instr.next = instr             // kinda a special one, in actuality the return instruction is dynamic
                Opcode.JUMP -> {
                    if(instr.callLabel==null) {
                        throw VmExecutionException("stackVm doesn't support JUMP to memory address")
                    } else {
                        // jump to label
                        val target = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                        instr.next = target
                    }
                }
                Opcode.BCC, Opcode.BCS, Opcode.BZ, Opcode.BNZ, Opcode.BNEG, Opcode.BPOS -> {
                    if(instr.callLabel==null) {
                        throw VmExecutionException("stackVm doesn't support branch to memory address")
                    } else {
                        // branch to label
                        val jumpInstr = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                        instr.next = jumpInstr
                        instr.nextAlt = nextInstr
                    }
                }
                Opcode.CALL -> {
                    if(instr.callLabel==null) {
                        throw VmExecutionException("stackVm doesn't support CALL to memory address")
                    } else {
                        // call label
                        val jumpInstr = labels[instr.callLabel] ?: throw VmExecutionException("undefined label: ${instr.callLabel}")
                        instr.next = jumpInstr
                        instr.nextAlt = nextInstr  // instruction to return to
                    }
                }
                else -> instr.next = nextInstr
            }
        }
    }

    fun print(out: PrintStream, embeddedLabels: Boolean=true) {
        out.println("; stackVM program code for '$name'")
        out.println("%memory")
        if(memory.isNotEmpty()) {
            TODO("print out initial memory load")
        }
        out.println("%end_memory")
        out.println("%heap")
        heap.allStrings().forEach {
            out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  \"${it.value.str}\"")
        }
        heap.allArrays().forEach {
            out.println("${it.index}  ${it.value.type.toString().toLowerCase()}  ${it.value.array!!.toList()}")
        }
        out.println("%end_heap")
        out.println("%variables")
        // just flatten all block vars into one global list for now...
        for(variable in variables.flatMap { e->e.value.entries}) {
            val valuestr = variable.value.toString()
            out.println("${variable.key}  ${variable.value.type.toString().toLowerCase()}  $valuestr")
        }
        out.println("%end_variables")
        out.println("%instructions")
        val labels = this.labels.entries.associateBy({it.value}) {it.key}
        for(instr in this.program) {
            if(!embeddedLabels) {
                val label = labels[instr]
                if (label != null)
                    out.println("$label:")
            } else {
                out.println(instr)
            }
        }
        out.println("%end_instructions")
    }
}