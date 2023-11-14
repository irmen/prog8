%import string
%zeropage basicsafe

main {

    sub start() {
        cat("aaaaa")
    }

sub cat(str s1) {
    str s2 = "three"
    ubyte n=2

    ; s1[n+1] = s1[2]     ; TODO compiler crash
    s2[n+1] = s2[2]     ; works fine
}

}
