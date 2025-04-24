main {
    ubyte @shared banknumber
    extsub @bank 10  $C04B = otherbank() clobbers(A,X,Y)
    extsub @bank banknumber  $C04B = otherbankvar() clobbers(A,X,Y)

    sub start() {
        otherbank()     ; TODO fix IR support... add JSRFAR instruction? With const bank and variable bank number
        otherbankvar()  ; TODO fix IR support... add JSRFAR instruction? With const bank and variable bank number
    }
}
