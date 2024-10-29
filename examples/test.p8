%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        txt.print("sdfdsf")
    }
}

txt {
    ; merges this block into the txt block coming from the textio library
    %option merge

    sub print(str text) {
        repeat 4 chrout('@')
        repeat {
            cx16.r0L = @(text)
            if_z break
            chrout(cx16.r0L)
            text++
        }
        repeat 4 chrout('@')
    }
}
