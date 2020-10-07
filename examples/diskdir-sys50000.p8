%target c64
%import textio
%import syslib
%zeropage basicsafe
%option no_sysinit
%launcher none
%address 50000

; This example shows the directory contents of disk drive 8.
; You load it with  LOAD "diskdir-sys50000",8,1
; and then call it with SYS 50000.

; The only difference with diskdir.p8 is the directives that make this load at 50000.

%import diskdir
