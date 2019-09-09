package sim65.components

import java.util.*

/**
 * A real-time clock (time of day clock).
 * byte   value
 *  00     year (lsb)
 *  01     year (msb)
 *  02     month, 1-12
 *  03     day, 1-31
 *  04     hour, 0-23
 *  05     minute, 0-59
 *  06     second, 0-59
 *  07     millisecond, 0-999 (lsb)
 *  08     millisecond, 0-999 (msb)
 */
class RealTimeClock(startAddress: Address, endAddress: Address) : MemMappedComponent(startAddress, endAddress) {

    val calendar = Calendar.getInstance()

    init {
        require(endAddress - startAddress + 1 == 9) { "rtc needs exactly 9 memory bytes" }
    }

    override fun clock() {
        /* not updated on clock pulse */
    }

    override fun reset() {
        /* never reset */
    }

    override operator fun get(address: Address): UByte {
        when (address - startAddress) {
            0 -> {
                val year = calendar.get(Calendar.YEAR)
                return (year and 255).toShort()
            }
            1 -> {
                val year = calendar.get(Calendar.YEAR)
                return (year ushr 8).toShort()
            }
            2 -> {
                val month = calendar.get(Calendar.MONTH) + 1
                return month.toShort()
            }
            3 -> {
                val day = calendar.get(Calendar.DAY_OF_MONTH)
                return day.toShort()
            }
            4 -> {
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                return hour.toShort()
            }
            5 -> {
                val minute = calendar.get(Calendar.MINUTE)
                return minute.toShort()
            }
            6 -> {
                val second = calendar.get(Calendar.SECOND)
                return second.toShort()
            }
            7 -> {
                val ms = calendar.get(Calendar.MILLISECOND)
                return (ms and 255).toShort()
            }
            8 -> {
                val ms = calendar.get(Calendar.MILLISECOND)
                return (ms ushr 8).toShort()
            }
            else -> return 0
        }
    }

    override operator fun set(address: Address, data: UByte) {
        /* real time clock can't be set */
    }
}
