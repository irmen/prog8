%import textio
%import conv
%import strings


textelite {

    const ubyte numforLave = 7      ;  Lave is 7th generated planet in galaxy one
    const ubyte numforZaonce = 129
    const ubyte numforDiso = 147
    const ubyte numforRiedquat = 46
    uword num_commands

    sub bench(uword max_time) -> uword {
        num_commands = 0
        txt.lowercase()
        cbm.SETTIM(0,0,0)
        while cbm.RDTIM16()<max_time {
            reinit()
            run_commands(max_time)
        }
        return num_commands
    }

    sub reinit() {
        ;txt.clear_screen()
        ;txt.print("\n --- TextElite v1.3 ---\n")
        txt.print("\nnew game\n")
        elite_planet.set_seed(0, 0)
        elite_galaxy.travel_to(1, numforLave)
        elite_market.init(0)  ;  Lave's market is seeded with 0
        elite_ship.init()
        elite_planet.display(false, 0)
        input_index = 0
    }

    sub run_commands(uword max_time) {
        while cbm.RDTIM16() < max_time {
            str input = "????????"
            ;txt.print("\nCash: ")
            ;elite_util.print_10s(elite_ship.cash)
            ;txt.print("\nCommand (?=help): ")
            ubyte num_chars = next_input(input)
            ;txt.nl()
            if num_chars!=0 {
                when input[0] {
                    'q' -> {
                        bool has_error = false
                        if elite_galaxy.number != 2 {
                            txt.print("\nERROR: galaxy is not 2: ")
                            txt.print_ub(elite_galaxy.number)
                            txt.nl()
                            has_error=true
                        }
                        if elite_planet.number != 164 {
                            txt.print("\nERROR: planet is not 164: ")
                            txt.print_ub(elite_planet.number)
                            txt.nl()
                            has_error=true
                        }
                        if elite_planet.x != 116 {
                            txt.print("\nERROR: planet.x is not 116: ")
                            txt.print_ub(elite_planet.x)
                            txt.nl()
                            has_error=true
                        }
                        if elite_planet.y != 201 {
                            txt.print("\nERROR: planet.y is not 201: ")
                            txt.print_ub(elite_planet.y)
                            txt.nl()
                            has_error=true
                        }
                        if "ribeen" != elite_planet.name {
                            txt.print("\nERROR: planet.name is not 'ribeen': ")
                            txt.print(elite_planet.name)
                            txt.nl()
                            has_error=true
                        }
                        if elite_ship.cash != 1212 {
                            txt.print("\nERROR: cash is not 1212: ")
                            txt.print_uw(elite_ship.cash)
                            txt.nl()
                            has_error=true
                        }
                        if elite_ship.fuel != 50 {
                            txt.print("\nERROR: fuel is not 50:")
                            txt.print_ub(elite_ship.fuel)
                            txt.nl()
                            has_error=true
                        }
                        if elite_ship.cargohold[0] != 3 {
                            txt.print("\nERROR: food is not 3:")
                            txt.print_ub(elite_ship.cargohold[0])
                            txt.nl()
                            has_error=true
                        }
                        if elite_ship.cargohold[1] != 0 {
                            txt.print("\nERROR: textiles is not 0:")
                            txt.print_ub(elite_ship.cargohold[1])
                            txt.nl()
                            has_error=true
                        }
                        if has_error
                            sys.exit(1)
                        return
                    }
                    'b' -> elite_trader.do_buy()
                    's' -> elite_trader.do_sell()
                    'f' -> elite_trader.do_fuel()
                    'j' -> elite_trader.do_jump()
                    't' -> elite_trader.do_teleport()
                    'g' -> elite_trader.do_next_galaxy()
                    'i' -> elite_trader.do_info()
                    'm' -> {
                        if input[1]=='a' and input[2]=='p'
                            elite_trader.do_map()
                        else
                            elite_trader.do_show_market()
                    }
                    'l' -> elite_trader.do_local()
                    'c' -> elite_trader.do_cash()
                    'h' -> elite_trader.do_hold()
                }
                num_commands++
            }
        }
    }

    str[] inputs = [
        "i",
        "diso",
        "i",
        "lave",
        "m",
        "b",
        "food",
        "15",
        "map",
        "g",
        "map",
        "l",
        "j",
        "zao",
        "s",
        "food",
        "12",
        "tele",
        "quti",
        "tele",
        "aro",
        "i",
        "diso",
        "i",
        "lave",
        "i",
        "zao",
        "galhyp",
        "fuel",
        "20",
        "j",
        "rib",
        "i",
        "rib",
        "i",
        "tiri",
        "q",
        0
    ]

    ubyte input_index

    sub next_input(str buffer) -> ubyte {
        input_index++
        return strings.copy(inputs[input_index], buffer)
    }
}

