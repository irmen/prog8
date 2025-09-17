; Particle fountain. (for the Virtual target)
; This is NOT necessarily the most efficient or idiomatic Prog8 way to do this!
; But it is just an example for how you could allocate and use structs dynamically.
; It uses a linked list to store all active particles.


%import math

main  {

    struct Particle {
        word x,y
        byte speedx, speedy
        ubyte brightness
        ^^Particle next
    }

    const uword MAX_PARTICLES = 450
    const ubyte GRAVITY = 1

    ^^Particle particles            ; linked list of all active particles
    uword active_particles = 0

    sub start() {

        repeat 4
            spawnrandom()

        sys.gfx_enable(0)       ; enable lo res screen

        repeat {
            sys.gfx_clear(0)
            update_particles()
            sys.wait(2)
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

            sys.gfx_plot(pp.x as uword, pp.y as uword, pp.brightness)
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
        uword result = next
        next += size
        return result
    }
}
