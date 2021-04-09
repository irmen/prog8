%import textio
%zeropage basicsafe

main {
    sub start() {
        const ubyte world_width=100

    struct Color {
        ubyte red
        ubyte green
        ubyte blue
    }

    Color rgb = [255,world_width,0]     ; note that struct initializer value is same as an array
    ;rgb = [255,world_width,0]     ; note that struct initializer value is same as an array
    ;rgb = [255,world_width/2,0]     ; note that struct initializer value is same as an array


;        struct Entity {
;            ubyte active
;            ubyte x
;            ubyte y
;            byte direction
;        }
;
;        Entity pede
;        pede = [1, 1, 0, -1]
    }
}

