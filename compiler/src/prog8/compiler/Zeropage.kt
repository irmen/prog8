package prog8.compiler

import prog8.ast.*


abstract class Zeropage(protected val options: CompilationOptions) {

    private val allocations = mutableMapOf<Int, Pair<String, DataType>>()
    val free = mutableListOf<Int>()     // subclasses must set this to the appropriate free locations.

    fun available() = free.size

    fun allocate(vardecl: VarDecl) : Int {
        assert(vardecl.name.isEmpty() || !allocations.values.any { it.first==vardecl.name } ) {"same name can't be allocated twice"}
        assert(vardecl.type== VarDeclType.VAR) {"can only allocate VAR type"}

        val size =
            if(vardecl.arrayspec!=null) {
                printWarning("allocating a large value (array) in zeropage", vardecl.position)
                val y = (vardecl.arrayspec.y as? LiteralValue)?.asIntegerValue
                if(y==null) {
                    // 1 dimensional array
                    when(vardecl.datatype) {
                        DataType.BYTE -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!!
                        DataType.WORD -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!! * 2
                        DataType.FLOAT -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!! *  5
                        else -> throw CompilerException("array can only be of byte, word, float")
                    }
                } else {
                    // 2 dimensional matrix (only bytes for now)
                    when(vardecl.datatype) {
                        DataType.BYTE -> (vardecl.arrayspec.x as LiteralValue).asIntegerValue!! * y
                        else -> throw CompilerException("matrix can only contain bytes")
                    }
                }
            } else {
                when (vardecl.datatype) {
                    DataType.BYTE -> 1
                    DataType.WORD -> 2
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

        throw CompilerException("ERROR: no free space in ZP to allocate $size sequential bytes")
    }

    private fun makeAllocation(location: Int, size: Int, datatype: DataType, name: String?): Int {
        free.removeAll(location until location+size)
        allocations[location] = Pair(name ?: "<unnamed>", datatype)
        return location
    }

    private fun loneByte(location: Int): Boolean {
        return free.contains(location) && !free.contains(location-1) && !free.contains(location+1)
    }

    private fun sequentialFree(location: Int, size: Int): Boolean {
        return free.containsAll((location until location+size).toList())
    }

}
