package sim65.components

import sim65.Petscii

abstract class BusComponent {
    lateinit var bus: Bus

    abstract fun clock()
    abstract fun reset()
}

abstract class MemMappedComponent(val startAddress: Address, val endAddress: Address): BusComponent() {
    abstract operator fun get(address: Address): UByte
    abstract operator fun set(address: Address, data: UByte)
    abstract fun cloneMem(): Array<UByte>

    init {
        require(endAddress>=startAddress)
        require(startAddress>=0 && endAddress <= 0xffff) { "can only have 16-bit address space" }
    }

    fun dump(from: Address, to: Address) {
        (from .. to).chunked(16).forEach {
            print("\$${it.first().toString(16).padStart(4, '0')}  ")
            val bytes = it.map { address -> get(address) }
            bytes.forEach { byte ->
                print(byte.toString(16).padStart(2, '0') + " ")
            }
            print("  ")
            print(Petscii.decodeScreencode(bytes, false).replace('\ufffe', '.'))
            println()
        }
    }

}
