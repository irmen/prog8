main {
    sub start() {
        void derp("a")
    }

    sub derp(str arg) -> ubyte {
        if arg==0
            return 42
        if arg==4444
            return 42
        if arg!=0
            return 42
        if arg!=4444
            return 42
        return 1
    }
}
