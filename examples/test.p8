%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        uword uw
        for uw in 50 downto 10 {
            cx16.r0++
        }
        word starw
        for starw in 50 downto 10  {        ; TODO fix compiler error + add unit test for this
            cx16.r0++
        }


        ubyte[] stuff1=[1,2,3]
        ubyte [] stuff2=[1,2,3]
        ubyte[ ] stuff3=[1,2,3]     ; TODO fix parse error
        stuff1[1]++
        stuff2[1]++
        stuff3[1]++
    }
}

