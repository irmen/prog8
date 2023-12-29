; Emulator debug interface.
; Docs: https://github.com/X16Community/x16-emulator#emulator-io-registers

emudbg {
    %option ignore_unused

    const uword EMU_BASE = $9fb0

    &ubyte EMU_DBG_HOTKEY_ENABLED = EMU_BASE + 0
    &ubyte EMU_LOG_VIDEO          = EMU_BASE + 1
    &ubyte EMU_LOG_KEYBOARD       = EMU_BASE + 2
    &ubyte EMU_ECHO_MODE          = EMU_BASE + 3
    &ubyte EMU_SAVE_ON_EXIT       = EMU_BASE + 4
    &ubyte EMU_RECORD_GIF         = EMU_BASE + 5
    &ubyte EMU_RECORD_WAV         = EMU_BASE + 6
    &ubyte EMU_CMDKEYS_DISABLED   = EMU_BASE + 7
    &ubyte EMU_CPUCLK_L           = EMU_BASE + 8
    &ubyte EMU_CPUCLK_M           = EMU_BASE + 9
    &ubyte EMU_CPUCLK_H           = EMU_BASE + 10
    &ubyte EMU_CPUCLK_U           = EMU_BASE + 11
    &ubyte EMU_CPUCLK_RESET       = EMU_BASE + 8     ; write: reset cpu clock to 0
    &ubyte EMU_DBGOUT1            = EMU_BASE + 9     ; write: outputs "User debug 1: $xx"
    &ubyte EMU_DBGOUT2            = EMU_BASE + 10    ; write: outputs "User debug 2: $xx"
    &ubyte EMU_CHROUT             = EMU_BASE + 11    ; write: outputs as character to console
    ; 12 is unused for now
    &ubyte EMU_KEYMAP             = EMU_BASE + 13
    &ubyte EMU_EMU_DETECT1        = EMU_BASE + 14
    &ubyte EMU_EMU_DETECT2        = EMU_BASE + 15

    sub is_emulator() -> bool {
        ; Test for emulator presence.
        ; It is recommended to only use the debug registers if this returns true.
        return EMU_EMU_DETECT1=='1' and EMU_EMU_DETECT2=='6'
    }

    sub console_write(str isoString) {
        ; note: make sure the text is in Iso encoding.
        if is_emulator() {
            ubyte chr
            repeat {
                chr = @(isoString)
                if_z
                    break
                EMU_CPUCLK_U = chr
                isoString++
            }
        }
    }

    sub console_chrout(ubyte char) {
        ; note: make sure the character is in Iso encoding.
        if is_emulator()
            EMU_CHROUT = char
    }

    sub console_value1(ubyte value) {
        if is_emulator()
            EMU_DBGOUT1 = value
    }

    sub console_value2(ubyte value) {
        if is_emulator()
            EMU_DBGOUT2 = value
    }
}
