package prog8.codegen.intermediate

import prog8.intermediate.*

/*
Construction helpers for the few IR opcodes that have no dedicated IRInstructions factory,
plus a small operand-inspection helper used by the optimizers.

The structured IRInstructions factory covers almost every opcode, but three opcodes have an
operand shape that no named factory produces (a lone source register, or a source register plus
an immediate without a destination). They are built here from schema-derived operands, so this
module never constructs IRInstruction directly.
*/

/** STOREHFACZERO / STOREHFACONE: a single source float register (no dedicated factory exists). */
internal fun storeHwFac(opcode: Opcode, fpRegister: Int): IRInstruction =
    IRInstructions.push(IRDataType.FLOAT, fpRegister).copy(opcode = opcode)

/** BITTST: a source register plus a byte immediate bit number, with no destination register. */
internal fun bitTest(type: IRDataType, register: Int, bit: Int): IRInstruction =
    IRInstructions.simple(Opcode.NOP).copy(
        opcode = Opcode.BITTST,
        type = type,
        srcA = IRInstructions.operandFor(Opcode.BITTST, type, InstructionSlot.SRC_A, register),
        immediate = IRInstructions.immediateFor(Opcode.BITTST, type, bit)
    )

/**
 * The name of the single symbol this instruction refers to, if any.
 * That is either the symbol a memory reference is based on, the symbol whose address is taken
 * in an immediate operand, or the label a branch/jump/call transfers control to.
 */
internal fun IRInstruction.referencedSymbol(): String? =
    memory?.symbolName
        ?: (immediate as? ImmediateOperand.SymbolAddress)?.symbol
        ?: labelTarget
