main {
    sub start() {
        cx16.r1H = %00000001        ; enable auto-indent
        cx16.r2L = 4
        cx16.r2H = 80
        cx16.r3L = 8
        cx16.r3H = 11<<4 | 7;  background_color<<4 | text_color
        cx16.r4 = 0
        cx16.r1L = 0
        cx16.rombank($d)
        %asm {{
            ldx #2
            ldy #64
            jmp $c006
        }}
    }
}

