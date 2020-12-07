%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack

main {
    sub start() {

        ubyte result = diskio.directory(8)

        txt.print("result: ")
        txt.print_ub(result)
        txt.chrout('\n')

        diskio.status(8)
        txt.chrout('\n')
        txt.chrout('\n')
        txt.chrout('\n')

        const ubyte max_files = 10
        uword[max_files] blocks
        str filenames = "?????????????????" * max_files

        ubyte num_files = diskio.listfiles(8, ".bin", true, filenames, blocks, max_files)
        txt.print("num files: ")
        txt.print_ub(num_files)
        txt.chrout('\n')
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
    }
}
