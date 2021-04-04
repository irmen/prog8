%zeropage basicsafe

main {

    sub start() {

        ubyte[6] array = [ 1,2,3,
; Comment here
                           4,5,6 ]

        ubyte zz = len(array)

        ubyte foobar
        empty2()

        %asm {{
            lda  foobar
        }}
    }

    sub nix() {
    }

    sub empty2() {
    }
}

derp {
    sub nix2() {
        ubyte zz
        zz++
    }
}
