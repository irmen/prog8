%option no_symbol_prefixing, ignore_unused

p8_sys_startup {
    asmsub init_system() {
        %asm {{
            rts
        }}
    }
    asmsub init_system_phase2() {
        %asm {{
            rts
        }}
    }
    asmsub cleanup_at_exit() {
        %asm {{
            trap  #0
            ; !notreached!
        }}
    }
}
