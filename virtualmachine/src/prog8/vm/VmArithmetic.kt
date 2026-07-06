package prog8.vm

import prog8.intermediate.IRDataType
import prog8.intermediate.IRInstruction

// Note: statusbitsNZ() was removed as part of the strict status-bits contract cleanup.
// The VM no longer sets Z/N on arithmetic or load operations; only CMP, CMPI, SGN,
// BITTST and the carry-out of shift/rotate operations modify the status flags. The
// IR generator always emits an explicit CMPI before any branch that depends on the
// result, so the VM doesn't need to track Z/N as a side effect of other operations.
// See VirtualMachine.kt (the init require() and the class-level contract comment)
// and CpuType.statusBitsOnMultiByteOps for the rationale.

internal fun VirtualMachine.statusbitsComparison(left: Int, right: Int, type: IRDataType) {
    val comparison = left - right
    statusZero = comparison == 0
    when (type) {
        IRDataType.BYTE -> {
            statusNegative = (comparison and 0x80) != 0
            statusCarry = (left and 0xff) >= (right and 0xff)
        }
        IRDataType.WORD -> {
            statusNegative = (comparison and 0x8000) != 0
            statusCarry = (left and 0xffff) >= (right and 0xffff)
        }
        IRDataType.POINTER -> {
            statusNegative = (comparison and 0x8000) != 0
            statusCarry = (left and 0xffff) >= (right and 0xffff)
        }
        IRDataType.LONG -> {
            statusNegative = comparison < 0
            statusCarry = Integer.compareUnsigned(left, right) >= 0
        }
        IRDataType.FLOAT -> { /* floats don't change the status bits */ }
    }
}

internal fun VirtualMachine.statusbitsComparisonWithOverflow(left: Int, right: Int, type: IRDataType) {
    statusbitsComparison(left, right, type)
    val comparison = left - right
    when (type) {
        IRDataType.BYTE -> {
            val signBit = 0x80
            val leftSign = left and signBit
            val rightSign = right and signBit
            statusOverflow = ((leftSign xor rightSign) and (leftSign xor (comparison and signBit))) != 0
        }
        IRDataType.WORD -> {
            val signBit = 0x8000
            val leftSign = left and signBit
            val rightSign = right and signBit
            statusOverflow = ((leftSign xor rightSign) and (leftSign xor (comparison and signBit))) != 0
        }
        IRDataType.POINTER -> {
            val signBit = 0x8000
            val leftSign = left and signBit
            val rightSign = right and signBit
            statusOverflow = ((leftSign xor rightSign) and (leftSign xor (comparison and signBit))) != 0
        }
        IRDataType.LONG -> {
            val leftSign = left < 0
            val rightSign = right < 0
            val resultSign = comparison < 0
            statusOverflow = leftSign != rightSign && leftSign != resultSign
        }
        IRDataType.FLOAT -> { /* floats don't set overflow */ }
    }
}

internal fun VirtualMachine.plusMinusMultAnyByte(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getUB(reg1)
    val right = registers.getUB(reg2)
    val result = when(operator) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    registers.setUB(reg1, result.toUByte())
}

internal fun VirtualMachine.multiplyAnyByteSigned(reg1: Int, reg2: Int) {
    val left = registers.getSB(reg1)
    val right = registers.getSB(reg2)
    val result = left * right
    registers.setSB(reg1, result.toByte())
}

