%import c64utils

~ block2 $4000 {
    return
}

~ block3 $3000 {
    return
}

~ blockNoAddr1 {
    return
}

~ blockNoAddr2 {
    return
}

~ block4 $6000 {
    return
}

~ blockNoAddr3 {
    return
}


~ main  {

sub start() {

    const ubyte screen_color = 9
    const ubyte border_color = 2
    const ubyte cursor_color = 7

    c64.BGCOL0 = screen_color
    c64.EXTCOL = border_color
    c64.COLOR = cursor_color
    return
}

}
