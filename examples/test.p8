main {
    sub start() {
    }
}

xyz {
    uword buffer_ptr = memory("buffers_stack", 8192, 0)

    sub pop() -> ubyte {
        return buffer_ptr[2]
    }
}
