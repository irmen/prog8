%import diskio
%import textio
%zeropage basicsafe

; A "GUI" for an interactive file selector, that scrolls the selection list if it doesn't fit on the screen.

; TODO sort entries alphabetically
; TODO also show directories (how to distinguish them? what with the result value?)
; TODO joystick control? mouse control?
; TODO keyboard typing; jump to the first entry that starts with that character


main {
    sub start() {

        uword chosen = showdir.show(8, 2)       ; use hiram bank 2 for buffers
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

showdir {

    const uword filenamesbuf = $a000        ; use HIRAM bank
    const uword filenamesbuf_size = $2000
    const ubyte DIALOG_TOPX = 4
    const ubyte DIALOG_TOPY = 2
    const ubyte MAX_LINES = 20

    str chosen_filename = "?" * 32
    uword name_ptr
    ubyte num_visible_files

    sub show(ubyte drive, ubyte rambank) -> uword {
        ubyte old_bank = cx16.getrambank()
        cx16.rambank(rambank)
        defer cx16.rambank(old_bank)

        num_visible_files = 0
        chosen_filename[0] = 0
        diskio.drivenumber = drive
        name_ptr = diskio.diskname()
        if name_ptr==0 or cbm.READST()!=0
            return 0

        txt.cls()
        txt.plot(DIALOG_TOPX, DIALOG_TOPY)
        txt.print("┌")
        linepart()
        txt.print("┐\n")
        txt.column(DIALOG_TOPX)
        txt.print("│ drive ")
        txt.print_ub(diskio.drivenumber)
        txt.print(": '")
        txt.print(name_ptr)
        txt.chrout('\'')
        txt.column(DIALOG_TOPX+31)
        txt.print("│\n")
        txt.column(DIALOG_TOPX)
        txt.print("│   scanning directory...      │\n")
        txt.column(DIALOG_TOPX)
        footerline()

        ubyte num_files = diskio.list_filenames(0, filenamesbuf, filenamesbuf_size)    ; use Hiram bank to store the files
        ubyte selected_line
        ubyte top_index
        uword[MAX_LINES] visible_names

        txt.plot(DIALOG_TOPX+2, DIALOG_TOPY+2)
        txt.print("select file:  (")
        txt.print_ub(num_files)
        txt.print(" total)")
        txt.column(DIALOG_TOPX+31)
        txt.print("│\n")
        txt.column(DIALOG_TOPX)
        txt.print("│ stop or q to abort           |\n")
        txt.column(DIALOG_TOPX)
        txt.print("├")
        linepart()
        txt.print("┤\n")
        print_up_indicator(false)
        name_ptr = filenamesbuf
        for selected_line in 0 to min(MAX_LINES, num_files)-1 {
            if cbm.STOP2() break

            txt.column(DIALOG_TOPX)
            txt.print("│ ")

            visible_names[selected_line] = name_ptr        ; keep pointer to name
            while @(name_ptr)!=0 {
                txt.chrout(@(name_ptr))
                name_ptr++
            }
            txt.column(DIALOG_TOPX+31)
            txt.print("│\n")
            name_ptr++
            num_visible_files++
        }
        print_down_indicator(false)
        txt.column(DIALOG_TOPX)
        footerline()

        name_ptr = filenamesbuf
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
                    void strings.copy(visible_names[selected_line], chosen_filename)
                    return chosen_filename
                }
                '[',130 -> {    ; PAGEUP
                    ; previous page of lines
                    invert(selected_line)
                    if selected_line==0
                        repeat MAX_LINES scroll_list_backward()
                    selected_line = 0
                    invert(selected_line)
                    print_up_and_down()
                }
                ']',2 -> {      ; PAGEDOWN
                    ; next page of lines
                    invert(selected_line)
                    if selected_line == MAX_LINES-1
                        repeat MAX_LINES scroll_list_forward()
                    selected_line = num_visible_files-1
                    invert(selected_line)
                    print_up_and_down()
                }
                17 -> {     ; down
                    invert(selected_line)
                    if selected_line<num_visible_files-1
                        selected_line++
                    else if num_files>MAX_LINES
                        scroll_list_forward()
                    invert(selected_line)
                    print_up_and_down()
                }
                145 -> {    ; up
                    invert(selected_line)
                    if selected_line>0
                        selected_line--
                    else if num_files>MAX_LINES
                        scroll_list_backward()
                    invert(selected_line)
                    print_up_and_down()
                }
            }
        }

        ubyte x,y

        sub scroll_list_forward() {
            if top_index+MAX_LINES< num_files {
                top_index++
                ; scroll the displayed list up 1
                scroll_txt_up(DIALOG_TOPX+2, DIALOG_TOPY+6, 28, MAX_LINES, sc:' ')
                ; shift the name pointer array 1 to the left
                for y in 0 to MAX_LINES-2
                    visible_names[y]=visible_names[y+1]

                ; set name pointer array last element with newly appeared name
                name_ptr = visible_names[MAX_LINES-1]
                while @(name_ptr)!=0
                    name_ptr++
                name_ptr++
                visible_names[MAX_LINES-1] = name_ptr

                ; print that new name at the bottom of the list
                txt.plot(DIALOG_TOPX+2, DIALOG_TOPY+6+MAX_LINES-1)
                txt.print(name_ptr)
            }
        }

        sub scroll_list_backward() {
            if top_index>0 {
                top_index--
                ; scroll the displayed list down 1
                scroll_txt_down(DIALOG_TOPX+2, DIALOG_TOPY+6, 28, MAX_LINES, sc:' ')
                ; shift the name pointer array 1 to the right
                for y in MAX_LINES-1 downto 1
                    visible_names[y]=visible_names[y-1]

                ; set name pointer array element 0 with newly appeared name
                name_ptr = visible_names[0]-2
                while name_ptr>filenamesbuf and @(name_ptr)!=0
                    name_ptr--
                if @(name_ptr)==0
                    name_ptr++
                visible_names[0] = name_ptr

                ; print that new name at the top of the list
                txt.plot(DIALOG_TOPX+2, DIALOG_TOPY+6)
                txt.print(name_ptr)
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
            if num_files<=MAX_LINES
                return
            print_up_indicator(top_index>0)
            print_down_indicator(num_visible_files < num_files)
        }

        sub print_up_indicator(bool shown) {
            txt.plot(DIALOG_TOPX, DIALOG_TOPY+5)
            txt.chrout('│')
            txt.column(30)
            if shown
                txt.print("(up) │\n")
            else
                txt.print("     │\n")
        }

        sub print_down_indicator(bool shown) {
            txt.plot(DIALOG_TOPX, DIALOG_TOPY+6+num_visible_files)
            txt.chrout('│')
            txt.column(28)
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
            cx16.r1L = DIALOG_TOPY+6+line
            ubyte charpos
            for charpos in DIALOG_TOPX+1 to DIALOG_TOPX+30 {
                txt.setchr(charpos, cx16.r1L, txt.getchr(charpos, cx16.r1L) ^ 128)
            }
        }
    }
}
