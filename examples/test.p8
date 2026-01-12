%import textio
%import diskio
%import strings
%zeropage basicsafe
%option no_sysinit

main {
    uword buffer=memory("filenames", 2000, 0)

    sub start() {
        txt.iso()

        str name = iso:"Irmen De Jong"
        txt.print(name)
        txt.nl()
        strings.lower(name)
        txt.print(name)
        txt.nl()
        sys.exit(1)
        txt.lowercase()
        ; diskio.chdir("ww/world")

        ubyte num_files = diskio.list_filenames_nocase("irmen*", buffer, 2000)
        txt.print_ub(num_files)
        txt.nl()
        uword fptr = buffer
        repeat num_files {
            txt.print(fptr)
            txt.nl()
            fptr += strings.length(fptr) + 1
        }
        sys.exit(1)

        diskio.lf_start_list_nocase("irmen*.txt")
        while diskio.lf_next_entry() {
            txt.print(diskio.list_filename)
            txt.nl()
        }
        diskio.lf_end_list()

        txt.nl()

        diskio.lf_start_list_files_nocase("irmen*.txt")
        while diskio.lf_next_entry() {
            txt.print(diskio.list_filename)
            txt.nl()
        }
        diskio.lf_end_list()

        txt.nl()

        diskio.lf_start_list_dirs_nocase("z*")
        while diskio.lf_next_entry() {
            txt.print(diskio.list_filename)
            txt.nl()
        }
        diskio.lf_end_list()
    }
}
