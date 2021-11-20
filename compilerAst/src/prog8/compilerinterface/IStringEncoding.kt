package prog8.compilerinterface

interface IStringEncoding {
    fun encodeString(str: String, altEncoding: Boolean): List<UByte>
    fun decodeString(bytes: List<UByte>, altEncoding: Boolean): String
}
