; NOTE: meant to test to virtual machine output target (use -target virtual)

%import math

main  {

    const ubyte MAX_PARTICLES = 128

    sub start() {
        word[MAX_PARTICLES] particleX
        word[MAX_PARTICLES] particleY
        byte[MAX_PARTICLES] particleDX
        byte[MAX_PARTICLES] particleDY

        ubyte pi
        for pi in 0 to MAX_PARTICLES-1 {
            particleX[pi] = math.rndw() % 319 as word
            particleY[pi] = math.rndw() % 240 as word
            particleDX[pi] = (math.rnd() & 1)*2 as byte - 1
            particleDY[pi] = (math.rnd() & 1)*2 as byte - 1
        }

        sys.gfx_enable(0)       ; enable lo res screen

        repeat {
            fade()
            plot_particles()
            sys.wait(1)
            sys.waitvsync()
        }

        sub fade() {
            uword xx
            uword yy
            for yy in 0 to 239 {
                for xx in 0 to 319 {
                    ubyte pixel = sys.gfx_getpixel(xx, yy)
                    if pixel>4 {
                        pixel-=4
                        sys.gfx_plot(xx, yy, pixel)
                    }
                }
            }
        }

        sub plot_particles() {
            for pi in 0 to MAX_PARTICLES-1 {
                particleX[pi] += particleDX[pi]
                particleY[pi] += particleDY[pi]
                if particleX[pi]<0 or particleX[pi]>319 {
                    particleDX[pi] *= -1
                    particleX[pi] += particleDX[pi] * 2
                }
                if particleY[pi]<0 or particleY[pi]>239 {
                    particleDY[pi] *= -1
                    particleY[pi] += particleDY[pi] * 2
                }
                sys.gfx_plot(particleX[pi] as uword, particleY[pi] as uword, 255)
            }
        }
    }
}
