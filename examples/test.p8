%zeropage basicsafe

main {
    sub start() {
        cx16.r0=0
        void routine22(1,2,3,4,5)
    }

    sub routine22(ubyte aa1, ubyte aa2, ubyte aa3, ubyte aa4, ubyte aa5) -> ubyte {
        return aa1+aa2+aa3+aa4+aa5
    }
}
