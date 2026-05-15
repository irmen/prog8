package prog8tests.helpers

import io.kotest.assertions.fail

/**
 * Asserts that the string contains the given [substrings] in the specified order.
 * This is much faster than using a large multi-line Regex.
 */
fun String.shouldContainInOrder(vararg substrings: String) {
    var currentSearchIndex = 0
    for (substring in substrings) {
        val index = this.indexOf(substring, currentSearchIndex)
        if (index == -1) {
            fail("Could not find '$substring' after index $currentSearchIndex")
        }
        currentSearchIndex = index + substring.length
    }
}
