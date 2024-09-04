%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        str name1 = "name1"
        str name2 = "name2"
        uword[] @split names = [name1, name2, "name3"]
        txt.print(names[2])

        ;uword[] @split names2 = [name1, name2, "name3"]
        ;uword[] addresses = [0,0,0]
        ;names = [1111,2222,3333]
        ;addresses = names
        ;names = addresses
        ;names2 = names
    }
}
