%import c64utils

~ main {

    word[1] rotatedx

    sub start()  {
        word xc = 2
rpt:
        word w = 2*xc
        rotatedx[0] = w       ; @ok!
        rotatedx[0] = 2*xc       ; @todo wrong code generated? crash!
        c64.CHROUT('.')
        goto rpt
    }
}
