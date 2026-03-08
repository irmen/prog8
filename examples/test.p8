%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        txt.print_ub(other.Priority::HIGH)
    }
}

other {
    enum Priority {
        LOW = 1,
        NORMAL,
        HIGH,
        EXTREME=255
    }
}
