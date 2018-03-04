"""
Simplistic 8/16 bit Virtual Machine to execute a stack based instruction language.
This is the VM itself (execution engine)

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

# 8/16 bit virtual machine

# machine specs:

# MEMORY: 64K bytes, treated as one single array, indexed per byte, ONLY DATA - NO CODE
#         elements addressable as one of three elementary data types:
#           8-bit byte (singed and unsigned),
#           16-bit words (two 8-bit bytes, signed and unsigned) (stored in LSB order),
#           5-byte MFLPT floating point
#         addressing is possible via byte index (for the $0000-$00ff range) or via an unsigned word.
#         there is NO memory management at all; all of the mem is globally shared and always available in full.
#         certain blocks of memory can be marked as read-only (write attempts will then crash the vm)
#
# MEMORY ACCESS:  via explicit load and store instructions,
#                 to put a value onto the stack or store the value on the top of the stack,
#                 or in one of the dynamic variables.
#
# I/O:  either via programmed I/O routines:
#           write [byte/bytearray to text output/screen],
#           read [byte/bytearray from keyboard],
#           wait [till any input comes available],  @todo
#           check [if input is available)   @todo
#       or via memory-mapped I/O  (text screen matrix, keyboard scan register @todo)
#
# CPU:  stack based execution, no registers.
#       unlimited dynamic variables (v0, v1, ...) that have a value and a type.
#       types:
#           1-bit boolean,
#           8-bit byte (singed and unsigned),
#           16-bit words (two 8-bit bytes, signed and unsigned),
#           floating point,
#           array of bytes (signed and unsigned),
#           array of words (signed and unsigned),
#           matrix (2-dimensional array) of bytes (signed and unsigned).
#       all of these can have the flag CONST as well which means they cannot be modified.
#
#       push (constant,
#       mark, unwind to previous mark.
#
# CPU INSTRUCTIONS:
#       stack manipulation mainly:
#       nop
#       push var / push2 var1, var2
#       pop var / pop2 var1, var2
#       various arithmetic operations, logical operations, boolean test and comparison operations
#       jump  label
#       jump_if_true  label, jump_if_false  label
#       @todo jump_if_status_XX  label  special system dependent status register conditional check such as carry bit or overflow bit)
#       return  (return values on stack)
#       syscall function    (special system dependent implementation)
#       call function  (arguments are on stack)
#       enter / exit   (function call frame)
#
# TIMER INTERRUPT:   triggered around each 1/60th of a second.
#       executes on a DIFFERENT stack and with a different PROGRAM LIST,
#       but with access to ALL THE SAME DYNAMIC VARIABLES.
#       This suspends the main program until the timer program RETURNs!
#

import time
import itertools
import collections
import array
import threading
import pprint
import tkinter
import tkinter.font
from typing import Dict, List, Tuple, Union, no_type_check
from il65.emit import mflpt5_to_float, to_mflpt5
from .program import Instruction, Variable, Block, Program, Opcode, Value, DataType


class ExecutionError(Exception):
    pass


class TerminateExecution(SystemExit):
    pass


class MemoryAccessError(Exception):
    pass


class Memory:
    def __init__(self):
        self.mem = bytearray(65536)
        self.readonly = bytearray(65536)

    def mark_readonly(self, start: int, end: int) -> None:
        self.readonly[start:end+1] = [1] * (end-start+1)

    def get_byte(self, index: int) -> int:
        return self.mem[index]

    def get_bytes(self, startindex: int, amount: int) -> int:
        return self.mem[startindex: startindex+amount]

    def get_sbyte(self, index: int) -> int:
        return 256 - self.mem[index]

    def get_word(self, index: int) -> int:
        return self.mem[index] + 256 * self.mem[index+1]

    def get_sword(self, index: int) -> int:
        return 65536 - (self.mem[index] + 256 * self.mem[index+1])

    def get_float(self, index: int) -> float:
        return mflpt5_to_float(self.mem[index: index+5])

    def set_byte(self, index: int, value: int) -> None:
        if self.readonly[index]:
            raise MemoryAccessError("read-only", index)
        self.mem[index] = value

    def set_sbyte(self, index: int, value: int) -> None:
        if self.readonly[index]:
            raise MemoryAccessError("read-only", index)
        self.mem[index] = value + 256

    def set_word(self, index: int, value: int) -> None:
        if self.readonly[index] or self.readonly[index+1]:
            raise MemoryAccessError("read-only", index)
        hi, lo = divmod(value, 256)
        self.mem[index] = lo
        self.mem[index+1] = hi

    def set_sword(self, index: int, value: int) -> None:
        if self.readonly[index] or self.readonly[index+1]:
            raise MemoryAccessError("read-only", index)
        hi, lo = divmod(value + 65536, 256)
        self.mem[index] = lo
        self.mem[index+1] = hi

    def set_float(self, index: int, value: float) -> None:
        if any(self.readonly[index:index+5]):
            raise MemoryAccessError("read-only", index)
        self.mem[index: index+5] = to_mflpt5(value)


class CallFrameMarker:
    __slots__ = ["returninstruction"]

    def __init__(self, instruction: Instruction) -> None:
        self.returninstruction = instruction

    def __str__(self) -> str:
        return repr(self)

    def __repr__(self) -> str:
        return "<CallFrameMarker returninstruction={:s}>".format(str(self.returninstruction))


StackValueType = Union[Value, CallFrameMarker]


class Stack:
    def __init__(self):
        self.stack = []
        self.pop_history = collections.deque(maxlen=10)

    def debug_peek(self, size: int) -> List[StackValueType]:
        return self.stack[-size:]

    def size(self) -> int:
        return len(self.stack)

    def pop(self) -> StackValueType:
        x = self.stack.pop()
        self.pop_history.append(x)
        return x

    def pop2(self) -> Tuple[StackValueType, StackValueType]:
        x, y = self.stack.pop(), self.stack.pop()
        self.pop_history.append(x)
        self.pop_history.append(y)
        return x, y

    def pop3(self) -> Tuple[StackValueType, StackValueType, StackValueType]:
        x, y, z = self.stack.pop(), self.stack.pop(), self.stack.pop()
        self.pop_history.append(x)
        self.pop_history.append(y)
        self.pop_history.append(z)
        return x, y, z

    def pop_under(self, number: int) -> StackValueType:
        return self.stack.pop(-1-number)

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

    def push_under(self, number: int, value: StackValueType) -> None:
        self.stack.insert(-number, value)

    def peek(self) -> StackValueType:
        return self.stack[-1] if self.stack else None

    def swap(self) -> None:
        x = self.stack[-1]
        self.stack[-1] = self.stack[-2]
        self.stack[-2] = x

    def _typecheck(self, value: StackValueType):
        if not isinstance(value, (Value, CallFrameMarker)):
            raise TypeError("invalid item type pushed", value)


# noinspection PyPep8Naming,PyUnusedLocal,PyMethodMayBeStatic
class VM:
    str_encoding = "iso-8859-15"
    str_alt_encoding = "iso-8859-15"
    readonly_mem_ranges = []        # type: List[Tuple[int, int]]
    timer_irq_resolution = 1/30

    def __init__(self, program: Program, timerprogram: Program=None) -> None:
        opcode_names = [oc.name for oc in Opcode]
        timerprogram = timerprogram or Program([])
        for ocname in opcode_names:
            if not hasattr(self, "opcode_" + ocname):
                raise NotImplementedError("missing opcode method for " + ocname)
        for method in dir(self):
            if method.startswith("opcode_"):
                if not method[7:] in opcode_names:
                    raise RuntimeError("opcode method for undefined opcode " + method)
        for oc in Opcode:
            if oc not in self.dispatch_table:
                raise NotImplementedError("no dispatch entry in table for " + oc.name)
        self.memory = Memory()
        for start, end in self.readonly_mem_ranges:
            self.memory.mark_readonly(start, end)
        self.main_stack = Stack()
        self.timer_stack = Stack()
        self.main_program, self.timer_program, self.variables, self.labels = self.flatten_programs(program, timerprogram)
        self.connect_instruction_pointers(self.main_program)
        self.connect_instruction_pointers(self.timer_program)
        self.program = self.main_program
        self.stack = self.main_stack
        self.pc = None           # type: Instruction
        self.charscreen_address = 0
        self.charscreen_width = 0
        self.charscreen_height = 0
        self.system = System(self)
        assert all(i.next for i in self.main_program
                   if i.opcode != Opcode.TERMINATE), "main: all instrs next must be set"
        assert all(i.next for i in self.timer_program
                   if i.opcode not in (Opcode.TERMINATE, Opcode.RETURN)), "timer: all instrs next must be set"
        assert all(i.alt_next for i in self.main_program
                   if i.opcode in (Opcode.CALL, Opcode.JUMP_IF_FALSE, Opcode.JUMP_IF_TRUE)), "main: alt_nexts must be set"
        assert all(i.alt_next for i in self.timer_program
                   if i.opcode in (Opcode.CALL, Opcode.JUMP_IF_FALSE, Opcode.JUMP_IF_TRUE)), "timer: alt_nexts must be set"
        print("[TinyVM starting up.]")

    def enable_charscreen(self, screen_address: int, width: int, height: int) -> None:
        self.charscreen_address = screen_address
        self.charscreen_width, self.charscreen_height = width, height

    def flatten_programs(self, main: Program, timer: Program) \
            -> Tuple[List[Instruction], List[Instruction], Dict[str, Variable], Dict[str, Instruction]]:
        variables = {}           # type: Dict[str, Variable]
        labels = {}              # type: Dict[str, Instruction]
        instructions_main = []   # type: List[Instruction]
        instructions_timer = []  # type: List[Instruction]
        for block in main.blocks:
            flat = self.flatten(block, variables, labels)
            instructions_main.extend(flat)
        instructions_main.append(Instruction(Opcode.TERMINATE, [], None, None))
        for block in timer.blocks:
            flat = self.flatten(block, variables, labels)
            instructions_timer.extend(flat)
        return instructions_main, instructions_timer, variables, labels

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
                newargs = []    # type: List[Union[str, int, Value]]
                for a in ins.args:
                    if isinstance(a, str):
                        newargs.append(prefix + "." + a)
                    else:
                        newargs.append(a)       # type: ignore
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
        del block.instructions
        del block.variables
        del block.labels
        return instructions

    def connect_instruction_pointers(self, instructions: List[Instruction]) -> None:
        i1, i2 = itertools.tee(instructions)
        next(i2, None)
        for i, nexti in itertools.zip_longest(i1, i2):
            if i.opcode in (Opcode.JUMP_IF_TRUE, Opcode.JUMP_IF_FALSE):
                i.next = nexti      # normal flow target
                i.alt_next = self.labels[i.args[0]]  # conditional jump target
            elif i.opcode == Opcode.JUMP:
                i.next = self.labels[i.args[0]]      # jump target
            elif i.opcode == Opcode.CALL:
                i.next = self.labels[i.args[1]]      # call target
                i.alt_next = nexti                   # return instruction
            else:
                i.next = nexti

    def run(self) -> None:
        if self.charscreen_address:
            threading.Thread(target=ScreenViewer.create,
                             args=(self.memory, self.system, self.charscreen_address, self.charscreen_width, self.charscreen_height),
                             name="screenviewer", daemon=True).start()
            time.sleep(0.05)

        self.pc = self.program[0]   # first instruction of the main program
        self.stack.push(CallFrameMarker(None))  # enter the call frame so the timer program can end with a RETURN
        try:
            counter = 0
            previous_timer_irq = time.perf_counter()
            while self.pc is not None:
                next_pc = self.dispatch_table[self.pc.opcode](self, self.pc)
                if next_pc:
                    self.pc = self.pc.next
                counter += 1
                if self.charscreen_address and counter % 1000 == 0:
                    time.sleep(0.001)    # allow the tkinter window to update
                time_since_irq = time.perf_counter() - previous_timer_irq
                if time_since_irq > 1/60:
                    self.timer_irq()
                    previous_timer_irq = time.perf_counter()
        except TerminateExecution as x:
            why = str(x)
            print("[TinyVM execution terminated{:s}]\n".format(": "+why if why else "."))
            return
        except Exception as x:
            print("EXECUTION ERROR")
            self.debug_stack(5)
            raise
        else:
            print("[TinyVM execution ended.]")

    def timer_irq(self) -> None:
        # This is the timer 'irq' handler. It is called to run the timer program at a certain interval.
        # During the execution the main program is halted
        if self.timer_program:
            previous_pc = self.pc
            previous_program = self.program
            previous_stack = self.stack
            self.stack = self.timer_stack
            self.program = self.timer_program
            self.pc = self.program[0]
            self.stack.push(CallFrameMarker(None))  # enter the call frame so the timer program can end with a RETURN
            while self.pc is not None:
                next_pc = self.dispatch_table[self.pc.opcode](self, self.pc)
                if next_pc:
                    self.pc = self.pc.next
            self.pc = previous_pc
            self.program = previous_program
            self.stack = previous_stack

    def debug_stack(self, size: int=5) -> None:
        stack = self.stack.debug_peek(size)
        if len(stack) > 0:
            print("** stack (top {:d}):".format(size))
            for i, value in enumerate(reversed(stack), start=1):
                print("  {:d}. {:s}  {:s}".format(i, type(value).__name__, str(value)))
        else:
            print("** stack is empty.")
        if self.stack.pop_history:
            print("** last {:d} values popped from stack (most recent on top):".format(self.stack.pop_history.maxlen))
            pprint.pprint(list(reversed(self.stack.pop_history)), indent=2, compact=True, width=20)    # type: ignore
        if self.pc is not None:
            print("* instruction:", self.pc)

    def assign_variable(self, variable: Variable, value: Value) -> None:
        assert not variable.const, "cannot modify a const"
        assert isinstance(value, Value)
        variable.value = value

    def opcode_NOP(self, instruction: Instruction) -> bool:
        # do nothing
        return True

    def opcode_TERMINATE(self, instruction: Instruction) -> bool:
        raise TerminateExecution()

    def opcode_PUSH(self, instruction: Instruction) -> bool:
        value = self.variables[instruction.args[0]].value       # type: ignore
        self.stack.push(value)
        return True

    def opcode_DUP(self, instruction: Instruction) -> bool:
        self.stack.push(self.stack.peek())
        return True

    def opcode_DUP2(self, instruction: Instruction) -> bool:
        x = self.stack.peek()
        self.stack.push(x)
        self.stack.push(x)
        return True

    def opcode_SWAP(self, instruction: Instruction) -> bool:
        value2, value1 = self.stack.pop2()
        self.stack.push2(value2, value1)
        return True

    @no_type_check
    def opcode_PUSH2(self, instruction: Instruction) -> bool:
        value1 = self.variables[instruction.args[0]].value
        value2 = self.variables[instruction.args[1]].value
        self.stack.push2(value1, value2)
        return True

    @no_type_check
    def opcode_PUSH3(self, instruction: Instruction) -> bool:
        value1 = self.variables[instruction.args[0]].value
        value2 = self.variables[instruction.args[1]].value
        value3 = self.variables[instruction.args[2]].value
        self.stack.push3(value1, value2, value3)
        return True

    @no_type_check
    def opcode_POP(self, instruction: Instruction) -> bool:
        value = self.stack.pop()
        variable = self.variables[instruction.args[0]]
        self.assign_variable(variable, value)
        return True

    @no_type_check
    def opcode_POP2(self, instruction: Instruction) -> bool:
        value1, value2 = self.stack.pop2()
        variable = self.variables[instruction.args[0]]
        self.assign_variable(variable, value1)
        variable = self.variables[instruction.args[1]]
        self.assign_variable(variable, value2)
        return True

    @no_type_check
    def opcode_POP3(self, instruction: Instruction) -> bool:
        value1, value2, value3 = self.stack.pop3()
        variable = self.variables[instruction.args[0]]
        self.assign_variable(variable, value1)
        variable = self.variables[instruction.args[1]]
        self.assign_variable(variable, value2)
        variable = self.variables[instruction.args[2]]
        self.assign_variable(variable, value3)
        return True

    @no_type_check
    def opcode_ADD(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(first + second)
        return True

    @no_type_check
    def opcode_SUB(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(first - second)
        return True

    @no_type_check
    def opcode_MUL(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(first * second)
        return True

    @no_type_check
    def opcode_DIV(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(first / second)
        return True

    def opcode_AND(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(first and second)
        return True

    def opcode_OR(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(first or second)
        return True

    def opcode_XOR(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        ifirst = 1 if first else 0
        isecond = 1 if second else 0
        self.stack.push(Value(DataType.BOOL, bool(ifirst ^ isecond)))
        return True

    def opcode_NOT(self, instruction: Instruction) -> bool:
        self.stack.push(Value(DataType.BOOL, not self.stack.pop()))
        return True

    def opcode_TEST(self, instruction: Instruction) -> bool:
        self.stack.push(Value(DataType.BOOL, bool(self.stack.pop())))
        return True

    def opcode_CMP_EQ(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(Value(DataType.BOOL, first == second))
        return True

    @no_type_check
    def opcode_CMP_LT(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(Value(DataType.BOOL, first < second))
        return True

    @no_type_check
    def opcode_CMP_GT(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(Value(DataType.BOOL, first > second))
        return True

    @no_type_check
    def opcode_CMP_LTE(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(Value(DataType.BOOL, first <= second))
        return True

    @no_type_check
    def opcode_CMP_GTE(self, instruction: Instruction) -> bool:
        second, first = self.stack.pop2()
        self.stack.push(Value(DataType.BOOL, first >= second))
        return True

    def opcode_CALL(self, instruction: Instruction) -> bool:
        # arguments are already on the stack
        num_args = instruction.args[0]
        assert isinstance(num_args, int)
        self.stack.push_under(num_args, CallFrameMarker(instruction.alt_next))
        return True

    def opcode_RETURN(self, instruction: Instruction) -> bool:
        num_returnvalues = instruction.args[0]
        assert isinstance(num_returnvalues, int)
        callframe = self.stack.pop_under(num_returnvalues)
        assert isinstance(callframe, CallFrameMarker), callframe
        self.pc = callframe.returninstruction
        return False

    def opcode_JUMP(self, instruction: Instruction) -> bool:
        return True    # jump simply points to the next instruction elsewhere

    def opcode_JUMP_IF_TRUE(self, instruction: Instruction) -> bool:
        result = self.stack.pop()
        if result:
            self.pc = self.pc.alt_next     # alternative next instruction
            return False
        return True

    def opcode_JUMP_IF_FALSE(self, instruction: Instruction) -> bool:
        result = self.stack.pop()
        if result:
            return True
        self.pc = self.pc.alt_next     # alternative next instruction
        return False

    def opcode_SYSCALL(self, instruction: Instruction) -> bool:
        syscall = instruction.args[0]
        assert isinstance(syscall, str)
        call = getattr(self.system, "syscall_" + syscall, None)
        if call:
            return call()
        else:
            raise RuntimeError("no syscall method for " + syscall)

    dispatch_table = {
        Opcode.TERMINATE: opcode_TERMINATE,
        Opcode.NOP: opcode_NOP,
        Opcode.PUSH: opcode_PUSH,
        Opcode.PUSH2: opcode_PUSH2,
        Opcode.PUSH3: opcode_PUSH3,
        Opcode.POP: opcode_POP,
        Opcode.POP2: opcode_POP2,
        Opcode.POP3: opcode_POP3,
        Opcode.DUP: opcode_DUP,
        Opcode.DUP2: opcode_DUP2,
        Opcode.SWAP: opcode_SWAP,
        Opcode.ADD: opcode_ADD,
        Opcode.SUB: opcode_SUB,
        Opcode.MUL: opcode_MUL,
        Opcode.DIV: opcode_DIV,
        Opcode.AND: opcode_AND,
        Opcode.OR: opcode_OR,
        Opcode.XOR: opcode_XOR,
        Opcode.NOT: opcode_NOT,
        Opcode.TEST: opcode_TEST,
        Opcode.CMP_EQ: opcode_CMP_EQ,
        Opcode.CMP_LT: opcode_CMP_LT,
        Opcode.CMP_GT: opcode_CMP_GT,
        Opcode.CMP_LTE: opcode_CMP_LTE,
        Opcode.CMP_GTE: opcode_CMP_GTE,
        Opcode.CALL: opcode_CALL,
        Opcode.RETURN: opcode_RETURN,
        Opcode.JUMP: opcode_JUMP,
        Opcode.JUMP_IF_TRUE: opcode_JUMP_IF_TRUE,
        Opcode.JUMP_IF_FALSE: opcode_JUMP_IF_FALSE,
        Opcode.SYSCALL: opcode_SYSCALL,
    }


class System:
    def __init__(self, vm: VM) -> None:
        self.vm = vm

    def encodestr(self, string: str, alt: bool=False) -> bytearray:
        return bytearray(string, self.vm.str_alt_encoding if alt else self.vm.str_encoding)

    def decodestr(self, bb: Union[bytearray, array.array], alt: bool=False) -> str:
        return str(bb, self.vm.str_alt_encoding if alt else self.vm.str_encoding)   # type: ignore

    def syscall_printstr(self) -> bool:
        value = self.vm.stack.pop()
        assert isinstance(value, Value)
        if value.dtype == DataType.ARRAY_BYTE:
            print(self.decodestr(value.value), end="")   # type: ignore
            return True
        else:
            raise TypeError("printstr expects bytearray", value)

    def syscall_printchr(self) -> bool:
        charactervalue = self.vm.stack.pop()
        assert isinstance(charactervalue, Value)
        if charactervalue.dtype == DataType.BYTE:
            print(self.decodestr(bytearray([charactervalue.value])), end="")    # type: ignore
            return True
        else:
            raise TypeError("printchr expects BYTE", charactervalue)

    def syscall_input(self) -> bool:
        self.vm.stack.push(Value(DataType.ARRAY_BYTE, self.encodestr(input())))
        return True

    def syscall_getchr(self) -> bool:
        self.vm.stack.push(Value(DataType.BYTE, self.encodestr(input() + '\n')[0]))
        return True

    def syscall_decimalstr_signed(self) -> bool:
        value = self.vm.stack.pop()
        assert isinstance(value, Value)
        if value.dtype in (DataType.SBYTE, DataType.SWORD):
            self.vm.stack.push(Value(DataType.ARRAY_BYTE, self.encodestr(str(value.value))))
            return True
        else:
            raise TypeError("decimalstr_signed expects signed int", value)

    def syscall_decimalstr_unsigned(self) -> bool:
        value = self.vm.stack.pop()
        assert isinstance(value, Value)
        if value.dtype in (DataType.BYTE, DataType.WORD):
            self.vm.stack.push(Value(DataType.ARRAY_BYTE, self.encodestr(str(value.value))))
            return True
        else:
            raise TypeError("decimalstr_signed expects unsigned int", value)

    def syscall_hexstr_signed(self) -> bool:
        value = self.vm.stack.pop()
        if type(value) is int:
            if value >= 0:      # type: ignore
                strvalue = "${:x}".format(value)
            else:
                strvalue = "-${:x}".format(-value)  # type: ignore
            self.vm.stack.push(Value(DataType.ARRAY_BYTE, self.encodestr(strvalue)))
            return True
        else:
            raise TypeError("hexstr expects int", value)

    def syscall_memwrite_byte(self) -> bool:
        value, address = self.vm.stack.pop2()
        assert isinstance(value, Value) and isinstance(address, Value)
        assert value.dtype == DataType.BYTE and address.dtype == DataType.WORD
        self.vm.memory.set_byte(address.value, value.value)    # type: ignore
        return True

    def syscall_memwrite_sbyte(self) -> bool:
        value, address = self.vm.stack.pop2()
        assert isinstance(value, Value) and isinstance(address, Value)
        assert value.dtype == DataType.SBYTE and address.dtype == DataType.WORD
        self.vm.memory.set_sbyte(address.value, value.value)    # type: ignore
        return True

    def syscall_memwrite_word(self) -> bool:
        value, address = self.vm.stack.pop2()
        assert isinstance(value, Value) and isinstance(address, Value)
        assert value.dtype in (DataType.WORD, DataType.BYTE) and address.dtype == DataType.WORD
        self.vm.memory.set_word(address.value, value.value)    # type: ignore
        return True

    def syscall_memwrite_sword(self) -> bool:
        value, address = self.vm.stack.pop2()
        assert isinstance(value, Value) and isinstance(address, Value)
        assert value.dtype in (DataType.SWORD, DataType.SBYTE, DataType.BYTE) and address.dtype == DataType.WORD
        self.vm.memory.set_sword(address.value, value.value)    # type: ignore
        return True

    def syscall_memwrite_float(self) -> bool:
        value, address = self.vm.stack.pop2()
        assert isinstance(value, Value) and isinstance(address, Value)
        assert value.dtype == DataType.FLOAT and address.dtype == DataType.WORD
        self.vm.memory.set_float(address.value, value.value)    # type: ignore
        return True

    def syscall_memwrite_str(self) -> bool:
        strbytes, address = self.vm.stack.pop2()
        assert isinstance(strbytes, Value) and isinstance(address, Value)
        assert strbytes.dtype == DataType.ARRAY_BYTE and address.dtype == DataType.WORD
        for i, b in enumerate(strbytes.value):          # type: ignore
            self.vm.memory.set_byte(address+i, b)       # type: ignore
        return True

    def syscall_smalldelay(self) -> bool:
        time.sleep(1/100)
        return True

    def syscall_delay(self) -> bool:
        time.sleep(0.1)
        return True


class ScreenViewer(tkinter.Tk):
    def __init__(self, memory: Memory, system: System, screen_addr: int, screen_width: int, screen_height: int) -> None:
        super().__init__()
        self.title("IL65 tinyvm")
        self.fontsize = 16
        self.memory = memory
        self.system = system
        self.address = screen_addr
        self.width = screen_width
        self.height = screen_height
        self.monospace = tkinter.font.Font(self, family="Courier", weight="bold", size=self.fontsize)   # type: ignore
        cw = self.monospace.measure("x")*self.width+8
        self.canvas = tkinter.Canvas(self, width=cw, height=self.fontsize*self.height+8, bg="blue")
        self.canvas.pack()
        self.after(10, self.update_screen)

    def update_screen(self):
        self.canvas.delete(tkinter.ALL)
        lines = []
        for y in range(self.height):
            line = self.system.decodestr(self.memory.get_bytes(self.address+y*self.width, self.width))
            lines.append("".join(c if c.isprintable() else " " for c in line))
        for y, line in enumerate(lines):
            self.canvas.create_text(4, self.fontsize*y, text=line, fill="white", font=self.monospace, anchor=tkinter.NW)
        self.after(30, self.update_screen)

    @classmethod
    def create(cls, memory: Memory, system: System, screen_addr: int, screen_width: int, screen_height: int) -> None:
        viewer = cls(memory, system, screen_addr, screen_width, screen_height)
        viewer.mainloop()
