%import textio
%import string
%zeropage basicsafe

main {
    sub start() {
        cx16.set_chrin_keyhandler(0, &keystroke_handler)
        cbm.CHRIN()
    }

    sub keystroke_handler() -> ubyte {
        %asm {{
            sta  cx16.r0L
        }}
        uword cmdxx = grab_cmdline()
        if_cs {
            ; first entry, decide if we want to override
            if cx16.r0L==9 {
                ; intercept TAB
                sys.clear_carry()
                return 0
            }
            sys.set_carry()
            return 0
        } else {
            if cx16.r0L==9 {
                %asm {{
                    brk ; BOOM
                }}
                uword cmd = grab_cmdline()
                if cmd and cmd[0] {
                    ;cx16.r5++
                }
                return '!'
            }
            return 0    ; eat all other characters
        }

        sub grab_cmdline() -> uword {
            cx16.r9++
            return $5000
        }
    }
}
