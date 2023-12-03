main {
    sub start() {
        const ubyte HEIGHT=240
        uword large = 320*240/8/8
        thing(large)
        thing(320*240/8/8)
        thing(320*HEIGHT/8/8)
        thing(320*HEIGHT)        ; overflow
    }

    sub thing(uword value) {
        value++
    }
}
