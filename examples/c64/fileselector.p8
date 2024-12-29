%import diskio
%import textio
%import strings
%zeropage basicsafe

; A "TUI" for an interactive file selector, that scrolls the selection list if it doesn't fit on the screen.
; Depends a lot on diskio routines, and uses the drive set in the diskio.drivenumber variable (usually just 8)

; TODO sort entries alphabetically? Or not, because C64/C128 directories tend to be not very large.


main {
    sub start() {

        ;; fileselector.configure(1, 5, 5)
        uword chosen = fileselector.select("*")
        txt.nl()
        if chosen!=0 {
            txt.print("\nchosen: ")
            txt.print(chosen)
            txt.nl()
        } else {
            txt.print("\nnothing chosen or error!\n")
            txt.print(diskio.status())
        }
    }
}

fileselector {

    uword filenamesbuffer
    uword filename_ptrs_start             ; array of 127 string pointers for each of the names in the buffer. ends with $0000.
    const uword filenamesbuf_size = $0f00
    ubyte dialog_topx = 4
    ubyte dialog_topy = 1
    ubyte max_lines = 15

    str chosen_filename = "?" * 32
    uword name_ptr
    ubyte num_visible_files

    sub configure(ubyte column, ubyte row, ubyte max_entries) {
        dialog_topx = column
        dialog_topy = row
        max_lines = max_entries
    }

    sub select(str pattern) -> uword {
        if sys.target==64 {
            ; c64 has memory at $c000
            filenamesbuffer = $c000
            filename_ptrs_start = $cf00
        } else if sys.target==128 {
            ; c128 has memory up to $c000  (if you use prog8's init code)
            filenamesbuffer = $b000
            filename_ptrs_start = $bf00
        } else {
            ; assume PET32, memory upto $8000
            filenamesbuffer = $7000
            filename_ptrs_start = $7f00
        }

        num_visible_files = 0
        chosen_filename[0] = 0
        name_ptr = diskio.diskname()
        if name_ptr==0 or cbm.READST()!=0
            return 0

        txt.cls()
        txt.plot(dialog_topx, dialog_topy)
        txt.print("┌")
        linepart()
        txt.print("┐\n")
        txt.column(dialog_topx)
        txt.print("│ drive ")
        txt.print_ub(diskio.drivenumber)
        txt.print(": '")
        txt.print(name_ptr)
        txt.chrout('\'')
        txt.column(dialog_topx+31)
        txt.print("│\n")
        txt.column(dialog_topx)
        txt.print("│   scanning directory...      │\n")
        txt.column(dialog_topx)
        footerline()

        ubyte num_files = diskio.list_filenames(pattern, filenamesbuffer, filenamesbuf_size)    ; use Hiram bank to store the files
        ubyte selected_line
        ubyte top_index
        uword filename_ptrs

        construct_name_ptr_array()
        ; initial display
        txt.plot(dialog_topx+2, dialog_topy+2)
        txt.print("select file:  (")
        txt.print_ub(num_files)
        txt.print(" total)")
        txt.column(dialog_topx+31)
        txt.print("│\n")
        txt.column(dialog_topx)
        txt.print("│ stop or q to abort           |\n")
        txt.column(dialog_topx)
        txt.print("├")
        linepart()
        txt.print("┤\n")
        print_up_indicator(false)
        if num_files>0 {
            for selected_line in 0 to min(max_lines, num_files)-1 {
                txt.column(dialog_topx)
                txt.print("│ ")
                print_filename(peekw(filename_ptrs_start+selected_line*$0002))
                txt.column(dialog_topx+31)
                txt.print("│\n")
                num_visible_files++
            }
        } else {
            txt.column(dialog_topx)
            txt.print("│ no matches.")
            txt.column(dialog_topx+31)
            txt.print("│\n")
        }
        print_down_indicator(false)
        txt.column(dialog_topx)
        footerline()
        selected_line = 0
        invert(selected_line)
        print_up_and_down()

        repeat {
            if cbm.STOP2()
                return 0

            ubyte key = cbm.GETIN2()
            when key {
                3, 27, 'q' -> return 0      ; STOP or Q  aborts  (and ESC?)
                '\n',' ' -> {
                    if num_files>0 {
                        void strings.copy(peekw(filename_ptrs_start + (top_index+selected_line)*$0002), chosen_filename)
                        return chosen_filename
                    }
                    return 0
                }
                '[', 157 -> {       ; cursor left
                    ; previous page of lines
                    invert(selected_line)
                    if selected_line==0
                        repeat max_lines scroll_list_backward()
                    selected_line = 0
                    invert(selected_line)
                    print_up_and_down()
                }
                ']', 29 -> {        ; cursor right
                    if num_files>0 {
                        ; next page of lines
                        invert(selected_line)
                        if selected_line == max_lines-1
                            repeat max_lines scroll_list_forward()
                        selected_line = num_visible_files-1
                        invert(selected_line)
                        print_up_and_down()
                    }
                }
                17 -> {     ; down
                    if num_files>0 {
                        invert(selected_line)
                        if selected_line<num_visible_files-1
                            selected_line++
                        else if num_files>max_lines
                            scroll_list_forward()
                        invert(selected_line)
                        print_up_and_down()
                    }
                }
                145 -> {    ; up
                    invert(selected_line)
                    if selected_line>0
                        selected_line--
                    else if num_files>max_lines
                        scroll_list_backward()
                    invert(selected_line)
                    print_up_and_down()
                }
            }
        }

        ubyte x,y

        sub construct_name_ptr_array() {
            filename_ptrs = filename_ptrs_start
            name_ptr = filenamesbuffer
            repeat num_files {
                pokew(filename_ptrs, name_ptr)
                while @(name_ptr)!=0
                    name_ptr++
                name_ptr++
                filename_ptrs+=2
            }
            pokew(filename_ptrs, 0)
        }

        sub print_filename(uword name) {
            repeat 28 {      ; maximum length displayed
                cx16.r0L = @(name)
                if_z
                    break
                if strings.isprint(cx16.r0L)     ; filter out control characters
                    txt.chrout(@(name))
                else
                    txt.chrout('?')
                name++
            }
        }

        sub scroll_list_forward() {
            if top_index+max_lines< num_files {
                top_index++
                ; scroll the displayed list up 1
                scroll_txt_up(dialog_topx+2, dialog_topy+6, 28, max_lines, sc:' ')
                ; print new name at the bottom of the list
                txt.plot(dialog_topx+2, dialog_topy+6+max_lines-1)
                print_filename(peekw(filename_ptrs_start + (top_index+ selected_line)*$0002))
            }
        }

        sub scroll_list_backward() {
            if top_index>0 {
                top_index--
                ; scroll the displayed list down 1
                scroll_txt_down(dialog_topx+2, dialog_topy+6, 28, max_lines, sc:' ')
                ; print new name at the top of the list
                txt.plot(dialog_topx+2, dialog_topy+6)
                print_filename(peekw(filename_ptrs_start + top_index * $0002))
            }
        }

        sub scroll_txt_up(ubyte col, ubyte row, ubyte width, ubyte height, ubyte fillchar) {
            ; scroll the rectangle in the screen memory matrix directly, using txt.setchr/getchr is too slow for that
            uword rowptr = cbm.Screen + row * $0028
            repeat height-1 {
                for x in col to col+width-1 {
                    @(rowptr + x) = @(rowptr + x + 40)
                }
                rowptr += 40
            }
            y = row+height-1
            for x in col to col+width-1 {
                txt.setchr(x,y, fillchar)
            }
        }

        sub scroll_txt_down(ubyte col, ubyte row, ubyte width, ubyte height, ubyte fillchar) {
            ; scroll the rectangle in the screen memory matrix directly, using txt.setchr/getchr is too slow for that
            uword rowptr = cbm.Screen + (row+height-1) * $0028
            repeat height-1 {
                for x in col to col+width-1 {
                    @(rowptr + x) = @(rowptr + x - 40)
                }
                rowptr -= 40
            }
            for x in col to col+width-1 {
                txt.setchr(x,row, fillchar)
            }
        }

        sub print_up_and_down() {
            if num_files<=max_lines
                return
            print_up_indicator(top_index>0)
            print_down_indicator(top_index + num_visible_files < num_files)
        }

        sub print_up_indicator(bool shown) {
            txt.plot(dialog_topx, dialog_topy+5)
            txt.chrout('│')
            txt.column(dialog_topx+26)
            if shown
                txt.print("(up) │\n")
            else
                txt.print("     │\n")
        }

        sub print_down_indicator(bool shown) {
            txt.plot(dialog_topx, dialog_topy+6+num_visible_files)
            txt.chrout('│')
            txt.column(dialog_topx+24)
            if shown
                txt.print("(down) │\n")
            else
                txt.print("       │\n")
        }

        sub footerline() {
            txt.chrout('└')
            linepart()
            txt.chrout('┘')
        }

        sub linepart() {
            repeat 30 txt.chrout('─')
        }

        sub invert(ubyte line) {
            cx16.r1L = dialog_topy+6+line
            ubyte charpos
            for charpos in dialog_topx+1 to dialog_topx+30 {
                txt.setchr(charpos, cx16.r1L, txt.getchr(charpos, cx16.r1L) ^ 128)
            }
        }
    }
}
