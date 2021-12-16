%option enable_floats

main {
    sub start() {
        ubyte[] @shared @zp array = [1,2,3,4]
        str @shared @zp name = "test"
        ubyte @shared @zp bytevar = 0
        float @shared @zp fl

        %asm {{
            lda  array
            lda  name
            lda  bytevar
        }}
    }
}