elite_trader {
    str input = "??????????"
    ubyte num_chars

    sub do_jump() {
        ;txt.print("\nJump to what system? ")
        jump_to_system()
    }

    sub do_teleport() {
        ;txt.print("\nCheat! Teleport to what system? ")
        ubyte fuel = elite_ship.fuel
        elite_ship.fuel = 255
        jump_to_system()
        elite_ship.fuel = fuel
    }

    sub jump_to_system() {
        void textelite.next_input(input)
        ubyte current_planet = elite_planet.number
        ubyte x = elite_planet.x
        ubyte y = elite_planet.y
        if elite_galaxy.search_closest_planet(input) {
            ubyte distance = elite_planet.distance(x, y)
            if distance <= elite_ship.fuel {
                elite_galaxy.init_market_for_planet()
                elite_ship.fuel -= distance
                ;txt.print("\n\nHyperspace jump! Arrived at:\n")
                elite_planet.display(true,0 )
                return
            }
            ;txt.print("\nInsufficient fuel\n")
        } else {
            ;txt.print(" Not found!\n")
        }
        elite_galaxy.travel_to(elite_galaxy.number, current_planet)
    }

    sub do_buy() {
        ;txt.print("\nBuy what commodity? ")
        str commodity = "???????????????"
        void textelite.next_input(commodity)
        ubyte ci = elite_market.match(commodity)
        if ci & 128 !=0 {
            txt.print("Unknown\n")
        } else {
            ;txt.print("\nHow much? ")
            void textelite.next_input(input)
            ubyte amount = conv.str2ubyte(input)
            if elite_market.current_quantity[ci] < amount {
                txt.print(" Insufficient supply!\n")
            } else {
                uword price = elite_market.current_price[ci] * amount
                ;txt.print(" Total price: ")
                ;elite_util.print_10s(price)
                if price > elite_ship.cash {
                    txt.print(" Not enough cash!\n")
                } else {
                    elite_ship.cash -= price
                    elite_ship.cargohold[ci] += amount
                    elite_market.current_quantity[ci] -= amount
                }
            }
        }
    }

    sub do_sell() {
        ;txt.print("\nSell what commodity? ")
        str commodity = "???????????????"
        void textelite.next_input(commodity)
        ubyte ci = elite_market.match(commodity)
        if ci & 128 !=0 {
            txt.print("Unknown\n")
        } else {
            ;txt.print("\nHow much? ")
            void textelite.next_input(input)
            ubyte amount = conv.str2ubyte(input)
            if elite_ship.cargohold[ci] < amount {
                txt.print(" Insufficient supply!\n")
            } else {
                uword price = elite_market.current_price[ci] * amount
                ;txt.print(" Total price: ")
                ;elite_util.print_10s(price)
                elite_ship.cash += price
                elite_ship.cargohold[ci] -= amount
                elite_market.current_quantity[ci] += amount
            }
        }
    }

    sub do_fuel() {
        ;txt.print("\nBuy fuel. Amount? ")
        void textelite.next_input(input)
        ubyte buy_fuel = 10*conv.str2ubyte(input)
        ubyte max_fuel = elite_ship.Max_fuel - elite_ship.fuel
        if buy_fuel > max_fuel
            buy_fuel = max_fuel
        uword price = buy_fuel as uword * elite_ship.Fuel_cost
        if price > elite_ship.cash {
            txt.print("Not enough cash!\n")
        } else {
            elite_ship.cash -= price
            elite_ship.fuel += buy_fuel
        }
    }

    sub do_cash() {
        ;txt.print("\nCheat! Set cash amount: ")
        void textelite.next_input(input)
        elite_ship.cash = conv.str2uword(input)
    }

    sub do_hold() {
        ;txt.print("\nCheat! Set cargohold size: ")
        void textelite.next_input(input)
        elite_ship.Max_cargo = conv.str2ubyte(input)
    }

    sub do_next_galaxy() {
        txt.print("\n>>>>> Galaxy Hyperjump!\n")
        elite_galaxy.travel_to(elite_galaxy.number+1, elite_planet.number)
        elite_planet.display(false, 0)
    }

    sub do_info() {
        ;txt.print("\nSystem name (empty=current): ")
        num_chars = textelite.next_input(input)
        if num_chars!=0 {
            ubyte current_planet = elite_planet.number
            ubyte x = elite_planet.x
            ubyte y = elite_planet.y
            if elite_galaxy.search_closest_planet(input) {
                ubyte distance = elite_planet.distance(x, y)
                elite_planet.display(false, distance)
            } else {
                ;txt.print(" Not found!")
            }
            elite_galaxy.travel_to(elite_galaxy.number, current_planet)
        } else {
            elite_planet.display(false, 0)
        }
    }

    sub do_local() {
        elite_galaxy.local_area()
    }

    sub do_map() {
        ;txt.print("\n(l)ocal or (g)alaxy starmap? ")
        num_chars = textelite.next_input(input)
        if num_chars!=0 {
            elite_galaxy.starmap(input[0]=='l')
        }
    }

    sub do_show_market() {
        elite_market.display()
        ;txt.print("\nFuel: ")
        ;elite_util.print_10s(elite_ship.fuel)
        ;txt.print("   Cargohold space: ")
        ;txt.print_ub(elite_ship.cargo_free())
        ;txt.print("t\n")
    }
}

