package prog8.code.core

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result


class MemAllocationError(message: String) : Exception(message)


abstract class MemoryAllocator(protected val options: CompilationOptions) {
    data class VarAllocation(val address: UInt, val dt: DataType, val size: Int)

    abstract fun allocate(name: String,
                          datatype: DataType,
                          numElements: Int?,
                          position: Position?,
                          errors: IErrorReporter): Result<VarAllocation, MemAllocationError>
}


abstract class Zeropage(options: CompilationOptions): MemoryAllocator(options) {

    abstract val SCRATCH_B1 : UInt      // temp storage for a single byte
    abstract val SCRATCH_REG : UInt     // temp storage for a register byte, must be B1+1
    abstract val SCRATCH_W1 : UInt      // temp storage 1 for a word
    abstract val SCRATCH_W2 : UInt      // temp storage 2 for a word
    abstract val SCRATCH_PTR : UInt     // temp storage for a pointer


    // the variables allocated into Zeropage.
    // name (scoped) ==> pair of address to (Datatype + bytesize)
    val allocatedVariables = mutableMapOf<String, VarAllocation>()

    val free = mutableListOf<UInt>()     // subclasses must set this to the appropriate free locations.

    fun removeReservedFromFreePool() {
        synchronized(this) {
            for (reserved in options.zpReserved)
                reserve(reserved)

            free.removeAll(arrayOf(SCRATCH_B1, SCRATCH_REG, SCRATCH_W1, SCRATCH_W1 + 1u, SCRATCH_W2, SCRATCH_W2 + 1u, SCRATCH_PTR, SCRATCH_PTR+1u))
        }
    }

    fun retainAllowed() {
        synchronized(this) {
            for(allowed in options.zpAllowed)
                free.retainAll { it in allowed }
        }
    }

    fun availableBytes() = if(options.zeropage== ZeropageType.DONTUSE) 0 else free.size
    fun hasByteAvailable() = if(options.zeropage== ZeropageType.DONTUSE) false else free.isNotEmpty()
    fun hasWordAvailable(): Boolean {
        if(options.zeropage== ZeropageType.DONTUSE)
            return false

        return free.windowed(2).any { it[0] == it[1] - 1u }
    }

    override fun allocate(name: String,
                          datatype: DataType,
                          numElements: Int?,
                          position: Position?,
                          errors: IErrorReporter): Result<VarAllocation, MemAllocationError> {

        require(name.isEmpty() || name !in allocatedVariables) {"name can't be allocated twice"}

        if(options.zeropage== ZeropageType.DONTUSE)
            return Err(MemAllocationError("zero page usage has been disabled"))

        val size: Int =
                when {
                    datatype.isIntegerOrBool -> options.compTarget.memorySize(datatype, null)
                    datatype.isPointer -> options.compTarget.memorySize(datatype, null)
                    datatype.isString || datatype.isArray -> {
                        val memsize = options.compTarget.memorySize(datatype, numElements!!)
                        if(position!=null)
                            errors.warn("allocating a large value in zeropage; str/array $memsize bytes", position)
                        else
                            errors.warn("$name: allocating a large value in zeropage; str/array $memsize bytes", Position.DUMMY)
                        memsize
                    }
                    datatype.isFloat -> {
                        if (options.floats) {
                            val memsize = options.compTarget.memorySize(DataType.FLOAT, null)
                            if(position!=null)
                                errors.warn("allocating a large value in zeropage; float $memsize bytes", position)
                            else
                                errors.warn("$name: allocating a large value in zeropage; float $memsize bytes", Position.DUMMY)
                            memsize
                        } else return Err(MemAllocationError("floating point option not enabled"))
                    }
                    else -> throw MemAllocationError("weird dt")
                }

        synchronized(this) {
            if(free.isNotEmpty()) {
                if(size==1) {
                    for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                        if(oneSeparateByteFree(candidate))
                            return Ok(VarAllocation(makeAllocation(candidate, 1, datatype, name), datatype,1))
                    }
                    return Ok(VarAllocation(makeAllocation(free[0], 1, datatype, name), datatype,1))
                }
                for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                    if (sequentialFree(candidate, size))
                        return Ok(VarAllocation(makeAllocation(candidate, size, datatype, name), datatype, size))
                }
            }
        }

        return Err(MemAllocationError("no more free space in ZP to allocate $size sequential bytes"))
    }

    private fun reserve(range: UIntRange) = free.removeAll(range)

    private fun makeAllocation(address: UInt, size: Int, datatype: DataType, name: String): UInt {
        require(size>=0)
        free.removeAll(address until address+size.toUInt())
        if(name.isNotEmpty()) {
            allocatedVariables[name] = when {
                datatype.isNumericOrBool -> VarAllocation(address, datatype, size)        // numerical variables in zeropage never have an initial value here because they are set in separate initializer assignments
                datatype.isString -> VarAllocation(address, datatype, size)
                datatype.isArray -> VarAllocation(address, datatype, size)
                datatype.isPointer -> VarAllocation(address, datatype, size)
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
}


// TODO: this class is not yet used
class GoldenRam(options: CompilationOptions, val region: UIntRange): MemoryAllocator(options) {
    private var nextLocation: UInt = region.first

    override fun allocate(
        name: String,
        datatype: DataType,
        numElements: Int?,
        position: Position?,
        errors: IErrorReporter): Result<VarAllocation, MemAllocationError> {

        val size: Int =
            when {
                datatype.isIntegerOrBool -> options.compTarget.memorySize(datatype, null)
                datatype.isString -> numElements!!
                datatype.isArray -> options.compTarget.memorySize(datatype, numElements!!)
                datatype.isFloat -> {
                    if (options.floats) {
                        options.compTarget.memorySize(DataType.FLOAT, null)
                    } else return Err(MemAllocationError("floating point option not enabled"))
                }
                else -> throw MemAllocationError("weird dt")
            }

        return if(nextLocation<=region.last && (region.last + 1u - nextLocation) >= size.toUInt()) {
            val result = Ok(VarAllocation(nextLocation, datatype, size))
            nextLocation += size.toUInt()
            result
        } else
            Err(MemAllocationError("no more free space in Golden RAM to allocate $size sequential bytes"))
    }
}