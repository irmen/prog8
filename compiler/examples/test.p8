%import c64utils

~ main $c800 {


sub start() {

    const ubyte screen_color = 9
    const ubyte border_color = 2
    const ubyte cursor_color = 7
    memory ubyte screen = $d021
    memory ubyte border = $d020
    memory ubyte cursor = 646

    screen = screen_color
    border = border_color
    cursor = cursor_color
    return
}

}

~ block2 $c000 {
    return
}

