main {
    struct element {
        word  y
    }

    sub start() {
        ^^element zp_element = 20000
        zp_element.y = cx16.r0L as byte
    }
}
