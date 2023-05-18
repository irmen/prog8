package prog8.codegen.intermediate

import prog8.intermediate.IRProgram


class IRRegisterOptimizer(private val irProg: IRProgram) {
    fun optimize() {
        // reuseRegisters()
    }

/*
    TODO: this register re-use renumbering isn't going to work like this,
    because subroutines will be clobbering the registers that the subroutine
    which is calling them might be using...


    private fun reuseRegisters() {

        fun addToUsage(usage: MutableMap<Pair<Int, IRDataType>, MutableSet<IRCodeChunkBase>>,
                       regnum: Int,
                       dt: IRDataType,
                       chunk: IRCodeChunkBase) {
            val key = regnum to dt
            val chunks = usage[key] ?: mutableSetOf()
            chunks.add(chunk)
            usage[key] = chunks
        }

        val usage: MutableMap<Pair<Int, IRDataType>, MutableSet<IRCodeChunkBase>> = mutableMapOf()

        irProg.foreachCodeChunk { chunk ->
            chunk.usedRegisters().regsTypes.forEach { (regNum, types) ->
                types.forEach { dt ->
                    addToUsage(usage, regNum, dt, chunk)
                }
            }
        }

        val registerReplacements = usage.asSequence()
            .filter { it.value.size==1 }
            .map { it.key to it.value.iterator().next() }
            .groupBy({ it.second }, {it.first})
            .asSequence()
            .associate { (chunk, registers) ->
                chunk to registers.withIndex().associate { (index, reg) -> reg to 50000+index }
            }

        registerReplacements.forEach { replaceRegisters(it.key, it.value) }
    }

    private fun replaceRegisters(chunk: IRCodeChunkBase, replacements: Map<Pair<Int, IRDataType>, Int>) {
        val (rF, rI) = replacements.asSequence().partition { it.key.second==IRDataType.FLOAT }
        val replacementsInt = rI.associate { it.key.first to it.value }
        val replacementsFloat = rF.associate { it.key.first to it.value }

        fun replaceRegs(fcallArgs: FunctionCallArgs?): FunctionCallArgs? {
            if(fcallArgs==null)
                return null
            val args = if(fcallArgs.arguments.isEmpty()) fcallArgs.arguments else {
                fcallArgs.arguments.map {
                    FunctionCallArgs.ArgumentSpec(
                        it.name,
                        it.address,
                        FunctionCallArgs.RegSpec(
                            it.reg.dt,
                            if(it.reg.dt==IRDataType.FLOAT)
                                replacementsFloat.getOrDefault(it.reg.registerNum, it.reg.registerNum)
                            else
                                replacementsInt.getOrDefault(it.reg.registerNum, it.reg.registerNum),
                            it.reg.cpuRegister
                        )
                    )
                }
            }
            val rt = fcallArgs.returns
            val returns = if(rt==null) null else {
                FunctionCallArgs.RegSpec(
                    rt.dt,
                    if(rt.dt==IRDataType.FLOAT)
                        replacementsFloat.getOrDefault(rt.registerNum, rt.registerNum)
                    else
                        replacementsInt.getOrDefault(rt.registerNum, rt.registerNum),
                    rt.cpuRegister
                )
            }
            return FunctionCallArgs(args, returns)
        }

        fun replaceRegs(instruction: IRInstruction): IRInstruction {
            val reg1 = replacementsInt.getOrDefault(instruction.reg1, instruction.reg1)
            val reg2 = replacementsInt.getOrDefault(instruction.reg2, instruction.reg2)
            val fpReg1 = replacementsFloat.getOrDefault(instruction.fpReg1, instruction.fpReg1)
            val fpReg2 = replacementsFloat.getOrDefault(instruction.fpReg2, instruction.fpReg2)
            return instruction.copy(reg1 = reg1, reg2 = reg2, fpReg1 = fpReg1, fpReg2 = fpReg2, fcallArgs = replaceRegs(instruction.fcallArgs))
        }
        val newInstructions = chunk.instructions.map {
            replaceRegs(it)
        }
        chunk.instructions.clear()
        chunk.instructions.addAll(newInstructions)
    }
*/
}