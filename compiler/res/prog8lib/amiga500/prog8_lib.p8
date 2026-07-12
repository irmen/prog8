%option no_symbol_prefixing, ignore_unused

p8_sys_startup {
    %option force_output

    sub init_system() {
        ; TODO
    }

    sub init_system_phase2() {
        %asm {{
            moveq   #0,d0
            moveq   #0,d1
            moveq   #0,d2
            moveq   #0,d3
            moveq   #0,d4
            moveq   #0,d5
            moveq   #0,d6
            moveq   #0,d7
            move.w  d0,ccr
            suba.l  a0,a0
            suba.l  a1,a1
            suba.l  a2,a2
            suba.l  a3,a3
            suba.l  a4,a4
            suba.l  a5,a5
            suba.l  a6,a6
        }}
    }

    sub cleanup_at_exit() {
        ; TODO
    }
}
