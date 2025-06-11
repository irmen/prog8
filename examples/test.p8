main {
    sub start() {
        cx16.r0++
    }
}

some_block {
    uword buffer = memory("arena", 2000, 0)
}


other_block {
    sub  redherring  (uword buffer)  {
        %ir {{
            loadm.w r99000,other_block.redherring.buffer
        }}
    }
}
