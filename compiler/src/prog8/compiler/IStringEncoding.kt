package prog8.compiler

interface IStringEncoding {
    fun encodeString(str: String, altEncoding: Boolean): List<Short>
    fun decodeString(bytes: List<Short>, altEncoding: Boolean): String
}