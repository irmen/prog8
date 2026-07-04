; Prog8 definitions for the Qemu M68k target

%option no_symbol_prefixing, ignore_unused

qemu {
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
        ; -- kill the program by jumping into the debugger/monitor (if available). Status code is in register A, a pointer to the death message is in X,Y.
        str @shared warning = iso:"\n\nPROGRAM DIED: "
        ; TODO print die message and exit qemu
        poweroff_system()
    }

    sub wait(uword jiffies) {
        ; --- wait approximately the given number of jiffies (1/60th seconds)
        ; TODO implement the wait
    }

    sub memcopy(uword source, uword tgt, uword count)  {
        ; TODO implement memcopy
    }

    sub memset(uword mem, uword numbytes, ubyte value)  {
        ; TODO implement memset
    }

    sub memsetw(uword mem, uword numwords, uword value)  {
        ; TODO implement memsetw
    }

    sub memcmp(uword address1, uword address2, uword size) -> byte {
        ; Compares two blocks of memory of up to 65535 bytes in size
        ; Returns -1 (255), 0 or 1, meaning: block 1 sorts before, equal or after block 2.
        ; TODO implement memcmp
        return 0
    }

    sub exit(ubyte returnvalue) {
        ; -- immediately exit the program with a return code in the D0 register
        ; TODO implement exit
        poweroff_system()
    }

    sub set_carry() {
        ; TODO is this 68000-68030 compatible?
        %asm {{
            moveq  #1,d0
            move.w  d0,ccr
        }}
    }

    sub clear_carry() {
        ; TODO is this 68000-68030 compatible?
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
        return $10000          ; fixed for now  TODO should be a linker symbol?
    }

    sub progend() -> long {
        return $000ffffe        ; just a dummy value TODO should be a linker symbol
    }
}
