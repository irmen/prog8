; Prog8 definitions for the Amiga500 target

%option no_symbol_prefixing, ignore_unused
%import shared_m68k_memory_routines

%import dos

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 50         ;  compilation target specifier.

    const ubyte SIZEOF_BOOL  = sizeof(bool)
    const ubyte SIZEOF_BYTE  = sizeof(byte)
    const ubyte SIZEOF_UBYTE = sizeof(ubyte)
    const ubyte SIZEOF_WORD  = sizeof(word)
    const ubyte SIZEOF_UWORD = sizeof(uword)
    const ubyte SIZEOF_LONG  = sizeof(long)
    const ubyte SIZEOF_POINTER = sizeof(&sys.wait)
    const ubyte SIZEOF_FLOAT = sizeof(float)
    const byte  MIN_BYTE     = -128
    const byte  MAX_BYTE     = 127
    const ubyte MIN_UBYTE    = 0
    const ubyte MAX_UBYTE    = 255
    const word  MIN_WORD     = -32768
    const word  MAX_WORD     = 32767
    const uword MIN_UWORD    = 0
    const uword MAX_UWORD    = 65535
    const long  MIN_LONG     = -2147483648
    const long  MAX_LONG     = 2147483647
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if imported


    ; SysBase/ExecBase is always simply available at 4.w
    pointer @shared DOSBase
    pointer @shared GfxBase
    pointer @shared IntuitionBase
    pointer @shared IconBase
    pointer @shared UtilityBase     ; kickstart 2.0+
    pointer @shared RexxSysBase     ; kickstart 2.0+, loaded from disk on demand
    pointer @shared IFFParseBase    ; kickstart 2.0+, loaded from disk on demand
    pointer @shared AsyncIOBase     ; loaded from disk on demand
    pointer @shared LowLevelBase    ; kickstart 2.0+, loaded from disk on demand
    pointer @shared TimerBase       ; opened on demand

    ^^ubyte @shared arguments       ; CLI argument string (null-terminated), or NULL if Workbench launch

    sub  reset_system()  {
        %option noframe
        %asm {{
            move.l  4.w,a6
            move.w  20(a6),d0       ; ExecBase version
            cmpi.w  #36,d0
            bge.s   .cold_reboot

            move.l  4.w,a0          ; Kickstart 1.3 fallback
            reset
            move.l  (a0),a0
            jmp     (a0)

.cold_reboot:
            jmp     -726(a6)       ; ColdReboot()
        }}
    }

    asmsub wait(long ticks @D1) {
        ; --- wait approximately the given number of ticks (1/50th seconds, i.e. 20ms each)
        %asm {{
            move.l  sys.DOSBase,a6
            jmp     -198(a6)        ; Delay
        }}
    }

    inline asmsub waitvsync()  {
        ; --- Wait until the next vsync has occurred.
        ;     This routine requires the OS to be functioning.
        ;     If you have disabled the OS/interrupts and are banging the hardware directly, use custom.waitvsync() instead.
        %asm {{
            move.l  sys.GfxBase,a6
            jsr     -270(a6)        ; WaitTOF
        }}
    }

    asmsub exit(word returnvalue @D0) {
        ; -- exit the program with a return code in D0. When invoked from Prog8 via sys.exit(), all active defers in the call chain are unwound program-wide (LIFO) before system cleanup. sys.reset_system() does not run defers.
        %asm {{
            and.l    #$ffff,d0
            move.l   p8_sys_startup.orig_stackpointer,sp
            jmp  p8_sys_startup.cleanup_at_exit
        }}
    }

    sub set_carry() {
        %option noframe
        %asm {{
            ; set both C (comparison carry) and X (rotate carry) bits
            moveq  #$11,d0
            move.w  d0,ccr
        }}
    }

    sub clear_carry() {
        %option noframe
        %asm {{
            ; clear C and X bits
            moveq  #0,d0
            move.w  d0,ccr
        }}
    }

    inline asmsub memcopy(long source @A0, long tgt @A1, long count @D0) {
        %asm {{
            move.l  4.w,a6
            jsr  exec.CopyMem(a6)
        }}
    }

    asmsub exec_version() -> uword @D0 {
        ; Returns the exec library version.
        ; This corresponds to the Kickstart/ROM version:
        ;   30 = KS 1.0, 33 = KS 1.2, 34 = KS 1.3,
        ;   37 = KS 2.0, 39 = KS 3.0, 40 = KS 3.1
        %asm {{
            move.l  $4,a0
            move.w  20(a0),d0
            rts
        }}
    }

    inline asmsub progstart() -> long @A0 {
        %asm {{
            lea  prog8_program_start,a0
        }}
    }

    inline asmsub progend() -> long @A0 {
        %asm {{
            lea  prog8_program_end,a0
        }}
    }

    asmsub stack_size() -> long @D0 {
        ; -- Return the amount of stack space still available, in bytes.
        %asm {{
            move.l  4.w,a6          ; ExecBase
            move.l  $114(a6),a0     ; ThisTask
            move.l  sp,d0
            sub.l   $3a(a0),d0      ; minus tc_SPLower
            rts
        }}
    }

    asmsub cpuAtLeast68020() clobbers (A6, D0) -> bool @Pz {
        %asm {{
            move.l  4.w,a6
            move.w  296(a6),d0
            andi.w  #$008e,d0
            seq     d0
            tst.b   d0
            rts
        }}
    }

    sub die(uword code, str message) {
        %asm {{
            moveq   #0,d0
            move.w  14(a5),d0
            lea      .alert(pc),a1
            lea      .alert_limit(pc),a3
            move.w   #20,(a1)+
            move.b   #16,(a1)+
            lea      .prefix(pc),a2
.copy_prefix:
            move.b   (a2)+,d1
            beq.s    .copy_code
            move.b   d1,(a1)+
            bra.s    .copy_prefix
.copy_code:
            move.w  14(a5),d2
            moveq    #3,d4
.copy_code_digit:
            move.w   d2,d3
            lsr.w    #8,d3
            lsr.w    #4,d3
            andi.w   #$000f,d3
            cmpi.w   #10,d3
            bcs.s    .copy_code_number
            addi.w   #'A'-10,d3
            bra.s    .copy_code_store
.copy_code_number:
            addi.w   #'0',d3
.copy_code_store:
            move.b   d3,(a1)+
            lsl.w    #4,d2
            dbra     d4,.copy_code_digit
            clr.b    (a1)+
            move.b   #1,(a1)+
            move.w   #20,(a1)+
            move.b   #32,(a1)+
.copy_message:
            move.l   8(a5),a0
.copy_message_loop:
            cmpa.l   a3,a1
            bhi.s    .message_full
            move.b   (a0)+,d1
            move.b   d1,(a1)+
            bne.s    .copy_message_loop
            clr.b    (a1)
            bra.s    .display
.message_full:
            subq.l   #1,a1
            clr.b    (a1)
            clr.b    1(a1)
.display:
            moveq   #48,d1
            movea.l  sys.IntuitionBase,a6
            lea      .alert(pc),a0
            jsr      -90(a6)
            moveq   #0,d0
            move.w  14(a5),d0
            jmp      sys.exit

            even
.alert:
            ds.b     158
.alert_limit:
            ds.b     2
.alert_end:
.prefix:
            dc.b     "PROGRAM DIED code $",0
            ; !notreached!
        }}
    }
}
