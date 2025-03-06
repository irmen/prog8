%import sprites
%import coroutines
%import math


animsprites {
    uword num_iterations
    ubyte[64] sx
    ubyte[64] sy
    ubyte[64] sc
    ubyte[64] dx
    ubyte[64] dy
    uword maximum_duration

    sub benchmark(uword max_duration) -> uword {
        maximum_duration = max_duration
        math.rndseed(1122,9876)
        cx16.set_screen_mode(3)
        cx16.mouse_config2(1)
        sprites.set_mousepointer_hand()
        repeat 64
            void coroutines.add(animsprite, 0)
        cx16.mouse_config2(0)

        cbm.SETTIM(0,0,0)
        coroutines.run(supervisor)

        sprites.reset(0, 64)
        return num_iterations
    }

    sub supervisor() -> bool {
        if cbm.RDTIM16() >= maximum_duration {
            coroutines.killall()
            return false
        }
        return true
    }

    sub animsprite() {
        num_iterations++
        ; set up the sprite
        ubyte sprnum = coroutines.current()
        cx16.r6L, cx16.r7 = sprites.get_data_ptr(0)
        sprites.init(sprnum, cx16.r6L, cx16.r7, sprites.SIZE_16, sprites.SIZE_16, sprites.COLORS_256, 0)
        sx[sprnum] = math.rnd()
        sy[sprnum] = math.rnd()
        sc[sprnum] = math.rnd()
        dx[sprnum] = if math.rnd()&1 == 1  1 else 255
        dy[sprnum] = if math.rnd()&1 == 1  1 else 255

        ; move the sprite around
        while sc[sprnum]!=0 {
            animate(sprnum)
            void coroutines.yield()
            sprnum = coroutines.current()
        }

        sub animate(ubyte spr) {
            defer sc[spr]--
            sprites.pos(spr, sx[spr], sy[spr])
            sx[spr] += dx[spr]
            sy[spr] += dy[spr]
        }

        ; end the task but replace it with a fresh animated sprite task
        void coroutines.add(animsprite, 0)
    }
}
