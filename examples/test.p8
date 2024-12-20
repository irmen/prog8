%import compression

main {
    sub start() {
        ; compression.decode_rle(0,0,0)
        ; compression.decode_zx0(0,0)
        compression.decode_tscrunch(0,0)
    }
}
