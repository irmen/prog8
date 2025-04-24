main {

    sub start()  {
        uword[] texts1 = [ 1,2,3 ]
        uword[] @nosplit texts2 = [ 1,2,3 ]

        cx16.r4 = texts1
        cx16.r5 = texts2
    }
}
