package prog8.intermediate

/**
 * Print a dump of all variables in the IR program.
 * Target-independent parts (static variables, memory mapped variables, memory slabs) are
 * always shown; zero-page placement is only shown when a [zpAddress] resolver is supplied
 * (zero-page allocation is backend/target specific and doesn't exist for all targets).
 */
fun dumpVariables(program: IRProgram, zpAddress: (String) -> UInt? = { null }) {
    println("---- VARIABLES DUMP ----")
    val st = program.st

    val zpVars = st.allVariables().filter { zpAddress(it.name) != null }.sortedBy { it.name }
    if (zpVars.toList().isNotEmpty()) {
        println("ZeroPage:")
        zpVars.sortedBy { zpAddress(it.name) }.forEach {
            println("  $${zpAddress(it.name)!!.toString(16).padStart(2, '0')}\t${it.dt}\t${it.name}")
        }
        st.allMemMappedVariables().filter { it.address <= 255u }.sortedBy { it.address }.forEach {
            println("  $${it.address.toString(16).padStart(2, '0')}\t${it.dt}\t${it.name}")
        }
    }

    val plain = st.allVariables().filter { zpAddress(it.name) == null }.sortedBy { it.name }
    if (plain.toList().isNotEmpty()) {
        println("Static variables (not in ZeroPage):")
        plain.forEach { println("  ${it.dt}\t${it.name}\t") }
    }

    val memmap = st.allMemMappedVariables().sortedWith(compareBy({ it.address }, { it.name }))
    if (memmap.toList().isNotEmpty()) {
        println("Memory mapped:")
        memmap.forEach { println("  $${it.address.toString(16).padStart(4, '0')}\t${it.dt}\t${it.name}") }
    }

    val slabs = st.allMemorySlabs().sortedBy { it.name }
    if (slabs.toList().isNotEmpty()) {
        println("Memory slabs:")
        slabs.forEach { println("  ${it.name}  ${it.size}  align ${it.align}") }
    }

    println("---- VARIABLES DUMP END ----")
}
