%target cx16
%import test_stack
%import textio
%import string
%zeropage basicsafe
%option no_sysinit


main {

    sub start() {
        txt.lowercase()
        txt.print("\nAssembler.\nEmpty line to stop.\n")

        textparse.user_input()

        ; test_stack.test()
    }

}

textparse {
    ; byte counts per address mode id:
    ubyte[16] operand_size = [0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 1, 1, 1]

    str input_line = "?" * 40
    uword[3] word_addrs
    uword program_counter = $4000

    sub user_input() {
        repeat {
            ubyte input_length = 0
            txt.chrout('A')
            txt.print_uwhex(program_counter, 1)
            txt.print(": ")
            ; simulate user always having at least one space at the start
            input_line[0] = ' '
            input_length = txt.input_chars(&input_line+1)
            txt.nl()

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

        ubyte nlen = conv.any2uword(word_addrs[2])
        if nlen and @(word_addrs[2]+nlen)==0 {
            if string.compare(word_addrs[0], "*")==0 {
                program_counter = cx16.r15
            } else {
                set_symbol(word_addrs[0], cx16.r15)
            }
            return
        }
        txt.print("?invalid operand\n")
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
            uword lastlabelchar = label_ptr + string.length(label_ptr)-1
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
;                txt.nl()

;                if operand_ptr {
;                    txt.print("operand: ")
;                    txt.print(operand_ptr)
;                    txt.nl()
;                }

            assemble_instruction(instr_ptr, operand_ptr)
        }
    }

    sub assemble_instruction(uword instr_ptr, uword operand_ptr) {
        uword instruction_info_ptr = instructions.match(instr_ptr)
        if instruction_info_ptr {
            ; we got a mnemonic match, now process the operand (and its value, if applicable, into cx16.r15)
            ubyte addr_mode = parse_operand(operand_ptr)
            if addr_mode {
                txt.print("operand ok, addr-mode=")
                txt.print_ub(addr_mode)
                txt.nl()
                ubyte opcode = instructions.opcode(instruction_info_ptr, addr_mode)
                if_cc {
                    txt.print("?invalid instruction\n")
                } else {
                    ubyte num_operand_bytes = operand_size[addr_mode-1]
                    txt.chrout(' ')
                    txt.print_uwhex(program_counter, 1)
                    txt.print("   ")
                    emit(opcode)
                    if num_operand_bytes==1 {
                        emit(lsb(cx16.r15))
                    } else if num_operand_bytes == 2 {
                        emit(lsb(cx16.r15))
                        emit(msb(cx16.r15))
                    }
                    txt.nl()
                }
                return
            }
            txt.print("?invalid operand\n")
            return
        }
        txt.print("?invalid instruction\n")
    }

    sub parse_operand(uword operand_ptr) -> ubyte {
        ; parses the operand. Returns 2 things:
        ; - addressing mode id as result value or 0 when error
        ; - operand numeric value in cx16.r15 (if applicable)
        ; TODO

        ubyte firstchr = @(operand_ptr)
        ubyte parsed_len
        when firstchr {
            0 -> return instructions.am_Imp
            '#' -> {
                ; lda #$99   Immediate
                operand_ptr++
                parsed_len = conv.any2uword(operand_ptr)
                if parsed_len {
                    operand_ptr += parsed_len
                    if @(operand_ptr)==0
                        return instructions.am_Imm
                }
            }
            'a' -> {
                ; possibly Accumulator operand
                ; TODO parse
                return instructions.am_Acc
            }
            '(' -> {
                ; various forms of indirect
                ; TODO parse number and other stuff
                cx16.r15 = $98ab
                return instructions.am_Ind
            }
            '$', '%', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                ; address optionally followed by ,x or ,y
                ; TODO Parse
                cx16.r0 = $9988
                return instructions.am_Abs
            }
        }
        return 0
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
        txt.nl()
    }

    sub lowercase(uword st) {
        ; TODO optimize in asm
        ubyte char = @(st)
        while char {
            @(st) = char & 127
            st++
            char = @(st)
        }
    }

    sub dummy(uword operand_ptr) -> uword {
        uword a1=rndw()
        uword a6=a1+operand_ptr
        return a6
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

    sub debug_print_words() {        ; TODO remove
        txt.print("(debug:) words: ")
        uword word_ptr
        for word_ptr in word_addrs {
            txt.chrout('[')
            txt.print(word_ptr)
            txt.print("] ")
        }
        txt.nl()
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
            string.copy(input_line2, src)
        }
    }
}

instructions {
    const ubyte am_Invalid = 0
    const ubyte am_Imp = 1
    const ubyte am_Acc = 2
    const ubyte am_Imm = 3
    const ubyte am_Zp = 4
    const ubyte am_ZpX = 5
    const ubyte am_ZpY = 6
    const ubyte am_Rel = 7
    const ubyte am_Abs = 8
    const ubyte am_AbsX = 9
    const ubyte am_AbsY = 10
    const ubyte am_Ind = 11
    const ubyte am_IzX = 12
    const ubyte am_IzY = 13
    const ubyte am_Zpr = 14
    const ubyte am_Izp = 15
    const ubyte am_IaX = 16

    ; TODO: explore (benchmark) hash based matchers

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
            stx  cx16.r5

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
            cmp  cx16.r5               ; check single possible addr.mode
            bne  _not_found
            iny
            lda  (P8ZP_SCRATCH_W2),y    ; get opcode
            sec
            rts

_not_found  lda  #0
            clc
            rts

_multi_addrmodes
            ldy  cx16.r5
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
