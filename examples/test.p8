%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        blah.test()
    }
}

txt {
    ; merges this block into the txt block coming from the textio library
    %option merge

    sub schrijf(str arg) {
        print(arg)
    }
}

blah {
    ; merges this block into the other 'blah' one
    %option merge

    sub test() {
        printit("test merge")
    }
}

blah {
    sub printit(str arg) {
        txt.schrijf(arg)
    }
}
