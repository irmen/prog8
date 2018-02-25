# 8/16 bit virtual machine

# machine specs:

# MEMORY: 64K bytes, treated as one single array, indexed per byte, ONLY DATA - NO CODE
#         elements addressable as one of three elementary data types:
#           8-bit byte (singed and unsigned),
#           16-bit words (two 8-bit bytes, signed and unsigned) (stored in LSB order),
#           5-byte MFLPT floating point
#         addressing is possible via byte index (for the $0000-$00ff range) or via an unsigned word.
#
# MEMORY ACCESS:  via explicit load and store instructions,
#                 to put a value onto the stack or store the value on the top of the stack,
#                 or in one of the dynamic variables.
#
# I/O:  either via programmed I/O routines:
#           write [byte to text output/screen],
#           read [byte from keyboard],
#           wait [till any input comes available],
#           check [if input is available)
#       or via memory-mapped I/O  (text screen matrix, keyboard scan register)
#
# CPU:  stack based execution, no registers.
#       unlimited dynamic variables (v0, v1, ...) that have a value and a type.
#       types:
#           1-bit boolean  (can be CONST),
#           8-bit byte (singed and unsigned)  (can be CONST),
#           16-bit words (two 8-bit bytes, signed and unsigned) (can be CONST),
#           floating point  (can be CONST),
#           array of bytes (signed and unsigned),
#           array of words (signed and unsigned),
#           matrix (2-dimensional array) of bytes (signed and unsigned).
#
#       push (constant,
#       mark, unwind to previous mark.
#
# CPU INSTRUCTIONS:
#       stack manipulation mainly:
#       nop
#       push var / push2 var1, var2
#       pop var / pop2 var1, var2
#       various arithmetic operations, logical operations, boolean comparison operations
#       jump  label
#       jump_if_true  label, jump_if_false  label
#       jump_if_status_XX  label  special system dependent status register conditional check such as carry bit or overflow bit)
#       return  (return values on stack)
#       syscall function    (special system dependent implementation)
#       call function  (arguments are on stack)
#       enter / exit   (block for function, loop)
#
# TIMER INTERRUPT:   triggered every 1/60th of a second.
#       executes on a DIFFERENT stack and with a different PROGRAM LIST,
#       but with the ALL THE SAME DYNAMIC VARIABLES.
#

import time
import itertools
from .core import Instruction, Variable, Block, Program, Opcode, CONDITIONAL_OPCODES
from typing import Dict, List, Tuple, Union
from il65.emit import mflpt5_to_float, to_mflpt5


class ExecutionError(Exception):
    pass


class Memory:
    def __init__(self):
        self.mem = bytearray(65536)

    def get_byte(self, index: int) -> int:
        return self.mem[index]

    def get_sbyte(self, index: int) -> int:
        return 256 - self.mem[index]

    def get_word(self, index: int) -> int:
        return self.mem[index] + 256 * self.mem[index+1]

    def get_sword(self, index: int) -> int:
        return 65536 - (self.mem[index] + 256 * self.mem[index+1])

    def get_float(self, index: int) -> float:
        return mflpt5_to_float(self.mem[index: index+5])

    def set_byte(self, index: int, value: int) -> None:
        self.mem[index] = value

    def set_sbyte(self, index: int, value: int) -> None:
        self.mem[index] = value + 256

    def set_word(self, index: int, value: int) -> None:
        hi, lo = divmod(value, 256)
        self.mem[index] = lo
        self.mem[index+1] = hi

    def set_sword(self, index: int, value: int) -> None:
        hi, lo = divmod(value + 65536, 256)
        self.mem[index] = lo
        self.mem[index+1] = hi

    def set_float(self, index: int, value: float) -> None:
        self.mem[index: index+5] = to_mflpt5(value)


StackValueType = Union[bool, int, float, bytearray]


