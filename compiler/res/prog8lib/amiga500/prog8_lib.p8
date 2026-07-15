%option no_symbol_prefixing, ignore_unused

%import exec

p8_sys_startup {
    %option force_output

    private long @shared orig_stackpointer  ; saved initial SP for sys.exit()
    private long @shared WBMsg

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

proc_MsgPort = 92
proc_CLI = 172

            ; Check if we are started from Workbench
            move.l  4.w,a6
		    sub.l   a1,a1
		    jsr     exec.FindTask(a6)
		    move.l  d0,a5
		    beq.w   1$
		    tst.l   proc_CLI(a5)
		    bne.b   1$
    		lea.l   proc_MsgPort(a5),a0
		    jsr     exec.WaitPort(a6)   ; Wait for workbench message
		    lea.l   proc_MsgPort(a5),a0
		    jsr     exec.GetMsg(a6)
		    move.l  d0,p8_sys_startup.WBMsg		; Store message pointer to reply at exit later
1$:
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
            movem.l d0,-(sp)       ; keep return code

            ; reply to Workbench message if it exists
    		move.l  4.w,a6
		    tst.l   p8_sys_startup.WBMsg
		    beq.b   1$
		    move.l  p8_sys_startup(pc),a1
		    jsr     exec.ReplyMsg(a6)
1$:
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
