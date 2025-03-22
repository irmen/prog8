%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit, romable

main {

    sub start()  {
        cx16.r8L = 3
        cx16.r9L = 20

        for cx16.r2L in cx16.r8L to cx16.r9L step 1 {
            txt.print_ub(cx16.r2L)
            txt.spc()
        }

;        cx16.r0L = @(ptr)
;
;        @(ptr)++
;
;        @(ptr)+=10
    }
}
