%import string
%import textio
%import conv

%zeropage basicsafe

main {

str[10] names = ["1??","2??","3??","4??","5??","6??","7??","8??","9??","a??"]
str[10] scores = ["9000","8000","7000","6000","5000","4000","3000","2000","1000","50"]
str p_str = "winner"
uword top_score

sub start() {
    print_hiscores()
    ubyte r = check_score(7500)
    print_hiscores()
}

sub check_score(uword score) -> ubyte {
	ubyte n
	ubyte i
	str tmp = "?????"

	if score < top_score {
		return(10) }
	else {
		for n in 0 to 9 {
			if score > conv.str2uword(scores[n]) {
				for i in 8 to n step -1 {
					names[i+1] = names[i]
					scores[i+1] = scores[i]
			    }
				;string.copy(p_str,names[n])
				;conv.str_uw0(score)
				;string.copy(conv.string_out,scores[n])
				return(n)
			}
		}
	}
}

sub print_hiscores() {
	ubyte n
	for n in 0 to 9 {
		txt.print_ub(n)
		txt.spc()
		txt.print(names[n])
		txt.spc()
		txt.print(scores[n])
		txt.nl()
	}
	txt.nl()
}

}
