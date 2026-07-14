%option no_symbol_prefixing, ignore_unused

%import exec

p8_sys_startup {
    %option force_output

    long orig_stackpointer  ; saved initial SP for sys.exit()

    asmsub clear_bss_section() {
        %asm {{
            ; clear BSS section
            lea  prog8_bss_section_start, a0
            lea  prog8_program_end, a1
            sub.l  a0, a1       ; bss size in bytes
            beq  2$
            moveq  #0, d0
1$
            move.b  d0, (a0)+
            subq.l  #1, a1
            bne  1$
2$
            rts
        }}
    }

    sub init_system() {
        %asm {{
            move.l  sp,a0
            addq.l  #4,a0
            move.l  a0,p8_sys_startup.orig_stackpointer
        }}
        sys.DOSBase = exec.OpenLibrary("dos.library",0)
        sys.GfxBase = exec.OpenLibrary("graphics.library",0)
        sys.IntuitionBase = exec.OpenLibrary("intuition.library",0)
        sys.IconBase = exec.OpenLibrary("icon.library",0)
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
        %asm {{
            movem.l  d0,-(sp)       ; keep return code
        }}

        if sys.IconBase != 0 exec.CloseLibrary(sys.IconBase)
        if sys.IntuitionBase != 0 exec.CloseLibrary(sys.IntuitionBase)
        if sys.GfxBase != 0 exec.CloseLibrary(sys.GfxBase)
        if sys.DOSBase != 0 exec.CloseLibrary(sys.DOSBase)

        %asm {{
            movem.l  (sp)+,d0       ; restore return code
        }}
    }
}
