%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {

    str str1 = "irmen"
    str str2 = "test"
    str_s strs1 = "irmen"
    str_s strs2 = "test"

    sub start() {

        str foo1 = "1\n"
        str foo2 = "12\n"

        c64scr.print(foo1)
        c64scr.print(foo2)
        c64scr.print("\n")
        c64scr.print("1\n")
        c64scr.print("12\n")
        c64scr.print("\n")
        c64scr.print("1\n")
        c64scr.print("12\n")

;        str str1x = "irmen"
;        str str2x = "test"
;        str_s strs1x = "irmen"
;        str_s strs2x = "test"
;
;        c64scr.print("yoooooo")
;        c64scr.print(str1)
;        c64.CHROUT('\n')
;        c64scr.print(str2)
;        c64.CHROUT('\n')
;        c64scr.print(str1x)
;        c64.CHROUT('\n')
;        c64scr.print(str2x)
;        c64.CHROUT('\n')
;
;        str1[0]='a'
;        str2[0]='a'
;        str1x[0]='a'
;        str2x[0]='a'
;        strs1x[0]='a'
;        strs2x[0]='a'
;        strs1[0]='a'
;        strs2[0]='a'
;
;        ; @TODO fix AstVm handling of strings (they're not modified right now) NOTE: array's seem to work fine
;        c64scr.print(str1)
;        c64.CHROUT('\n')
;        c64scr.print(str2)
;        c64.CHROUT('\n')
;        c64scr.print(str1x)
;        c64.CHROUT('\n')
;        c64scr.print(str2x)
;        c64.CHROUT('\n')
;
;
;        byte[] barr = [1,2,3]
;        word[] warr = [1000,2000,3000]
;        float[] farr = [1.1, 2.2, 3.3]
;
;        byte bb
;        word ww
;        float ff
;        for bb in barr {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        for ww in warr {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        for bb in 0 to len(farr)-1 {
;            c64flt.print_f(farr[bb])
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        barr[0] = 99
;        warr[0] = 99
;        farr[0] = 99.9
;        for bb in barr {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        for ww in warr {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        for bb in 0 to len(farr)-1 {
;            c64flt.print_f(farr[bb])
;            c64.CHROUT(',')
;        }
    }

}
