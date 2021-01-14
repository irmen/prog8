%target cx16
%import textio
%import diskio
%import string
%import test_stack
%zeropage basicsafe
%option no_sysinit

; raw file loading of the large assembly file $c000-$ffff: 372 jiffies
; time loading and actually processing it: 700 jiffies

main {

    sub start() {
        txt.print("\n65c02 file based assembler.\n")

        ; user_input()
        file_input()

        ; test_stack.test()
    }

    sub user_input() {
        txt.lowercase()
        parser.print_emit_bytes = true
        txt.print("Empty line to stop.\n")
        repeat {
            ubyte input_length = 0
            txt.chrout('A')
            txt.print_uwhex(parser.program_counter, 1)
            txt.print(": ")
            ; simulate user always having at least one space at the start
            parser.input_line[0] = ' '
            input_length = txt.input_chars(&parser.input_line+1)
            txt.nl()

            if not input_length {
                txt.print("exit\n")
                return
            }

            if not parser.process_line()
                break
        }
    }

    sub file_input() {
        parser.print_emit_bytes = false
        str filename = "romdis.asm"

        txt.print("\nread file: ")
        txt.print(filename)
        txt.nl()

        if diskio.f_open(8, filename) {
            c64.SETTIM(0,0,0)
            uword line=0
            repeat {
                if diskio.f_readline(parser.input_line) {
                    line++
                    if not lsb(line)
                        txt.chrout('.')

                    if not parser.process_line() {
                        txt.print("\nerror. last line was ")
                        txt.print_uw(line)
                        txt.chrout(':')
                        txt.print(parser.word_addrs[0])
                        txt.chrout(' ')
                        txt.print(parser.word_addrs[1])
                        txt.chrout(' ')
                        txt.print(parser.word_addrs[2])
                        txt.nl()
                        break
                    }
                    if c64.READST()
                        break
                    if c64.STOP2() {
                        txt.print("?break\n")
                        break
                    }
                } else
                    break
            }
            diskio.f_close()

            print_summary(line)
        }
    }

    sub print_summary(uword lines) {
        txt.print("\n\nfinal address: ")
        txt.print_uwhex(parser.program_counter, 1)
        txt.print("\n        lines: ")
        txt.print_uw(lines)

        txt.print("\n   time (sec): ")
        uword current_time = c64.RDTIM16()
        uword secs = current_time / 60
        current_time = (current_time - secs*60)*1000/60
        txt.print_uw(secs)
        txt.chrout('.')
        if current_time<10
            txt.chrout('0')
        if current_time<100
            txt.chrout('0')
        txt.print_uw(current_time)
        txt.nl()
    }
}

