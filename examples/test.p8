
%import textio
%import anyall

%option no_sysinit

main {
    byte[256] barray
    word[128] warray
    uword large_barray=memory("bytes", 1000, 0)
    uword large_warray=memory("words", 1000, 0)

    sub check() {
        txt.print_bool(anyall.all(barray, 256))
        txt.spc()
        txt.print_bool(anyall.any(barray, 256))
        txt.nl()
        txt.print_bool(anyall.allw(warray, 128))
        txt.spc()
        txt.print_bool(anyall.anyw(warray, 128))
        txt.nl()
        txt.print_bool(anyall.all(large_barray, 1000))
        txt.spc()
        txt.print_bool(anyall.any(large_barray, 1000))
        txt.nl()
        txt.print_bool(anyall.allw(large_warray, 500))
        txt.spc()
        txt.print_bool(anyall.anyw(large_warray, 500))
        txt.nl()
        txt.nl()
    }

    sub start() {
        sys.memset(large_barray, 1000, 0)
        sys.memset(large_warray, 1000, 0)

        check()
        barray[250] = 99
        warray[100] = $0100
        large_barray[900] = 99
        large_warray[900] = 99
        check()
        sys.memset(barray, 255, 1)
        sys.memset(warray, 254, 1)
        sys.memset(large_barray, 999, 1)
        sys.memset(large_warray, 998, 1)
        check()
        barray[255]=1
        warray[127]=1
        @(large_barray+999)=1
        @(large_warray+999)=1
        check()
        repeat {}

;        smallringbuffer.init()
;
;        smallringbuffer.put(123)
;        txt.print_ub(smallringbuffer.get())
;        txt.nl()
;
;        smallringbuffer.putw(12345)
;        txt.print_uw(smallringbuffer.getw())
;        txt.nl()
    }
}


;
;main {
;    sub start() {
;        signed()
;        unsigned()
;    }
;
;    sub signed() {
;        byte @shared bvalue = -100
;        word @shared wvalue = -20000
;
;        bvalue /= 2     ; TODO should be a simple bit shift?
;        wvalue /= 2     ; TODO should be a simple bit shift?
;
;        txt.print_b(bvalue)
;        txt.nl()
;        txt.print_w(wvalue)
;        txt.nl()
;
;        bvalue *= 2
;        wvalue *= 2
;
;        txt.print_b(bvalue)
;        txt.nl()
;        txt.print_w(wvalue)
;        txt.nl()
;    }
;
;    sub unsigned() {
;        ubyte @shared ubvalue = 100
;        uword @shared uwvalue = 20000
;
;        ubvalue /= 2
;        uwvalue /= 2
;
;        txt.print_ub(ubvalue)
;        txt.nl()
;        txt.print_uw(uwvalue)
;        txt.nl()
;
;        ubvalue *= 2
;        uwvalue *= 2
;
;        txt.print_ub(ubvalue)
;        txt.nl()
;        txt.print_uw(uwvalue)
;        txt.nl()
;    }
;}
