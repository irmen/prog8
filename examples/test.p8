%import textio
%zeropage basicsafe

main {
    sub start() {
        ^^Mblock mb = 4000

        uword @shared temp
        temp = mb
        temp += 64

        cx16.r0 = mb
        temp = cx16.r0
        temp += 64
    }

    struct Mblock {
        uword size
        bool free
    }
}
