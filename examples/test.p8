; scoping bug:

main {
    sub start() {
        thing.routine()
    }
}

thing {
    %option no_symbol_prefixing

    sub routine() {
        other.something()
        other.counter++
    }
}

other {
    sub something() {
    }

    uword @shared counter
}
