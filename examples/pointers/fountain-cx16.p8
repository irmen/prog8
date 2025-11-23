; Particle fountain. (for the cx16 target)
; This is NOT necessarily the most efficient or idiomatic Prog8 way to do this!
; But it is just an example for how you could allocate and use structs dynamically.
; It uses a linked list to store all active particles.
; NOTE: this example uses the X16's kernal graphics routines to plot the pixels.
;       those are relatively slow, and the program will run a lot faster if you use
;       the gfx_lores or monogfx prog8 library instead. However those increase the
;       PRG size by a lot because they embed a large multiplication lookup table.


%import textio
%import math
%import syslib

main  {

    struct Particle {
        word x,y
        byte speedx, speedy
        ubyte brightness
        ^^Particle next
    }

    const uword MAX_PARTICLES = 300
    const ubyte GRAVITY = 1

    ^^Particle particles            ; linked list of all active particles
    uword active_particles = 0

    sub start() {

        repeat 4
            spawnrandom()

        cx16.set_screen_mode(128)       ; enable lowres graphics
        cx16.GRAPH_set_colors(1,1,0)
        cx16.GRAPH_clear()

        repeat {
            clear_particles()
            update_particles()
            txt.home()
            txt.print_uw(active_particles)
            sys.waitvsync()
            sys.waitvsync()
        }
    }

    sub spawnrandom() {
        if active_particles < MAX_PARTICLES {
            ^^Particle pp = arena.alloc(sizeof(Particle))
            pp.next = particles
            particles = pp
            initparticle(pp)
            active_particles++
        }
    }

    sub initparticle(^^Particle pp) {
        pp.x = 160
        pp.y = 238
        pp.speedx = math.rnd() % 10 as byte -5
        if pp.speedx==0
           pp.speedx=1
        pp.speedy = -10 - math.rnd() % 12
        pp.brightness = 255
    }

    sub clear_particles() {
        ^^Particle pp = particles
        while pp!=0 {
            cx16.FB_cursor_position(pp.x as uword, pp.y as uword)
            cx16.FB_set_pixel(0)
            pp = pp.next
        }
    }

    sub update_particles() {
        ^^Particle pp = particles
        while pp!=0 {
            pp.speedy += GRAVITY
            pp.x += pp.speedx
            pp.y += pp.speedy

            if pp.y >= 239 {
                ; reuse the particle that went off the screen and spawn another one (if allowed)
                initparticle(pp)
                spawnrandom()
            } else {
                pp.x = clamp(pp.x, 0, 319)
            }

            cx16.FB_cursor_position(pp.x as uword, pp.y as uword)
            cx16.FB_set_pixel(16+pp.brightness/16)

            if pp.brightness>=7
                pp.brightness -= 7

            pp = pp.next
        }
    }
}



arena {
    ; extremely trivial arena allocator (that never frees)
    uword buffer = memory("arena", 4000, 0)
    uword next = buffer

    sub alloc(ubyte size) -> uword {
        defer next += size
        return next
    }
}
