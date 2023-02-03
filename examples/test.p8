main {


    sub start() {
        cx16.r0 = memory("slab", $c000, 0)
        cx16.r1 = memory("slab", $c000, 0)
        testscope.duplicate()
        cx16.r0L = testscope.duplicate2()
    }
}

testscope {

    sub sub1() {
        ubyte @shared duplicate
        ubyte @shared duplicate2
    }

    sub duplicate() {
        ; do nothing
    }

    sub duplicate2() -> ubyte {
        return cx16.r0L
    }
}
