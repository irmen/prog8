%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[] stuff1=[1,2,3]
        ubyte [] stuff2=[1,2,3]
        ubyte[ ] stuff3=[1,2,3]     ; TODO fix parse error
        stuff1[1]++
        stuff2[1]++
        stuff3[1]++
    }
}

