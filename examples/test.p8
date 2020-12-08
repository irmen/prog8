%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack
%option no_sysinit

errors {
    sub tofix() {

        while c64.CHRIN() {
            ; TODO: the loop condition isn't properly tested because a ldx is in the way before the beq
        }

        repeat {
            ubyte char2 = c64.CHRIN()
            if char2==0         ; TODO condition not properly tested after optimizing because there's only a sta char2 before it (works without optimizing)
                break
        }

        repeat {
            ubyte char3 = c64.CHRIN()
            if_z
                break   ; TODO wrong jump asm generated, works fine if you use a label instead to jump to
        }

        ; TODO fix undefined symbol:
        repeat {
            ubyte char = c64.CHRIN()
            ; ...
        }
;        do {
;            char = c64.CHRIN()      ; TODO fix undefined symbol error, should refer to 'char' above in the subroutine's scope
;        } until char==0

    }
}

main {
    sub start() {

        ubyte result = diskio.directory(8)

        txt.print("result: ")
        txt.print_ub(result)
        txt.chrout('\n')
        test_stack.test()

        diskio.status(8)
        txt.chrout('\n')
        txt.chrout('\n')
        txt.chrout('\n')
        test_stack.test()
        return

        const ubyte max_files = 10
        uword[max_files] blocks
        str filenames = "?????????????????" * max_files

        ubyte num_files=0
        ; num_files = diskio.listfiles(8, ".bin", true, filenames, blocks, max_files)
        txt.print("num files: ")
        txt.print_ub(num_files)
        txt.chrout('\n')

        test_stack.test()

        if num_files>0 {
            ubyte i
            uword filenameptr = &filenames
            for i in 0 to num_files-1 {
                txt.print_ub(i+1)
                txt.print(": ")
                txt.print(filenameptr)
                txt.print(" (")
                txt.print_uw(blocks[i])
                txt.print(" blocks)\n")
                filenameptr += strlen(filenameptr) + 1
            }
        }

        test_stack.test()
    }
}
