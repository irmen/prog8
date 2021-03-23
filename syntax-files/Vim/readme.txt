Vim syntax highlighting definitions.
Created by Elektron72 on github


To install:
    Copy prog8.vim into ~/.vim/syntax/

To enable:
    Type:
        :set ft=prog8
    to enable syntax highlighting in the open file.  Alternatively, if you would
    like to enable syntax highlighting for all .p8 files, add the following line
    to your .vimrc:
        au BufRead,BufNewFile *.p8 setfiletype prog8
