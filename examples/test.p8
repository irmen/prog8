%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        bool[] barray =  [true, false, true, false]
        uword[] warray = [&value1, &barray, &value5, 4242]

        bool @shared value1
        bool @shared value2 = barray[2]         ; should be const!
        bool @shared value3 = true
        bool @shared value4 = false
        bool @shared value5 = barray[cx16.r0L]      ; cannot be const
        uword @shared value6 = warray[3]        ; should be const!
        uword @shared value7 = warray[2]        ; cannot be const

        txt.nl()
    }
}
