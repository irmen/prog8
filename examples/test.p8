%import textio
%import math
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        uword sprite_reg
        cx16.r0 = sprite_reg+1
        cx16.vpoke(1, sprite_reg+1, 42)
    }
}
