%target cx16
%import test_stack
%import textio
%zeropage basicsafe
%option no_sysinit


main {

    sub start() {
        txt.lowercase()
        txt.print("\nAssembler.\nEmpty line to stop.\n")

        textparse.user_input()
        ; benchmark.benchmark()

        ; test_stack.test()
    }

}

textparse {
    str[16] addr_modes =     ["Imp", "Acc", "Imm", "Zp", "ZpX", "ZpY", "Rel", "Abs", "AbsX", "AbsY", "Ind", "IzX", "IzY", "Zpr", "Izp", "IaX" ]
    ubyte[16] operand_size = [0,     0,     1,     1,    1,     1,     1,     2,     2,      2,      2,     1,     1,     1,     1,     1]

    str input_line = "?" * 40
    uword[3] word_addrs
    uword program_counter = $1000

    sub user_input() {
        repeat {
            ubyte input_length = 0
            txt.chrout('A')
            txt.print_uwhex(program_counter, 1)
            txt.print(": ")
            ; simulate user always having at least one space at the start
            input_line[0] = ' '
            input_length = txt.input_chars(&input_line+1)
            txt.chrout('\n')

            if not input_length {
                txt.print("exit\n")
                return
            }

            preprocess_assignment_spacing()
            split_input()
            ; debug_print_words()

            if word_addrs[1] and @(word_addrs[1])=='='
                do_assign()
            else
                do_label_or_instr()
        }
    }

    sub do_assign() {
        ; target is in word_addrs[0], value is in word_addrs[2]   ('=' is in word_addrs[1])
        if not word_addrs[2] {
            txt.print("?syntax error\n")
            return
        }

        uword value = parse_number(word_addrs[2])
        if strcmp("*", word_addrs[0])==0 {
            if value == $ffff {
                txt.print("?invalid address\n")
                return
            }
            program_counter = value
        } else {
            set_symbol(word_addrs[0], value)
        }
    }

    sub do_label_or_instr() {
        uword label_ptr = 0
        uword instr_ptr = 0
        uword operand_ptr = 0
        ubyte starts_with_whitespace = input_line[0]==' ' or input_line[0]==9 or input_line[0]==160

        if word_addrs[2] {
            label_ptr = word_addrs[0]
            instr_ptr = word_addrs[1]
            operand_ptr = word_addrs[2]
            lowercase(operand_ptr)
        } else if word_addrs[1] {
            if starts_with_whitespace {
                instr_ptr = word_addrs[0]
                operand_ptr = word_addrs[1]
                lowercase(operand_ptr)
            } else {
                label_ptr = word_addrs[0]
                instr_ptr = word_addrs[1]
            }
        } else if word_addrs[0] {
            if starts_with_whitespace
                instr_ptr = word_addrs[0]
            else
                label_ptr = word_addrs[0]
        }

        if label_ptr {
            uword lastlabelchar = label_ptr + strlen(label_ptr)-1
            if @(lastlabelchar) == ':'
                @(lastlabelchar) = 0
            if instructions.match(label_ptr) {
                txt.print("?label cannot be a mnemonic\n")
                return
            }
            set_symbol(label_ptr, program_counter)
        }
        if instr_ptr {
;                txt.print("instr: ")
;                txt.print(instr_ptr)
;                txt.chrout('\n')

;                if operand_ptr {
;                    txt.print("operand: ")
;                    txt.print(operand_ptr)
;                    txt.chrout('\n')
;                }

            assemble_instruction(instr_ptr, operand_ptr)
        }
    }

    sub assemble_instruction(uword instr_ptr, uword operand_ptr) {
        uword instruction_info_ptr = instructions.match(instr_ptr)
        if instruction_info_ptr {
            ubyte addr_mode = instructions.determine_addrmode(operand_ptr)
            ubyte opcode = instructions.opcode(instruction_info_ptr, addr_mode)
            if_cc {
                txt.print("?invalid operand\n")
            } else {
                ubyte num_operand_bytes = operand_size[addr_mode-1]
;                txt.print("(debug:) addr.mode: ")
;                txt.print_ub(addr_mode)
;                txt.chrout('\n')
                txt.chrout(' ')
                txt.print_uwhex(program_counter, 1)
                txt.print("   ")
                emit(opcode)
                repeat num_operand_bytes {
                    emit($00)   ; TODO determine correct bytes
                }
                repeat 2-num_operand_bytes {
                    txt.print("   ")
                }
                txt.chrout(' ')
                txt.print(word_addrs[0])
                if word_addrs[1] {
                    txt.chrout(' ')
                    txt.print(word_addrs[1])
                }
                if word_addrs[2] {
                    txt.chrout(' ')
                    txt.print(word_addrs[2])
                }
                txt.chrout('\n')
            }
        } else {
            txt.print("?instruction error\n")
        }
    }

    sub emit(ubyte value) {
        @(program_counter) = value
        program_counter++

        txt.print_ubhex(value, 0)
        txt.chrout(' ')
    }

    sub set_symbol(uword symbolname_ptr, uword value) {
        txt.print("symbol: ")
        txt.print(symbolname_ptr)
        txt.chrout('=')
        txt.print_uwhex(value, true)
        txt.chrout('\n')
    }

    sub lowercase(uword string) {
        ; TODO optimize in asm
        ubyte char = @(string)
        while char {
            @(string) = char & 127
            string++
            char = @(string)
        }
    }

    sub parse_number(uword strptr) -> uword {
        ; TODO move to conv module and optimize
        if @(strptr)=='$'
            return conv.hex2uword(strptr)
        if @(strptr)=='%'
            return conv.bin2uword(strptr)
        return conv.str2uword(strptr)
    }

    sub split_input() {
        ; first strip the input string of extra whitespace and comments
        ubyte copying_word = false
        ubyte word_count
        ubyte char_idx = 0

        word_addrs[0] = 0
        word_addrs[1] = 0
        word_addrs[2] = 0

        ubyte char
        for char in input_line {
            when char {
                ' ', 9, 160 -> {
                    if copying_word
                        input_line[char_idx] = 0; terminate word
                    copying_word = false
                }
                ';', 0 -> {
                    ; terminate line on comment char or end-of-string
                    break
                }
                else -> {
                    if not copying_word {
                        if word_count==3
                            break
                        word_addrs[word_count] = &input_line + char_idx
                        word_count++
                    }
                    copying_word = true
                }
            }
            char_idx++
        }

        char = input_line[char_idx]
        if char==' ' or char==9 or char==160 or char==';'
            input_line[char_idx] = 0
    }

    sub debug_print_words() {
        txt.print("(debug:) words: ")   ; TODO remove
        uword word_ptr
        for word_ptr in word_addrs {
            txt.chrout('[')
            txt.print(word_ptr)
            txt.print("] ")
        }
        txt.chrout('\n')
    }

    sub preprocess_assignment_spacing() {
        ; TODO optimize this... only do this if a valid instruction couldn't be parsed?
        str input_line2 = "?" * 40
        uword src = &input_line
        uword dest = &input_line2
        ubyte changed = 0

        ubyte cc
        for cc in input_line {
            if cc=='=' {
                @(dest) = ' '
                dest++
                @(dest) = '='
                dest++
                cc = ' '
                changed++
            }
            @(dest) = cc
            dest++
        }
        if changed {
            @(dest)=0
            void strcopy(input_line2, src)
        }
    }
}

