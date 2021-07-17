package prog8.ast

interface IStringEncoding {
    fun encodeString(str: String, altEncoding: Boolean): List<Short>
    fun decodeString(bytes: List<Short>, altEncoding: Boolean): String
}