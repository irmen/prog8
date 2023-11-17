%import textio

main {
    sub start() {
        str key = "test"
        txt.print(":")
        if key != ":" {
            cx16.r0++
        }
    }
}
