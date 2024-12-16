" Vim syntax file
" Language: Prog8
" Maintainer: Elektron72
" Latest Revision: 23 March 2021

if exists("b:current_syntax")
    finish
endif


syn match prog8Comment ";.*$"

syn region prog8String start=+@\?"+ skip=+\\"+ end=+"+
syn region prog8Character start=+@\?'+ skip=+\\'+ end=+'+
syn match prog8Number "\<\d\+\>"
syn match prog8Number "$\x\+\>"
syn match prog8Number "%[01]\+\>"
syn keyword prog8Boolean true false
syn match prog8Float "\<\d\+\.\d\+\([eE]\d\+\)\?\>"

syn region prog8Expression matchgroup=prog8AddressOp start="@(" end=")"
            \ transparent
syn match prog8Function "\(\<\(asm\)\?sub\>\s\+\)\@16<=\<\w\+\>"
syn match prog8Function "\(extsub\s\+$\x\+\s\+=\s\+\)\@16<=\<\w\+\>"

syn keyword prog8Statement break continue goto return asmsub sub inline
syn match prog8Statement "\<\(asm\|rom\)\?sub\>"
syn keyword prog8Conditional if else when
syn keyword prog8Conditional if_cs if_cc if_vs if_vc if_eq if_z if_ne if_nz
syn keyword prog8Conditional if_pl if_pos if_mi if_neg
syn keyword prog8Conditional when
syn keyword prog8Repeat for while in do until repeat unroll
syn match prog8Label "\<\w\+\>:"
syn keyword prog8Operator and or to downto as void

syn match prog8Directive "\(^\|\s\)%\(output\|launcher\|zeropage\)\>"
syn match prog8Directive "\(^\|\s\)%\(zpreserved\|zpallowed\|address\|encoding\|import\|option\)\>"
syn match prog8Directive "\(^\|\s\)%\(align\|asmbinary\|asminclude\|breakpoint\)\>"
syn match prog8Directive "\(^\|\s\)%\(asm\|ir\)\>"

syn match prog8Type "\<\%(u\?byte\|u\?word\|float\|str\|bool\|long\)\>"
syn region prog8ArrayType matchgroup=prog8Type
            \ start="\<\%(u\?byte\|u\?word\|float\|str\|bool\)\[" end="\]"
            \ transparent
syn keyword prog8StorageClass const
syn match prog8StorageClass "\(^\|\s\)\(@zp\|@bank\|@shared\|@split\|@nosplit\|@nozp\|@requirezp\|@align64\|@alignword\|@alignpage\|@dirty\)\>"

syn region prog8Block start="{" end="}" transparent
syn region prog8Expression start="(" end=")" transparent
syn region prog8Array start="\[" end="\]" transparent


if !exists("g:prog8_no_highlight_builtins")
    runtime! syntax/prog8_builtins.vim
endif


syn region prog8Asm start="\(%asm\)\@16<=\s\+{{" end="}}" contains=
            \prog8Comment,
            \prog8Character,
            \prog8Number,
            \prog8AsmIdentifier,
            \prog8AsmStatement,
            \prog8AsmLabel,
            \prog8BuiltInVar,
            \prog8BuiltInFunc
syn sync match prog8AsmSync groupthere prog8Asm "%asm\s\+{{"

syn keyword prog8AsmIdentifier a x y contained

syn keyword prog8AsmStatement adc and asl bbr bbs bcc bcs beq bit bmi contained
syn keyword prog8AsmStatement bne bpl bra brk bvc bvs clc cld cli clv contained
syn keyword prog8AsmStatement cmp cpx cpy dec dex dey eor inc inx iny contained
syn keyword prog8AsmStatement jmp jsr lda ldx ldy lsr nop ora pha php contained
syn keyword prog8AsmStatement phx phy pla plp plx ply rmb rol ror rti contained
syn keyword prog8AsmStatement rts sbc sec sed sei smb sta stp stx sty contained
syn keyword prog8AsmStatement stz tax tay trb tsb tsx txa txs tya wai contained
syn match prog8AsmLabel "^\([-+]\|\(\w\+\.\)*\w\+\)" contained


hi def link prog8Comment Comment

hi def link prog8String String
hi def link prog8Character Character
hi def link prog8Number Number
hi def link prog8Boolean Boolean
hi def link prog8Float Float

hi def link prog8AddressOp Identifier
hi def link prog8Function Function

hi def link prog8Statement Statement
hi def link prog8Conditional Conditional
hi def link prog8Repeat Repeat
hi def link prog8Label Label
hi def link prog8Operator Operator

hi def link prog8Directive PreProc

hi def link prog8Type Type
hi def link prog8StorageClass StorageClass
hi def link prog8Structure Structure


hi def link prog8AsmIdentifier Identifier

hi def link prog8AsmStatement Statement
hi def link prog8AsmLabel Label
