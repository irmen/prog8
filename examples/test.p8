%import textio
%import strings
%option no_sysinit ; leave the CX16 defaults in place
%zeropage basicsafe ; don't step on BASIC zero page locations
; %import textio


main {
    sub start() {
        uword[] buffers = [
            memory("buffer1", 100, 0),
            memory("buffer2", 100, 0),
            memory("buffer3", 100, 0),
        ]

        void strings.copy("apple", buffers[0])
        void strings.copy("banana", buffers[1])
        void strings.copy("citron", buffers[2])

        txt.print(buffers[0])
        txt.nl()
        txt.print(buffers[1])
        txt.nl()
        txt.print(buffers[2])
        txt.nl()
    }
}
