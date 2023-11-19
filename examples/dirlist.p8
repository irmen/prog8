%import textio
%import diskio
%zeropage basicsafe
%import test_stack
%option no_sysinit

; Note: this program can be compiled for multiple target systems.

main {
    sub start() {
        diskio.drivenumber = 8
        txt.print("showing the full directory of drive 8:\n")
        void diskio.directory()
        txt.nl()

        if diskio.lf_start_list("test*") {
            txt.print("\nfiles starting with 'test':\n")
            while diskio.lf_next_entry() {
                txt.print(diskio.list_filename)
                txt.print("  ")
                txt.print_uw(diskio.list_blocks)
                txt.print(" blocks\n")
            }
            diskio.lf_end_list()
        } else {
            txt.print("error\n")
        }

        if diskio.lf_start_list("*bin") {
            txt.print("\nfiles ending with 'bin':\n")
            while diskio.lf_next_entry() {
                txt.print(diskio.list_filename)
                txt.print("  ")
                txt.print_uw(diskio.list_blocks)
                txt.print(" blocks\n")
            }
            diskio.lf_end_list()
        } else {
            txt.print("error\n")
        }

        if diskio.lf_start_list(0) {
            txt.print("\nfirst 10 files:\n")
            ubyte counter=0
            while counter < 10 and diskio.lf_next_entry() {
                txt.print(diskio.list_filename)
                txt.print("  ")
                txt.print_uw(diskio.list_blocks)
                txt.print(" blocks\n")
                counter++
            }
            diskio.lf_end_list()
        } else {
            txt.print("error\n")
        }

        ; test_stack.test()
    }
}
