%import textio
%import string

%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte x
        ubyte y
        uword bytes = $a000

        for y in 0 to 59 {
            for x in 0 to 79 {
                txt.setchr(x, y, @(bytes+$1000))
                bytes++
            }
        }
    }

    sub stringcopy() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        uword[] addresses = [0,0,0]
        names = [1111,2222,3333]
        addresses = names
    }
}
