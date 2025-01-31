package prog8.code.core

enum class Encoding(val prefix: String) {
    DEFAULT("default"),         // depends on compilation target
    PETSCII("petscii"),         // c64/c128/cx16
    SCREENCODES("sc"),          // c64/c128/cx16
    ATASCII("atascii"),         // atari
    ISO("iso"),                 // cx16  (iso-8859-15)
    ISO5("iso5"),               // cx16  (iso-8859-5, cyrillic)
    ISO16("iso16"),             // cx16  (iso-8859-16, eastern european)
    CP437("cp437"),             // cx16  (ibm pc, codepage 437)
    KATAKANA("kata"),           // cx16  (katakana)
    C64OS("c64os")              // c64 (C64 OS)
}

interface IStringEncoding {
    val defaultEncoding: Encoding

    fun encodeString(str: String, encoding: Encoding): List<UByte>
    fun decodeString(bytes: Iterable<UByte>, encoding: Encoding): String
}
