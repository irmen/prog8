; clone of the old Scramble arcade game.
; TODO work in progress...

%import c64lib
%import c64utils
%zeropage basicsafe

main {

    ubyte perform_scroll = false

    sub start() {
        c64scr.plot(30,2)
        c64scr.print("skramble !")

        c64.SCROLX = c64.SCROLX & %11110111     ; 38 column mode

        c64utils.set_rasterirq(1)     ; enable animation

        ubyte target_height = 10
        ubyte active_height = 25

        forever {
            if active_height < target_height {
                active_height++
            } else if active_height > target_height {
                active_height--
            } else {
                target_height = 8 + rnd() % 16
                continue
            }

            while not perform_scroll {
                ; let the raster irq do its timing job
            }
            perform_scroll = false
            c64scr.scroll_left_full(true)
            ubyte yy
            for yy in 0 to active_height-1 {
                c64scr.setcc(39, yy, 32, 2)         ; clear top
            }
            for yy in active_height to 24 {
                c64scr.setcc(39, yy, 102, 8)        ; draw mountain
            }

            yy = rnd()
            if yy > 100 {
                ; draw a star
                c64scr.setcc(39, yy % (active_height-1), '.', rnd())
            }

            if yy > 200 {
                ; draw a tree
                ubyte tree = 30
                ubyte treecolor = 5
                if yy & %01000000 != 0
                    tree = 88
                else if yy & %00100000 != 0
                    tree = 65
                if rnd() > 130
                    treecolor = 13
                c64scr.setcc(39, active_height-1, tree, treecolor)
            }

            if yy > 235 {
                ; draw a camel
                c64scr.setcc(39, active_height-1, 94, 9)
            }

            ; check ship crash
;            ubyte shipchar3 = c64scr.getchr(2, 10)
;            if (shipchar3!=@'.' and shipchar3!=@' ') {
;                break       ; game over
;            }

            ; draw space ship
;            c64scr.setcc(0, 10, @'*', 15)
;            c64scr.setcc(1, 10, @'=', 15)
;            c64scr.setcc(2, 10, @'>', 15)

        }
    }
}


irq {
    ubyte smoothx=7
    sub irq() {
        smoothx = (smoothx-1) & 7
        main.perform_scroll = smoothx==0
        c64.SCROLX = (c64.SCROLX & %11111000) | smoothx
    }
}
