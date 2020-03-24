; clone of the old Scramble arcade game.
; TODO work in progress...

%import c64lib
%import c64utils
%zeropage basicsafe

main {

    sub start() {
        c64scr.plot(30,2)
        c64scr.print("skramble !")

        ubyte target_height = 10
        ubyte active_height = 25

        forever {
            if active_height < target_height {
                active_height++
            } else if active_height > target_height {
                active_height--
            } else {
                target_height = 10 + rnd() % 13
                continue
            }

            c64scr.scroll_left_full(true)
            ubyte yy
            for yy in 0 to active_height-1 {
                c64scr.setcc(39, yy, 32, 2)
            }
            for yy in active_height to 24 {
                c64scr.setcc(39, yy, 102, 8)
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

            for A in 0 to 4 {
                while @($d012) != 100 {
                    ; just wait for the raster beam...
                }
            }
        }
    }
}
