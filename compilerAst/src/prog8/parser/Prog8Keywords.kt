package prog8.parser

/**
 * Provides the complete set of Prog8 reserved words, derived from the
 * ANTLR grammar vocabulary so it never goes out of sync with the language.
 */
object Prog8Keywords {
    val reservedWords: Set<String> by lazy {
        val words = sortedSetOf<String>()
        val vocab = Prog8ANTLRParser.VOCABULARY
        for (i in 0..vocab.maxTokenType) {
            val literal = vocab.getLiteralName(i) ?: continue
            // literal is like "'for'" or "'^^'"; keep only word-like ones longer than 1 char
            if (literal.length >= 2 && literal.startsWith("'") && literal.endsWith("'")) {
                val word = literal.substring(1, literal.length - 1)
                if (word.length > 1 && word.matches(Regex("[a-z][a-z_0-9]*"))) {
                    words.add(word)
                }
            }
        }
        words
    }
}