class Stack:
    def __init__(self):
        self.stack = []

    def debug_peek(self, size: int) -> List[StackValueType]:
        return self.stack[-size:]

    def size(self) -> int:
        return len(self.stack)

    def pop(self) -> StackValueType:
        return self.stack.pop()

    def pop2(self) -> Tuple[StackValueType, StackValueType]:
        return self.stack.pop(), self.stack.pop()

    def pop3(self) -> Tuple[StackValueType, StackValueType, StackValueType]:
        return self.stack.pop(), self.stack.pop(), self.stack.pop()

    def push(self, item: StackValueType) -> None:
        self._typecheck(item)
        self.stack.append(item)

    def push2(self, first: StackValueType, second: StackValueType) -> None:
        self._typecheck(first)
        self._typecheck(second)
        self.stack.append(first)
        self.stack.append(second)

    def push3(self, first: StackValueType, second: StackValueType, third: StackValueType) -> None:
        self._typecheck(first)
        self._typecheck(second)
        self._typecheck(third)
        self.stack.extend([first, second, third])

    def peek(self) -> StackValueType:
        return self.stack[-1] if self.stack else None

    def swap(self) -> None:
        x = self.stack[-1]
        self.stack[-1] = self.stack[-2]
        self.stack[-2] = x

    def _typecheck(self, value: StackValueType):
        if type(value) not in (bool, int, float, bytearray):
            raise TypeError("stack can only contain bool, int, float, bytearray")


