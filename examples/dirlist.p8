%import textio
%import diskio
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start() {
        const ubyte max_files = 8
        uword[max_files] blocks
        uword[max_files] names
        str filenamesbuffer = "?????????????????" * max_files

        ubyte num_files=0

        txt.print("\nfiles starting with 'cub':\n")
        num_files = diskio.listfiles(8, "cub", false, names, blocks, filenamesbuffer, max_files)
        print_listing()
        txt.print("\nfiles ending with 'gfx':\n")
        num_files = diskio.listfiles(8, "gfx", true, names, blocks, filenamesbuffer, max_files)
        print_listing()
        txt.print("\nfiles, no filtering (limited number):\n")
        num_files = diskio.listfiles(8, 0, false, names, blocks, filenamesbuffer, max_files)
        print_listing()
        ;test_stack.test()

        sub print_listing() {
            if num_files==0
                txt.print("no files found.\n")
            else {
                ubyte i
                for i in 0 to num_files-1 {
                    txt.print_ub(i+1)
                    txt.print(": ")
                    txt.print(names[i])
                    txt.print("   = ")
                    txt.print_uw(blocks[i])
                    txt.print(" blocks\n")
                }
            }
        }
    }
}
