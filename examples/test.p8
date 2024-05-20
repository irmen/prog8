%zeropage basicsafe

main {
    sub start() {
        bool @split bb
        ubyte @split bt
        byte[200] @split ba
        float @split fl
        float[10] @split fla

        bb=true
        bt++
        fl++
    }
}
