%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
            %asm {{

                rmb 0,$22
                smb 1,$22
                bbr 2,$22
                bbs 3,$22
            }}

    }
}