benchmark {
    sub benchmark() {
        str[20] mnemonics = ["lda", "ldx", "ldy", "jsr", "bcs", "rts", "lda", "ora", "and", "eor", "wai", "nop", "wai", "nop", "wai", "nop", "wai", "nop", "wai", "nop"]
        ubyte[20] modes =   [3,     4,     8,     8,     7,     1,     12,    13,     5,     4,     1,     1,     1,     1,     1,     1,     1,     1,     1,     1]
        uword valid = 0

        const uword iterations = 40000 / len(mnemonics)
        const uword amount = iterations * len(mnemonics)

        txt.print("Benchmark.\nMatching ")
        txt.print_uw(amount)
        txt.print(" mnemonics")

        c64.SETTIM(0,0,0)

        uword total = 0
        repeat iterations {
            if lsb(total)==0
                txt.chrout('.')
            ubyte idx
            for idx in 0 to len(mnemonics)-1 {
                uword instr_info = instructions.match(mnemonics[idx])
                ubyte opcode = instructions.opcode(instr_info, modes[idx])
                if_cs
                    valid++
                total++
            }
        }

        uword current_time = c64.RDTIM16()
        txt.print("\nDone.\nValid: ")
        txt.print_uw(valid)
        txt.print("\ninvalid: ")
        txt.print_uw(amount-valid)
        txt.print("\ntotal: ")
        txt.print_uw(total)
        txt.print("\nSeconds:")
        uword secs = current_time / 60
        current_time = (current_time - secs*60)*1000/60
        txt.print_uw(secs)
        txt.chrout('.')
        if current_time<10
            txt.chrout('0')
        if current_time<100
            txt.chrout('0')
        txt.print_uw(current_time)
        txt.chrout('\n')
    }
}