elite_ship {
    const ubyte Max_fuel = 70
    const ubyte Fuel_cost = 2
    ubyte Max_cargo = 20

    ubyte fuel
    uword cash
    ubyte[17] cargohold

    sub init() {
        sys.memset(cargohold, len(cargohold), 0)
        fuel = Max_fuel
        cash = 1000
    }
}

elite_market {
    ubyte[17] baseprices = [$13, $14, $41, $28, $53, $C4, $EB, $9A, $75, $4E, $7C, $B0, $20, $61, $AB, $2D, $35]
    byte[17] gradients = [-$02, -$01, -$03, -$05, -$05, $08, $1D, $0E, $06, $01, $0d, -$09, -$01, -$01, -$02, -$01, $0F]
    ubyte[17] basequants = [$06, $0A, $02, $E2, $FB, $36, $08, $38, $28, $11, $1D, $DC, $35, $42, $37, $FA, $C0]
    ubyte[17] maskbytes = [$01, $03, $07, $1F, $0F, $03, $78, $03, $07, $1F, $07, $3F, $03, $07, $1F, $0F, $07]
    str[17] names = ["Food", "Textiles", "Radioactives", "Slaves", "Liquor/Wines", "Luxuries", "Narcotics", "Computers",
                     "Machinery", "Alloys", "Firearms", "Furs", "Minerals", "Gold", "Platinum", "Gem-Stones", "Alien Items"]

    ubyte[17] current_quantity
    uword[17] current_price

    sub init(ubyte fluct) {
        ; Prices and availabilities are influenced by the planet's economy type
        ; (0-7) and a random "fluctuation" byte that was kept within the saved
        ; commander position to keep the market prices constant over gamesaves.
        ; Availabilities must be saved with the game since the player alters them
        ; by buying (and selling(?))
        ;
        ; Almost all commands are one byte only and overflow "errors" are
        ; extremely frequent and exploited.
        ;
        ; Trade Item prices are held internally in a single byte=true value/4.
        ; The decimal point in prices is introduced only when printing them.
        ; Internally, all prices are integers.
        ; The player's cash is held in four bytes.
        ubyte ci
        for ci in 0 to len(names)-1 {
            word product
            byte changing
            product = elite_planet.economy as word * gradients[ci]
            changing = fluct & maskbytes[ci]  as byte
            ubyte q = (basequants[ci] as word + changing - product) as ubyte
            if q & $80 !=0
                q = 0  ; clip to positive 8-bit
            current_quantity[ci] = q & $3f
            q = (baseprices[ci] + changing + product) as ubyte
            current_price[ci] = q * $0004
        }
        current_quantity[16] = 0        ; force nonavailability of Alien Items
    }

    sub display() {
        return
;        ubyte ci
;        txt.nl()
;        elite_planet.print_name_uppercase()
;        txt.print(" trade market:\n    COMMODITY / PRICE / AVAIL / IN HOLD\n")
;        for ci in 0 to len(names)-1 {
;            elite_util.print_right(13, names[ci])
;            txt.print("   ")
;            elite_util.print_10s(current_price[ci])
;            txt.column(24)
;            txt.print_ub(current_quantity[ci])
;            txt.chrout(' ')
;            when units[ci] {
;                0 -> txt.chrout('t')
;                1 -> txt.print("kg")
;                2 -> txt.chrout('g')
;            }
;            txt.column(32)
;            txt.print_ub(elite_ship.cargohold[ci])
;            txt.nl()
;        }
    }

    sub match(uword nameptr) -> ubyte {
        ubyte ci
        for ci in 0 to len(names)-1 {
            if elite_util.prefix_matches(nameptr, names[ci])
                return ci
        }
        return 255
    }
}

