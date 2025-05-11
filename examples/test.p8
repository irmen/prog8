%import textio
%zeropage basicsafe

main {
    sub start() {

        when cx16.r0L {
            1 -> goto thing.func1
            2 -> goto thing.func2
            3 -> goto thing.func3
            else -> cx16.r0++
        }
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
