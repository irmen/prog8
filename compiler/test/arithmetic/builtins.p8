%import textio
%import floats
%import string
%import syslib
%import math
%import test_stack
%zeropage basicsafe

main {

    sub start() {
        rotations()
        integers()
        floatingpoint()

        test_stack.test()
    }

    sub rotations() {
        ubyte[] ubarr = [%11000111]
        uword[] uwarr = [%1100111110101010]

        repeat(10) {
            txt.nl()
        }

        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        txt.nl()

        uwarr[0] = %1100111110101010
        ror(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        txt.nl()

        sys.clear_carry()
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        rol2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        rol2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        rol2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        rol2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        txt.nl()

        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        ror2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        ror2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        ror2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        ror2(uwarr[0])
        txt.print_uwbin(uwarr[0], true)
        txt.nl()
        txt.nl()

        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        rol(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        txt.nl()

        sys.set_carry()
        ror(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        sys.set_carry()
        ror(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        txt.nl()


        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        rol2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        rol2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        rol2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        rol2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        txt.nl()

        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        ror2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        ror2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        ror2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        ror2(ubarr[0])
        txt.print_ubbin(ubarr[0], true)
        txt.nl()
        txt.nl()

        &ubyte  membyte = $c000
        uword addr = $c000

        @(addr) = %10110101
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        rol(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        rol(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        rol(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        rol(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        txt.nl()

        @(addr) = %10110101
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        ror(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        ror(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        ror(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        sys.set_carry()
        ror(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        txt.nl()

        @(addr) = %10110101
        txt.print_ubbin(@(addr), true)
        txt.nl()
        rol2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        rol2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        rol2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        rol2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        txt.nl()

        @(addr) = %10110101
        txt.print_ubbin(@(addr), true)
        txt.nl()
        ror2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        ror2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        ror2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        ror2(@(addr))
        txt.print_ubbin(@(addr), true)
        txt.nl()
        txt.nl()

        test_stack.test()

    }

    sub integers() {
        ubyte[]  ubarr = [1,2,3,4,5,0,4,3,2,1, 255, 255, 255]
        byte[]  barr = [1,2,3,4,5,-4,0,-3,2,1, -128, -128, -127]
        uword[]  uwarr = [100,200,300,400,0,500,400,300,200,100]
        word[] warr = [100,200,300,400,500,0,-400,-300,200,100,-99, -4096]

        ubyte zero=0
        ubyte ub
        ubyte ub2
        byte bb
        uword uw
        word ww

        repeat(20) {
            txt.nl()
        }

        ub = sys.read_flags()
        txt.print_ub(ub)
        txt.nl()
        ub = zero+sys.read_flags()*1+zero
        txt.print_ub(ub)
        txt.nl()


        ub = math.rnd()
        txt.print_ub(ub)
        txt.nl()
        ub = zero+math.rnd()*1+zero
        txt.print_ub(ub)
        txt.nl()

        uw = math.rndw()
        txt.print_uw(uw)
        txt.nl()
        uw = zero+math.rndw()*1+zero
        txt.print_uw(uw)
        txt.nl()


        uw = 50000
        ub = sqrt(uw)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+sqrt(uw)*1+zero
        txt.print_ub(ub)
        txt.nl()

        bb = -100
        bb = sgn(bb)
        txt.print_b(bb)
        txt.nl()
        bb = -100
        bb = zero+sgn(bb)*1+zero
        txt.print_b(bb)
        txt.nl()

        ub = 100
        bb = sgn(ub)
        txt.print_b(bb)
        txt.nl()
        ub = 100
        bb = zero+sgn(ub)*1+zero
        txt.print_b(bb)
        txt.nl()

        ww = -1000
        bb = sgn(ww)
        txt.print_b(bb)
        txt.nl()
        bb = zero+sgn(ww)*1+zero
        txt.print_b(bb)
        txt.nl()

        uw = 1000
        bb = sgn(uw)
        txt.print_b(bb)
        txt.nl()
        bb = zero+sgn(uw)*1+zero
        txt.print_b(bb)
        txt.nl()

        bb = -100
        bb = abs(bb) as byte
        txt.print_b(bb)
        txt.nl()
        bb = -100
        bb = zero+(abs(bb) as byte)*1+zero
        txt.print_b(bb)
        txt.nl()

        ww = -1000
        ww = abs(ww) as word
        txt.print_w(ww)
        txt.nl()
        ww = -1000
        ww = zero+(abs(ww) as word)*1+zero
        txt.print_w(ww)
        txt.nl()
        
        ub = any(ubarr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+any(ubarr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = any(barr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+any(barr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = any(uwarr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+any(uwarr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = any(warr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+any(warr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = all(ubarr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+all(ubarr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = all(barr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+all(barr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = all(uwarr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+all(uwarr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        ub = all(warr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+all(warr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        sort(ubarr)
        sort(barr)
        sort(uwarr)
        sort(warr)
        reverse(ubarr)
        reverse(barr)
        reverse(uwarr)
        reverse(warr)

        test_stack.test()
    }

    sub floatingpoint() {
        ubyte[]  barr = [1,2,3,4,5,0,4,3,2,1]
        float[] flarr = [1.1, 2.2, 3.3, 0.0, -9.9, 5.5, 4.4]

        ubyte zero=0
        ubyte ub
        byte bb
        uword uw
        float fl
        float fzero=0.0

        fl = -9.9
        bb = sgn(fl)
        txt.print_b(bb)
        txt.nl()
        fl = -9.9
        bb = zero+sgn(fl)*1+zero
        txt.print_b(bb)
        txt.nl()

        ub = any(flarr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+any(flarr)*1+zero
        txt.print_ub(ub)
        txt.nl()
        ub = all(flarr)
        txt.print_ub(ub)
        txt.nl()
        ub = zero+all(flarr)*1+zero
        txt.print_ub(ub)
        txt.nl()

        reverse(flarr)
        for ub in 0 to len(flarr)-1 {
            floats.print(flarr[ub])
            txt.chrout(',')
        }
        txt.nl()

        test_stack.test()
    }
}