instructions {
    sub determine_addrmode(uword operand_ptr) -> ubyte {
        ;    Imp = 1,
        ;    Acc = 2,
        ;    Imm = 3,
        ;    Zp = 4,
        ;    ZpX = 5,
        ;    ZpY = 6,
        ;    Rel = 7,
        ;    Abs = 8,
        ;    AbsX = 9,
        ;    AbsY = 10,
        ;    Ind = 11,
        ;    IzX = 12,
        ;    IzY = 13,
        ;    Zpr = 14,
        ;    Izp = 15,
        ;    IaX = 16

        if not operand_ptr
            return 1        ; implied

        when @(operand_ptr) {
            0 -> return 1       ; implied
            'a' -> {
                if @(operand_ptr+1) == 0
                    return 2     ; accumulator
                ; some expression TODO
                return 0
            }
            '#' -> {
                if @(operand_ptr+1)
                    return 3     ; immediate
                return 0
            }
            '(' -> {
                ; some indirect TODO
                if @(operand_ptr+1)
                    return 13
                return 0
            }
            '$' -> {
                ; hex address TODO
                if @(operand_ptr+1)
                    return 8
                return 0
            }
            '%' -> {
                ; bin address TODO
                if @(operand_ptr+1)
                    return 8
                return 0
            }
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                ; absolute or indexed address TODO
                return 8
            }
            else -> return 0    ; unknown
        }

        return 0    ; unknown
    }

    asmsub  match(uword mnemonic_ptr @AY) -> uword @AY {
        ; -- input: mnemonic_ptr in AY,   output:  pointer to instruction info structure or $0000 in AY
        %asm {{
            phx
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            sta  cx16.r4                ; fourth letter in R4 (only exists for the few 4-letter mnemonics)
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f   ; lowercase
            sta  cx16.r5                ; fifth letter in R5 (should always be zero or whitespace for a valid mnemonic)
            pla
            tay
            pla
            tax
            pla
            jsr  get_opcode_info
            plx
            rts
        }}
    }

    asmsub  opcode(uword instr_info_ptr @AY, ubyte addr_mode @X) clobbers(X) -> ubyte @A, ubyte @Pc {
        ; -- input: instruction info struct ptr @AY,  desired addr_mode @X
        ;    output: opcode @A,   valid @carrybit
        %asm {{
            cpy  #0
            beq  _not_found
            sta  P8ZP_SCRATCH_W2
            sty  P8ZP_SCRATCH_W2+1
            stx  cx16.r15

            ; debug result address
            ;sec
            ;jsr  txt.print_uwhex
            ;lda  #13
            ;jsr  c64.CHROUT

            ldy  #0
            lda  (P8ZP_SCRATCH_W2),y
            beq  _multi_addrmodes
            iny
            lda  (P8ZP_SCRATCH_W2),y
            cmp  cx16.r15               ; check single possible addr.mode
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W2),y    ; get opcode
            sec
            rts

_not_found  lda  #0
            clc
            rts

_multi_addrmodes
            ldy  cx16.r15
            lda  (P8ZP_SCRATCH_W2),y    ; check opcode for addr.mode
            bne  _valid
            ; opcode $00 usually means 'invalid' but for "brk" it is actually valid so check for "brk"
            ldy  #0
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f       ; lowercase
            cmp  #'b'
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f       ; lowercase
            cmp  #'r'
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W1),y
            and  #$7f       ; lowercase
            cmp  #'k'
            bne  _not_found
            lda  #0
_valid      sec
            rts
        }}
    }

    %asminclude "opcodes.asm", ""
}