# noinspection PyPep8Naming,PyUnusedLocal,PyMethodMayBeStatic
class VM:
    str_encoding = "iso-8859-15"        # @todo machine encoding via cbmcodecs or something?
    str_alt_encoding = "iso-8859-15"    # @todo machine encoding via cbmcodecs or something?

    def __init__(self, program: Program, timerprogram: Program) -> None:
        opcode_names = [oc.name for oc in Opcode]
        for ocname in opcode_names:
            if not hasattr(self, "opcode_" + ocname):
                raise NotImplementedError("missing opcode method for " + ocname)
        for method in dir(self):
            if method.startswith("opcode_"):
                if not method[7:] in opcode_names:
                    raise RuntimeError("opcode method for undefined opcode " + method)
        self.memory = Memory()
        self.main_stack = Stack()
        self.timer_stack = Stack()
        (self.main_program, self.timer_program), self.variables, self.labels = self.flatten_programs(program, timerprogram)
        self.connect_instruction_pointers(self.main_program)
        self.connect_instruction_pointers(self.timer_program)
        self.program = self.main_program
        self.stack = self.main_stack
        self.pc = None           # type: Instruction
        self.previous_pc = None  # type: Instruction
        assert all(i.next for i in self.main_program if i.opcode != Opcode.TERMINATE), "main: all instrs next must be set"
        assert all(i.next for i in self.timer_program if i.opcode != Opcode.TERMINATE), "main: all instrs next must be set"
        assert all(i.condnext for i in self.main_program if i.opcode in CONDITIONAL_OPCODES), "timer: all conditional instrs condnext must be set"
        assert all(i.condnext for i in self.timer_program if i.opcode in CONDITIONAL_OPCODES), "timer: all conditional instrs condnext must be set"

    def flatten_programs(self, *programs: Program) -> Tuple[List[List[Instruction]], Dict[str, Variable], Dict[str, Instruction]]:
        variables = {}   # type: Dict[str, Variable]
        labels = {}      # type: Dict[str, Instruction]
        flat_programs = []    # type: List[List[Instruction]]
        for program in programs:
            for block in program.blocks:
                flat = self.flatten(block, variables, labels)
                flat_programs.append(flat)
        return flat_programs, variables, labels

    def flatten(self, block: Block, variables: Dict[str, Variable], labels: Dict[str, Instruction]) -> List[Instruction]:
        def block_prefix(b: Block) -> str:
            if b.parent:
                return block_prefix(b.parent) + "." + b.name
            else:
                return b.name
        prefix = block_prefix(block)
        instructions = block.instructions
        for ins in instructions:
            if ins.opcode == Opcode.SYSCALL:
                continue
            if ins.args:
                newargs = []
                for a in ins.args:
                    if type(a) is str:
                        newargs.append(prefix + "." + a)
                    else:
                        newargs.append(a)
                ins.args = newargs
        for vardef in block.variables:
            vname = prefix + "." + vardef.name
            assert vname not in variables
            variables[vname] = vardef
        for name, instr in block.labels.items():
            name = prefix + "." + name
            assert name not in labels
            labels[name] = instr
        for subblock in block.blocks:
            instructions.extend(self.flatten(subblock, variables, labels))
        instructions.append(Instruction(Opcode.TERMINATE, [], None, None))
        del block.instructions
        del block.variables
        del block.labels
        return instructions

    def connect_instruction_pointers(self, instructions: List[Instruction]) -> None:
        i1, i2 = itertools.tee(instructions)
        next(i2, None)
        for i, nexti in itertools.zip_longest(i1, i2):
            i.next = nexti
            if i.opcode in CONDITIONAL_OPCODES:
                i.condnext = self.labels[i.args[0]]

    def run(self) -> None:
        last_timer = time.time()
        self.pc = self.program[0]   # first instruction of the main program
        steps = 1
        try:
            while True:
                now = time.time()
                # if now - last_timer >= 1/60:
                #     last_timer = now
                #     # start running the timer interrupt program instead
                #     self.previous_pc = self.pc
                #     self.program = self.timer_program
                #     self.stack = self.timer_stack
                #     self.pc = 0
                #     while True:
                #         self.dispatch(self.program[self.pc])
                #         return True
                #     self.pc = self.previous_pc
                #     self.program = self.mainprogram
                #     self.stack = self.mainstack
                print("Step", steps)
                self.debug_stack()
                next_pc = getattr(self, "opcode_" + self.pc.opcode.name)(self.pc)
                if next_pc:
                    self.pc = self.pc.next
                steps += 1
        except Exception as x:
            print("EXECUTION ERROR")
            self.debug_stack(5)
            raise

    def debug_stack(self, size: int=5) -> None:
        stack = self.stack.debug_peek(size)
        if len(stack) > 0:
            print(" stack (top {:d}):".format(size))
            for i, value in enumerate(reversed(stack), start=1):
                print(" {:d}. {:s}  {!r}".format(i, type(value).__name__, value))
        else:
            print(" stack is empty.")
        if self.pc is not None:
            print(" instruction:", self.pc)

    def _encodestr(self, string: str, alt: bool=False) -> bytearray:
        return bytearray(string, self.str_alt_encoding if alt else self.str_encoding)

    def _decodestr(self, bb: bytearray, alt: bool=False) -> str:
        return str(bb, self.str_alt_encoding if alt else self.str_encoding)

    def opcode_NOP(self, instruction: Instruction) -> bool:
        # do nothing
        return True

    def opcode_TERMINATE(self, instruction: Instruction) -> bool:
        # immediately terminate the VM
        raise ExecutionError("virtual machine terminated")

    def opcode_PUSH(self, instruction: Instruction) -> bool:
        # push a value onto the stack
        value = self.variables[instruction.args[0]].value
        self.stack.push(value)
        return True

    def opcode_PUSH2(self, instruction: Instruction) -> bool:
        # push two values onto the stack
        value1 = self.variables[instruction.args[0]].value
        value2 = self.variables[instruction.args[1]].value
        self.stack.push2(value1, value2)
        return True

    def opcode_PUSH3(self, instruction: Instruction) -> bool:
        # push three values onto the stack
        value1 = self.variables[instruction.args[0]].value
        value2 = self.variables[instruction.args[1]].value
        value3 = self.variables[instruction.args[2]].value
        self.stack.push3(value1, value2, value3)
        return True

    def opcode_POP(self, instruction: Instruction) -> bool:
        # pop value from stack and store it in a variable
        value = self.stack.pop()
        variable = self.variables[instruction.args[0]]
        assert not variable.const
        variable.value = value
        return True

    def opcode_POP2(self, instruction: Instruction) -> bool:
        # pop two values from tack and store it in two variables
        value1, value2 = self.stack.pop2()
        variable = self.variables[instruction.args[0]]
        assert not variable.const
        variable.value = value1
        variable = self.variables[instruction.args[1]]
        assert not variable.const
        variable.value = value2
        return True

    def opcode_POP3(self, instruction: Instruction) -> bool:
        # pop three values from tack and store it in two variables
        value1, value2, value3 = self.stack.pop3()
        variable = self.variables[instruction.args[0]]
        assert not variable.const
        variable.value = value1
        variable = self.variables[instruction.args[1]]
        assert not variable.const
        variable.value = value2
        variable = self.variables[instruction.args[2]]
        assert not variable.const
        variable.value = value3
        return True

    def opcode_ADD(self, instruction: Instruction) -> bool:
        # add top to second value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 + v1)        # type: ignore
        return True

    def opcode_SUB(self, instruction: Instruction) -> bool:
        # subtract top from second value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 - v1)        # type: ignore
        return True

    def opcode_MUL(self, instruction: Instruction) -> bool:
        # multiply top with second value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 * v1)        # type: ignore
        return True

    def opcode_DIV(self, instruction: Instruction) -> bool:
        # divide second value by top value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 / v1)        # type: ignore
        return True

    def opcode_AND(self, instruction: Instruction) -> bool:
        # second value LOGICAL_AND top value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 and v1)
        return True

    def opcode_OR(self, instruction: Instruction) -> bool:
        # second value LOGICAL_OR top value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 or v1)
        return True

    def opcode_XOR(self, instruction: Instruction) -> bool:
        # second value LOGICAL_XOR top value on stack and replace them with the result
        v1, v2 = self.stack.pop2()
        i1 = 1 if v1 else 0
        i2 = 1 if v2 else 0
        self.stack.push(bool(i1 ^ i2))
        return True

    def opcode_NOT(self, instruction: Instruction) -> bool:
        # replace top value on stack with its LOGICAL_NOT
        self.stack.push(not self.stack.pop())
        return True

    def opcode_CMP_EQ(self, instruction: Instruction) -> bool:
        # replace second and top value on stack with their == comparison
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 == v1)
        return True

    def opcode_CMP_LT(self, instruction: Instruction) -> bool:
        # replace second and top value on stack with their < comparison
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 < v1)        # type: ignore
        return True

    def opcode_CMP_GT(self, instruction: Instruction) -> bool:
        # replace second and top value on stack with their > comparison
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 > v1)        # type: ignore
        return True

    def opcode_CMP_LTE(self, instruction: Instruction) -> bool:
        # replace second and top value on stack with their <= comparison
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 <= v1)        # type: ignore
        return True

    def opcode_CMP_GTE(self, instruction: Instruction) -> bool:
        # replace second and top value on stack with their >= comparison
        v1, v2 = self.stack.pop2()
        self.stack.push(v2 >= v1)        # type: ignore
        return True

    def opcode_RETURN(self, instruction: Instruction) -> bool:
        # returns from the current function call
        # any return values have already been pushed on the stack
        raise NotImplementedError("return")

    def opcode_JUMP(self, instruction: Instruction) -> bool:
        # jumps unconditionally by resetting the PC to the given instruction index value
        return True

    def opcode_JUMP_IF_TRUE(self, instruction: Instruction) -> bool:
        # pops stack and jumps if that value is true, by resetting the PC to the given instruction index value
        result = self.stack.pop()
        if result:
            self.pc = self.pc.condnext
            return False
        return True

    def opcode_JUMP_IF_FALSE(self, instruction: Instruction) -> bool:
        # pops stack and jumps if that value is false, by resetting the PC to the given instruction index value
        result = self.stack.pop()
        if result:
            return True
        self.pc = self.pc.condnext
        return False

    def opcode_SYSCALL(self, instruction: Instruction) -> bool:
        call = getattr(self, "syscall_" + instruction.args[0], None)
        if call:
            return call()
        else:
            raise RuntimeError("no syscall method for " + instruction.args[0])

    def syscall_printstr(self) -> bool:
        value = self.stack.pop()
        if isinstance(value, bytearray):
            print(self._decodestr(value), end="")
            return True
        else:
            raise TypeError("printstr expects bytearray value", value)

    def syscall_decimalstr_signed(self) -> bool:
        value = self.stack.pop()
        if type(value) is int:
            self.stack.push(self._encodestr(str(value)))
            return True
        else:
            raise TypeError("decimalstr expects int value", value)

    def syscall_hexstr_signed(self) -> bool:
        value = self.stack.pop()
        if type(value) is int:
            if value >= 0:      # type: ignore
                strvalue = "${:x}".format(value)
            else:
                strvalue = "-${:x}".format(-value)  # type: ignore
            self.stack.push(self._encodestr(strvalue))
            return True
        else:
            raise TypeError("hexstr expects int value", value)
