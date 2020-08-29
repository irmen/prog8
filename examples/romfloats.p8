%import c64flt
%zeropage basicsafe

main {

    sub start() {
        print_rom_floats_values()
       	c64.CHROUT('\n')
        print_rom_floats()
       	c64.CHROUT('\n')
    }

    sub print_rom_floats() {
        c64flt.FL_PIVAL=9.9999
    	c64flt.print_f(c64flt.FL_PIVAL)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_N32768)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_FONE)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_SQRHLF)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_SQRTWO)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_NEGHLF)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_LOG2)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_TENC)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_NZMIL)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_FHALF)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_LOGEB2)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_PIHALF)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_TWOPI)
    	c64.CHROUT('\n')
    	c64flt.print_f(c64flt.FL_FR4)
    	c64.CHROUT('\n')
    }

    sub print_rom_floats_values() {
        ; these are all floating point constants defined in the ROM so no allocation required
        ; the compiler recognises these and will substitute the ROM values automatically

        c64flt.print_f(3.141592653589793)
        c64.CHROUT('\n')

        c64flt.print_f(-32768.0)
        c64.CHROUT('\n')

        c64flt.print_f( 1.0)
        c64.CHROUT('\n')

        c64flt.print_f(0.7071067811865476)
        c64.CHROUT('\n')

        c64flt.print_f(1.4142135623730951)
        c64.CHROUT('\n')

        c64flt.print_f( -0.5)
        c64.CHROUT('\n')

        c64flt.print_f(0.6931471805599453)
        c64.CHROUT('\n')

        c64flt.print_f(10.0)
        c64.CHROUT('\n')

        c64flt.print_f(1.0e9)
        c64.CHROUT('\n')

        c64flt.print_f(0.5)
        c64.CHROUT('\n')

        c64flt.print_f(1.4426950408889634)
        c64.CHROUT('\n')

        c64flt.print_f(1.5707963267948966)
        c64.CHROUT('\n')

        c64flt.print_f(6.283185307179586)
        c64.CHROUT('\n')

        c64flt.print_f(0.25)
        c64.CHROUT('\n')

        c64flt.print_f(0.0)
        c64.CHROUT('\n')
    }
}
