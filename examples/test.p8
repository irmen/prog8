%import textio
%zeropage basicsafe

main {
    sub start() {
        word[3] @shared sprites_x   ; = sprites.sprites_x
        sprites_x = sprites.sprites_x
        word[3] @shared sprites_y  ; = sprites.sprites_y
        sprites_y = sprites.sprites_y

        txt.print_w(sprites.sprites_x[2])
        txt.nl()
        txt.print_w(sprites.sprites_y[2])
        txt.nl()
        txt.print_w(sprites_x[2])
        txt.nl()
        txt.print_w(sprites_y[2])
        txt.nl()
    }
}

sprites {
    word[3] sprites_x = [111,222,333]
    word[3] sprites_y = [666,777,888]
}
