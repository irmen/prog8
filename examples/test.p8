%zeropage basicsafe

main {
    sub start() {
        word[5] xpos

        xpos[4] &= $fff8
        xpos[4] &= $fff8 as word
        xpos[4] = xpos[4] & $fff8
        xpos[4] = xpos[4] & $fff8 as word

        xpos[4] &= $7000
        xpos[4] &= $7000 as word
        xpos[4] = xpos[4] & $7000
        xpos[4] = xpos[4] & $7000 as word

        xpos[4] |= $7000
        xpos[4] |= $7000 as word
        xpos[4] = xpos[4] | $7000
        xpos[4] = xpos[4] | $7000 as word

        xpos[4] += $7000
        xpos[4] += $7000 as word
        xpos[4] = xpos[4] + $7000
        xpos[4] = xpos[4] + $7000 as word
    }
}
