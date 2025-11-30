%option no_sysinit ; leave the CX16 defaults in place
%zeropage basicsafe ; don't step on BASIC zero page locations
%import textio

main {
    sub start() {
        long @shared l1, l2

;        l2 = 0
;        ; expect:  <=0, <=0, >0
;        l1 = 0
;        if l1+l2<=0
;            txt.print("l1 <= 0\n")
;        else
;            txt.print("l1 > 0\n")
;
;        l1 = -1234567
;        if l1+l2<=0
;            txt.print("l1 <= 0\n")
;        else
;            txt.print("l1 > 0\n")
;
;
;        l1 = 1234
;        if l1+l2<=0
;            txt.print("l1 <= 0\n")
;        else
;            txt.print("l1 > 0\n")
;
;        ; expect:  >=0, >=0, <0
;        txt.nl()
;        l1 = 0
;        if l1+l2>=0
;            txt.print("l1 >= 0\n")
;        else
;            txt.print("l1 < 0\n")
;
;        l1 = 1234
;        if l1+l2>=0
;            txt.print("l1 >= 0\n")
;        else
;            txt.print("l1 < 0\n")
;
;        l1 = -123456
;        if l1+l2>=0
;            txt.print("l1 >= 0\n")
;        else
;            txt.print("l1 < 0\n")


        while l1+l2==0 {
            break
        }
        while l1+l2!=0 {
            break
        }
        while l1+l2>0 {
            break
        }
        while l1+l2<0 {
            break
        }

        while l1+l2>=0 {
            break
        }
        while l1+l2<=0 {
            break
        }
    }
}
