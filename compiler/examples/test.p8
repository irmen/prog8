%option enable_floats


~ main {

sub start() {

    byte  bvar = -88
    ubyte ubvar = 222
    word wvar = -12345
    uword uwvar = 55555


    byte bv2
    ubyte ubv2
    word wv2
    uword uwv2


    bv2 = ub2b(ubvar)
    ubv2 = b2ub(bvar)
    wv2 = wrd(uwvar)
    uwv2 = uwrd(wvar)
    wv2 = wrd(ubvar)
    wv2 = wrd(bvar)
    uwv2 = uwrd(ubvar)
    uwv2 = uwrd(bvar)

    return

}

}


~ block2 $c000 {

    str derp="hello"
    byte v =44
    return
}
