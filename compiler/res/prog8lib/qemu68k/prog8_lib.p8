%option no_symbol_prefixing, ignore_unused

p8_sys_startup {
    %option force_output

    sub init_system() {
    }
    sub init_system_phase2() {
    }
    sub cleanup_at_exit() {
        %asm {{
            movea.l  #qemu.CTRL_REG_CMD,a1
            move.l   #qemu.CTRL_CMD_HALT,(a1)
        }}
    }
}
