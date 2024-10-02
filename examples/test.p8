%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool[256] cells
        word starw
        byte bb
        uword uw
        ubyte ub

        for starw in 50 downto 10  {        ; TODO fix compiler error + add unit test for this
            cx16.r0++
        }
        for starw in cx16.r0L downto 10  {        ; TODO fix compiler error + add unit test for this
            cx16.r0++
        }

        for ub in 0 to len(cells)-1 {
            cx16.r0++
        }
        for ub in cx16.r0L to len(cells)-1 {
            cx16.r0++
        }
        for bb in 50 downto 10  {
            cx16.r0++
        }
        for bb in cx16.r0sL downto 10  {
            cx16.r0++
        }


;        for starw in 500 downto 10  {
;            cx16.r0++
;        }
;        for uw in 50 downto 10 {
;            cx16.r0++
;        }
;        for uw in 500 downto 10 {
;            cx16.r0++
;        }
    }
}

