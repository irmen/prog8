
main {
    sub start() {
        other.bptr^^ = true
        ; other.bptr = 2222
        ;cx16.r0bL = other.bptr^^
    }
}

other {
    ^^bool bptr
}