elite_galaxy {
    const uword GALSIZE = 256
    const uword base0 = $5A4A       ; seeds for the first galaxy
    const uword base1 = $0248
    const uword base2 = $B753

    str pn_pairs = "..lexegezacebisousesarmaindirea.eratenberalavetiedorquanteisrion"

    ubyte number

    uword[3] seed

    sub init(ubyte galaxynum) {
        number = 1
        elite_planet.number = 255
        seed[0] = base0
        seed[1] = base1
        seed[2] = base2
        repeat galaxynum-1 {
            nextgalaxy()
        }
    }

    sub nextgalaxy() {
        textelite.num_commands++

        seed[0] = twist(seed[0])
        seed[1] = twist(seed[1])
        seed[2] = twist(seed[2])
        number++
        if number==9
            number = 1
    }

    sub travel_to(ubyte galaxynum, ubyte system) {
        init(galaxynum)
        generate_next_planet()   ; always at least planet 0  (separate to avoid repeat ubyte overflow)
        repeat system {
            generate_next_planet()
            textelite.num_commands++
        }
        elite_planet.name = make_current_planet_name()
        init_market_for_planet()
    }

    sub init_market_for_planet() {
        elite_market.init(lsb(seed[0])+msb(seed[2]))
    }

    sub search_closest_planet(uword nameptr) -> bool {
        textelite.num_commands++

        ubyte x = elite_planet.x
        ubyte y = elite_planet.y
        ubyte current_planet_num = elite_planet.number

        init(number)
        bool found = false
        ubyte current_closest_pi
        ubyte current_distance = 127
        ubyte pi
        for pi in 0 to 255 {
            generate_next_planet()
            elite_planet.name = make_current_planet_name()
            if elite_util.prefix_matches(nameptr, elite_planet.name) {
                ubyte distance = elite_planet.distance(x, y)
                if distance < current_distance {
                    current_distance = distance
                    current_closest_pi = pi
                    found = true
                }
            }
        }

        if found
            travel_to(number, current_closest_pi)
        else
            travel_to(number, current_planet_num)

        return found
    }

    sub local_area() {
        ubyte current_planet = elite_planet.number
        ubyte px = elite_planet.x
        ubyte py = elite_planet.y
        ubyte pn = 0

        init(number)
;        txt.print("\nGalaxy #")
;        txt.print_ub(number)
;        txt.print(" - systems in vicinity:\n")
        do {
            generate_next_planet()
            ubyte distance = elite_planet.distance(px, py)
            if distance <= elite_ship.Max_fuel {
;                if distance <= elite_ship.fuel
;                    txt.chrout('*')
;                else
;                    txt.chrout('-')
;                txt.spc()
                elite_planet.name = make_current_planet_name()
                elite_planet.display(true, distance)
            }
            pn++
        } until pn==0

        travel_to(number, current_planet)
    }

    sub starmap(bool local) {
        ubyte current_planet = elite_planet.number
        ubyte px = elite_planet.x
        ubyte py = elite_planet.y
        str current_name = "        "       ; 8 max
        ubyte pn = 0

        current_name = elite_planet.name
        init(number)
;        txt.clear_screen()
;        txt.print("Galaxy #")
;        txt.print_ub(number)
;        if local
;            txt.print(" - local systems")
;        else
;            txt.print(" - galaxy")
;        txt.print(" starmap:\n")
        ubyte max_distance = 255
        if local
            max_distance = elite_ship.Max_fuel
        ubyte home_sx
        ubyte home_sy
        ubyte home_distance

        do {
            generate_next_planet()
            ubyte distance = elite_planet.distance(px, py)
            if distance <= max_distance {
                elite_planet.name = make_current_planet_name()
                elite_planet.name[0] = strings.upperchar(elite_planet.name[0])
                uword tx = elite_planet.x
                uword ty = elite_planet.y
                if local {
                    tx = tx + 24 - px
                    ty = ty + 24 - py
                }
                ubyte sx = display_scale_x(tx)
                ubyte sy = display_scale_y(ty)
                ubyte char = '*'
                if elite_planet.number==current_planet
                    char = '%'
                if local {
                    print_planet_details(elite_planet.name, sx, sy, distance)
                } else if elite_planet.number==current_planet {
                    home_distance = distance
                    home_sx = sx
                    home_sy = sy
                }
                ; txt.setchr(2+sx, 2+sy, char)
            }
            pn++
        } until pn==0

        if not local
            print_planet_details(current_name, home_sx, home_sy, home_distance)

;        if local
;            txt.plot(0, display_scale_y(64) + 4)
;        else
;            txt.plot(0, display_scale_y(256) + 4 as ubyte)
        travel_to(number, current_planet)

        sub print_planet_details(str name, ubyte screenx, ubyte screeny, ubyte d) {
            return
;            txt.plot(2+screenx-2, 2+screeny+1)
;            txt.print(name)
;            if d!=0 {
;                txt.plot(2+screenx-2, 2+screeny+2)
;                elite_util.print_10s(d)
;                txt.print(" LY")
;            }
        }

        sub display_scale_x(uword x) -> ubyte {
            if local
                return x/2 as ubyte
            return x/8 as ubyte
        }

        sub display_scale_y(uword y) -> ubyte {
            if local
                return y/4 as ubyte
            return y/16 as ubyte
        }
    }

    ubyte pn_pair1
    ubyte pn_pair2
    ubyte pn_pair3
    ubyte pn_pair4
    bool longname

    sub generate_next_planet() {
        determine_planet_properties()
        longname = lsb(seed[0]) & 64 !=0

        ; Always four iterations of random number
        pn_pair1 = (msb(seed[2]) & 31) * 2
        tweakseed()
        pn_pair2 = (msb(seed[2]) & 31) * 2
        tweakseed()
        pn_pair3 = (msb(seed[2]) & 31) * 2
        tweakseed()
        pn_pair4 = (msb(seed[2]) & 31) * 2
        tweakseed()
    }

    sub make_current_planet_name() -> str {
        ubyte ni = 0
        str name = "         "    ; max 8

        if pn_pairs[pn_pair1] != '.' {
            name[ni] = pn_pairs[pn_pair1]
            ni++
        }
        if pn_pairs[pn_pair1+1] != '.' {
            name[ni] = pn_pairs[pn_pair1+1]
            ni++
        }
        if pn_pairs[pn_pair2] != '.' {
            name[ni] = pn_pairs[pn_pair2]
            ni++
        }
        if pn_pairs[pn_pair2+1] != '.' {
            name[ni] = pn_pairs[pn_pair2+1]
            ni++
        }
        if pn_pairs[pn_pair3] != '.' {
            name[ni] = pn_pairs[pn_pair3]
            ni++
        }
        if pn_pairs[pn_pair3+1] != '.' {
            name[ni] = pn_pairs[pn_pair3+1]
            ni++
        }

        if longname {
            if pn_pairs[pn_pair4] != '.' {
                name[ni] = pn_pairs[pn_pair4]
                ni++
            }
            if pn_pairs[pn_pair4+1] != '.' {
                name[ni] = pn_pairs[pn_pair4+1]
                ni++
            }
        }

        name[ni] = 0
        return name
    }

    sub determine_planet_properties() {
        ; create the planet's characteristics
        elite_planet.number++
        elite_planet.x = msb(seed[1])
        elite_planet.y = msb(seed[0])
        elite_planet.govtype = lsb(seed[1]) >> 3 & 7  ; bits 3,4 &5 of w1
        elite_planet.economy = msb(seed[0]) & 7  ; bits 8,9 &A of w0
        if elite_planet.govtype <= 1
            elite_planet.economy = (elite_planet.economy | 2)
        elite_planet.techlevel = (msb(seed[1]) & 3) + (elite_planet.economy ^ 7)
        elite_planet.techlevel += elite_planet.govtype >> 1
        if elite_planet.govtype & 1 !=0
            elite_planet.techlevel++
        elite_planet.population = 4 * elite_planet.techlevel + elite_planet.economy
        elite_planet.population += elite_planet.govtype + 1
        elite_planet.productivity = ((elite_planet.economy ^ 7) + 3) * (elite_planet.govtype + 4)
        elite_planet.productivity *= elite_planet.population * 8
        ubyte seed2_msb = msb(seed[2])
        elite_planet.radius = mkword((seed2_msb & 15) + 11, elite_planet.x)
        elite_planet.species_is_alien = lsb(seed[2]) & 128 !=0      ; bit 7 of w2_lo
        if elite_planet.species_is_alien {
            elite_planet.species_size = (seed2_msb >> 2) & 7      ; bits 2-4 of w2_hi
            elite_planet.species_color = seed2_msb >> 5           ; bits 5-7 of w2_hi
            elite_planet.species_look = (seed2_msb ^ msb(seed[1])) & 7   ;bits 0-2 of (w0_hi EOR w1_hi)
            elite_planet.species_kind = (elite_planet.species_look + (seed2_msb & 3)) & 7      ;Add bits 0-1 of w2_hi to A from previous step, and take bits 0-2 of the result
        }

        elite_planet.goatsoup_seed[0] = lsb(seed[1])
        elite_planet.goatsoup_seed[1] = msb(seed[1])
        elite_planet.goatsoup_seed[2] = lsb(seed[2])
        elite_planet.goatsoup_seed[3] = seed2_msb
    }

    sub tweakseed() {
        uword temp = seed[0] + seed[1] + seed[2]
        seed[0] = seed[1]
        seed[1] = seed[2]
        seed[2] = temp
    }

    sub twist(uword x) -> uword {
        ubyte xh = msb(x)
        ubyte xl = lsb(x)
        xh <<= 1        ; make sure carry flag is not used on first shift!
        rol(xl)
        return mkword(xh, xl)
    }
}

