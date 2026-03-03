%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        sprptr = 4000
        poke(4000+2*sizeof(Sprite), 0)
        pokew(4000+2*sizeof(Sprite)+1, 4242)
        txt.print_uw(sprptr[2].y)
        txt.nl()
        sprptr[2]^^.y = 9999
        txt.print_uw(sprptr[2].y)
        txt.nl()

;        pokew(sprptr as uword + (sizeof(Sprite) as uword)*2 + offsetof(Sprite.y), 99)
;        sprptr[cx16.r0L]^^.y = 99
;        pokew(sprptr as uword + (sizeof(Sprite) as uword)*cx16.r0L + offsetof(Sprite.y), 99)
;
;        sprites[2]^^.y = 99
;        pokew(sprites[2] as uword + offsetof(Sprite.y), 99)
;        sprites[cx16.r0L]^^.y = 99
;        pokew(sprites[cx16.r0L] as uword + offsetof(Sprite.y), 99)
    }

    struct Sprite {
        ubyte x
        uword y
    }

    ^^Sprite[4] @shared sprites
    ^^Sprite @shared sprptr
}
