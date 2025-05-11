%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.r9L = 2

        if cx16.r9L < len(jumplist)
            goto jumplist[cx16.r9L]

        uword[] @nosplit jumplist = [thing.func1, thing.func2, thing.func3]
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
