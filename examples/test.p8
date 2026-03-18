main {
    sub start() {
        alias s1 = testblock.sub1
        alias s2 = s1.sub2
        alias vx = s2.var2
        vx = 99

        alias tb = testblock
        alias vv = tb.variable
        vv = 99
    }
}


testblock {
    ubyte @shared variable

    sub sub1() {
        sub sub2() {
            ubyte @shared var2
        }
    }
}
