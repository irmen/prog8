main {
    sub start() {
        %asm {{
            lda  #<blockname
            lda  #<blockname.subroutine
correctlabel:
            nop     ; rout orrectlab locknam
        }}
    }

}

blockname {
    sub subroutine() {
        @($c000) = 0
    }

    sub correctlabel() {
        @($c000) = 0
    }
}

; all block and subroutines below should NOT be found in asm because they're only substrings of the names in there, or in a comment
locknam {
    sub rout() {
        @($c000) = 0
    }

    sub orrectlab() {
        @($c000) = 0
    }
}

