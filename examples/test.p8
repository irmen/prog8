%import textio
%import strings
%import diskio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        diskio.lf_start_list("")
        while diskio.lf_next_entry() {
            txt.print_uw(diskio.list_blocks)
            txt.spc()
            txt.print(diskio.list_filename)
            txt.nl()
        }
        diskio.lf_end_list()
    }
}
