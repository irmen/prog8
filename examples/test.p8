%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        byte[]  array=[1,2,3,4,5]
        word[5] warray
        float[5] flarray

        warray[0]=flarray[0] as word

        ubyte length = len(array)
        c64scr.print_ub(length)
        c64.CHROUT(',')
        ubyte length1 = any(array)
        c64scr.print_ub(length1)
        c64.CHROUT(',')
        ubyte length1b = all(array)
        c64scr.print_ub(length1b)
        c64.CHROUT(',')
        ubyte length1c = max(array)
        c64scr.print_ub(length1c)
        c64.CHROUT(',')
        ubyte length1d = min(array)
        c64scr.print_ub(length1d)
        c64.CHROUT(',')
        ubyte xlength = len([1,2,3])
        c64scr.print_ub(xlength)
        c64.CHROUT('\n')
        ubyte xlength1 = any([1,0,3])
        c64scr.print_ub(xlength1)
        c64.CHROUT(',')
        ubyte xlength1b = all([1,0,3])
        c64scr.print_ub(xlength1b)
        c64.CHROUT(',')
        ubyte xlength1c = max([1,2,3])
        c64scr.print_ub(xlength1c)
        c64.CHROUT(',')
        ubyte xlength1d = min([1,2,3])
        c64scr.print_ub(xlength1d)
        c64.CHROUT('\n')

        word s1 = sum(array)
        c64scr.print_w(s1)
        c64.CHROUT(',')

        uword s2 = sum([1,23])
        c64scr.print_uw(s2)
        c64.CHROUT(',')

        float ff1=avg(array)
        c64flt.print_f(ff1)
        c64.CHROUT(',')
        float ff2=avg([1,2,3])

        c64flt.print_f(ff2)
        c64.CHROUT('\n')
        return
    }

}
