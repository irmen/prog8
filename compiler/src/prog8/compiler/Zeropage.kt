package prog8.compiler

import prog8.ast.DataType
import prog8.ast.LiteralValue
import prog8.ast.VarDecl
import prog8.ast.VarDeclType


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
        if(options.zeropage==ZeropageType.FULL) {
            free.addAll(0x04 .. 0xfa)
            free.add(0xff)
            free.removeAll(listOf(0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6))        // these are updated by IRQ
        } else {
            if(options.zeropage==ZeropageType.KERNALSAFE) {
                // add the Zp addresses that are just used by BASIC routines to the free list
                free.addAll(listOf(0x09, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f, 0x10, 0x11,
                        0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, 0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, 0x21,
                        0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46,
                        0x47, 0x48, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x53, 0x6f, 0x70))
            }
            // add the Zp addresses not even used by BASIC
            // these are valid for the C-64 (when no RS232 I/O is performed):
            // ($02, $03, $fb-$fc, $fd-$fe are reserved as scratch addresses for various routines)
            // KNOWN WORKING FREE: 0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa))
            free.addAll(listOf(0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0d, 0x0e,
                    0x12, 0x2a, 0x52, 0x94, 0x95, 0xa7, 0xa8, 0xa9, 0xaa,
                    0xb5, 0xb6, 0xf7, 0xf8, 0xf9, 0xfa))
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
                println("${vardecl.position} warning: allocating a large value (array) in zeropage")
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
                            println("${vardecl.position} warning: allocating a large value (float) in zeropage")
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
