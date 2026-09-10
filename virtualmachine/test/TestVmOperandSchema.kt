import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import prog8.code.core.CompilationOptions
import prog8.code.core.OutputType
import prog8.code.core.Position
import prog8.code.core.ZeropageType
import prog8.code.target.VMTarget
import prog8.intermediate.*
import prog8.vm.ProgramExitException
import prog8.vm.Syscall
import prog8.vm.VirtualMachine

/**
 * Every VM instruction handler must read exactly the operands that the OpcodeSchema declares for
 * that (opcode, type) combination. This test builds a canonical instruction for every combination
 * straight from the schema and executes it, so a handler that reaches for an operand slot the
 * instruction doesn't have (or casts a memory reference to the wrong kind) fails here.
 */
class TestVmOperandSchema: FunSpec({

    fun getTestOptions(): CompilationOptions {
        val target = VMTarget()
        return CompilationOptions.builder(target)
            .output(OutputType.RAW)
            .zeropage(ZeropageType.DONTUSE)
            .floats(true)
            .compilerVersion("99.99")
            .loadAddress(target.PROGRAM_LOAD_ADDRESS)
            .memtopAddress(0xffffu)
            .build()
    }

    val targetLabel = "main.test"
    val dataAddress = 0x2000u

    // registers used for the generic operands; they are all pre-loaded with harmless values
    val destReg = 1
    val destBReg = 2
    val srcAReg = 3
    val srcBReg = 4
    val indexReg = 5
    val pointerReg = 6
    val indirectTargetReg = 7
    val bankReg = 8
    val argReg = 9
    val resultReg = 10

    fun registerOperand(schema: OpcodeSchema, slotSchema: RegisterSlotSchema, number: Int): RegisterOperand {
        val type = schema.typeFor(slotSchema.typeRule) ?: IRDataType.WORD
        val register = if (slotSchema.registerFile == RegisterFile.FLOAT)
            VirtualRegister.float(number) else VirtualRegister.int(number)
        return RegisterOperand(register, type, slotSchema.role, slotSchema.direction)
    }

    // a calling convention slot that the VM understands for the given data type
    fun hardwareSlotFor(type: IRDataType?): CallingConventionSlot = when (type) {
        IRDataType.BYTE -> CallingConventionSlot(0)          // A
        IRDataType.WORD -> CallingConventionSlot(3)          // AX
        IRDataType.LONG, IRDataType.POINTER -> CallingConventionSlot(10)    // D0
        IRDataType.FLOAT -> CallingConventionSlot(6)         // FAC0
        null -> CallingConventionSlot(0)
    }

    fun callSiteFor(kind: CallKind): CallSite = when (kind) {
        CallKind.NORMAL -> CallSite(
            CallTarget.Direct(codeLabel(targetLabel)),
            arguments = listOf(
                Calls.argument(argReg, IRDataType.WORD, CallLocation.ParameterMemory("main.test.arg", dataAddress.toAddress()))
            ),
            results = listOf(Calls.result(resultReg, IRDataType.WORD))
        )
        CallKind.INDIRECT -> CallSite(CallTarget.Direct(codeIndirect(indirectTargetReg)))
        CallKind.FAR -> CallSite(CallTarget.Banked(1, codeAddress(dataAddress)))
        CallKind.FAR_VARBANK -> CallSite(
            CallTarget.BankedVariable(
                RegisterOperand(VirtualRegister.int(bankReg), IRDataType.BYTE, OperandRole.VALUE, OperandDirection.USE),
                codeAddress(dataAddress)
            )
        )
        CallKind.SYSCALL -> CallSite(
            CallTarget.SystemCall(Syscall.RNDSEED.ordinal),
            arguments = listOf(
                Calls.argument(argReg, IRDataType.WORD),
                Calls.argument(argReg, IRDataType.WORD)
            )
        )
    }

    fun buildInstruction(opcode: Opcode, type: IRDataType?): IRInstruction {
        val schema = OpcodeSchemas.get(opcode, type)
        var dest: RegisterOperand? = null
        var destB: RegisterOperand? = null
        var srcA: RegisterOperand? = null
        var srcB: RegisterOperand? = null
        var immediate: ImmediateOperand? = null
        var hardwareSlot: HardwareSlotOperand? = null
        var memory: MemoryReference? = null
        var target: CodeReference? = null
        var callSite: CallSite? = null

        for (slotSchema in schema.slots) {
            when (slotSchema) {
                is RegisterSlotSchema -> when (slotSchema.slot) {
                    InstructionSlot.DEST -> dest = registerOperand(schema, slotSchema, destReg)
                    InstructionSlot.DEST_B -> destB = registerOperand(schema, slotSchema, destBReg)
                    InstructionSlot.SRC_A -> srcA = registerOperand(schema, slotSchema, srcAReg)
                    InstructionSlot.SRC_B -> srcB = registerOperand(schema, slotSchema, srcBReg)
                    else -> throw IllegalArgumentException("unexpected register slot ${slotSchema.slot}")
                }
                is ImmediateSlotSchema -> {
                    val immType = schema.typeFor(slotSchema.typeRule) ?: IRDataType.WORD
                    immediate = if (immType == IRDataType.FLOAT)
                        ImmediateOperand.FloatValue(2.0) else ImmediateOperand.Integer(1, immType)
                }
                is MemorySlotSchema -> memory = when (slotSchema.kind) {
                    MemoryKind.DIRECT -> IRMemory.direct(dataAddress)
                    MemoryKind.INDEXED -> IRMemory.indexed(dataAddress, indexReg, IRDataType.WORD)
                    MemoryKind.INDIRECT -> IRMemory.indirect(pointerReg)
                }
                is HardwareSlotSchema -> hardwareSlot =
                    HardwareSlotOperand(hardwareSlotFor(type), schema.typeFor(slotSchema.typeRule) ?: IRDataType.WORD)
                is TargetSlotSchema -> target = when (slotSchema.kind) {
                    TargetKind.STATIC -> codeLabel(targetLabel)
                    TargetKind.INDIRECT -> codeIndirect(indirectTargetReg)
                }
                is CallSiteSlotSchema -> callSite = callSiteFor(slotSchema.kind)
            }
        }

        return IRInstruction(opcode, type, dest, destB, srcA, srcB, immediate, hardwareSlot, memory, target, callSite)
    }

    fun runSingle(instruction: IRInstruction) {
        val program = IRProgram("test", IRSymbolTable(), getTestOptions(), VMTarget())
        val block = IRBlock("main", false, IRBlock.Options(), Position.DUMMY)
        val sub = IRSubroutine(targetLabel, emptyList(), emptyList(), Position.DUMMY)
        val code = IRCodeChunk(sub.label, null)
        code += instruction
        code += IRInstructions.returnVoid()
        sub += code
        block += sub
        program.addBlock(block)

        val vm = VirtualMachine(program)
        vm.breakpointHandler = { _, _ -> }
        vm.reset(true)      // memory starts out random; zero it so pointer variables hold valid addresses
        // give all operand registers harmless but valid values
        vm.registers.setUL(pointerReg, dataAddress)
        vm.registers.setUL(indirectTargetReg, dataAddress)
        vm.registers.setUB(indexReg, 0u)
        vm.registers.setUB(bankReg, 0u)
        vm.registers.setUW(srcAReg, 100u)
        vm.registers.setUW(srcBReg, 7u)
        vm.registers.setFloat(srcAReg, 100.0)
        vm.registers.setFloat(srcBReg, 7.0)
        // the pointer variable that LOADP_INC/STOREP_INC dereference
        vm.memory.setSL(dataAddress, 0x3000)
        // enough bytes on the value stack for POP of any width
        repeat(16) { vm.valueStack.add(0u) }
        vm.step()
    }

    test("every opcode handler matches its operand schema") {
        val operandProblems = mutableListOf<String>()
        for (opcode in Opcode.entries) {
            for (type in OpcodeSchemas.typesFor(opcode)) {
                val instruction = buildInstruction(opcode, type)
                try {
                    runSingle(instruction)
                } catch (ex: ProgramExitException) {
                    // fine: return/exit style instruction ran to completion
                } catch (ex: NullPointerException) {
                    operandProblems += "$instruction -> NPE (operand assumed present): ${ex.message}"
                } catch (ex: ClassCastException) {
                    operandProblems += "$instruction -> wrong operand kind: ${ex.message}"
                } catch (ex: IllegalArgumentException) {
                    val message = ex.message ?: ""
                    // "missing ... operand" comes from the require*() operand accessors, so it means
                    // the handler reads a slot that this instruction doesn't have.
                    if ("missing" in message && "operand" in message)
                        operandProblems += "$instruction -> $message"
                } catch (ex: Exception) {
                    operandProblems += "$instruction -> unexpected ${ex::class.simpleName}: ${ex.message}"
                }
            }
        }
        operandProblems shouldBe emptyList()
    }
})
