%option no_symbol_prefixing, ignore_unused

%import exec

p8_sys_startup {
    %option force_output

    private long @shared orig_stackpointer  ; saved initial SP for sys.exit()
    private long @shared WBMsg

    ; NOTE: the executable loader already zero-fills the BSS section
    ; (LoadSeg on Amiga HUNK, or the ELF loader on qemu68k), so this
    ; manual clear is unnecessary and has been commented out.
    ; asmsub clear_bss_section() {
    ;     %asm {{
    ;         ; clear BSS section
    ;         lea  prog8_bss_section_start, a0
    ;         lea  prog8_program_end, a1
    ;         sub.l  a0, a1       ; bss size in bytes
    ;         beq  2$
    ;         moveq  #0, d0
    ; 1$
    ;         move.b  d0, (a0)+
    ;         subq.l  #1, a1
    ;         bne  1$
    ; 2$
    ;         rts
    ;     }}
    ; }

    sub init_system() {
        %asm {{
            ; save CLI arguments
            move.l  d0,-(sp)
            move.l  a0,-(sp)

            ; save original stackpointer
            move.l  sp,a0
            add.l   #4*3,a0       ; take care of JSR to this routine, and the 2 saved longs
            move.l  a0,p8_sys_startup.orig_stackpointer

proc_MsgPort = 92
proc_CLI = 172

            move.l  4.w,a6
            cmp.w   #36,20(a6)      ; KS 2.0 = V36
            blo.s   1$

            move.l  #-1,d0
            move.l  d0,d1
            jsr     -648(a6)        ; CacheControl(): turn on all cpu caches
1$:
            ; Check if we are started from Workbench
		    sub.l   a1,a1
		    jsr     exec.FindTask(a6)
		    move.l  d0,a5
		    beq.w   2$
		    tst.l   proc_CLI(a5)
		    bne.b   2$
    		lea.l   proc_MsgPort(a5),a0
		    jsr     exec.WaitPort(a6)   ; Wait for workbench message
		    lea.l   proc_MsgPort(a5),a0
		    jsr     exec.GetMsg(a6)
		    move.l  d0,p8_sys_startup.WBMsg		; Store message pointer to reply at exit later
2$:

            ; CLI launch: restore saved arguments (if any)
            move.l  (sp)+,a0
            move.l  (sp)+,d0
            tst.l   p8_sys_startup.WBMsg
            bne.s   .workbench
            tst.l   d0
            beq.s   .emptyargs
            subq.l  #1,d0
            clr.b   (a0,d0.w)      ; replace newline by 0 terminator
            move.l  a0,sys.arguments
            bra.s   .done
.emptyargs:
            move.l  a0,sys.arguments   ; empty string, but still CLI launch
            bra.s   .done
.workbench:
            moveq   #0,d0
            move.l  d0,sys.arguments   ; NULL for Workbench launches
.done:
        }}

        sys.DOSBase = exec.OpenLibrary("dos.library",0)
        sys.GfxBase = exec.OpenLibrary("graphics.library",0)
        sys.IntuitionBase = exec.OpenLibrary("intuition.library",0)
        sys.IconBase = exec.OpenLibrary("icon.library",0)
        sys.UtilityBase = exec.OpenLibrary("utility.library",0)     ; only succeeds on kickstart 2.0+
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
		    move.l  p8_sys_startup.WBMsg,a1
		    jsr     exec.ReplyMsg(a6)
1$:
        }}

        if sys.RexxSysBase != 0 exec.CloseLibrary(sys.RexxSysBase)
        if sys.IFFParseBase != 0 exec.CloseLibrary(sys.IFFParseBase)
        if sys.UtilityBase != 0 exec.CloseLibrary(sys.UtilityBase)
        if sys.IconBase != 0 exec.CloseLibrary(sys.IconBase)
        if sys.IntuitionBase != 0 exec.CloseLibrary(sys.IntuitionBase)
        if sys.GfxBase != 0 exec.CloseLibrary(sys.GfxBase)
        if sys.DOSBase != 0 exec.CloseLibrary(sys.DOSBase)

        %asm {{
            movem.l  (sp)+,d0       ; restore return code
        }}
    }
}

prog8_lib {
    asmsub strcmp(str st1 @D0, str st2 @D1) -> byte @D0 {
        ; Compares two strings for sorting, case-sensitively.
        ; Returns -1 (255 as byte), 0 or 1.
        %asm {{
            movea.l  d0,a0
            movea.l  d1,a1
.loop:
            move.b   (a0)+,d0
            move.b   (a1)+,d1
            cmp.b    d1,d0
            bne      .diff
            tst.b    d0
            bne      .loop
            moveq    #0,d0
            rts
.diff:
            blo      .less
            moveq    #1,d0
            rts
.less:
            moveq    #-1,d0
            rts
        }}
    }
}
