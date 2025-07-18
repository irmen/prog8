; Particle fountain.
; based on fixed array allocation of arrays of all the particle's properties.

%import math

main  {

    const ubyte MAX_PARTICLES = 255
    const ubyte GRAVITY = 1

    word[MAX_PARTICLES] particleX
    word[MAX_PARTICLES] particleY
    byte[MAX_PARTICLES] particleSpeedX
    byte[MAX_PARTICLES] particleSpeedY
    ubyte[MAX_PARTICLES] particleBrightness

    ubyte active_particles = 0

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
            initparticle(active_particles)
            active_particles++
        }
    }

    sub initparticle(ubyte pi) {
        particleX[pi] = 160
        particleY[pi] = 238
        particleSpeedX[pi] = math.rnd() % 10 as byte -5
        if particleSpeedX[pi]==0
            particleSpeedX[pi]=1
        particleSpeedY[pi] = -10 - math.rnd() % 12
        particleBrightness[pi] = 255
    }

    sub update_particles() {
        ubyte pi
        for pi in 0 to active_particles-1 {

            particleSpeedY[pi] += GRAVITY
            particleX[pi] += particleSpeedX[pi]
            particleY[pi] += particleSpeedY[pi]

            if particleY[pi] >= 239 {
                ; reuse the particle that went off the screen and spawn another one (if allowed)
                initparticle(pi)
                spawnrandom()
            } else {
                particleX[pi] = clamp(particleX[pi], 0, 319)
            }

            sys.gfx_plot(particleX[pi] as uword, particleY[pi] as uword, particleBrightness[pi])
            if particleBrightness[pi]>=7
                particleBrightness[pi] -= 7
        }
    }
}
