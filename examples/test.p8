%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte yy
        ubyte joy=1
        ubyte zz

        joy >>= 1
        if_cs
            yy++

        joy >>= 1
        if_cs
            yy++

; TODO the shifting checks above result in way smaller code than this:
        if joy+44 > 33 {
            yy++
        }

        yy=joy+44>33
        if yy {
            yy++
        }

        if joy & %00000001
            yy++
        if joy & %00000010
            yy++

    }
}

