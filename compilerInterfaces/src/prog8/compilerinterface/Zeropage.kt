package prog8.compilerinterface

import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import prog8.ast.base.*


class ZeropageDepletedError(message: String) : Exception(message)


abstract class Zeropage(protected val options: CompilationOptions) {

    abstract val SCRATCH_B1 : UInt      // temp storage for a single byte
    abstract val SCRATCH_REG : UInt     // temp storage for a register, must be B1+1
    abstract val SCRATCH_W1 : UInt      // temp storage 1 for a word  $fb+$fc
    abstract val SCRATCH_W2 : UInt      // temp storage 2 for a word  $fb+$fc


    private val allocations = mutableMapOf<UInt, Pair<String, DataType>>()
    val free = mutableListOf<UInt>()     // subclasses must set this to the appropriate free locations.

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

    fun allocate(scopedname: String, datatype: DataType, arraySize: Int?, position: Position?, errors: IErrorReporter): Result<Pair<UInt, Int>, ZeropageDepletedError> {
        require(scopedname.isEmpty() || !allocations.values.any { it.first==scopedname } ) {"scopedname can't be allocated twice"}

        if(options.zeropage== ZeropageType.DONTUSE)
            throw InternalCompilerException("zero page usage has been disabled")

        val size: Int =
                when (datatype) {
                    in IntegerDatatypes -> options.compTarget.memorySize(datatype)
                    DataType.STR, in ArrayDatatypes  -> {
                        val memsize = arraySize!! * options.compTarget.memorySize(ArrayToElementTypes.getValue(datatype))
                        if(position!=null)
                            errors.warn("allocating a large value in zeropage; str/array $memsize bytes", position)
                        else
                            errors.warn("$scopedname: allocating a large value in zeropage; str/array $memsize bytes", Position.DUMMY)
                        memsize
                    }
                    DataType.FLOAT -> {
                        if (options.floats) {
                            val memsize = options.compTarget.memorySize(DataType.FLOAT)
                            if(position!=null)
                                errors.warn("allocating a large value in zeropage; float $memsize bytes", position)
                            else
                                errors.warn("$scopedname: allocating a large value in zeropage; float $memsize bytes", Position.DUMMY)
                            memsize
                        } else throw InternalCompilerException("floating point option not enabled")
                    }
                    else -> throw InternalCompilerException("cannot put datatype $datatype in zeropage")
                }

        synchronized(this) {
            if(free.size > 0) {
                if(size==1) {
                    for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                        if(oneSeparateByteFree(candidate))
                            return Ok(Pair(makeAllocation(candidate, 1, datatype, scopedname), 1))
                    }
                    return Ok(Pair(makeAllocation(free[0], 1, datatype, scopedname), 1))
                }
                for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1u) {
                    if (sequentialFree(candidate, size))
                        return Ok(Pair(makeAllocation(candidate, size, datatype, scopedname), size))
                }
            }
        }

        return Err(ZeropageDepletedError("no more free space in ZP to allocate $size sequential bytes"))
    }

    private fun reserve(range: UIntRange) = free.removeAll(range)

    private fun makeAllocation(address: UInt, size: Int, datatype: DataType, name: String?): UInt {
        require(size>=0)
        free.removeAll(address until address+size.toUInt())
        allocations[address] = (name ?: "<unnamed>") to datatype
        if(name!=null)
            allocatedVariables[name] = (address to size) to datatype
        return address
    }

    private fun oneSeparateByteFree(address: UInt) = address in free && address-1u !in free && address+1u !in free
    private fun sequentialFree(address: UInt, size: Int): Boolean {
        require(size>0)
        return free.containsAll((address until address+size.toUInt()).toList())
    }

    fun allocatedZeropageVariable(scopedname: String): Pair<Pair<UInt, Int>, DataType>? = allocatedVariables[scopedname]

    private val allocatedVariables = mutableMapOf<String, Pair<Pair<UInt, Int>, DataType>>()
}
