%zeropage basicsafe


~ main {

    ubyte @zp var1

    sub start() {
        ubyte @zp var1
        A=20
        A=var1
        Y=main.var1
    }

}
