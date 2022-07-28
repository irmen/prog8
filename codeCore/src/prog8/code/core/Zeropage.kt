package prog8.code.core

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result


class ZeropageAllocationError(message: String) : Exception(message)


abstract class Zeropage(protected val options: CompilationOptions) {

    abstract val SCRATCH_B1 : UInt      // temp storage for a single byte
    abstract val SCRATCH_REG : UInt     // temp storage for a register, must be B1+1
    abstract val SCRATCH_W1 : UInt      // temp storage 1 for a word  $fb+$fc
    abstract val SCRATCH_W2 : UInt      // temp storage 2 for a word  $fb+$fc

    data class ZpAllocation(val address: UInt, val dt: DataType, val size: Int)

    // the variables allocated into Zeropage.
    // name (scoped) ==> pair of address to (Datatype + bytesize)
    val allocatedVariables = mutableMapOf<List<String>, ZpAllocation>()

    val free = mutableListOf<UInt>()     // subclasses must set this to the appropriate free locations.

    fun removeReservedFromFreePool() {
        synchronized(this) {
            for (reserved in options.zpReserved)
                reserve(reserved)

            free.removeAll(setOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_W1, SCRATCH_W1 + 1u, SCRATCH_W2, SCRATCH_W2 + 1u))
        }
    }

    fun availableBytes() = if(options.zeropage== ZeropageType.DONTUSE) 0 else free.size
    fun hasByteAvailable() = if(options.zeropage== ZeropageType.DONTUSE) false else free.isNotEmpty()
    fun hasWordAvailable(): Boolean {
        if(options.zeropage== ZeropageType.DONTUSE)
            return false

        return free.windowed(2).any { it[0] == it[1] - 1u }
    }

    fun allocate(name: List<String>,
                 datatype: DataType,
                 numElements: Int?,
                 position: Position?,
                 errors: IErrorReporter
    ): Result<Pair<UInt, Int>, ZeropageAllocationError> {

        require(name.isEmpty() || name !in allocatedVariables) {"name can't be allocated twice"}

        if(options.zeropage== ZeropageType.DONTUSE)
            return Err(ZeropageAllocationError("zero page usage has been disabled"))

        val size: Int =
                when (datatype) {
                    in IntegerDatatypes -> options.compTarget.memorySize(datatype)
                    DataType.STR, in ArrayDatatypes  -> {
                        val memsize = options.compTarget.memorySize(datatype, numElements!!)
                        if(position!=null)
                            errors.warn("allocating a large value in zeropage; str/array $memsize bytes", position)
                        else
                            errors.warn("$name: allocating a large value in zeropage; str/array $memsize bytes", Position.DUMMY)
                        memsize
                    }
                    DataType.FLOAT -> {
                        if (options.floats) {
                            val memsize = options.compTarget.memorySize(DataType.FLOAT)
                            if(position!=null)
                                errors.warn("allocating a large value in zeropage; float $memsize bytes", position)
                            else
                                errors.warn("$name: allocating a large value in zeropage; float $memsize bytes", Position.DUMMY)
                            memsize
                        } else return Err(ZeropageAllocationError("floating point option not enabled"))
                    }
                    else -> return Err(ZeropageAllocationError("cannot put datatype $datatype in zeropage"))
                }

        synchronized(this) {
            if(free.size > 0) {
                if(size==1) {
                    for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                        if(oneSeparateByteFree(candidate))
                            return Ok(Pair(makeAllocation(candidate, 1, datatype, name), 1))
                    }
                    return Ok(Pair(makeAllocation(free[0], 1, datatype, name), 1))
                }
                for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                    if (sequentialFree(candidate, size))
                        return Ok(Pair(makeAllocation(candidate, size, datatype, name), size))
                }
            }
        }

        return Err(ZeropageAllocationError("no more free space in ZP to allocate $size sequential bytes"))
    }

    private fun reserve(range: UIntRange) = free.removeAll(range)

    private fun makeAllocation(address: UInt, size: Int, datatype: DataType, name: List<String>): UInt {
        require(size>=0)
        free.removeAll(address until address+size.toUInt())
        if(name.isNotEmpty()) {
            allocatedVariables[name] = when(datatype) {
                in NumericDatatypes -> ZpAllocation(address, datatype, size)        // numerical variables in zeropage never have an initial value here because they are set in separate initializer assignments
                DataType.STR -> ZpAllocation(address, datatype, size)
                in ArrayDatatypes -> ZpAllocation(address, datatype, size)
                else -> throw AssemblyError("invalid dt")
            }
        }
        return address
    }

    private fun oneSeparateByteFree(address: UInt) = address in free && address-1u !in free && address+1u !in free
    private fun sequentialFree(address: UInt, size: Int): Boolean {
        require(size>0)
        return free.containsAll((address until address+size.toUInt()).toList())
    }

    abstract fun allocateCx16VirtualRegisters()
}
