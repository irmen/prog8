Vim syntax highlighting definitions.

Created by Elektron72 on github. Thanks!


To install:
    Copy prog8.vim and prog8_builtins.vim into ~/.vim/syntax/. If you will never
    use highlighting for built-in functions and variables, you do not need to
    copy prog8_builtins.vim.

To enable:
    Type:
        :set ft=prog8
    to enable syntax highlighting in the open file.  Alternatively, if you would
    like to enable syntax highlighting for all .p8 files, add the following line
    to your .vimrc:
        au BufRead,BufNewFile *.p8 setfiletype prog8

    If you would like to disable highlighting for built-in functions and
    variables without deleting or renaming the file, add the following line to
    your .vimrc:
        let g:prog8_no_highlight_builtins = 1
    (The value doesn't actually matter, only that the variable is defined.)
