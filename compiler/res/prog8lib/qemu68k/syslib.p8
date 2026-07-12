; Prog8 definitions for the Qemu M68k target

%option no_symbol_prefixing, ignore_unused

qemu {
    %option force_output

    ; MMIO base addresses (from QEMU hw/m68k/virt.c)
    const long GF_PIC_BASE     = $ff000000   ; 6 PICs, 0x1000 apart
    const long GF_RTC_BASE     = $ff006000   ; 2 RTCs, 0x1000 apart
    const long GF_TTY_BASE     = $ff008000
    const long VIRT_CTRL_BASE  = $ff009000
    const long VIRTIO_BASE     = $ff010000   ; 128 slots, 0x200 apart

    ; Virt Controller (hw/misc/virt_ctrl.c)
    const long CTRL_REG_FEATURES = VIRT_CTRL_BASE + $00
    const long CTRL_REG_CMD      = VIRT_CTRL_BASE + $04
    const ubyte CTRL_CMD_NOOP    = 0
    const ubyte CTRL_CMD_RESET   = 1
    const ubyte CTRL_CMD_HALT    = 2
    const ubyte CTRL_CMD_PANIC   = 3

    ; Goldfish TTY (hw/char/goldfish_tty.c)
    const long TTY_PUT_CHAR      = GF_TTY_BASE + $00
    const long TTY_BYTES_READY   = GF_TTY_BASE + $04
    const long TTY_CMD           = GF_TTY_BASE + $08
    const long TTY_DATA_PTR      = GF_TTY_BASE + $10
    const long TTY_DATA_LEN      = GF_TTY_BASE + $14
    const long TTY_DATA_PTR_HIGH = GF_TTY_BASE + $18
    const long TTY_VERSION       = GF_TTY_BASE + $20
    const ubyte TTY_CMD_INT_DISABLE  = 0
    const ubyte TTY_CMD_INT_ENABLE   = 1
    const ubyte TTY_CMD_WRITE_BUFFER = 2
    const ubyte TTY_CMD_READ_BUFFER  = 3

    ; Goldfish PIC register offsets (from base + idx*$1000)
    ; eg PIC #1 base = GF_PIC_BASE, PIC #2 = GF_PIC_BASE + $1000
    const ubyte PIC_REG_STATUS          = $00
    const ubyte PIC_REG_IRQ_PENDING     = $04
    const ubyte PIC_REG_IRQ_DISABLE_ALL = $08
    const ubyte PIC_REG_DISABLE         = $0c
    const ubyte PIC_REG_ENABLE          = $10

    ; Goldfish RTC register offsets (from GF_RTC_BASE)
    const ubyte RTC_TIME_LOW        = $00
    const ubyte RTC_TIME_HIGH       = $04
    const ubyte RTC_ALARM_LOW       = $08
    const ubyte RTC_ALARM_HIGH      = $0c
    const ubyte RTC_IRQ_ENABLED     = $10
    const ubyte RTC_CLEAR_ALARM     = $14
    const ubyte RTC_ALARM_STATUS    = $18
    const ubyte RTC_CLEAR_INTERRUPT = $1c


    asmsub chrout(ubyte char @D0) {
        %asm {{
            move.l  d0,qemu.TTY_PUT_CHAR
            rts
        }}
    }

    ; Read one character from TTY input (blocking)
    asmsub chrin() -> ubyte @D0 {
        %asm {{
            suba.l  #4,sp
            movea.l  sp,a0       ; buffer = stack pointer (in A0 for input_data)
            moveq   #1,d1        ; maxlen = 1
            bsr     qemu.input_data
            move.b  (sp),d0
            adda.l  #4,sp
            rts
        }}
    }

    ; Check if at least one character is available on TTY input (non-zero means data available)
    asmsub keypressed() -> bool @Pz {
        %asm {{
            movea.l #qemu.TTY_BYTES_READY,a1
            move.l  (a1),d0
            tst.l   d0
            rts
        }}
    }

    ; Read up to maxlen bytes from TTY input into buffer.
    ; Blocks until at least one byte is available. Does NOT add a trailing 0-byte.
    ; Returns the actual number of bytes read (may be less than maxlen).
    asmsub input_data(long buffer @A0, uword maxlen @D1) -> uword @D0 {
        %asm {{
1$:         movea.l  #qemu.TTY_BYTES_READY,a1
            move.l   (a1),d0
            tst.l    d0
            beq      1$                  ; wait for at least 1 byte
            ; d0 = available bytes, d1 = maxlen
            cmp.l    d1,d0               ; available - maxlen
            bls      2$                  ; if available <= maxlen, keep d0
            move.l   d1,d0               ; else cap at maxlen
2$:         movea.l  #qemu.TTY_DATA_PTR,a1
            move.l   a0,(a1)
            movea.l  #qemu.TTY_DATA_LEN,a1
            move.l   d0,(a1)
            movea.l  #qemu.TTY_CMD,a1
            move.l   #qemu.TTY_CMD_READ_BUFFER,(a1)
            rts
        }}
    }

    ; Print a null-terminated string to the TTY.
    ; Preserves all registers (no caller-saved registers clobbered).
    asmsub puts(str msg @A0) {
        %asm {{
            move.l  d0,-(sp)
            move.l  a0,-(sp)
.loop:
            move.b   (a0)+,d0
            beq      .done
            move.l   d0,qemu.TTY_PUT_CHAR
            bra.s    .loop
.done:
            move.l  (sp)+,a0
            move.l  (sp)+,d0
            rts
        }}
    }

    ; === Bootinfo record parsing ===
    ; The m68k QEMU virt machine places bootinfo records right after the kernel's
    ; ELF LOAD segment, word-aligned.  Each record:
    ;   uint16 tag   (0 = BI_LAST sentinel)
    ;   uint16 size  (total record size in bytes, including 4-byte header)
    ;   uint8  data[size-4]

    ; Returns address of the first bootinfo record (word-aligned past end of program).
    sub bootinfo_ptr() -> long {
        return sys.progend() + 1 & $fffffffe
    }

    ; Advance to the next bootinfo record.
    ; Input: ptr = current record pointer
    ; Output: next record pointer, or 0 if BI_LAST was reached
    sub bootinfo_next(long ptr) -> long {
        uword tag = peekw(ptr)
        if tag == 0
            return 0
        return ptr + peekw(ptr + 2)
    }

    ; --- Bootinfo record tags ---
    const uword BI_LAST                = $0000
    const uword BI_MACHTYPE            = $0001
    const uword BI_CPUTYPE             = $0002
    const uword BI_FPUTYPE             = $0003
    const uword BI_MMUTYPE             = $0004
    const uword BI_MEMCHUNK            = $0005
    const uword BI_RAMDISK             = $0006
    const uword BI_COMMAND_LINE        = $0007
    const uword BI_RNG_SEED            = $0008
    const uword BI_VIRT_QEMU_VERSION   = $8000
    const uword BI_VIRT_GF_PIC_BASE    = $8001
    const uword BI_VIRT_GF_RTC_BASE    = $8002
    const uword BI_VIRT_GF_TTY_BASE    = $8003
    const uword BI_VIRT_VIRTIO_BASE    = $8004
    const uword BI_VIRT_CTRL_BASE      = $8005

    ; --- Machine type constants (for BI_MACHTYPE) ---
    const long MACH_AMIGA   = 1
    const long MACH_ATARI   = 2
    const long MACH_MAC     = 3
    const long MACH_APOLLO  = 4
    const long MACH_SUN3    = 5
    const long MACH_MVME147 = 6
    const long MACH_MVME16x = 7
    const long MACH_BVME6000 = 8
    const long MACH_HP300   = 9
    const long MACH_Q40     = 10
    const long MACH_SUN3x   = 11
    const long MACH_M54xx   = 12
    const long MACH_M5441x  = 13
    const long MACH_VIRT    = 14

    ; --- CPU feature bits (for BI_CPUTYPE) ---
    const long CPU_FEATURE_68020   = 1
    const long CPU_FEATURE_68030   = 2
    const long CPU_FEATURE_68040   = 4
    const long CPU_FEATURE_68060   = 8
    const long CPU_FEATURE_COLDFIRE = 16

    ; Dump all bootinfo records to the TTY in hex.
    asmsub bootinfo_dump() {
        %asm {{
            bsr     qemu.bootinfo_ptr
            movea.l d0,a2           ; a2 = first record
1$:         clr.l   d0
            move.w  (a2),d0         ; tag
            beq     9$              ; BI_LAST
            clr.l   d1
            move.w  2(a2),d1        ; total size
            move.l  d0,d4           ; save tag in d4
            move.l  d1,d5           ; save size in d5
            move.l  a2,a3           ; save current pointer
            move.l  a2,qemu.bootinfo_next.ptr    ; store ptr
            bsr     qemu.bootinfo_next
            movea.l d0,a2           ; a2 = next record

            ; print "REC "
            lea     .str_rec,a0
            bsr     qemu.puts
            ; print tag as 4-digit hex
            move.w  d4,d0           ; restore tag from d4 (16-bit)
            moveq   #3,d7
2$:         rol.w   #4,d0
            move.b  d0,d3
            and.b   #$f,d3
            add.b   #'0',d3
            cmp.b   #'9',d3
            bls     3$
            add.b   #7,d3
3$:         move.l  d3,qemu.TTY_PUT_CHAR
            dbra    d7,2$
            ; print " size="
            lea     .str_size,a0
            bsr     qemu.puts
            ; print size as 4-digit hex
            move.w  d5,d0           ; restore size (16-bit)
            moveq   #3,d7
4$:         rol.w   #4,d0
            move.b  d0,d3
            and.b   #$f,d3
            add.b   #'0',d3
            cmp.b   #'9',d3
            bls     5$
            add.b   #7,d3
5$:         move.l  d3,qemu.TTY_PUT_CHAR
            dbra    d7,4$
            ; print " data="
            lea     .str_data,a0
            bsr     qemu.puts
            ; print each data byte as 2-digit hex
            movea.l a3,a0
            adda.l  #4,a0           ; a0 -> data payload
            move.l  d5,d1
            subq.l  #4,d1           ; d1 = data size
            ble     7$              ; skip if size<=4 (no payload bytes)
            moveq   #0,d6
6$:         move.b  (a0)+,d3
            lsr.b   #4,d3
            add.b   #'0',d3
            cmp.b   #'9',d3
            bls     8$
            add.b   #7,d3
8$:         move.l  d3,qemu.TTY_PUT_CHAR
            move.b  -1(a0),d3
            and.b   #$f,d3
            add.b   #'0',d3
            cmp.b   #'9',d3
            bls     10$
            add.b   #7,d3
10$:        move.l  d3,qemu.TTY_PUT_CHAR
            move.b  #' ',d3
            move.l  d3,qemu.TTY_PUT_CHAR
            addq.l  #1,d6
            cmp.l   d1,d6
            blo     6$
7$:         ; newline (must use 32-bit write, goldfish TTY ignores byte writes)
            moveq   #10,d0
            move.l  d0,qemu.TTY_PUT_CHAR
            bra     1$

9$:         lea     .str_end,a0
            bra     qemu.puts

.str_rec:   dc.b "REC ",0
.str_size:  dc.b " size=",0
.str_data:  dc.b " data=",0
.str_end:   dc.b "--- end bootinfo ---",10,0
            ; !notreached!
        }}
    }
}

