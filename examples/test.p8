%import textio
%import floats
%import test_stack
%zeropage basicsafe

main {

label:
    sub start() {

        ubyte ub1
        ubyte ub2
        uword uw1
        uword uw2
        float fl1
        float fl2
        ubyte[10] ubarr
        uword[10] uwarr
        float[10] flarr

        swap(ub1, ub2)
        swap(uw1, uw2)
        swap(fl1, fl2)
        swap(ubarr[1], ubarr[2])
        swap(uwarr[1], uwarr[2])
        swap(flarr[1], flarr[2])

        ubyte ix1
        ubyte ix2

        swap(ubarr[ix1], ubarr[ix2])
        swap(uwarr[ix1], uwarr[ix2])
        swap(flarr[ix1], flarr[ix2])
        swap(flarr[ix1], flarr[2])
        swap(flarr[2], flarr[ix2])

        swap(ubarr[ix1], ubarr[ix1+2])
        swap(uwarr[ix1], uwarr[ix1+2])
        swap(flarr[ix1], flarr[ix1+2])

        uword ptr = $c000
        swap(@(ptr+1), @(ptr+2))
        swap(@(ptr+ub1), @(ptr+ub2))

        test_stack.test()
    }
}
