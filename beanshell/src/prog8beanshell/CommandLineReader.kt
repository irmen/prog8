package prog8beanshell

import java.io.FilterReader
import java.io.Reader


class CommandLineReader(val input: Reader): FilterReader(input)  {
    private val normal = 0
    private val lastCharNL = 1
    private val sentSemi = 2
    private var state = lastCharNL

    override fun read(): Int {
        if (state == sentSemi) {
            this.state = lastCharNL
            return 10
        } else {
            var b = input.read()
            while(b==13) b = input.read()

            if (b == 10) {
                if (this.state == lastCharNL) {
                    b = 59
                    this.state = sentSemi
                } else {
                    this.state = lastCharNL
                }
            } else {
                this.state = normal
            }

            return b
        }

    }

    override fun read(buff: CharArray, off: Int, len: Int): Int {
        val b = read()
        if (b == -1) {
            return -1
        } else {
            buff[off] = b.toChar()
            return 1
        }

    }
}

