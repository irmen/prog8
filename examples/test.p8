%zeropage basicsafe

main {
    sub start() {
    }

    private inline sub derp() {
        cx16.r0++
    }

    private inline asmsub foo() {
        %asm {{
            tay
        }}
    }
}
