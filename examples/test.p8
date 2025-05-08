%zeropage basicsafe

main {
    sub start() {
        const uword cbuffer = $2000
        cx16.r1 = &cbuffer[cx16.r0]        ; ERROR
    }
}
