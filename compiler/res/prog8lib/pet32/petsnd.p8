; Play sounds from your PET speaker
; Uses the VIA timer to be able to play them without blocking the program in a delay loop.
; Notes lower than A3 cannot be played with this mechanism and need a custom CPU delay loop.
;
; inspiration: http://blog.tynemouthsoftware.co.uk/2022/05/pet-sounds.html
;
; Example:
;       petsnd.on()
;       petsnd.note(petsnd.C_4)
;       sys.wait(30)
;       petsnd.note(petsnd.E_4)
;       sys.wait(30)
;       petsnd.note(petsnd.A_4)
;       sys.wait(30)
;       petsnd.off()

petsnd {

    %option ignore_unused

    ; octaves:
    const ubyte OCTAVES_456 = 1
    const ubyte OCTAVES_567 = 2
    const ubyte OCTAVES_678 = 3

    ; notes:
    const ubyte  A_SHARP_3    = $FF    ; A#3  = 233.08 Hz
    const ubyte  B_3          = $FA    ; B3   = 246.94 Hz
    const ubyte  C_4          = $EE    ; C4   = 261.63 Hz
    const ubyte  C_SHARP_4    = $E0    ; C#4  = 277.18 Hz
    const ubyte  D_4          = $D2    ; D4   = 293.66 Hz
    const ubyte  D_SHARP_4    = $C7    ; D#4  = 311.13 Hz
    const ubyte  E_4          = $BC    ; E4   = 329.63 Hz
    const ubyte  F_4          = $B1    ; F4   = 349.23 Hz
    const ubyte  F_SHARP_4    = $A8    ; F#4  = 369.99 Hz
    const ubyte  G_4          = $9E    ; G4   = 392.00 Hz
    const ubyte  G_SHARP_4    = $95    ; G#4  = 415.30 Hz
    const ubyte  A_4          = $8C    ; A4   = 440.00 Hz
    const ubyte  A_SHARP_4    = $85    ; A#4  = 466.16 Hz
    const ubyte  B_4          = $7D    ; B4   = 493.78 Hz
    const ubyte  C_5          = $76    ; C5   = 523.25 Hz
    const ubyte  C_SHARP_5    = $6E    ; C#5  = 554.37 Hz
    const ubyte  D_5          = $68    ; D5   = 587.33 Hz
    const ubyte  D_SHARP_5    = $63    ; D#5  = 622.25 Hz
    const ubyte  E_5          = $5D    ; E5   = 659.26 Hz
    const ubyte  F_5          = $58    ; F5   = 698.46 Hz
    const ubyte  F_SHARP_5    = $53    ; F#5  = 739.99 Hz
    const ubyte  G_5          = $4E    ; G5   = 783.99 Hz
    const ubyte  G_SHARP_5    = $4A    ; G#5  = 830.61 Hz
    const ubyte  A_5          = $45    ; A5   = 880.00 Hz
    const ubyte  A_SHARP_5    = $41    ; A#5  = 932.33 Hz
    const ubyte  B_5          = $3D    ; B5   = 987.77 Hz
    const ubyte  C_6          = $39    ; C6   = 1046.50 Hz

    sub on() {
        pet.via1acr = $10       ; "shift out free running at T2 rate"
        octaves(petsnd.OCTAVES_456)
    }

    sub off() {
        pet.via1t2 = 0          ; clear note
        pet.via1acr = 0         ; stop sound.
    }

    sub octaves(ubyte oct) {
        ; set octave(s), choice from range 1 to 3.   (1=octaves 4,5,6,  2=octaves 5,6,7, 3=octaves 6,7,8)
        ; the VIA shift register value determines octave
        when oct {
            OCTAVES_456 -> pet.via1sr = %00001111
            OCTAVES_567 -> pet.via1sr = %00110011
            OCTAVES_678 -> pet.via1sr = %01010101
            else -> pet.via1sr = 0
        }
    }

    sub note(ubyte n) {
        pet.via1t2 = n        ; timer 2 rate (L)
    }
}
