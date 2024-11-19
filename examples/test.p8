%import textio

main {
    sub start() {
        myblock.printit()
        myblock2.printit2()
    }
}

myblock {
    sub printit() {
        txt.print_uwhex(&printit, true)
        txt.nl()
        txt.print_uwhex(&myblock, true)
        txt.nl()
    }
}

myblock2 {
    sub printit2() {
        txt.print_uwhex(&printit2, true)
        txt.nl()
        txt.print_uwhex(&myblock2, true)
        txt.nl()
    }
}
