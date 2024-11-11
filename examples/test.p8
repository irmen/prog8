%import floats
%option no_sysinit
%zeropage basicsafe

main {

    extsub $8000 = routine(float xx @FAC1, float yy @FAC2)

    sub start() {
        @($8000) = $60
        routine(1.234, 2.3445)
    }
}
