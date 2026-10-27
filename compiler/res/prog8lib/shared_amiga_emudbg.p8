; Emulator debug interface for the Amiga emulator Copperline (with partial WinUAE support).
; console_write, console_nl and exitemu use the WinUAE-compatible uaelib trap (function 86/13)
; and work on both WinUAE and Copperline. The remaining routines (console_chrout, console_value_*,
; console_hex_*, debug_arg, debug_arg_str, debug_printf) use Copperline-specific debug ports
; at $BFFF00/$BFFF04 and only work on Copperline.
; Debug output appears on the emulator's host console (as 'DBG:' lines) and in the debugger console.
; Docs: https://copperline.dev/docs/run#debugging
;
; WARNING: every routine here first probes the uaelib trap area at $F0FF60 to detect
; the emulator. That probe is harmless inside the emulator, but on real Amiga hardware
; the probe can hang the machine (A500/A2000 have no bus timeout) or cause a bus error
; (A1200). Only run code using this module inside an emulator, not on real hardware.

emudbg {
    %option merge, ignore_unused

    asmsub is_emulator() -> bool @D0 {
        ; the uaelib trap stub at $F0FF60 starts with a JSR instruction ($4EB9);
        ; WinUAE's alternative A-line form starts with $A00E
        %asm {{
            moveq   #0,d0
            move.w  $f0ff60,d1
            cmp.w   #$4eb9,d1
            beq.s   .present
            cmp.w   #$a00e,d1
            bne.s   .done
.present
            moveq   #1,d0
.done
            rts
        }}
    }

    sub exitemu() {
        ; ask the emulator to terminate the session (uaelib ExitEmu, function 13):
        ; the window closes / a headless run stops and the emulator process exits with status 0
        if is_emulator()
            call_trap(13)
    }

    sub debug_arg(long value) {
        ; queue one 32-bit printf argument at the $BFFF00 port; consume it later with
        ; debug_printf(). At most 32 arguments can be queued for a single message.
        if is_emulator()
            write_arg(value)
    }

    sub debug_arg_str(str text) {
        ; queue a pointer to a NUL-terminated string as printf argument, for the %s conversion
        if is_emulator()
            write_arg_text(text)
    }

    sub debug_printf(str format) {
        ; write a pointer to a NUL-terminated printf-style format string to the $BFFF04 port:
        ; this prints one message with the queued arguments and clears the argument queue.
        ; Conversions: %d %i %u %x %X %o %p %c %s %b, use 'l' for 32-bit values, %% is a literal percent sign.
        ; Without 'l', the integer conversions use the low 16 bits of the argument (signed for %d/%i).
        if is_emulator()
            write_format(format)
    }

    sub console_write(str text) {
        ; write a string to the emulator's debug console (WinUAE-compatible via trap 86).
        ; Works on both WinUAE and Copperline. Adds a trailing newline.
        if is_emulator()
            write_trap_console(text)
    }

    sub console_nl() {
        ; write a newline to the emulator's debug console (WinUAE-compatible via trap 86)
        if is_emulator()
            write_trap_console("")
    }

    sub console_chrout(ubyte char) {
        ; write a single character to the emulator's debug console
        if is_emulator() {
            write_arg(char)
            write_format("%c")
        }
    }

    sub console_value_word(word value) {
        ; write a signed 16-bit value as decimal to the debug console, followed by a newline
        if is_emulator() {
            write_arg(value)
            write_format("%d\n")
        }
    }

    sub console_value_long(long value) {
        ; write a signed 32-bit value as decimal to the debug console, followed by a newline
        if is_emulator() {
            write_arg(value)
            write_format("%ld\n")
        }
    }

    sub console_hex_word(uword value) {
        ; write a 16-bit value as 4 hex digits to the debug console, followed by a newline
        if is_emulator() {
            write_arg(value)
            write_format("%04x\n")
        }
    }

    sub console_hex_long(long value) {
        ; write a 32-bit value as 8 hex digits to the debug console, followed by a newline
        if is_emulator() {
            write_arg(value)
            write_format("%08lx\n")
        }
    }

    private asmsub write_arg(long value @D0) {
        %asm {{
            move.l  d0,$bfff00
            rts
        }}
    }

    private asmsub write_arg_text(str text @D0) {
        %asm {{
            move.l  d0,$bfff00
            rts
        }}
    }

    private asmsub write_format(str format @D0) {
        %asm {{
            move.l  d0,$bfff04
            rts
        }}
    }

    private asmsub write_trap_console(str text @D0) {
        ; WinUAE-compatible debug output via the uaelib trap (function 86).
        ; Prints "DBG: %s\n" to the host console. Works on both WinUAE and Copperline.
        %asm {{
            move.l  d0,-(sp)
            move.l  #86,-(sp)
            jsr     $f0ff60
            addq.l  #8,sp
            rts
        }}
    }

    private asmsub call_trap(long functionNumber @D0) {
        ; call the uaelib trap like a C function: the function number is the first stack argument
        %asm {{
            move.l  d0,-(sp)
            jsr     $f0ff60
            addq.l  #4,sp
            rts
        }}
    }
}
