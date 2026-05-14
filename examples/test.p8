%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        bool @shared xx = DO_RESET_MEDIA()
    }

    sub DO_RESET_MEDIA() -> bool {
        return (peekl(&cx16.r5) > 3333333)
    }
}
