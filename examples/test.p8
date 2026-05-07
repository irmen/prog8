%zeropage basicsafe
%option no_sysinit

main {
    uword ptr = 1000
    uword @shared ptr_var = 2000
    uword terrain = memory("terrain", 320, 0)
    uword @shared terrain_var = memory("terrainvar", 320, 0)

    sub start() {
        cx16.r0L = terrain[10]          ; TODO fix weird  AST transformation with <<
        cx16.r1L = terrain_var[10]
        cx16.r2L = ptr[10]
        cx16.r3L = ptr_var[10]

        terrain[0] = 50         ; TODO fix weird  AST transformation with <<
        terrain_var[0] = 50
        ptr[0] = 50
        ptr_var[0] = 50

        terrain[319] = 200      ; TODO fix weird  AST transformation with <<
        terrain_var[319] = 200
        ptr[319] = 200
        ptr_var[319] = 200
    }
}
