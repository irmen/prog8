%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        block1.sub1()
        block1.sub2()
    }
}

block1 {
    %option merge

    sub sub1() {
        txt.print("sub1")
    }
}


block1 {
    %option merge

    sub sub2() {
        txt.print("sub2")
    }
}

