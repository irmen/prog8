%import c64utils
%import mathlib

~ main  {

sub start() {

    const ubyte screen_color = 9
    const ubyte border_color = 2
    const ubyte cursor_color = 7

    ubyte ubb
    uword uww
    byte color
    byte color2

    AX=XY
    uww=XY
    AY=uww

    A++
    X++
    ;AY++

    A = ~X
    A = not Y
    ubb = ~ubb
    uww = ~uww
    color2 = ~color
    uww = not uww

    return


}

}
