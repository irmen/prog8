package prog8.code.core

enum class Encoding(val prefix: String) {
    DEFAULT("default"),         // depends on compilation target
    PETSCII("petscii"),         // c64/c128/cx16
    SCREENCODES("sc"),          // c64/c128/cx16
    ATASCII("atascii"),         // atari
    ISO("iso")                  // cx16
}

interface IStringEncoding {
    fun encodeString(str: String, encoding: Encoding): List<UByte>
    fun decodeString(bytes: List<UByte>, encoding: Encoding): String
}
