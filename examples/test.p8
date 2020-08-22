%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    ; todo make it possible to use cpu opcodes as varnames such as 'nop' by prefixing all asm vars with something such as '_'

    sub start() {

        byte nop2

        nop2=4
        nop2++
        foo.xxx()

    derp:
        goto main.nop2
        main.nop2()
    }

    sub nop2 () {
        c64.CHROUT('\n')
    }
}

foo {

    sub xxx() {

    bar:
        goto bar
    }


}
