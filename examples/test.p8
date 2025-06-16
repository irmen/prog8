%import textio
%import strings

; Animal guessing game where the computer gets smarter every time.
; Note: this program can be compiled for multiple target systems.

main {

    str userinput = "x"*80

    sub start() {
        db.init()
        txt.print_uw(db.first)
        txt.nl()
        cx16.r0 = db.first

        cx16.r1 = db.first.negative
        cx16.r0 = db.first.negative.animal
        txt.print_uw(db.first.negative)
        txt.nl()
        txt.print(db.first.negative.animal)
        txt.nl()
        txt.print(db.first.positive.animal)
        txt.nl()
    }
}

db {
    struct Node {
        str question
        str animal
        ^^Node negative
        ^^Node positive
    }

    ^^Node first

    sub init() {
        first = Node("does it swim", 0, 0, 0)
        ^^Node eagle = Node(0, "eagle", 0, 0)
        ^^Node dolpin = Node(0, "dolpin", 0, 0)
        first.negative = eagle
        first.positive = dolpin
    }
}

arena {
    ; extremely trivial arena allocator (that never frees)
    uword buffer = memory("arena", 10000, 0)
    uword next = buffer

    sub alloc(ubyte size) -> uword {
        defer next += size
        return next
    }
}
