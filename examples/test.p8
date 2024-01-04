%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; is optimizing this useful? :   not a1 or not a2 -> not(a1 and a2)  likewise for and.
        bool @shared a1 = true
        bool @shared a2
        bool @shared a
        bool @shared b

        ; absorbption opt:
;        if a or (a and b)
;            cx16.r0 ++
;        if a or (b and a)
;            cx16.r0 ++
;        if a and (a or b)
;            cx16.r0 ++
;        if a and (b or a)
;            cx16.r0 ++
;
;        ; no opt:
;        if a and (b and a)
;            cx16.r0 ++
;        if a or (b or a)
;            cx16.r0 ++

        bool @shared iteration_in_progress = false
        ubyte @shared num_bytes = 99

        if not iteration_in_progress or not num_bytes
            txt.print("yep1")
        else
            txt.print("nope1")

        iteration_in_progress = true
        if not iteration_in_progress or not num_bytes
            txt.print("yep2")
        else
            txt.print("nope2")

;
;        if a1==0 and a2==0
;            cx16.r0++
;
;        if (a1!=0 or a2!=0)==0
;            cx16.r0++
;
;        if a1==0 or a2==0
;            cx16.r0++
;
;        if (a1!=0 and a2!=0)==0
;            cx16.r0++


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
