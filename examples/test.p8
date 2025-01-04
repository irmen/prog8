%import textio
%option no_sysinit
%zeropage dontuse

main {
    sub start() {
        uword @nozp pointer
        pointer++

        cx16.r0L = @(pointer)
        cx16.r1L = @(pointer+100)
        cx16.r1 = peekw(pointer)
        cx16.r2 = peekw(pointer+100)
        cx16.r3L = peek(pointer+100)
        @(pointer) = 99
        @(pointer+100) = 99
        poke(pointer, 99)
        pokew(pointer, 99)
    }
}
