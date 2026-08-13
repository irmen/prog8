%zeropage basicsafe

main {
    private inline asmsub foo() {
        %asm {{
            tay
        }}
    }
}
