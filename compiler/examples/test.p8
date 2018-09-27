%option enable_floats

%import c64lib
%import mathlib
%import prog8lib

~ main {

sub start() -> () {

    const word yoffset=100
    const float height=20.2
    word pixely
    float yy
    float v

    yy = 11.0-(v-22.0)
    yy = 11.0-(22.0-v)
    yy = (v-22.0)-11.0
    yy = (22.0-v)-11.0

    yy = 11.0/(v/22.0)
    yy = 11.0/(22.0/v)
    yy = (v/22.0)/11.0
    yy = (22.0/v)/11.0

}

sub printIt(length: XY, control: A) -> (A) {
    return 42  ; length / control
}
}
