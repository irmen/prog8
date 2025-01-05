%import textio
%option no_sysinit
%zeropage kernalsafe

main {
    sub start() {
        ubyte[] array = [11,22,33,44,55,66,77,88,99]
        uword @shared ptr = &array[5]
        ubyte @shared offset


        cx16.r0L = @(&start + 1)
        cx16.r1L = @(&start - 1)
        @(&start+1) = 99
        @(&start-1) = 99

;        @(ptr+1) = cx16.r0L
;        @(ptr+2) = cx16.r0L
;        @(ptr+offset) = cx16.r0L
;        @(ptr-1) = cx16.r0L
;        @(ptr-2) = cx16.r0L
;        @(ptr-offset) = cx16.r0L


;        cx16.r0L = @(ptr+1)
;        cx16.r1L = @(ptr+2)
;        cx16.r2L = @(ptr+offset)
;        cx16.r3L = @(ptr-1)
;        cx16.r4L = @(ptr-2)
;        cx16.r5L = @(ptr-offset)



;        %asm {{
;            dec  p8v_ptr+1
;            ldy  #255
;            lda  (p8v_ptr),y
;            inc  p8v_ptr+1
;            sta  cx16.r0L
;        }}

        repeat {
        }

    }
}
