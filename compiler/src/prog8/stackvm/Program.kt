package prog8.stackvm

import prog8.ast.*
import prog8.compiler.HeapValues
import prog8.compiler.IntegerOrAddressOf
import prog8.compiler.intermediate.*
import java.io.File
import java.util.*
import java.util.regex.Pattern

class Program (val name: String,
               val program: MutableList<Instruction>,
               val variables: Map<String, Value>,
               val memoryPointers: Map<String, Pair<Int, DataType>>,
               val labels: Map<String, Int>,
               val memory: Map<Int, List<Value>>,
               val heap: HeapValues)
{
    init {
        // add end of program marker and some sentinel instructions, to correctly connect all others
        program.add(LabelInstr("____program_end", false))
        program.add(Instruction(Opcode.TERMINATE))
        program.add(Instruction(Opcode.NOP))
    }

    companion object {
        fun load(filename: String): Program {
            val lines = File(filename).readLines().withIndex().iterator()
            val memory = mutableMapOf<Int, List<Value>>()
            val heap = HeapValues()
            val program = mutableListOf<Instruction>()
            val variables = mutableMapOf<String, Value>()
            val memoryPointers = mutableMapOf<String, Pair<Int, DataType>>()
            val labels = mutableMapOf<String, Int>()

            while(lines.hasNext()) {
                val (lineNr, line) = lines.next()
                if(line.startsWith(';') || line.isEmpty())
                    continue
                else if(line=="%memory")
                    loadMemory(lines, memory)
                else if(line=="%heap")
                    loadHeap(lines, heap)
                else if(line.startsWith("%block "))
                    loadBlock(lines, heap, program, variables, memoryPointers, labels)
                else throw VmExecutionException("syntax error at line ${lineNr + 1}")
            }
            return Program(filename, program, variables, memoryPointers, labels, memory, heap)
        }

        private fun loadBlock(lines: Iterator<IndexedValue<String>>,
                              heap: HeapValues,
                              program: MutableList<Instruction>,
                              variables: MutableMap<String, Value>,
                              memoryPointers: MutableMap<String, Pair<Int, DataType>>,
                              labels: MutableMap<String, Int>)
        {
            while(true) {
                val (_, line) = lines.next()
                if(line.isEmpty())
                    continue
                else if(line=="%end_block")
                    return
                else if(line=="%variables")
                    loadVars(lines, variables)
                else if(line=="%memorypointers")
                    loadMemoryPointers(lines, memoryPointers, heap)
                else if(line=="%instructions") {
                    val (blockInstructions, blockLabels) = loadInstructions(lines, heap)
                    val baseIndex = program.size
                    program.addAll(blockInstructions)
                    val labelsWithIndex = blockLabels.mapValues { baseIndex+blockInstructions.indexOf(it.value) }
                    labels.putAll(labelsWithIndex)
                }
            }
        }

        private fun loadHeap(lines: Iterator<IndexedValue<String>>, heap: HeapValues) {
            val splitpattern = Pattern.compile("\\s+")
            val heapvalues = mutableListOf<Triple<Int, DataType, String>>()
            while(true) {
                val (_, line) = lines.next()
                if (line == "%end_heap")
                    break
                val parts = line.split(splitpattern, limit=3)
                val value = Triple(parts[0].toInt(), DataType.valueOf(parts[1].toUpperCase()), parts[2])
                heapvalues.add(value)
            }
            heapvalues.sortedBy { it.first }.forEach {
                when(it.second) {
                    DataType.STR, DataType.STR_S -> heap.addString(it.second, unescape(it.third.substring(1, it.third.length-1), Position("<stackvmsource>", 0, 0, 0)))
                    DataType.ARRAY_UB, DataType.ARRAY_B,
                    DataType.ARRAY_UW, DataType.ARRAY_W -> {
                        val numbers = it.third.substring(1, it.third.length-1).split(',')
                        val intarray = numbers.map{number->
                            val num=number.trim()
                            if(num.startsWith("&")) {
                                // it's AddressOf
                                val scopedname = num.substring(1)
                                val iref = IdentifierReference(scopedname.split('.'), Position("<intermediate>", 0,0,0))
                                val addrOf = AddressOf(iref, Position("<intermediate>", 0,0,0))
                                addrOf.scopedname=scopedname
                                IntegerOrAddressOf(null, addrOf)
                            } else {
                                IntegerOrAddressOf(num.toInt(), null)
                            }
                        }.toTypedArray()
                        heap.addIntegerArray(it.second, intarray)
                    }
                    DataType.ARRAY_F -> {
                        val numbers = it.third.substring(1, it.third.length-1).split(',')
                        val doublearray = numbers.map{number->number.trim().toDouble()}.toDoubleArray()
                        heap.addDoublesArray(doublearray)
                    }
                    in NumericDatatypes -> throw VmExecutionException("invalid heap value type ${it.second}")
                    else -> throw VmExecutionException("weird datatype")
                }
            }
        }

        private fun loadInstructions(lines: Iterator<IndexedValue<String>>, heap: HeapValues): Pair<MutableList<Instruction>, Map<String, Instruction>> {
            val instructions = mutableListOf<Instruction>()
            val labels = mutableMapOf<String, Instruction>()
            val splitpattern = Pattern.compile("\\s+")
            val nextInstructionLabels = Stack<String>()     // more than one label can occur on the isSameAs line

            while(true) {
                val (lineNr, line) = lines.next()
                if(line.isEmpty())
                    continue
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
                        Opcode.BZ, Opcode.BNZ, Opcode.BCS, Opcode.BCC,
                        Opcode.JZ, Opcode.JNZ, Opcode.JZW, Opcode.JNZW -> {
                            if(args!!.startsWith('$')) {
                                Instruction(opcode, Value(DataType.UWORD, args.substring(1).toInt(16)))
                            } else {
                                Instruction(opcode, callLabel = args)
                            }
                        }
                        in opcodesWithVarArgument -> {
                            val withoutQuotes =
                                    if(args!!.startsWith('"') && args.endsWith('"'))
                                        args.substring(1, args.length-1) else args

                            Instruction(opcode, callLabel = withoutQuotes)
                        }
                        Opcode.SYSCALL -> {
                            if(args!! in syscallNames) {
                                val call = Syscall.valueOf(args)
                                Instruction(opcode, Value(DataType.UBYTE, call.callNr))
                            } else {
                                val args2 = args.replace('.', '_')
                                if(args2 in syscallNames) {
                                    val call = Syscall.valueOf(args2)
                                    Instruction(opcode, Value(DataType.UBYTE, call.callNr))
                                } else {
                                    // the syscall is not yet implemented. emit a stub.
                                    Instruction(Opcode.SYSCALL, Value(DataType.UBYTE, Syscall.SYSCALLSTUB.callNr), callLabel = args2)
                                }
                            }
                        }
                        Opcode.INCLUDE_FILE -> {
                            val argparts = args!!.split(' ')
                            val filename = argparts[0]
                            val offset = if(argparts.size>=2 && argparts[1]!="null") getArgValue(argparts[1], heap) else null
                            val length = if(argparts.size>=3 && argparts[2]!="null") getArgValue(argparts[2], heap) else null
                            Instruction(opcode, offset, length, filename)
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
                "ub" -> Value(DataType.UBYTE, valueStr.toShort(16))
                "w" -> Value(DataType.WORD, valueStr.toInt(16))
                "uw" -> Value(DataType.UWORD, valueStr.toInt(16))
                "f" -> Value(DataType.FLOAT, valueStr.toDouble())
                "heap" -> {
                    val heapId = valueStr.toInt()
                    Value(heap.get(heapId).type, heapId)
                }
                else -> throw VmExecutionException("invalid datatype $type")
            }
        }

        private fun loadVars(lines: Iterator<IndexedValue<String>>,
                             vars: MutableMap<String, Value>) {
            val splitpattern = Pattern.compile("\\s+")
            while(true) {
                val (_, line) = lines.next()
                if(line=="%end_variables")
                    return
                val (name, typeStr, valueStr) = line.split(splitpattern, limit = 3)
                if(valueStr[0] !='"' && ':' !in valueStr)
                    throw VmExecutionException("missing value type character")
                val value = when(val type = DataType.valueOf(typeStr.toUpperCase())) {
                    DataType.UBYTE -> Value(DataType.UBYTE, valueStr.substring(3).toShort(16))
                    DataType.BYTE -> Value(DataType.BYTE, valueStr.substring(2).toShort(16))
                    DataType.UWORD -> Value(DataType.UWORD, valueStr.substring(3).toInt(16))
                    DataType.WORD -> Value(DataType.WORD, valueStr.substring(2).toInt(16))
                    DataType.FLOAT -> Value(DataType.FLOAT, valueStr.substring(2).toDouble())
                    in StringDatatypes -> {
                        if(valueStr.startsWith('"') && valueStr.endsWith('"'))
                            throw VmExecutionException("encountered a var with a string value, but all string values should already have been moved into the heap")
                        else if(!valueStr.startsWith("heap:"))
                            throw VmExecutionException("invalid string value, should be a heap reference")
                        else {
                            val heapId = valueStr.substring(5).toInt()
                            Value(type, heapId)
                        }
                    }
                    in ArrayDatatypes -> {
                        if(!valueStr.startsWith("heap:"))
                            throw VmExecutionException("invalid array value, should be a heap reference")
                        else {
                            val heapId = valueStr.substring(5).toInt()
                            Value(type, heapId)
                        }
                    }
                    else -> throw VmExecutionException("weird datatype")
                }
                vars[name] = value
            }
        }

        private fun loadMemoryPointers(lines: Iterator<IndexedValue<String>>,
                                       pointers: MutableMap<String, Pair<Int, DataType>>,
                                       heap: HeapValues) {
            val splitpattern = Pattern.compile("\\s+")
            while(true) {
                val (_, line) = lines.next()
                if(line=="%end_memorypointers")
                    return
                val (name, typeStr, valueStr) = line.split(splitpattern, limit = 3)
                if(valueStr[0] !='"' && ':' !in valueStr)
                    throw VmExecutionException("missing value type character")
                val type = DataType.valueOf(typeStr.toUpperCase())
                val value = getArgValue(valueStr, heap)!!.integerValue()
                pointers[name] = Pair(value, type)
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
                            2 -> values.add(Value(DataType.UBYTE, it.toShort(16)))
                            4 -> values.add(Value(DataType.UWORD, it.toInt(16)))
                            else -> throw VmExecutionException("invalid value at line $lineNr+1")
                        }
                    }
                    memory[address] = values
                }
            }
        }
    }
}
