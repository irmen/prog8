%import textio
%import floats
%zeropage basicsafe
%option enable_floats

; Note: this program is compatible with C64 and CX16.

; TODO fix byte var in arrayvar fail


main {

    ; this is only a parser/compiler test, there's no actual working program

    sub start() {
        txt.print("this is only a parser/compiler test\n")
        return

        str  s1 = "hello"
        str  s2 = @"screencodes"

        &str  ms1 = $c000


        byte[4] barray
        ubyte[4] ubarray
        word[4] warray
        uword[4] uwarray
        float[4] flarray

        &byte[4] mbarray = $c000
        &ubyte[4] mubarray = $c000
        &word[4] mwarray = $c000
        &uword[4] muwarray = $c000
        &float[4] mflarray = $c000

        ubyte A
        byte bb
        ubyte ub
        word ww
        uword uw
        float fl

        ; read array
        A=s1[2]
        ub=s1[2]
        bb=barray[2]
        ub=ubarray[2]
        ww=warray[2]
        uw=uwarray[2]
        fl=flarray[2]
        A=ms1[2]
        ub=ms1[2]
        bb=mbarray[2]
        ub=mubarray[2]
        ww=mwarray[2]
        uw=muwarray[2]
        fl=mflarray[2]

        A=s1[A]
        ub=s1[A]
        bb=barray[A]
        ub=ubarray[A]
        ww=warray[A]
        uw=uwarray[A]
        fl=flarray[A]
        A=ms1[A]
        ub=ms1[A]
        bb=mbarray[A]
        ub=mubarray[A]
        ww=mwarray[A]
        uw=muwarray[A]
        fl=mflarray[A]

        A=s1[bb]
        ub=s1[bb]
        bb=barray[bb]
        ub=ubarray[bb]
        ww=warray[bb]
        uw=uwarray[bb]
        fl=flarray[bb]
        A=ms1[bb]
        ub=ms1[bb]
        bb=mbarray[bb]
        ub=mubarray[bb]
        ww=mwarray[bb]
        uw=muwarray[bb]
        fl=mflarray[bb]

        A=s1[bb*3]
        ub=s1[bb*3]
        bb=barray[bb*3]
        ub=ubarray[bb*3]
        ww=warray[bb*3]
        uw=uwarray[bb*3]
        fl=flarray[bb*3]
        A=ms1[bb*3]
        ub=ms1[bb*3]
        bb=mbarray[bb*3]
        ub=mubarray[bb*3]
        ww=mwarray[bb*3]
        uw=muwarray[bb*3]
        fl=mflarray[bb*3]

        ; write array
        barray[2]++
        barray[2]--
        s1[2] = A
        s1[2] = ub
        barray[2] = bb
        ubarray[2] = ub
        warray[2] = ww
        uwarray[2] = uw
        flarray[2] = fl
        ms1[2] = A
        ms1[2] = ub
        mbarray[2]++
        mbarray[2] = bb
        mbarray[2] = bb
        mubarray[2] = ub
        mwarray[2] = ww
        muwarray[2] = uw
        mflarray[2] = fl

        s1[A] = ub
        barray[A] = bb
        ubarray[A] = ub
        warray[A] = ww
        uwarray[A] = uw
        flarray[A] = fl

        s1[bb] = ub
        barray[bb] = bb
        ubarray[bb] = ub
        warray[bb] = ww
        uwarray[bb] = uw
        flarray[bb] = fl

        s1[bb*3] = ub
        barray[bb*3] = bb
        ubarray[bb*3] = ub
        warray[bb*3] = ww
        uwarray[bb*3] = uw
        flarray[bb*3] = fl
    }
}
