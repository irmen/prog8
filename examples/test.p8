%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; is optimizing this useful? :   not a1 or not a2 -> not(a1 and a2)  likewise for and.
        bool @shared a1 = true
        bool @shared a2
        bool @shared a4

        if a1==0 and a2==0
            cx16.r0++

        if (a1!=0 or a2!=0)==0
            cx16.r0++

        if a1==0 or a2==0
            cx16.r0++

        if (a1!=0 and a2!=0)==0
            cx16.r0++


;        if not a1 or not a2
;            cx16.r0++
;        if not (a1 and a2)
;            cx16.r0++
;        if not a1 and not a2
;            cx16.r0++
;        if not (a1 or a2)
;            cx16.r0++
    }
}
