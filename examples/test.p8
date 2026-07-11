; TODO fix struct initialization / assignment bugs on m68k


%import textio

main {

    sub start() {
        db.init()
        ^^db.Node active = db.first

        repeat 3 {
            txt.print("a.q=")
            txt.print_ulhex(active.question as long, true)
            txt.nl()
            if active.question!=0 {
                txt.print("Q:")
                txt.print(active.question)
                txt.nl()
            active = active.negative
            } else {
                txt.print("A:")
                txt.print(active.animal)
                sys.exit(1)
            }
        }
        sys.exit(1)
    }
}

db {
    ; knowledge about animals is stored in a tree that can grow with new animals.
    ; questions have y/n answers that point to possible animals.

    struct Node {
        str question
        str animal
        ^^Node negative
        ^^Node positive
    }

    ^^Node first

    sub debug(str name, ^^Node node) {
        txt.print(name)
        txt.chrout('@')
        txt.print_ulhex(node as long, true)
        txt.print(" :: ")
        txt.print_ulhex(node.question as long, true)
        txt.spc()
        txt.print_ulhex(node.animal as long, true)
        txt.spc()
        txt.print_ulhex(node.negative as long, true)
        txt.spc()
        txt.print_ulhex(node.positive as long, true)
        txt.nl()
    }

    sub init() {
        first = ^^Node: ["does it swim", 0, 0, 0]
        ^^Node question = ^^Node: ["can it fly", 0, 0, 0]
        first.negative = question
        first.positive = ^^Node: [0, "dolphin", 0, 0]
        question.negative = ^^Node: [0, "horse", 0, 0]
        question.positive = ^^Node: [0, "eagle", 0, 0]

        debug("first", first)
        debug("first.positive", first.positive)
        debug("first.negative", first.negative)
        debug("question", question)
        debug("question.positive", question.positive)
        debug("question.negative", question.negative)
    }
}
