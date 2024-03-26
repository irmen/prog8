; test_divmode.p8
%import textio

%zeropage basicsafe
%option no_sysinit


main {
    ubyte c
    ubyte l

    sub start() {
        divmod(99, 10, c, l)
    }
}
