%import textio
%output library
%address $6000

main {
    ; Create a jump table as first thing in the library.
    ; NOTE: the compiler has inserted a single JMP instruction at the start
    ; of the 'main' block, that jumps to the start() routine.
    ; This is convenient because the rest of the jump table simply follows it,
    ; making the first jump neatly be the required initialization routine
    ; for the library (initializing variables and BSS region).
    ; Think of it as the implicit first entry of the jump table.
    %jmptable (
        examplelib.message
    )

    sub start() {
        ; has to remain here for initialization
    }
}

examplelib {
    sub message() {
        txt.print("hello from library!\n")
    }
}
