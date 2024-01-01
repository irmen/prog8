%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[10] array1
        ubyte[10] array2
        ubyte @shared xx

       cx16.r0 = (cx16.r1+cx16.r2) / (cx16.r2+cx16.r1)
       cx16.r1 = 4*(cx16.r1+cx16.r2) + 3*(cx16.r1+cx16.r2)
       cx16.r2 = array1[xx+20]==10 or array2[xx+20]==20 or array1[xx+20]==30 or array2[xx+20]==40
    }
}
