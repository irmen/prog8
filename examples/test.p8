%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        const uword vera_freq = (136.5811 / 0.3725290298461914) as uword
        const uword v_f_10 = (136.5811 / 0.3725290298461914 + 0.5) as uword

        txt.print_uw(v_f_10)
        txt.print_uw(vera_freq)
    }
 }
