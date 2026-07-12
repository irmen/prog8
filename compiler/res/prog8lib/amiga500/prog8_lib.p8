%option no_symbol_prefixing, ignore_unused

%import exec

p8_sys_startup {
    %option force_output

    sub init_system() {
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
        if sys.IconBase != 0 exec.CloseLibrary(sys.IconBase)
        if sys.IntuitionBase != 0 exec.CloseLibrary(sys.IntuitionBase)
        if sys.GfxBase != 0 exec.CloseLibrary(sys.GfxBase)
        if sys.DOSBase != 0 exec.CloseLibrary(sys.DOSBase)
    }
}
