; Minimal reproduction of 64tass 1.60 `.section BSS` failure inside nested .proc scopes.
;
; Symptoms:
;   - `.section BSS` at line ~27 gives "not defined 'BSS'" when the FULL file is assembled
;   - The same `.section BSS` at that exact location WORKS when assembled in isolation
;     (try: `sed -n '22,35p' test_bss_bug.asm | 64tass --ascii --case-sensitive ...`)
;   - `.section "BSS"` at the same spot gives "label required" instead
;   - Surrounding context before the problem block causes the failure (something in
;     the earlier p8b_main block or the block-level `.section BSS` declarations)
;
; Hypothesis: 64tass caches section symbol information per scope, and when a section
; name like `BSS` is used both inside a .proc scope AND at file level (or in multiple
; sibling .proc scopes), the parser gets confused about whether the name is a section
; reference or a regular symbol reference.  The failure only manifests at specific
; nesting depths where the scope chain lookup returns ambiguous results.
;
; The workaround in new6502gen strips `.section`/`.send` directives from asm subroutines'
; raw assembly.  The variables end up in the code section (wasting a few bytes) but
; function correctly.

.cpu "65c02"
* = $0801

; ---- Block-level BSS sections (these are fine at file scope) ----
.section BSS
    .byte ?
.send BSS

; ---- First block: p8b_main ----
p8b_main  .proc
p8s_start  .proc
    lda  #15
    jsr  $ffd2
    rts
.pend
.pend

; ---- Second block: p8_sys_startup ----
; This is where the problem manifests.  The `.section BSS` below at line ~27
; is inside a TWO-level nested .proc (p8_sys_startup > cleanup_at_exit).
; Comment out the block-level `.section BSS` and `.dsection` lines above/below
; and this block assembles fine in isolation, but with the full file it fails.

p8_sys_startup  .proc

init_system  .proc
    sei
    cli
    rts
.pend

cleanup_at_exit  .proc
    ; When the full file is assembled, 64tass fails here with:
    ;   "not defined 'BSS'"
    ; but this exact code block works when assembled alone.
    ; The mere presence of the PRIOR block-level .section/.dsection
    ; declarations seems to poison the symbol table for this depth.
    lda  #1
    sta  $00        ; ram bank 1
    lda  #4
    sta  $01        ; rom bank 4 (basic)
    jsr  $ffcc
    lda  #9
    jsr  $ffd2
    rts

    .section BSS           ; <<<< FAILS HERE with full file
_exitcarry  .byte ?
_exitcode   .byte ?
    .send BSS

    ; !notreached!
.pend

.pend

; ---- More file-level BSS ----
.dsection BSS_NOCLEAR
.dsection BSS_SLABS
