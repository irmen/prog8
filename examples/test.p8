%import c64lib
%import c64utils

main {

    sub start() {
        A = testsub(33)
    }

    asmsub testsub(ubyte foo @stack) -> ubyte @stack {
        %asm {{
            Y=44
            rts
        }}
    }

}
