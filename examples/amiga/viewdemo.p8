; Bare-metal display demo: 320x256x8 on AGA, displaying all 256 colors.
; The custom.compute_display helper computes shadow registers;
; this program writes the actual custom registers from them, which also
; keeps the programmed values available for inspection.

%import exec
%import graphics
%import custom

main {
    const word WIDTH = 320
    const word HEIGHT = 256
    const ubyte DEPTH = 8

    ; Chip-RAM bitmap backing the display; the shadow registers live in the
    ; custom.compute_display returns a pointer to them).
    ^^graphics.BitMap bm
    ^^custom.DisplayRegs regs

    sub start() {
        if setup_bitmap() == false
            return
        regs = custom.compute_display(WIDTH, HEIGHT, DEPTH, custom.DISPLAY_DEFAULT, bm)
        if regs == 0 {
            free_bitmap()
            return
        }
        custom.grab_system(true)
        custom.waitvsync()
        install_registers()
        setup_palette()
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_RASTER
        paint_stuff()
        repeat 100 {
            custom.waitvsync()
            install_pointers()
        }
        custom.FMODE = $0000      ; back to 16-bit fetch before the OS takes over
        custom.restore_system()
        free_bitmap()
    }

    sub install_registers() {
        custom.DIWSTRT = regs.diwstrt
        custom.DIWSTOP = regs.diwstop
        custom.DDFSTRT = regs.ddfstrt
        custom.DDFSTOP = regs.ddfstop
        custom.BPLCON0 = regs.bplcon0
        custom.BPLCON1 = regs.bplcon1
        custom.BPL1MOD = regs.bpl1mod
        custom.BPL2MOD = regs.bpl2mod
        if custom.detect_chipset() == custom.CHIPSET::AGA
            custom.FMODE = regs.fmode
        install_pointers()
    }

    sub install_pointers() {
        for p in 0 to DEPTH - 1 {
            ^^pointer reg = $dff0e0 as pointer + (p as uword) * 4
            reg[0] = regs.planes[p]
        }
    }

    sub setup_bitmap() -> bool {
        bm = exec.AllocMem(sizeof(graphics.BitMap), exec.MEMF_CLEAR)
        if bm == 0
            return false
        graphics.InitBitMap(bm, DEPTH as byte, WIDTH, HEIGHT)
        for p in 0 to DEPTH - 1 {
            bm.Planes[p] = graphics.AllocRaster(WIDTH as uword, HEIGHT as uword)
            if bm.Planes[p] == 0
                return false
        }
        return true
    }

    sub free_bitmap() {
        for p in 0 to bm.Depth - 1 {
            graphics.FreeRaster(bm.Planes[p], bm.BytesPerRow * 8, bm.Rows)
        }
        exec.FreeMem(bm, sizeof(graphics.BitMap))
    }

    sub setup_palette() {
        for c in 0 to 255 {
            ubyte r = c as ubyte
            ubyte g = ((c * 37) & 255) as ubyte
            ubyte b = ((c * 97) & 255) as ubyte
            custom.set_aga_color(c as ubyte, ((r as long) << 16) | ((g as long) << 8) | (b as long))
        }
    }

    sub paint_stuff() {
        ; Write the 16x16 grid of 8x8 color cells straight into the
        ; bitplanes: plane p of cell c is filled when bit p of c is set.
        uword bpr = bm.BytesPerRow
        for c in 0 to 255 {
            ubyte col = c as ubyte
            uword base = ((c >> 4) as uword) * 8 * bpr + ((c & 15) as uword)
            for p in 0 to bm.Depth - 1 {
                ^^ubyte plane = bm.Planes[p] as ^^ubyte
                ubyte fill = 0
                if (col & ($01 << p)) != 0
                    fill = $ff
                for dy in 0 to 7 {
                    plane[base + (dy as uword) * bpr] = fill
                }
            }
        }
    }
}
