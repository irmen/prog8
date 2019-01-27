package prog8.compiler

import prog8.ast.*


class ZeropageDepletedError(message: String) : Exception(message)


abstract class Zeropage(private val options: CompilationOptions) {

    private val allocations = mutableMapOf<Int, Pair<String, DataType>>()
    val free = mutableListOf<Int>()     // subclasses must set this to the appropriate free locations.

    fun available() = free.size

    fun allocate(name: String, type: DataType) =
        allocate(VarDecl(VarDeclType.VAR, type, true, null, name, null, Position("",0,0,0)))

    fun allocate(vardecl: VarDecl) : Int {
        assert(vardecl.name.isEmpty() || !allocations.values.any { it.first==vardecl.name } ) {"same name can't be allocated twice"}
        assert(vardecl.type== VarDeclType.VAR) {"can only allocate VAR type"}

        val size =
            if(vardecl.arrayspec!=null) {
                printWarning("allocating a large value (arrayspec) in zeropage", vardecl.position)
                when(vardecl.datatype) {
                    DataType.UBYTE, DataType.BYTE -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!!
                    DataType.UWORD, DataType.UWORD -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!! * 2
                    DataType.FLOAT -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!! *  5
                    else -> throw CompilerException("array can only be of byte, word, float")
                }
            } else {
                when (vardecl.datatype) {
                    DataType.UBYTE, DataType.BYTE -> 1
                    DataType.UWORD, DataType.WORD -> 2
                    DataType.FLOAT -> {
                        if (options.floats) {
                            printWarning("allocating a large value (float) in zeropage", vardecl.position)
                            5
                        } else throw CompilerException("floating point option not enabled")
                    }
                    else -> throw CompilerException("cannot put datatype ${vardecl.datatype} in zeropage")
                }
            }

        if(free.size > 0) {
            if(size==1) {
                for(candidate in free.min()!! .. free.max()!!+1) {
                    if(loneByte(candidate))
                        return makeAllocation(candidate, 1, vardecl.datatype, vardecl.name)
                }
                return makeAllocation(free[0], 1, vardecl.datatype, vardecl.name)
            }
            for(candidate in free.min()!! .. free.max()!!+1) {
                if (sequentialFree(candidate, size))
                    return makeAllocation(candidate, size, vardecl.datatype, vardecl.name)
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