internal fun VirtualMachine.plusMinusMultConstByte(operator: String, reg1: Int, value: UByte) {
    val left = registers.getUB(reg1)
    val result = when(operator) {
        "+" -> left + value
        "-" -> left - value
        "*" -> left * value
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    registers.setUB(reg1, result.toUByte())
}

internal fun VirtualMachine.multiplyConstByteSigned(reg1: Int, value: Byte) {
    val left = registers.getSB(reg1)
    val result = left * value
    registers.setSB(reg1, result.toByte())
}

internal fun VirtualMachine.plusMinusMultAnyByteInplace(operator: String, reg1: Int, address: UInt) {
    val memvalue = memory.getUB(address)
    val operand = registers.getUB(reg1)
    val result = when(operator) {
        "+" -> memvalue + operand
        "-" -> memvalue - operand
        "*" -> memvalue * operand
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    memory.setUB(address, result.toUByte())
}

internal fun VirtualMachine.multiplyAnyByteSignedInplace(reg1: Int, address: UInt) {
    val memvalue = memory.getSB(address)
    val operand = registers.getSB(reg1)
    val result = memvalue * operand
    memory.setSB(address, result.toByte())
}

internal fun VirtualMachine.divModByteSigned(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getSB(reg1)
    val right = registers.getSB(reg2)
    val result = when(operator) {
        "/" -> {
            if(right==0.toByte()) 127
            else left / right
        }
        "%" -> {
            if(right==0.toByte()) 0
            else left % right
        }
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    registers.setSB(reg1, result.toByte())
}

internal fun VirtualMachine.divModConstByteSigned(operator: String, reg1: Int, value: Byte) {
    val left = registers.getSB(reg1)
    val result = when(operator) {
        "/" -> {
            if(value==0.toByte()) 127
            else left / value
        }
        "%" -> {
            if(value==0.toByte()) 0
            else left % value
        }
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    registers.setSB(reg1, result.toByte())
}

internal fun VirtualMachine.divModByteSignedInplace(operator: String, reg1: Int, address: UInt) {
    val left = memory.getSB(address)
    val right = registers.getSB(reg1)
    val result = when(operator) {
        "/" -> {
            if(right==0.toByte()) 127
            else left / right
        }
        "%" -> {
            if(right==0.toByte()) 0
            else left % right
        }
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    memory.setSB(address, result.toByte())
}

internal fun VirtualMachine.divOrModByteUnsigned(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getUB(reg1)
    val right = registers.getUB(reg2)
    val result = when(operator) {
        "/" -> {
            if(right==0.toUByte()) 0xffu
            else left / right
        }
        "%" -> {
            if(right==0.toUByte()) 0u
            else left % right
        }
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    registers.setUB(reg1, result.toUByte())
}

internal fun VirtualMachine.divOrModConstByteUnsigned(operator: String, reg1: Int, value: UByte) {
    val left = registers.getUB(reg1)
    val result = when(operator) {
        "/" -> {
            if(value==0.toUByte()) 0xffu
            else left / value
        }
        "%" -> {
            if(value==0.toUByte()) 0u
            else left % value
        }
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    registers.setUB(reg1, result.toUByte())
}

internal fun VirtualMachine.divAndModUByte(reg1: Int, reg2: Int) {
    val left = registers.getUB(reg1)
    val right = registers.getUB(reg2)
    val division = if(right==0.toUByte()) 0xffu else left / right
    val remainder = if(right==0.toUByte()) 0u else left % right
    valueStack.add(division.toUByte())
    valueStack.add(remainder.toUByte())
}

internal fun VirtualMachine.divAndModConstUByte(reg1: Int, value: UByte) {
    val left = registers.getUB(reg1)
    val division = if(value==0.toUByte()) 0xffu else left / value
    val remainder = if(value==0.toUByte()) 0u else left % value
    valueStack.add(division.toUByte())
    valueStack.add(remainder.toUByte())
}

internal fun VirtualMachine.divAndModUWord(reg1: Int, reg2: Int) {
    val left = registers.getUW(reg1)
    val right = registers.getUW(reg2)
    val division = if(right==0.toUShort()) 0xffffu else left / right
    val remainder = if(right==0.toUShort()) 0u else left % right
    valueStack.pushw(division.toUShort())
    valueStack.pushw(remainder.toUShort())
}

internal fun VirtualMachine.divAndModConstUWord(reg1: Int, value: UShort) {
    val left = registers.getUW(reg1)
    val division = if(value==0.toUShort()) 0xffffu else left / value
    val remainder = if(value==0.toUShort()) 0u else left % value
    valueStack.pushw(division.toUShort())
    valueStack.pushw(remainder.toUShort())
}

internal fun VirtualMachine.divAndModSByte(reg1: Int, reg2: Int) {
    val left = registers.getSB(reg1)
    val right = registers.getSB(reg2)
    val division = if(right==0.toByte()) 127 else left / right
    val remainder = if(right==0.toByte()) 0 else left % right
    valueStack.add(division.toUByte())
    valueStack.add(remainder.toUByte())
}

internal fun VirtualMachine.divAndModConstSByte(reg1: Int, value: Byte) {
    val left = registers.getSB(reg1)
    val division = if(value==0.toByte()) 127 else left / value
    val remainder = if(value==0.toByte()) 0 else left % value
    valueStack.add(division.toUByte())
    valueStack.add(remainder.toUByte())
}

internal fun VirtualMachine.divAndModSWord(reg1: Int, reg2: Int) {
    val left = registers.getSW(reg1)
    val right = registers.getSW(reg2)
    val division = if(right==0.toShort()) 32767 else left / right
    val remainder = if(right==0.toShort()) 0 else left % right
    valueStack.pushw(division.toUShort())
    valueStack.pushw(remainder.toUShort())
}

internal fun VirtualMachine.divAndModConstSWord(reg1: Int, value: Short) {
    val left = registers.getSW(reg1)
    val division = if(value==0.toShort()) 32767 else left / value
    val remainder = if(value==0.toShort()) 0 else left % value
    valueStack.pushw(division.toUShort())
    valueStack.pushw(remainder.toUShort())
}

internal fun VirtualMachine.divModByteUnsignedInplace(operator: String, reg1: Int, address: UInt) {
    val left = memory.getUB(address)
    val right = registers.getUB(reg1)
    val result = when(operator) {
        "/" -> {
            if(right==0.toUByte()) 0xffu
            else left / right
        }
        "%" -> {
            if(right==0.toUByte()) 0u
            else left % right
        }
        else -> throw IllegalArgumentException("operator byte $operator")
    }
    memory.setUB(address, result.toUByte())
}

internal fun VirtualMachine.plusMinusMultAnyWord(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getUW(reg1)
    val right = registers.getUW(reg2)
    val result: UInt
    when(operator) {
        "+" -> result = left + right
        "-" -> result = left - right
        "*" -> {
            result = left.toUInt() * right
            mul16LastUpper = result shr 16
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setUW(reg1, result.toUShort())
}

internal fun VirtualMachine.multiplyAnyWordSigned(reg1: Int, reg2: Int) {
    val left = registers.getSW(reg1)
    val right = registers.getSW(reg2)
    val result = left.toInt() * right
    mul16LastUpper = result.toUInt() shr 16
    registers.setSW(reg1, result.toShort())
}

internal fun VirtualMachine.plusMinusMultConstWord(operator: String, reg1: Int, value: UShort) {
    val left = registers.getUW(reg1)
    val result: UInt
    when(operator) {
        "+" -> result = left + value
        "-" -> result = left - value
        "*" -> {
            result = left.toUInt() * value
            mul16LastUpper = result shr 16
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setUW(reg1, result.toUShort())
}

internal fun VirtualMachine.multiplyConstWordSigned(reg1: Int, value: Short) {
    val left = registers.getSW(reg1)
    val result = left.toInt() * value
    mul16LastUpper = result.toUInt() shr 16
    registers.setSW(reg1, result.toShort())
}

internal fun VirtualMachine.plusMinusMultAnyWordInplace(operator: String, reg1: Int, address: UInt) {
    val memvalue = memory.getUW(address)
    val operand = registers.getUW(reg1)
    val result: UInt
    when(operator) {
        "+" -> result = memvalue + operand
        "-" -> result = memvalue - operand
        "*" -> {
            result = memvalue.toUInt() * operand
            mul16LastUpper = result shr 16
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    memory.setUW(address, result.toUShort())
}

internal fun VirtualMachine.multiplyAnyWordSignedInplace(reg1: Int, address: UInt) {
    val memvalue = memory.getSW(address)
    val operand = registers.getSW(reg1)
    val result = memvalue.toInt() * operand
    mul16LastUpper = result.toUInt() shr 16
    memory.setSW(address, result.toShort())
}

internal fun VirtualMachine.divOrModWordUnsigned(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getUW(reg1)
    val right = registers.getUW(reg2)
    val result = when(operator) {
        "/" -> {
            if(right==0.toUShort()) 0xffffu
            else left / right
        }
        "%" -> {
            if(right==0.toUShort()) 0u
            else left % right
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setUW(reg1, result.toUShort())
}

internal fun VirtualMachine.divOrModConstWordUnsigned(operator: String, reg1: Int, value: UShort) {
    val left = registers.getUW(reg1)
    val result = when(operator) {
        "/" -> {
            if(value==0.toUShort()) 0xffffu
            else left / value
        }
        "%" -> {
            if(value==0.toUShort()) 0u
            else left % value
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setUW(reg1, result.toUShort())
}

internal fun VirtualMachine.divModWordUnsignedInplace(operator: String, reg1: Int, address: UInt) {
    val left = memory.getUW(address)
    val right = registers.getUW(reg1)
    val result = when(operator) {
        "/" -> {
            if(right==0.toUShort()) 0xffffu
            else left / right
        }
        "%" -> {
            if(right==0.toUShort()) 0u
            else left % right
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    memory.setUW(address, result.toUShort())
}

internal fun VirtualMachine.divModWordSigned(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getSW(reg1)
    val right = registers.getSW(reg2)
    val result = when(operator) {
        "/" -> {
            if(right==0.toShort()) 32767
            else left / right
        }
        "%" -> {
            if(right==0.toShort()) 0
            else left % right
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setSW(reg1, result.toShort())
}

internal fun VirtualMachine.divModConstWordSigned(operator: String, reg1: Int, value: Short) {
    val left = registers.getSW(reg1)
    val result = when(operator) {
        "/" -> {
            if(value==0.toShort()) 32767
            else left / value
        }
        "%" -> {
            if(value==0.toShort()) 0
            else left % value
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setSW(reg1, result.toShort())
}

internal fun VirtualMachine.divModWordSignedInplace(operator: String, reg1: Int, address: UInt) {
    val left = memory.getSW(address)
    val right = registers.getSW(reg1)
    val result = when(operator) {
        "/" -> {
            if(right==0.toShort()) 32767
            else left / right
        }
        "%" -> {
            if(right==0.toShort()) 0
            else left % right
        }
        else -> throw IllegalArgumentException("operator word $operator")
    }
    memory.setSW(address, result.toShort())
}

internal fun arithFloat(left: Double, operator: String, right: Double): Double = when(operator) {
    "+" -> left + right
    "-" -> left - right
    "*" -> left * right
    "/" -> {
        if(right==0.0) Double.MAX_VALUE
        else left / right
    }
    "%" -> {
        if(right==0.0) 0.0
        else left % right
    }
    else -> throw IllegalArgumentException("operator word $operator")
}

internal fun VirtualMachine.getBranchOperands(i: IRInstruction): Pair<Int, Int> {
    return when(i.type) {
        IRDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
        IRDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
        IRDataType.POINTER -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
        IRDataType.LONG -> Pair(registers.getSL(i.reg1!!), registers.getSL(i.reg2!!))
        IRDataType.FLOAT -> {
            throw IllegalArgumentException("can't use float here")
        }
        null -> throw IllegalArgumentException("need type for branch instruction")
    }
}

internal fun VirtualMachine.getBranchOperandsImm(i: IRInstruction): Pair<Int, Int> {
    return when(i.type) {
        IRDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), i.immediate!!)
        IRDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), i.immediate!!)
        IRDataType.POINTER -> Pair(registers.getSW(i.reg1!!).toInt(), i.immediate!!)
        IRDataType.LONG -> Pair(registers.getSL(i.reg1!!), i.immediate!!)
        IRDataType.FLOAT -> {
            throw IllegalArgumentException("can't use float here")
        }
        null -> throw IllegalArgumentException("need type for branch instruction")
    }
}

internal fun VirtualMachine.getBranchOperandsU(i: IRInstruction): Pair<UInt, UInt> {
    return when(i.type) {
        IRDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
        IRDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
        IRDataType.POINTER -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
        IRDataType.LONG -> Pair(registers.getSL(i.reg1!!).toUInt(), registers.getSL(i.reg2!!).toUInt())
        IRDataType.FLOAT -> {
            throw IllegalArgumentException("can't use float here")
        }
        null -> throw IllegalArgumentException("need type for branch instruction")
    }
}

internal fun VirtualMachine.getBranchOperandsImmU(i: IRInstruction): Pair<UInt, UInt> {
    return when(i.type) {
        IRDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), i.immediate!!.toUInt())
        IRDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), i.immediate!!.toUInt())
        IRDataType.POINTER -> Pair(registers.getUW(i.reg1!!).toUInt(), i.immediate!!.toUInt())
        IRDataType.LONG -> Pair(registers.getSL(i.reg1!!).toUInt(), i.immediate!!.toUInt())
        IRDataType.FLOAT -> {
            throw IllegalArgumentException("can't use float here")
        }
        null -> throw IllegalArgumentException("need type for branch instruction")
    }
}

internal fun VirtualMachine.getLogicalOperandsU(i: IRInstruction): Pair<UInt, UInt> {
    return when(i.type) {
        IRDataType.BYTE -> Pair(registers.getUB(i.reg1!!).toUInt(), registers.getUB(i.reg2!!).toUInt())
        IRDataType.WORD -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
        IRDataType.POINTER -> Pair(registers.getUW(i.reg1!!).toUInt(), registers.getUW(i.reg2!!).toUInt())
        IRDataType.LONG -> Pair(registers.getSL(i.reg1!!).toUInt(), registers.getSL(i.reg2!!).toUInt())
        IRDataType.FLOAT -> {
            throw IllegalArgumentException("can't use float here")
        }
        null -> throw IllegalArgumentException("need type for logical instruction")
    }
}

internal fun VirtualMachine.getLogicalOperandsS(i: IRInstruction): Pair<Int, Int> {
    return when(i.type) {
        IRDataType.BYTE -> Pair(registers.getSB(i.reg1!!).toInt(), registers.getSB(i.reg2!!).toInt())
        IRDataType.WORD -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
        IRDataType.POINTER -> Pair(registers.getSW(i.reg1!!).toInt(), registers.getSW(i.reg2!!).toInt())
        IRDataType.LONG -> Pair(registers.getSL(i.reg1!!), registers.getSL(i.reg2!!))
        IRDataType.FLOAT -> {
            throw IllegalArgumentException("can't use float here")
        }
        null -> throw IllegalArgumentException("need type for logical instruction")
    }
}

internal fun VirtualMachine.plusMinusMultAnyLong(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getSL(reg1)
    val right = registers.getSL(reg2)
    val result: Int = when(operator) {
        "+" -> left + right
        "-" -> left - right
        "*" -> left * right
        else -> throw IllegalArgumentException("operator word $operator")
    }
    registers.setSL(reg1, result)
}

internal fun VirtualMachine.plusMinusMultConstLong(operator: String, reg1: Int, value: Int) {
    val left = registers.getSL(reg1)
    val result: Int = when(operator) {
        "+" -> left + value
        "-" -> left - value
        "*" -> left * value
        else -> throw IllegalArgumentException("operator long $operator")
    }
    registers.setSL(reg1, result)
}

internal fun VirtualMachine.plusMinusMultAnyLongInplace(operator: String, reg1: Int, address: UInt) {
    val memvalue = memory.getSL(address)
    val operand = registers.getSL(reg1)
    val result: Int = when(operator) {
        "+" -> memvalue + operand
        "-" -> memvalue - operand
        "*" -> memvalue * operand
        else -> throw IllegalArgumentException("operator word $operator")
    }
    memory.setSL(address, result)
}

internal fun VirtualMachine.multiplyAnyLongSigned(reg1: Int, reg2: Int) {
    val result = registers.getSL(reg1) * registers.getSL(reg2)
    registers.setSL(reg1, result)
}

internal fun VirtualMachine.multiplyConstLongSigned(reg1: Int, value: Int) {
    val result = registers.getSL(reg1) * value
    registers.setSL(reg1, result)
}

internal fun VirtualMachine.multiplyAnyLongSignedInplace(reg1: Int, address: UInt) {
    val result = memory.getSL(address) * registers.getSL(reg1)
    memory.setSL(address, result)
}

internal fun VirtualMachine.divModLongSigned(operator: String, reg1: Int, reg2: Int) {
    val left = registers.getSL(reg1)
    val right = registers.getSL(reg2)
    val result = when(operator) {
        "/" -> {
            if(right==0) Int.MAX_VALUE
            else left / right
        }
        "%" -> {
            if(right==0) 0
            else left % right
        }
        else -> throw IllegalArgumentException("operator long $operator")
    }
    registers.setSL(reg1, result)
}

internal fun VirtualMachine.divModConstLongSigned(operator: String, reg1: Int, immediate: Int) {
    val left = registers.getSL(reg1)
    val result = when(operator) {
        "/" -> {
            if(immediate==0) Int.MAX_VALUE
            else left / immediate
        }
        "%" -> {
            if(immediate==0) 0
            else left % immediate
        }
        else -> throw IllegalArgumentException("operator long $operator")
    }
    registers.setSL(reg1, result)
}

internal fun VirtualMachine.divModLongSignedInplace(operator: String, reg1: Int, address: UInt) {
    val left = memory.getSL(address)
    val right = registers.getSL(reg1)
    val result = when(operator) {
        "/" -> {
            if(right==0) Int.MAX_VALUE
            else left / right
        }
        "%" -> {
            if(right==0) 0
            else left % right
        }
        else -> throw IllegalArgumentException("operator long $operator")
    }
    memory.setSL(address, result)
}
