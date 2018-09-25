%option enable_floats

~ main {

sub start() -> () {

    byte i

    float yy
    word pixely
    word yoffset
    word height


    ; @todo expression must not result in float but in word
    yy = flt(height+1.1)
    pixely = height / 100
    ;yy = height- 1.1
    ;yy = height*1.1
    ;yy = height/3.6
    ;yy = height//3.6
    ;yy = height**3.6
    ;yy = height%3.6
    ;yy = height/3.6+0.4
    ;yy = 2/height/3.6+0.4
    ;yy = (pixely-yoffset)/height/3.6+0.4

}
}
