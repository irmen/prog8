%option enable_floats

main {
    struct Node {
        ubyte id
        str name
        uword array
        bool flag
        float perc
    }
    struct Foobar {
        bool thing
    }

    sub start() {
        ^^Node[] @shared nodeswithtype = [
            ^^Node: [1,"one", 1000, true, 1.111],
            ^^Node: [],
            ^^Foobar: []        ; TODO fix so that type error
        ]

        ^^Node derp = ^^Foobar: []      ; TODO fix so that type error
        ^^Node derp2 = ^^Node: []

        ^^ubyte bptr = derp2 as ^^ubyte
        bptr = derp as ^^ubyte

        ^^Node[] @shared nodeswithout = [
            [2,"two", 2000, false, 2.222],
            [1,2,3,true,5],
            []
        ]

        ^^Node @shared nptrwithtype = ^^Node : [1, "one", 1000, false, 3.333]
        ^^Node @shared nptrwithouttype = [1, "one", 1000, false, 3.333]
    }
}