elite_planet {
    str[] words81 = ["fabled", "notable", "well known", "famous", "noted"]
    str[] words82 = ["very", "mildly", "most", "reasonably", ""]
    str[] words83 = ["ancient", "\x95", "great", "vast", "pink"]
    str[] words84 = ["\x9E \x9D plantations", "mountains", "\x9C", "\x94 forests", "oceans"]
    str[] words85 = ["shyness", "silliness", "mating traditions", "loathing of \x86", "love for \x86"]
    str[] words86 = ["food blenders", "tourists", "poetry", "discos", "\x8E"]
    str[] words87 = ["talking tree", "crab", "bat", "lobst", "\xB2"]
    str[] words88 = ["beset", "plagued", "ravaged", "cursed", "scourged"]
    str[] words89 = ["\x96 civil war", "\x9B \x98 \x99s", "a \x9B disease", "\x96 earthquakes", "\x96 solar activity"]
    str[] words8A = ["its \x83 \x84", "the \xB1 \x98 \x99", "its inhabitants' \x9A \x85", "\xA1", "its \x8D \x8E"]
    str[] words8B = ["juice", "brandy", "water", "brew", "gargle blasters"]
    str[] words8C = ["\xB2", "\xB1 \x99", "\xB1 \xB2", "\xB1 \x9B", "\x9B \xB2"]
    str[] words8D = ["fabulous", "exotic", "hoopy", "unusual", "exciting"]
    str[] words8E = ["cuisine", "night life", "casinos", "sit coms", " \xA1 "]
    str[] words8F = ["\xB0", "The planet \xB0", "The world \xB0", "This planet", "This world"]
    str[] words90 = ["n unremarkable", " boring", " dull", " tedious", " revolting"]
    str[] words91 = ["planet", "world", "place", "little planet", "dump"]
    str[] words92 = ["wasp", "moth", "grub", "ant", "\xB2"]
    str[] words93 = ["poet", "arts graduate", "yak", "snail", "slug"]
    str[] words94 = ["tropical", "dense", "rain", "impenetrable", "exuberant"]
    str[] words95 = ["funny", "wierd", "unusual", "strange", "peculiar"]
    str[] words96 = ["frequent", "occasional", "unpredictable", "dreadful", "deadly"]
    str[] words97 = ["\x82 \x81 for \x8A", "\x82 \x81 for \x8A and \x8A", "\x88 by \x89", "\x82 \x81 for \x8A but \x88 by \x89", "a\x90 \x91"]
    str[] words98 = ["\x9B", "mountain", "edible", "tree", "spotted"]
    str[] words99 = ["\x9F", "\xA0", "\x87oid", "\x93", "\x92"]
    str[] words9A = ["ancient", "exceptional", "eccentric", "ingrained", "\x95"]
    str[] words9B = ["killer", "deadly", "evil", "lethal", "vicious"]
    str[] words9C = ["parking meters", "dust clouds", "ice bergs", "rock formations", "volcanoes"]
    str[] words9D = ["plant", "tulip", "banana", "corn", "\xB2weed"]
    str[] words9E = ["\xB2", "\xB1 \xB2", "\xB1 \x9B", "inhabitant", "\xB1 \xB2"]
    str[] words9F = ["shrew", "beast", "bison", "snake", "wolf"]
    str[] wordsA0 = ["leopard", "cat", "monkey", "goat", "fish"]
    str[] wordsA1 = ["\x8C \x8B", "\xB1 \x9F \xA2", "its \x8D \xA0 \xA2", "\xA3 \xA4", "\x8C \x8B"]
    str[] wordsA2 = ["meat", "cutlet", "steak", "burgers", "soup"]
    str[] wordsA3 = ["ice", "mud", "Zero-G", "vacuum", "\xB1 ultra"]
    str[] wordsA4 = ["hockey", "cricket", "karate", "polo", "tennis"]

    uword[] @shared wordlists = [
        words81, words82, words83, words84, words85, words86, words87, words88,
        words89, words8A, words8B, words8C, words8D, words8E, words8F, words90,
        words91, words92, words93, words94, words95, words96, words97, words98,
        words99, words9A, words9B, words9C, words9D, words9E, words9F, wordsA0,
        wordsA1, wordsA2, wordsA3, wordsA4]

    str pairs0 = "abouseitiletstonlonuthnoallexegezacebisousesarmaindirea.eratenbe"

    ubyte[4] goatsoup_rnd = [0, 0, 0, 0]
    ubyte[4] goatsoup_seed = [0, 0, 0, 0]

    str name = "        "       ; 8 max
    ubyte number                ; starts at 0 in new galaxy, then increases by 1 for each generated planet
    ubyte x
    ubyte y
    ubyte economy
    ubyte govtype
    ubyte techlevel
    ubyte population
    uword productivity
    uword radius
    bool species_is_alien      ; otherwise "Human Colonials"
    ubyte species_size
    ubyte species_color
    ubyte species_look
    ubyte species_kind

    sub set_seed(uword s1, uword s2) {
        goatsoup_seed[0] = lsb(s1)
        goatsoup_seed[1] = msb(s1)
        goatsoup_seed[2] = lsb(s2)
        goatsoup_seed[3] = msb(s2)
        reset_rnd()
    }

    sub reset_rnd() {
        goatsoup_rnd[0] = goatsoup_seed[0]
        goatsoup_rnd[1] = goatsoup_seed[1]
        goatsoup_rnd[2] = goatsoup_seed[2]
        goatsoup_rnd[3] = goatsoup_seed[3]
    }

    sub random_name() -> str {
        ubyte ii
        str randname = "        "       ; 8 chars max
        ubyte nx = 0
        for ii in 0 to goatsoup_rnd_number() & 3 {
            ubyte xx = goatsoup_rnd_number() & $3e
            if pairs0[xx] != '.' {
                randname[nx] = pairs0[xx]
                nx++
            }
            xx++
            if pairs0[xx] != '.' {
                randname[nx] = pairs0[xx]
                nx++
            }
        }
        randname[nx] = 0
        randname[0] = strings.upperchar(randname[0])
        return randname
    }

    sub goatsoup_rnd_number() -> ubyte {
        ubyte xx = goatsoup_rnd[0] * 2
        uword a = xx as uword + goatsoup_rnd[2]
        if goatsoup_rnd[0] > 127
            a ++
        goatsoup_rnd[0] = lsb(a)
        goatsoup_rnd[2] = xx
        xx = goatsoup_rnd[1]
        ubyte ac = xx + goatsoup_rnd[3] + msb(a)
        goatsoup_rnd[1] = ac
        goatsoup_rnd[3] = xx
        return ac
    }

    sub distance(ubyte px, ubyte py) -> ubyte {
        uword ax
        uword ay
        if px>x
            ax=px-x
        else
            ax=x-px
        if py>y
            ay=py-y
        else
            ay=y-py
        ay /= 2
        ubyte d = sqrt(ax*ax + ay*ay)
        if d>63
            return 255
        return d*4
    }

    sub soup() -> str {
        str planet_result = " " * 160
        uword[6] source_stack
        ubyte stack_ptr = 0
        str start_source = "\x8F is \x97."
        uword source_ptr = &start_source
        uword result_ptr = &planet_result

        reset_rnd()
        recursive_soup()
        return planet_result

        sub recursive_soup() {
            repeat {
                ubyte c = @(source_ptr)
                source_ptr++
                if c == $00 {
                    @(result_ptr) = 0
                    return
                }
                else if c <= $80 {
                    @(result_ptr) = c
                    result_ptr++
                }
                else {
                    if c <= $a4 {
                        ubyte rnr = goatsoup_rnd_number()
                        ubyte wordNr = ((rnr >= $33) as ubyte) + ((rnr >= $66) as ubyte) + ((rnr >= $99) as ubyte) + ((rnr >= $CC) as ubyte)
                        source_stack[stack_ptr] = source_ptr
                        stack_ptr++
                        source_ptr = getword(c, wordNr)
                        recursive_soup()    ; RECURSIVE CALL - ignore the warning message from the compiler; we don't use local variables or parameters so we're safe in this case
                        stack_ptr--
                        source_ptr = source_stack[stack_ptr]
                    } else {
                        if c == $b0 {
                            @(result_ptr) = strings.upperchar(name[0])
                            result_ptr++
                            concat_string(&name + 1)
                        }
                        else if c == $b1 {
                            @(result_ptr) = strings.upperchar(name[0])
                            result_ptr++
                            ubyte ni
                            for ni in 1 to len(name) {
                                ubyte cc = name[ni]
                                if cc in ['e', 'o', 0]
                                    break
                                else {
                                    @(result_ptr) = cc
                                    result_ptr++
                                }
                            }
                            @(result_ptr) = 'i'
                            result_ptr++
                            @(result_ptr) = 'a'
                            result_ptr++
                            @(result_ptr) = 'n'
                            result_ptr++
                        }
                        else if c == $b2 {
                            concat_string(random_name())
                        }
                        else {
                            @(result_ptr) = c
                            result_ptr++
                        }
                    }
                }
            }
        }

        sub concat_string(uword str_ptr) {
            repeat {
                ubyte c = @(str_ptr)
                if c==0
                    break
                else {
                    @(result_ptr) = c
                    str_ptr++
                    result_ptr++
                }
            }
        }
    }

    sub display(bool compressed, ubyte distance) {
        txt.print(soup())
        txt.nl()
    }

    sub getword(ubyte listnum, ubyte wordidx) -> uword {
        uword list = wordlists[listnum-$81]
        return peekw(list + wordidx*2)
    }
}

elite_util {
    sub prefix_matches(uword prefixptr, uword stringptr) -> bool {
        repeat {
            ubyte pc = @(prefixptr)
            ubyte sc = @(stringptr)
            if pc == 0
                return true
            ; to lowercase for case insensitive compare:
            if strings.lowerchar(pc)!=strings.lowerchar(sc)
                return false
            prefixptr++
            stringptr++
        }
    }
}
