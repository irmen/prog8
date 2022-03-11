package prog8.code.target

import prog8.code.core.*
import prog8.code.target.atari.AtariMachineDefinition


class AtariTarget: ICompilationTarget, IStringEncoding by Encoder, IMemSizer {
    override val name = NAME
    override val machine = AtariMachineDefinition()
    override val supportedEncodings = setOf(Encoding.ATASCII)
    override val defaultEncoding = Encoding.ATASCII

    companion object {
        const val NAME = "atari"
    }

    override fun memorySize(dt: DataType): Int {
        return when(dt) {
            in ByteDatatypes -> 1
            in WordDatatypes, in PassByReferenceDatatypes -> 2
            DataType.FLOAT -> 6
            else -> Int.MIN_VALUE
        }
    }
}
