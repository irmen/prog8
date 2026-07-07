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
    ; MIN_FLOAT and MAX_FLOAT are defined in the floats module if imported


    sub  reset_system()  {
        %asm {{
            movea.l  #qemu.CTRL_REG_CMD,a1
            move.l   #qemu.CTRL_CMD_RESET,(a1)
        }}
    }

    sub poweroff_system() {
        %asm {{
            movea.l  #qemu.CTRL_REG_CMD,a1
            move.l   #qemu.CTRL_CMD_HALT,(a1)
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
            bra  .loop1
.done1:
            move.l  sys.die.message,a0
.loop2:
            move.b  (a0)+,d0
            beq  .done2
            move.l  d0,qemu.TTY_PUT_CHAR
            bra  .loop2
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

    sub memcopy(^^ubyte source, ^^ubyte tgt, uword count)  {
        %asm {{
            movea.l  sys.memcopy.source,a0
            movea.l  sys.memcopy.tgt,a1
            moveq  #0,d2
            move.w  sys.memcopy.count,d2
            beq  .done
            subq.w  #1,d2
.loop:
            move.b  (a0)+,(a1)+
            dbra  d2,.loop
.done:
        }}
    }

    sub memset(^^ubyte mem, uword numbytes, ubyte value)  {
        %asm {{
            movea.l  sys.memset.mem,a0
            moveq  #0,d1
            move.w  sys.memset.numbytes,d1
            beq  .done
            moveq  #0,d2
            move.b  sys.memset.value,d2

            ; align address to even for word/longword transfers
            move.l  a0,d3
            btst  #0,d3
            beq  .aligned
            move.b  d2,(a0)+
            subq.l  #1,d1
            beq  .done

.aligned:
            ; expand fill byte to all byte positions in d2
            move.l  d2,d3
            lsl.l   #8,d3
            or.l   d3,d2
            move.l  d2,d3
            swap   d3
            or.l   d3,d2

            ; longword fill (4 bytes per iteration)
            move.l  d1,d0
            lsr.l   #2,d0
            beq  .bytes
            subq.w  #1,d0
.loop_l:
            move.l  d2,(a0)+
            dbra  d0,.loop_l

            ; remaining bytes (0-3)
.bytes:
            and.w  #3,d1
            beq  .done
            subq.w  #1,d1
.loop_b:
            move.b  d2,(a0)+
            dbra  d1,.loop_b
.done:
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
        ; TODO implement m68k CLI
    }

    sub set_irqd() {
        ; TODO implement m68k SEI
    }

    sub progstart() -> long {
        %asm {{
            lea  prog8_program_start,a0
            move.l  a0,d0
            rts
        }}
    }

    asmsub progend() -> long @D0 {
        %asm {{
            lea  prog8_program_end,a0
            move.l  a0,d0
            rts
        }}
    }
}
