%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start () {

        uword scanline_data_ptr= $6000
        uword pixptr = xx/8 + scanline_data_ptr      ; TODO why is this code so much larger than the following line:
        uword pixptr2 = scanline_data_ptr + xx/8

        test_stack.test()
    }
}
