package prog8.compilerinterface

import prog8.ast.base.*


class ZeropageDepletedError(message: String) : Exception(message)


abstract class Zeropage(protected val options: CompilationOptions) {

    abstract val SCRATCH_B1 : UInt      // temp storage for a single byte
    abstract val SCRATCH_REG : UInt     // temp storage for a register
    abstract val SCRATCH_W1 : UInt      // temp storage 1 for a word  $fb+$fc
    abstract val SCRATCH_W2 : UInt      // temp storage 2 for a word  $fb+$fc


    private val allocations = mutableMapOf<UInt, Pair<String, DataType>>()
    val free = mutableListOf<UInt>()     // subclasses must set this to the appropriate free locations.

    val allowedDatatypes = NumericDatatypes

    fun removeReservedFromFreePool() {
        for (reserved in options.zpReserved)
            reserve(reserved)

        free.removeAll(setOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_W1, SCRATCH_W1 + 1u, SCRATCH_W2, SCRATCH_W2 + 1u))
    }

    fun availableBytes() = if(options.zeropage== ZeropageType.DONTUSE) 0 else free.size
    fun hasByteAvailable() = if(options.zeropage== ZeropageType.DONTUSE) false else free.isNotEmpty()
    fun hasWordAvailable(): Boolean {
        if(options.zeropage== ZeropageType.DONTUSE)
            return false

        return free.windowed(2).any { it[0] == it[1] - 1u }
    }

    fun allocate(scopedname: String, datatype: DataType, position: Position?, errors: IErrorReporter): UInt {
        require(scopedname.isEmpty() || !allocations.values.any { it.first==scopedname } ) {"scopedname can't be allocated twice"}

        if(options.zeropage== ZeropageType.DONTUSE)
            throw InternalCompilerException("zero page usage has been disabled")

        val size: Int =
                when (datatype) {
                    in ByteDatatypes -> 1
                    in WordDatatypes -> 2
                    DataType.FLOAT -> {
                        if (options.floats) {
                            if(position!=null)
                                errors.warn("allocated a large value (float) in zeropage", position)
                            else
                                errors.warn("$scopedname: allocated a large value (float) in zeropage", Position.DUMMY)
                            options.compTarget.machine.FLOAT_MEM_SIZE
                        } else throw InternalCompilerException("floating point option not enabled")
                    }
                    else -> throw InternalCompilerException("cannot put datatype $datatype in zeropage")
                }

        if(free.size > 0) {
            if(size==1) {
                for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                    if(loneByte(candidate))
                        return makeAllocation(candidate, 1, datatype, scopedname)
                }
                return makeAllocation(free[0], 1, datatype, scopedname)
            }
            for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                if (sequentialFree(candidate, size))
                    return makeAllocation(candidate, size, datatype, scopedname)
            }
        }

        throw ZeropageDepletedError("ERROR: no free space in ZP to allocate $size sequential bytes")
    }

    protected fun reserve(range: UIntRange) = free.removeAll(range)

    private fun makeAllocation(address: UInt, size: Int, datatype: DataType, name: String?): UInt {
        require(size>=0)
        free.removeAll(address until address+size.toUInt())
        allocations[address] = (name ?: "<unnamed>") to datatype
        return address
    }

    private fun loneByte(address: UInt) = address in free && address-1u !in free && address+1u !in free
    private fun sequentialFree(address: UInt, size: Int): Boolean {
        require(size>0)
        return free.containsAll((address until address+size.toUInt()).toList())
    }
}
