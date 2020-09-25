package prog8.compiler

import prog8.ast.base.*


class ZeropageDepletedError(message: String) : Exception(message)


abstract class Zeropage(protected val options: CompilationOptions) {

    abstract val SCRATCH_B1 : Int      // temp storage for a single byte
    abstract val SCRATCH_REG : Int     // temp storage for a register
    abstract val SCRATCH_W1 : Int      // temp storage 1 for a word  $fb+$fc
    abstract val SCRATCH_W2 : Int      // temp storage 2 for a word  $fb+$fc


    private val allocations = mutableMapOf<Int, Pair<String, DataType>>()
    val free = mutableListOf<Int>()     // subclasses must set this to the appropriate free locations.

    val allowedDatatypes = NumericDatatypes

    fun available() = if(options.zeropage==ZeropageType.DONTUSE) 0 else free.size

    fun allocate(scopedname: String, datatype: DataType, position: Position?, errors: ErrorReporter): Int {
        assert(scopedname.isEmpty() || !allocations.values.any { it.first==scopedname } ) {"scopedname can't be allocated twice"}

        if(options.zeropage==ZeropageType.DONTUSE)
            throw CompilerException("zero page usage has been disabled")

        val size =
                when (datatype) {
                    in ByteDatatypes -> 1
                    in WordDatatypes -> 2
                    DataType.FLOAT -> {
                        if (options.floats) {
                            if(position!=null)
                                errors.warn("allocated a large value (float) in zeropage", position)
                            else
                                errors.warn("$scopedname: allocated a large value (float) in zeropage", position ?: Position.DUMMY)
                            5
                        } else throw CompilerException("floating point option not enabled")
                    }
                    else -> throw CompilerException("cannot put datatype $datatype in zeropage")
                }

        if(free.size > 0) {
            if(size==1) {
                for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1) {
                    if(loneByte(candidate))
                        return makeAllocation(candidate, 1, datatype, scopedname)
                }
                return makeAllocation(free[0], 1, datatype, scopedname)
            }
            for(candidate in free.minOrNull()!! .. free.maxOrNull()!!+1) {
                if (sequentialFree(candidate, size))
                    return makeAllocation(candidate, size, datatype, scopedname)
            }
        }

        throw ZeropageDepletedError("ERROR: no free space in ZP to allocate $size sequential bytes")
    }

    protected fun reserve(range: IntRange) = free.removeAll(range)

    private fun makeAllocation(address: Int, size: Int, datatype: DataType, name: String?): Int {
        free.removeAll(address until address+size)
        allocations[address] = Pair(name ?: "<unnamed>", datatype)
        return address
    }

    private fun loneByte(address: Int) = address in free && address-1 !in free && address+1 !in free
    private fun sequentialFree(address: Int, size: Int) = free.containsAll((address until address+size).toList())
}
