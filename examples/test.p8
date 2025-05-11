%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        cx16.r13L = 1
        cx16.r12L = 0

        on math.randrange(5) call (
            thing.func1,
            thing.func2,
            thing.func3)
        else {
            txt.print("not jumped\n")
        }

        on math.randrange(5) goto (thing.func1, thing.func2, thing.func3)
    }
}

thing {
    sub func1() {
        cx16.r10++
        txt.print("one\n")
    }
    sub func2() {
        cx16.r10++
        txt.print("two\n")
    }
    sub func3() {
        cx16.r10++
        txt.print("three\n")
    }
    sub other() {
        cx16.r10++
        txt.print("other\n")
    }

}
