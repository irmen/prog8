%output raw
%launcher none

main {
    sub start() {
        extsub $fff1 = WriteCharacter(ubyte character @A)

        for cx16.r0L in "\n\n\n.... Hello from Prog8 :-)"
            WriteCharacter(cx16.r0L)

        repeat {
        }
    }


    sub start2() {

    %asm {{

; Program constants
CURSOR_POS_X = #0  ; character display 'X' coordinate
CURSOR_POS_Y = #21 ; character display 'Y' coordinate
NEWLINE_CHAR = #13 ; ASCII character code


;--------------;
; Main Program ;
;--------------;

start:
  ;-----------------------------------------------;
  ; Play sound effect - (API Group 8, Function 5) ;
  ;-----------------------------------------------;

  lda  neo.API_SOUND_CH_00    ; sound channel               (API::sound->play->channel)
  sta  neo.API_PARAMETERS + 0 ; set API 'Parameter0'        (API::sound->play->channel)
  lda  neo.API_SFX_COIN       ; sound effect index          (API::sound->play->effect)
  sta  neo.API_PARAMETERS + 1 ; set API 'Parameter1'        (API::sound->play->effect)
  lda  neo.API_FN_PLAY_SOUND  ; sound effect function       (API::sound->play)
  sta  neo.API_FUNCTION       ; set API 'Function'          (API::sound->play)
  lda  neo.API_GROUP_SOUND    ; 'Sound' API function group  (API::sound)
  sta  neo.API_COMMAND        ; trigger 'Sound' API routine (API::sound)


  ;--------------------------------------------------;
  ; Set cursor position - (API Group 2, Function 7) ;
  ;--------------------------------------------------;

  ; reposition the cursor to overwrite the default welcome text
  lda  neo.API_FN_SET_CURSOR_POS ; set cursor position function  (API::console->cursor)
  sta  neo.API_FUNCTION          ; set API 'Function'            (API::console->cursor)
  lda  CURSOR_POS_X          ; cursor 'X' coordinate         (API::console->cursor->x)
  sta  neo.API_PARAMETERS + 0    ; set API 'Parameter0'          (API::console->cursor->x)
  lda  CURSOR_POS_Y          ; cursor 'Y' coordinate         (API::console->cursor->y)
  sta  neo.API_PARAMETERS + 1    ; set API 'Parameter1'          (API::console->cursor->y)
  lda  neo.API_GROUP_CONSOLE     ; 'Console' API function group  (API::console)
  sta  neo.API_COMMAND           ; trigger 'Console' API routine (API::console)

  ; this simply repeats the same routine as the previous block,
  ; but using the generic convenience macro, for the sake of demonstration
  lda  CURSOR_POS_X
  sta  neo.API_PARAMETERS + 0
  lda  CURSOR_POS_Y
  sta  neo.API_PARAMETERS + 1
  #neo.DoSendMessage ; send command 2,7
  .byte 2,7


  ;--------------------------------------------------------;
  ; Write character to console - (API Group 2, Function 6) ;
  ;--------------------------------------------------------;

  ; first, write a single newline character, using the special convenience macro
  lda  NEWLINE_CHAR
  jsr  neo.WriteCharacter
  ; the text foreground color can also be set by injecting a control character
  lda  neo.COLOR_DARK_GREEN
  jsr  neo.WriteCharacter

  ; next, print the welcome message (a string of characters), using the API
  ldx  #0                 ; initialize string iteration index
  lda  neo.API_FN_WRITE_CHAR  ; console write function        (API::console->write)
  sta  neo.API_FUNCTION       ; set API 'Function'            (API::console->write)
print_next_char:
  lda  neo.API_COMMAND        ; previous API routine status
  bne  print_next_char    ; wait for previous API routine to complete

  lda  hello_msg , x      ; next character of 'hello_msg' (API::console->write->char)
  beq  end                ; test for string end null byte
  sta  neo.API_PARAMETERS + 0 ; set API 'Parameter0'          (API::console->write->char)
  lda  neo.API_GROUP_CONSOLE  ; 'Console' API function group  (API::console)
  sta  neo.API_COMMAND        ; trigger 'Console' API routine (API::console)

  inx                    ; increment iteration index
  jmp print_next_char    ; continue 'hello_msg' print loop

end:
  jmp end ; infinite loop


;--------------;
; Program data ;
;--------------;

hello_msg:
  .text "                   Hello Neo6502"                      ; line 1 to display
  .text 13                                                      ; newline
  .text "                                                     " ; 53 blanks
  .text 13                                                      ; newline
  .text "          Now you're playing with Neo Power!"          ; line 2 to display
  .text 13                                                      ; newline
  .text "              (Some assembly required)"                ; line 3 to display
  .text 0                                                       ; null-terminated


        }}
    }
}
