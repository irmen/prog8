
package demo

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.CoreMatchers.`is` as Is
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestSource {
    @Test
    fun f() {
        assertThat(2, Is(equalTo(2)))
    }

    @Test
    fun f2() {
        assertThat(2, Is(equalTo(3)))
    }
}
