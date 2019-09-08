package sim65.components

class Timer(startAddress: Address, endAddress: Address): MemMappedComponent(startAddress, endAddress) {
    private var counter: Long = 0

    init {
        require(endAddress - startAddress + 1 == 4) { "timer needs exactly 4 memory bytes" }
    }

    override fun clock() {
        counter++
        if (counter > 0xffffffff)
            counter = 0
        println("TIMER CLOCK $counter")
    }

    override fun reset() {
        counter = 0
    }

    override operator fun get(address: Address): UByte {
        TODO("timer read $address")
    }

    override operator fun set(address: Address, data: UByte) {
        TODO("timer write $address = $data")
    }
}