sys {
    ; ------- lowlevel system routines --------

    const ubyte target = 68         ;  compilation target specifier.

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


    sub  reset_system()  {
        %asm {{
            movea.l  #qemu.CTRL_REG_CMD,a1
            move.l   #qemu.CTRL_CMD_RESET,(a1)
            stop #$2000
        }}
    }

    sub poweroff_system() {
        %asm {{
            movea.l  #qemu.CTRL_REG_CMD,a1
            move.l   #qemu.CTRL_CMD_HALT,(a1)
            stop #0
        }}
    }

    sub die(ubyte code, str message) {
        ; -- kill the program: print death message to goldfish TTY, then exit.
        str @shared warning = iso:"\n\nPROGRAM DIED: "
        %asm {{
            lea  sys.die.warning,a0
.loop1:
            move.b  (a0)+,d0
            beq  .done1
            move.l  d0,qemu.TTY_PUT_CHAR
            bra.s .loop1
.done1:
            move.l  sys.die.message,a0
.loop2:
            move.b  (a0)+,d0
            beq  .done2
            move.l  d0,qemu.TTY_PUT_CHAR
            bra.s  .loop2
.done2:
            moveq  #10,d0
            move.l  d0,qemu.TTY_PUT_CHAR
        }}
        exit(code)
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        ; TODO implement the wait
    }

; ------- memory routines --------

    asmsub memcopy(long source @D0, long tgt @D1, uword count @D2) {
        ; Copy bytes from source to target.
        ; Bulk of the work uses long-aligned longword copies;
        ; pre-alignment and remainder bytes use individual byte copies.
        %asm {{
            movea.l  d0,a0
            movea.l  d1,a1
            move.w   d2,d2
            beq      .done

            ; check if both addresses share the same alignment offset
            move.l   a0,d0
            move.l   a1,d3
            eor.l    d3,d0
            btst     #1,d0
            bne      .bytes

            ; same alignment: pre-align both to a long boundary
            move.l   a0,d3
            and.w    #3,d3             ; d3 = offset (0-3)
            beq      .bulk
            move.w   d3,d0             ; d0 = bytes to align
            cmp.w    d2,d0
            bls      .pre
            move.w   d2,d0             ; cap at count left

.pre:
            subq.w   #1,d0
.loop_pre:
            move.b   (a0)+,(a1)+
            subq.w   #1,d2             ; track remaining count
            dbra     d0,.loop_pre
            beq      .done

.bulk:
            move.w   d2,d3
            move.w   d3,d0
            and.w    #3,d0             ; d0 = remainder 0-3 bytes after longwords
            lsr.w    #2,d3             ; d3 = longword pairs
            beq      .rem
            subq.w   #1,d3
.loop_l:
            move.l   (a0)+,(a1)+
            dbra     d3,.loop_l

.rem:
            subq.w   #1,d0
            bmi      .done
.loop_rem:
            move.b   (a0)+,(a1)+
            dbra     d0,.loop_rem
.done:
            rts

.bytes:
            subq.w   #1,d2
.loop_b:
            move.b   (a0)+,(a1)+
            dbra     d2,.loop_b
            rts
        }}
    }

    asmsub memset(long mem @D0, uword numbytes @D1, ubyte value @D2) {
        %asm {{
            movea.l  d0,a0
            tst.w    d1
            beq      .done
            tst.b    d2
            beq      sys.memclear       ; tail-call: value==0

            ; save fill byte zero-extended
            moveq    #0,d3
            move.b   d2,d3

            ; align address to even for word/longword transfers
            move.l   a0,d0
            btst     #0,d0
            beq      .aligned
            move.b   d3,(a0)+
            subq.l   #1,d1
            beq      .done

.aligned:
            ; expand fill byte to all byte positions in d3
            move.l   d3,d0
            lsl.l    #8,d0
            or.l     d0,d3
            move.l   d3,d0
            swap     d0
            or.l     d0,d3

            ; longword fill (4 bytes per iteration)
            move.l   d1,d0
            lsr.l    #2,d0
            beq      .bytes
            subq.w   #1,d0
.loop_l:
            move.l   d3,(a0)+
            dbra     d0,.loop_l

            ; remaining bytes (0-3)
.bytes:
            and.w    #3,d1
            beq      .done
            subq.w   #1,d1
.loop_b:
            move.b   d3,(a0)+
            dbra     d1,.loop_b
.done:
            rts
        }}
    }

    asmsub memclear(long mem @D0, uword numbytes @D1) {
        ; Clear memory (fill with zero).  Uses movem.l d0-d7,-(a0)
        ; for the bulk clear (32 bytes per instruction, decrementing from
        ; the end address).  Handles misaligned start and remaining bytes.
        ; All instructions are plain 68000-compatible.
        %asm {{
            movea.l  d0,a0
            moveq    #0,d0
            move.w   d1,d0           ; d0 = numbytes (zero-extended)
            beq      .done

            ; align to even for word/longword access
            move.l   a0,d1
            btst     #0,d1
            beq      .compute
            clr.b    (a0)+
            subq.l   #1,d0
            beq      .done

.compute:
            move.l   d0,d1
            move.l   d1,d2
            and.w    #31,d2          ; d2 = tail bytes after 32-byte blocks
            lsr.l    #5,d1           ; d1 = number of 32-byte blocks
            move.l   d2,a2           ; a2 = tail bytes
            movea.l  d1,a1           ; a1 = block count (save before zeroing regs)

            ; point a0 past the last byte, so we can decrement with movem
            move.l   d1,d3
            lsl.l    #5,d3
            adda.l   d3,a0           ; a0 = end address

            ; zero all data registers except d7 (d7 will be the loop counter, not in movem)
            move.l   d1,d7           ; d7 = block count (save before zeroing)
            moveq    #0,d0
            move.l   d0,d1
            move.l   d0,d2
            move.l   d0,d3
            move.l   d0,d4
            move.l   d0,d5
            move.l   d0,d6
            movea.l  d0,a1           ; also zero a1 (part of movem)

            ; bulk clear: 32 bytes per movem (d0-d6=28 bytes + a1=4 bytes), decrementing a0
            subq.w   #1,d7
            bmi      .tail
.loop_m:
            movem.l  d0-d6/a1,-(a0)
            dbra     d7,.loop_m

.tail:
            movea.l  a2,a3
            move.l   a3,d0           ; copy to data reg for shift/btst
            beq      .done
            move.l   d0,d1
            lsr.l    #2,d1           ; d1 = longwords in tail
            beq      .tail_words
            subq.l   #1,d1
.loop_l:
            clr.l    (a0)+
            subq.l   #1,d1
            bpl      .loop_l

.tail_words:
            btst     #1,d0
            beq      .tail_byte
            clr.w    (a0)+

.tail_byte:
            btst     #0,d0
            beq      .done
            clr.b    (a0)+
.done:
            rts
        }}
    }

    asmsub memsetw(long mem @D0, uword numwords @D1, uword value @D2) {
        ; Fill memory with the given 16-bit word value, for the given number of words.
        ; Word-aligned start: fast path with longword stores (2 words at a time).
        ; Odd start: first byte written individually to realign, then same fast path
        ; with byte-swapped fill to keep the pattern correct, plus trailing byte.
        %asm {{
            movea.l  d0,a0
            moveq    #0,d0
            move.w   d1,d0           ; d0 = numwords (zero-extended)
            beq      .done
            move.w   d2,d1           ; d1 = fill word value

            move.l   a0,d3
            btst     #0,d3
            beq      .aligned

            ; odd start: write high byte to align to even
            move.b   d1,(a0)+
            subq.l   #1,d0
            blt      .done
            ; swap fill bytes so aligned fills continue the pattern correctly
            move.w   d1,d2
            swap     d2              ; d2 = fill word, bytes swapped
            moveq    #1,d3           ; flag: odd start (need trailing byte)
            bra      .setup

.aligned:
            move.w   d1,d2           ; d2 = fill word as-is
            moveq    #0,d3           ; flag: even start (no trailing byte)

.setup:
            move.l   d2,d4
            swap     d4
            move.w   d2,d4           ; d4 = d2 : d2  (longword fill pattern)

            ; longword fill (2 words at a time)
            move.l   d0,d5
            lsr.l    #1,d5           ; d5 = numwords / 2
            beq      .odd_word
            subq.w   #1,d5
.loop_l:
            move.l   d4,(a0)+
            dbra     d5,.loop_l

.odd_word:
            btst     #0,d0
            beq      .trailing
            move.w   d2,(a0)+

.trailing:
            tst.b    d3
            beq      .done
            move.b   d1,(a0)         ; original value's low byte to complete last word
.done:
            rts
        }}
    }

    sub exit(ubyte returnvalue) {
        ; -- immediately exit the program with a return code in the D0 register
        %asm {{
            moveq.l  #0,d0
            move.b   sys.exit.returnvalue,d0
        }}
        poweroff_system()
    }

    sub set_carry() {
        %asm {{
            moveq  #1,d0
            move.w  d0,ccr
        }}
    }

    sub clear_carry() {
        %asm {{
            moveq  #0,d0
            move.w  d0,ccr
        }}
    }


    sub clear_irqd() {
        %asm {{
            andi.w  #$f8ff,sr       ; privileged !
        }}
    }

    sub set_irqd() {
        %asm {{
            ori.w  #$0700,sr        ; privileged !
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
}
