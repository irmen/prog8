%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        uword target = 4444
        @($d020) = A
        @($d020) = A+4
        @(target) = A+4
        @(target+4) = A+4

    }
}
