%option enable_floats

~ main {

sub start() -> () {

    word pixely

    pixely = A % 0      ; @todo divide 0
    pixely = A / 0      ; @todo divide 0
    pixely = A // 0     ; @todo divide 0

    pixely |= 1     ; pixely = pixely | 1
    pixely &= 1     ; pixely = pixely & 1
    pixely ^= 1     ; pixely = pixely ^ 1

}
}
