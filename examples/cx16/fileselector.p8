%import diskio
%import textio
%import sorting
%import strings
%zeropage basicsafe

; A "TUI" for an interactive file selector, that scrolls the selection list if it doesn't fit on the screen.
; Depends a lot on diskio routines, and uses the drive set in the diskio.drivenumber variable (usually just 8)

; TODO also show directories (how to distinguish them? what with the result value? start with a slash , so they sort together too?)
; TODO joystick control? mouse control?
; TODO keyboard typing; jump to the first entry that starts with that character?


main {
    sub start() {

        fileselector.configure(20, 10, 20, 2)
        uword chosen = fileselector.select("*")
        txt.nl()
        txt.print_ub(cx16.getrambank())
        txt.nl()
        if chosen!=0 {
            txt.print("chosen: ")
            txt.print(chosen)
            txt.nl()
        } else {
            txt.print("nothing chosen or error!\n")
            txt.print(diskio.status())
        }
    }
}

fileselector {

    const uword filenamesbuffer = $a000      ; use a HIRAM bank
    const uword filenamesbuf_size = $1e00    ; leaves room for a 256 entry string pointer table at $be00-$bfff
    const uword filename_ptrs_start = $be00  ; array of 256 string pointers for each of the names in the buffer. ends with $0000.
    ubyte dialog_topx = 4
    ubyte dialog_topy = 10
    ubyte max_lines = 20
    ubyte buffer_rambank = 1    ; default hiram bank to use for the data buffers

    str chosen_filename = "?" * 32
    uword name_ptr
    ubyte num_visible_files


    sub configure(ubyte column, ubyte row, ubyte max_entries, ubyte rambank) {
        dialog_topx = column
        dialog_topy = row
        max_lines = max_entries
        buffer_rambank = rambank
    }

    sub select(str pattern) -> uword {
        ubyte old_bank = cx16.getrambank()
        cx16.rambank(buffer_rambank)
        defer cx16.rambank(old_bank)

;        if pattern!=0 and pattern[0]==0
;            pattern = 0        ; force pattern to be 0 instead of empty string, to be compatible with prog8 11.0 or older

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
        ; sort alphabetically
        sorting.shellsort_pointers(filename_ptrs_start, num_files, sorting.string_comparator)

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
                '[',130,157 -> {    ; PAGEUP, cursor left
                    ; previous page of lines
                    invert(selected_line)
                    if selected_line==0
                        repeat max_lines scroll_list_backward()
                    selected_line = 0
                    invert(selected_line)
                    print_up_and_down()
                }
                ']',2,29 -> {      ; PAGEDOWN, cursor right
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
                if @(name)==0
                    break
                txt.chrout(128)     ; don't print control characters
                txt.chrout(@(name))
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
            for y in row to row+height-2 {
                for x in col to col+width-1 {
                    txt.setchr(x,y, txt.getchr(x, y+1))
                }
            }
            y = row+height-1
            for x in col to col+width-1 {
                txt.setchr(x,y, fillchar)
            }
        }

        sub scroll_txt_down(ubyte col, ubyte row, ubyte width, ubyte height, ubyte fillchar) {
            for y in row+height-1 downto row+1 {
                for x in col to col+width-1 {
                    txt.setchr(x,y, txt.getchr(x, y-1))
                }
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
