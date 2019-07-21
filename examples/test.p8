%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        if_z goto start
        if_pos goto start
        if_cc goto start
        if_nz goto start

    }

}