parser {
    ; byte counts per address mode id:
    ubyte[17] operand_size = [$ff, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 1, 1, 2, 1, 2]

    str input_line = "?" * 160
    uword[3] word_addrs
    uword program_counter = $4000
    ubyte print_emit_bytes = true

    sub process_line() -> ubyte {
        string.lower(input_line)
        preprocess_assignment_spacing()
        split_input()

        if word_addrs[1] and @(word_addrs[1])=='='
            return do_assign()
        else
            return do_label_andor_instr()

        return false
    }

    sub do_assign() -> ubyte {
        ; target is in word_addrs[0], value is in word_addrs[2]   ('=' is in word_addrs[1])
        if not word_addrs[2] {
            txt.print("?syntax error\n")
            return false
        }
        ubyte valid_operand=false
        if @(word_addrs[2])=='*' {
            cx16.r15 = program_counter
            valid_operand = true
        } else {
            ubyte nlen = conv.any2uword(word_addrs[2])
            valid_operand = nlen and @(word_addrs[2]+nlen)==0
        }

        if valid_operand {
            if string.compare(word_addrs[0], "*")==0 {
                program_counter = cx16.r15
                txt.print("\n* = ")
                txt.print_uwhex(program_counter, true)
                txt.nl()
            } else {
                symbols.setvalue(word_addrs[0], cx16.r15)
            }
            return true
        }
        txt.print("?invalid operand\n")
        return false
    }

    sub do_label_andor_instr() -> ubyte {
        uword label_ptr = 0
        uword instr_ptr = 0
        uword operand_ptr = 0
        ubyte starts_with_whitespace = input_line[0]==' ' or input_line[0]==9 or input_line[0]==160

        if word_addrs[2] {
            label_ptr = word_addrs[0]
            instr_ptr = word_addrs[1]
            operand_ptr = word_addrs[2]
        } else if word_addrs[1] {
            if starts_with_whitespace {
                instr_ptr = word_addrs[0]
                operand_ptr = word_addrs[1]
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
                return false
            }
            symbols.setvalue(label_ptr, program_counter)
        }
        if instr_ptr {
            if @(instr_ptr)=='.'
                return process_assembler_directive(instr_ptr, operand_ptr)

            return assemble_instruction(instr_ptr, operand_ptr)
        }

        return true     ; empty line
    }

    sub assemble_instruction(uword instr_ptr, uword operand_ptr) -> ubyte {
        uword instruction_info_ptr = instructions.match(instr_ptr)
        if instruction_info_ptr {
            ; we got a mnemonic match, now process the operand (and its value, if applicable, into cx16.r15)
            ubyte addr_mode = parse_operand(operand_ptr)

            if addr_mode {
                ubyte opcode = instructions.opcode(instruction_info_ptr, addr_mode)
                if_cc {
                    ; most likely an invalid instruction BUT could also be a branchin instruction
                    ; that needs its "absolute" operand recalculated as relative.
                    ubyte retry = false
                    when addr_mode {
                        instructions.am_Abs -> {
                            if @(instr_ptr)=='b' {
                                addr_mode = instructions.am_Rel
                                if not calc_relative_branch_into_r14()
                                    return false
                                cx16.r15 = cx16.r14
                                retry = true
                            }
                        }
                        instructions.am_Imp -> {
                            addr_mode = instructions.am_Acc
                            retry = true
                        }
                        instructions.am_Izp -> {
                            addr_mode = instructions.am_Ind
                            retry = true
                        }
                        instructions.am_Zp -> {
                            addr_mode = instructions.am_Abs
                            retry = true
                        }
                    }

                    if retry
                        opcode = instructions.opcode(instruction_info_ptr, addr_mode)

                    if not opcode {
                        txt.print("?invalid instruction\n")
                        return false
                    }
                }

                if addr_mode==instructions.am_Zpr {
                    ; instructions like BBR4 $zp,$aaaa   (dual-operand)
                    uword comma = string.find(operand_ptr,',')
                    if comma {
                        comma++
                        cx16.r13 = cx16.r15
                        if parse_operand(comma) {
                            program_counter++
                            if not calc_relative_branch_into_r14()
                                return false
                            program_counter--
                            cx16.r15 = (cx16.r14 << 8) | lsb(cx16.r13)
                        } else {
                            txt.print("?invalid operand\n")
                            return false
                        }
                    } else {
                        txt.print("?invalid operand\n")
                        return false
                    }
                }

                ubyte num_operand_bytes = operand_size[addr_mode]
                if print_emit_bytes {
                    txt.chrout(' ')
                    txt.print_uwhex(program_counter, 1)
                    txt.print("   ")
                }
                emit(opcode)
                if num_operand_bytes==1 {
                    emit(lsb(cx16.r15))
                } else if num_operand_bytes == 2 {
                    emit(lsb(cx16.r15))
                    emit(msb(cx16.r15))
                }
                if print_emit_bytes
                    txt.nl()
                return true
            }
            txt.print("?invalid operand\n")
            return false
        }
        txt.print("?invalid instruction\n")
        return false
    }

    sub calc_relative_branch_into_r14() -> ubyte {
        cx16.r14 = cx16.r15 - program_counter - 2
        if msb(cx16.r14)  {
            if cx16.r14 < $ff80 {
                txt.print("?branch out of range\n")
                return false
            }
        } else if cx16.r14 > $007f {
            txt.print("?branch out of range\n")
            return false
        }
        return true
    }

    sub parse_operand(uword operand_ptr) -> ubyte {
        ; parses the operand. Returns 2 things:
        ; - addressing mode id as result value or 0 (am_Invalid) when error
        ; - operand numeric value in cx16.r15 (if applicable)

        ubyte @zp firstchr = @(operand_ptr)
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
                if not @(operand_ptr+1)
                    return instructions.am_Acc      ; Accumulator - no value.

                ; TODO its a symbol/label, immediate or indexed addressing
                txt.print("TODO symbol: ")
                txt.print(operand_ptr)
                txt.nl()
            }
            '(' -> {
                ; various forms of indirect
                operand_ptr++
                parsed_len = conv.any2uword(operand_ptr)
                if parsed_len {
                    operand_ptr+=parsed_len
                    if msb(cx16.r15) {
                        ; absolute indirects
                        if str_is1(operand_ptr, ')')
                            return instructions.am_Ind
                        if str_is3(operand_ptr, ",x)")
                            return instructions.am_IaX
                    } else {
                        ; zero page indirects
                        if str_is1(operand_ptr, ')')
                            return instructions.am_Izp
                        if str_is3(operand_ptr, ",x)")
                            return instructions.am_IzX
                        if str_is3(operand_ptr, "),y")
                            return instructions.am_IzY
                    }
                }
            }
            '$', '%', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                ; address optionally followed by ,x or ,y or ,address
                parsed_len = conv.any2uword(operand_ptr)
                if parsed_len {
                    operand_ptr += parsed_len
                    if msb(cx16.r15) {
                        ; absolute or abs indirects
                        if @(operand_ptr)==0
                            return instructions.am_Abs
                        if str_is2(operand_ptr, ",x")
                            return instructions.am_AbsX
                        if str_is2(operand_ptr, ",y")
                            return instructions.am_AbsY
                    } else {
                        ; zero page or zp indirects
                        if @(operand_ptr)==0
                            return instructions.am_Zp
                        if str_is2(operand_ptr, ",x")
                            return instructions.am_ZpX
                        if str_is2(operand_ptr, ",y")
                            return instructions.am_ZpY
                        if @(operand_ptr)==',' {
                            ; assume BBR $zp,$aaaa or BBS $zp,$aaaa
                            return instructions.am_Zpr
                        }
                    }
                }
            }
        }
        return instructions.am_Invalid
    }

    sub process_assembler_directive(uword directive, uword operand) -> ubyte {
        ; we only recognise .byte right now
        if string.compare(directive, ".byte")==0 {
            if operand {
                ubyte length
                length = conv.any2uword(operand)
                if length {
                    if msb(cx16.r15) {
                        txt.print("?byte value too large\n")
                        return false
                    }
                    if print_emit_bytes {
                        txt.chrout(' ')
                        txt.print_uwhex(program_counter, 1)
                        txt.print("   ")
                    }
                    emit(lsb(cx16.r15))
                    operand += length
                    while @(operand)==',' {
                        operand++
                        length = conv.any2uword(operand)
                        if not length
                            break
                        if msb(cx16.r15) {
                            txt.print("?byte value too large\n")
                            return false
                        }
                        emit(lsb(cx16.r15))
                        operand += length
                    }
                    if print_emit_bytes
                        txt.nl()
                    return true
                }
            }
        }
        txt.print("?syntax error\n")
        return false
    }

    asmsub str_is1(uword st @R0, ubyte char @A) clobbers(Y) -> ubyte @A {
        %asm {{
            cmp  (cx16.r0)
            bne  +
            ldy  #1
            lda  (cx16.r0),y
            bne  +
            lda  #1
            rts
+           lda  #0
            rts
        }}
    }

    asmsub str_is2(uword st @R0, uword compare @AY) clobbers(Y) -> ubyte @A {
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
            jmp  str_is3._is_2_entry
        }}
    }

    asmsub str_is3(uword st @R0, uword compare @AY) clobbers(Y) -> ubyte @A {
        %asm {{
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            lda  (cx16.r0)
            cmp  (P8ZP_SCRATCH_W1)
            bne  +
            ldy  #1
_is_2_entry
            lda  (cx16.r0),y
            cmp  (P8ZP_SCRATCH_W1),y
            bne  +
            iny
            lda  (cx16.r0),y
            cmp  (P8ZP_SCRATCH_W1),y
            bne  +
            iny
            lda  (cx16.r0),y
            bne  +
            lda  #1
            rts
+           lda  #0
            rts
        }}
    }

    sub emit(ubyte value) {
        @(program_counter) = value
        program_counter++

        if print_emit_bytes {
            txt.print_ubhex(value, 0)
            txt.chrout(' ')
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
        ubyte @zp char_idx = 0

        word_addrs[0] = 0
        word_addrs[1] = 0
        word_addrs[2] = 0

        ubyte @zp char
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
        if not string.find(input_line, '=')
            return

        ; split the line around the '='
        str input_line2 = "?" * 40
        uword src = &input_line
        uword dest = &input_line2
        ubyte @zp cc
        for cc in input_line {
            if cc=='=' {
                @(dest) = ' '
                dest++
                @(dest) = '='
                dest++
                cc = ' '
            }
            @(dest) = cc
            dest++
        }
        @(dest)=0
        void string.copy(input_line2, src)
    }
}

symbols {
    sub setvalue(uword symbolname_ptr, uword value) {
        txt.print("symbol: ")
        txt.print(symbolname_ptr)
        txt.chrout('=')
        txt.print_uwhex(value, true)
        txt.nl()
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

    ; TODO: explore (benchmark) hash based matchers.   Faster (although the bulk of the time is not in the mnemonic matching)? Less memory?

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
