%option no_sysinit
%zeropage basicsafe
%import textio

main {
    sub start() {
        word @shared ww = 2222

        abs(ww)
        sgn(ww)
        sqrt(ww)
        min(ww, 0)
        max(ww, 0)
        clamp(ww, 0, 319)
    }
}
