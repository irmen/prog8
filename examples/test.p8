%import textio
%zeropage basicsafe

main {
    sub start() {
        move()
    }

    sub move() {
        ubyte mb = cx16.mouse_pos()
        ubyte @shared xx = cx16.r0H
        ubyte @shared yy = cx16.r0L
        func(cx16.r0H)
        func(cx16.r0L)
        cx16.vpoke(1, $fc08+2, cx16.r0H)
        cx16.vpoke(1, $fc08+3, cx16.r0L)
    }

    asmsub func(ubyte qq @A) {
        %asm {{
            rts
        }}
    }
}
