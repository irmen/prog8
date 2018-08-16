package il65.compiler

import il65.ast.DataType
import il65.ast.LiteralValue
import il65.ast.VarDecl
import il65.ast.VarDeclType


class Zeropage(private val options: CompilationOptions) {

    companion object {
        const val SCRATCH_B1 = 0x02
        const val SCRATCH_B2 = 0x03
        const val SCRATCH_W1 = 0xfb     // $fb/$fc
        const val SCRATCH_W2 = 0xfd     // $fd/$fe
    }

    private val allocations = mutableMapOf<Int, Pair<String, DataType>>()
    val free = mutableListOf<Int>()

    init {
        if(options.zeropage==ZeropageType.FULL || options.zeropage==ZeropageType.FULL_RESTORE) {
            free.addAll(0x04 .. 0xfa)
            free.add(0xff)
            free.removeAll(listOf(0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6))        // these are updated by IRQ
        } else {
            // these are valid for the C-64 (when no RS232 I/O is performed):
            // ($02, $03, $fb-$fc, $fd-$fe are reserved as scratch addresses for various routines)
            free.addAll(listOf(0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa))
        }
        assert(!free.contains(Zeropage.SCRATCH_B1))
        assert(!free.contains(Zeropage.SCRATCH_B2))
        assert(!free.contains(Zeropage.SCRATCH_W1))
        assert(!free.contains(Zeropage.SCRATCH_W2))
    }

    fun available() = free.size

    fun allocate(vardecl: VarDecl) : Int {
        assert(vardecl.name.isEmpty() || !allocations.values.any { it.first==vardecl.name } ) {"same name can't be allocated twice"}
        assert(vardecl.type==VarDeclType.VAR) {"can only allocate VAR type"}

        val size =
            if(vardecl.arrayspec!=null) {
                println("${vardecl.position} warning: allocating a large value in zeropage")
                val y = (vardecl.arrayspec.y as? LiteralValue)?.intvalue
                if(y==null) {
                    // 1 dimensional array
                    when(vardecl.datatype) {
                        DataType.BYTE -> (vardecl.arrayspec.x as LiteralValue).intvalue!!
                        DataType.WORD -> (vardecl.arrayspec.x as LiteralValue).intvalue!! * 2
                        DataType.FLOAT -> (vardecl.arrayspec.x as LiteralValue).intvalue!! *  5
                        else -> throw CompilerException("array can only be of byte, word, float")
                    }
                } else {
                    // 2 dimensional matrix (only bytes for now)
                    when(vardecl.datatype) {
                        DataType.BYTE -> (vardecl.arrayspec.x as LiteralValue).intvalue!! * y
                        else -> throw CompilerException("matrix can only be of byte")
                    }
                }
            } else {
                when (vardecl.datatype) {
                    DataType.BYTE -> 1
                    DataType.WORD -> 2
                    DataType.FLOAT -> {
                        if (options.floats) {
                            println("${vardecl.position} warning: allocating a large value in zeropage")
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
