%import textio
%import conv
%import diskio
%import strings
%zeropage basicsafe

; Prog8 adaptation of the Text-Elite galaxy system trading simulation engine.
; Original C-version obtained from: http://www.elitehomepage.org/text/index.htm

; Note: this program can be compiled for multiple target systems, including the virtual machine.

main {

    const ubyte numforLave = 7      ;  Lave is 7th generated planet in galaxy one
    const ubyte numforZaonce = 129
    const ubyte numforDiso = 147
    const ubyte numforRiedquat = 46
    ubyte terminal_width

    sub start() {
        terminal_width = txt.width()
        txt.lowercase()
        txt.clear_screen()
        txt.print("\n --- TextElite v1.3 ---\n")

        planet.set_seed(0, 0)
        galaxy.travel_to(1, numforLave)
        market.init(0)  ;  Lave's market is seeded with 0
        ship.init()
        planet.display(false, 0)

        repeat {
            str input = "????????"
            txt.print("\nCash: ")
            util.print_10s(ship.cash)
            txt.print("\nCommand (?=help): ")
            ubyte num_chars = txt.input_chars(input)
            txt.nl()
            if num_chars!=0 {
                when input[0] {
                    '?' -> {
                        txt.print("\nCommands are:\n"+
                            "buy   jump      info    map    >=save\n"+
                            "sell  teleport  market  cash   <=load\n"+
                            "fuel  galhyp    local   hold   quit\n")
                    }
                    'q' -> break
                    'b' -> trader.do_buy()
                    's' -> trader.do_sell()
                    'f' -> trader.do_fuel()
                    'j' -> trader.do_jump()
                    't' -> trader.do_teleport()
                    'g' -> trader.do_next_galaxy()
                    'i' -> trader.do_info()
                    'm' -> {
                        if input[1]=='a' and input[2]=='p'
                            trader.do_map()
                        else
                            trader.do_show_market()
                    }
                    'l' -> trader.do_local()
                    'c' -> trader.do_cash()
                    'h' -> trader.do_hold()
                    '<' -> trader.do_load()
                    '>' -> trader.do_save()
                }
            }
        }
    }
}

trader {
    str Savegame = "!commander.save"
    str input = "??????????"
    ubyte num_chars

    ubyte[23]  savedata
    ; format:
    ;  0 ubyte galaxy
    ;  1 ubyte planet
    ;  2-18  ubyte cargo0..cargo16
    ; 19 uword cash
    ; 21 ubyte max_cargo
    ; 22 ubyte fuel

    sub do_load() {
        txt.print("\nLoading universe...")
        if diskio.load(Savegame, &savedata)==&savedata+sizeof(savedata) {
            txt.print("ok\n")
        } else {
            txt.print("\ni/o error: ")
            txt.print(diskio.status())
            txt.nl()
            return
        }

        ship.cash = mkword(savedata[20], savedata[19])
        ship.Max_cargo = savedata[21]
        ship.fuel = savedata[22]
        sys.memcopy(&savedata + 2, ship.cargohold, len(ship.cargohold))
        galaxy.travel_to(savedata[0], savedata[1])

        planet.display(false, 0)
    }

    sub do_save() {
        savedata[0] = galaxy.number
        savedata[1] = planet.number
        savedata[19] = lsb(ship.cash)
        savedata[20] = msb(ship.cash)
        savedata[21] = ship.Max_cargo
        savedata[22] = ship.fuel
        sys.memcopy(ship.cargohold, &savedata + 2, len(ship.cargohold))

        txt.print("\nSaving universe...")
        diskio.delete(Savegame)
        if diskio.save(Savegame, &savedata, sizeof(savedata)) {
            txt.print("ok\n")
        } else {
            txt.print("\ni/o error: ")
            txt.print(diskio.status())
            txt.nl()
        }
    }

    sub do_jump() {
        txt.print("\nJump to what system? ")
        jump_to_system()
    }

    sub do_teleport() {
        txt.print("\nCheat! Teleport to what system? ")
        ubyte fuel = ship.fuel
        ship.fuel = 255
        jump_to_system()
        ship.fuel = fuel
    }

    sub jump_to_system() {
        void txt.input_chars(input)
        ubyte current_planet = planet.number
        ubyte x = planet.x
        ubyte y = planet.y
        if galaxy.search_closest_planet(input) {
            ubyte distance = planet.distance(x, y)
            if distance <= ship.fuel {
                galaxy.init_market_for_planet()
                ship.fuel -= distance
                txt.print("\n\nHyperspace jump! Arrived at:\n")
                planet.display(true,0 )
                return
            }
            txt.print("\nInsufficient fuel\n")
        } else {
            txt.print(" Not found!\n")
        }
        galaxy.travel_to(galaxy.number, current_planet)
    }

    sub do_buy() {
        txt.print("\nBuy what commodity? ")
        str commodity = "???????????????"
        void txt.input_chars(commodity)
        ubyte ci = market.match(commodity)
        if ci & 128 !=0 {
            txt.print("Unknown\n")
        } else {
            txt.print("\nHow much? ")
            void txt.input_chars(input)
            ubyte amount = conv.str2ubyte(input)
            if market.current_quantity[ci] < amount {
                txt.print(" Insufficient supply!\n")
            } else {
                uword price = market.current_price[ci] * amount
                txt.print(" Total price: ")
                util.print_10s(price)
                if price > ship.cash {
                    txt.print(" Not enough cash!\n")
                } else {
                    ship.cash -= price
                    ship.cargohold[ci] += amount
                    market.current_quantity[ci] -= amount
                }
            }
        }
    }

    sub do_sell() {
        txt.print("\nSell what commodity? ")
        str commodity = "???????????????"
        void txt.input_chars(commodity)
        ubyte ci = market.match(commodity)
        if ci & 128 !=0 {
            txt.print("Unknown\n")
        } else {
            txt.print("\nHow much? ")
            void txt.input_chars(input)
            ubyte amount = conv.str2ubyte(input)
            if ship.cargohold[ci] < amount {
                txt.print(" Insufficient supply!\n")
            } else {
                uword price = market.current_price[ci] * amount
                txt.print(" Total price: ")
                util.print_10s(price)
                ship.cash += price
                ship.cargohold[ci] -= amount
                market.current_quantity[ci] += amount
            }
        }
    }

    sub do_fuel() {
        txt.print("\nBuy fuel. Amount? ")
        void txt.input_chars(input)
        ubyte buy_fuel = 10*conv.str2ubyte(input)
        ubyte max_fuel = ship.Max_fuel - ship.fuel
        if buy_fuel > max_fuel
            buy_fuel = max_fuel
        uword price = buy_fuel as uword * ship.Fuel_cost
        if price > ship.cash {
            txt.print("Not enough cash!\n")
        } else {
            ship.cash -= price
            ship.fuel += buy_fuel
        }
    }

    sub do_cash() {
        txt.print("\nCheat! Set cash amount: ")
        void txt.input_chars(input)
        ship.cash = conv.str2uword(input)
    }

    sub do_hold() {
        txt.print("\nCheat! Set cargohold size: ")
        void txt.input_chars(input)
        ship.Max_cargo = conv.str2ubyte(input)
    }

    sub do_next_galaxy() {
        txt.print("\n>>>>>>>> Galaxy Hyperjump!\n")
        galaxy.travel_to(galaxy.number+1, planet.number)
        planet.display(false, 0)
    }

    sub do_info() {
        txt.print("\nSystem name (empty=current): ")
        num_chars = txt.input_chars(input)
        if num_chars!=0 {
            ubyte current_planet = planet.number
            ubyte x = planet.x
            ubyte y = planet.y
            if galaxy.search_closest_planet(input) {
                ubyte distance = planet.distance(x, y)
                planet.display(false, distance)
            } else {
                txt.print(" Not found!")
            }
            galaxy.travel_to(galaxy.number, current_planet)
        } else {
            planet.display(false, 0)
        }
    }

    sub do_local() {
        galaxy.local_area()
    }

    sub do_map() {
        txt.print("\n(l)ocal or (g)alaxy starmap? ")
        num_chars = txt.input_chars(input)
        if num_chars!=0 {
            galaxy.starmap(input[0]=='l')
        }
    }

    sub do_show_market() {
        market.display()
        txt.print("\nFuel: ")
        util.print_10s(ship.fuel)
        txt.print("   Cargohold space: ")
        txt.print_ub(ship.cargo_free())
        txt.print("t\n")
    }
}

ship {
    const ubyte Max_fuel = 70
    const ubyte Fuel_cost = 2
    ubyte Max_cargo = 20

    ubyte fuel = Max_fuel
    uword cash = 1000               ; actually has to be 4 bytes for the ultra rich....
    ubyte[17] cargohold

    sub init() {
        sys.memset(cargohold, len(cargohold), 0)
    }

    sub cargo_free() -> ubyte {
        ubyte ci
        ubyte total = 0
        for ci in 0 to len(cargohold)-1 {
            if market.units[ci]==0      ; tonnes only
                total += cargohold[ci]
        }
        return Max_cargo - total
    }
}

market {
    ubyte[17] baseprices = [$13, $14, $41, $28, $53, $C4, $EB, $9A, $75, $4E, $7C, $B0, $20, $61, $AB, $2D, $35]
    byte[17] gradients = [-$02, -$01, -$03, -$05, -$05, $08, $1D, $0E, $06, $01, $0d, -$09, -$01, -$01, -$02, -$01, $0F]
    ubyte[17] basequants = [$06, $0A, $02, $E2, $FB, $36, $08, $38, $28, $11, $1D, $DC, $35, $42, $37, $FA, $C0]
    ubyte[17] maskbytes = [$01, $03, $07, $1F, $0F, $03, $78, $03, $07, $1F, $07, $3F, $03, $07, $1F, $0F, $07]
    ubyte[17] units = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 0]
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
        ; Almost all operations are one byte only and overflow "errors" are
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
            product = planet.economy as word * gradients[ci]
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
        ubyte ci
        txt.nl()
        planet.print_name_uppercase()
        txt.print(" trade market:\n    COMMODITY / PRICE / AVAIL / IN HOLD\n")
        for ci in 0 to len(names)-1 {
            util.print_right(13, names[ci])
            txt.print("   ")
            util.print_10s(current_price[ci])
            txt.column(24)
            txt.print_ub(current_quantity[ci])
            txt.chrout(' ')
            when units[ci] {
                0 -> txt.chrout('t')
                1 -> txt.print("kg")
                2 -> txt.chrout('g')
            }
            txt.column(32)
            txt.print_ub(ship.cargohold[ci])
            txt.nl()
        }
    }

    sub match(uword nameptr) -> ubyte {
        ubyte ci
        for ci in 0 to len(names)-1 {
            if util.prefix_matches(nameptr, names[ci])
                return ci
        }
        return 255
    }
}

galaxy {
    const uword GALSIZE = 256
    const uword base0 = $5A4A       ; seeds for the first galaxy
    const uword base1 = $0248
    const uword base2 = $B753

    str pn_pairs = "..lexegezacebisousesarmaindirea.eratenberalavetiedorquanteisrion"

    ubyte number

    uword[3] seed

    sub init(ubyte galaxynum) {
        number = 1
        planet.number = 255
        seed[0] = base0
        seed[1] = base1
        seed[2] = base2
        repeat galaxynum-1 {
            nextgalaxy()
        }
    }

    sub nextgalaxy() {
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
        }
        planet.name = make_current_planet_name()
        init_market_for_planet()
    }

    sub init_market_for_planet() {
        market.init(lsb(seed[0])+msb(seed[2]))
    }

    sub search_closest_planet(uword nameptr) -> bool {
        ubyte x = planet.x
        ubyte y = planet.y
        ubyte current_planet_num = planet.number

        init(number)
        bool found = false
        ubyte current_closest_pi
        ubyte current_distance = 127
        ubyte pi
        for pi in 0 to 255 {
            generate_next_planet()
            planet.name = make_current_planet_name()
            if util.prefix_matches(nameptr, planet.name) {
                ubyte distance = planet.distance(x, y)
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
        ubyte current_planet = planet.number
        ubyte px = planet.x
        ubyte py = planet.y
        ubyte pn = 0

        init(number)
        txt.print("\nGalaxy #")
        txt.print_ub(number)
        txt.print(" - systems in vicinity:\n")
        do {
            generate_next_planet()
            ubyte distance = planet.distance(px, py)
            if distance <= ship.Max_fuel {
                if distance <= ship.fuel
                    txt.chrout('*')
                else
                    txt.chrout('-')
                txt.spc()
                planet.name = make_current_planet_name()
                planet.display(true, distance)
            }
            pn++
        } until pn==0

        travel_to(number, current_planet)
    }

    sub starmap(bool local) {
        ubyte current_planet = planet.number
        ubyte px = planet.x
        ubyte py = planet.y
        str current_name = "        "       ; 8 max
        ubyte pn = 0

        current_name = planet.name
        init(number)
        txt.clear_screen()
        txt.print("Galaxy #")
        txt.print_ub(number)
        if local
            txt.print(" - local systems")
        else
            txt.print(" - galaxy")
        txt.print(" starmap:\n")
        ubyte max_distance = 255
        if local
            max_distance = ship.Max_fuel
        ubyte home_sx
        ubyte home_sy
        ubyte home_distance

        do {
            generate_next_planet()
            ubyte distance = planet.distance(px, py)
            if distance <= max_distance {
                planet.name = make_current_planet_name()
                planet.name[0] = strings.upperchar(planet.name[0])
                uword tx = planet.x
                uword ty = planet.y
                if local {
                    tx = tx + 24 - px
                    ty = ty + 24 - py
                }
                ubyte sx = display_scale_x(tx)
                ubyte sy = display_scale_y(ty)
                ubyte char = '*'
                if planet.number==current_planet
                    char = '%'
                if local {
                    print_planet_details(planet.name, sx, sy, distance)
                } else if planet.number==current_planet {
                    home_distance = distance
                    home_sx = sx
                    home_sy = sy
                }
                txt.setchr(2+sx, 2+sy, char)
            }
            pn++
        } until pn==0

        if not local
            print_planet_details(current_name, home_sx, home_sy, home_distance)

        if local
            txt.plot(0, display_scale_y(64) + 4)
        else
            txt.plot(0, display_scale_y(256) + 4 as ubyte)
        travel_to(number, current_planet)

        sub print_planet_details(str name, ubyte screenx, ubyte screeny, ubyte d) {
            txt.plot(2+screenx-2, 2+screeny+1)
            txt.print(name)
            if d!=0 {
                txt.plot(2+screenx-2, 2+screeny+2)
                util.print_10s(d)
                txt.print(" LY")
            }
        }

        sub display_scale_x(uword x) -> ubyte {
            if main.terminal_width > 64 {
                if local
                    return x as ubyte
                return x/4 as ubyte
            }
            if local
                return x/2 as ubyte
            return x/8 as ubyte
        }

        sub display_scale_y(uword y) -> ubyte {
            if main.terminal_width > 64 {
                if local
                    return y/2 as ubyte
                return y/8 as ubyte
            }
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
        planet.number++
        planet.x = msb(seed[1])
        planet.y = msb(seed[0])
        planet.govtype = lsb(seed[1]) >> 3 & 7  ; bits 3,4 &5 of w1
        planet.economy = msb(seed[0]) & 7  ; bits 8,9 &A of w0
        if planet.govtype <= 1
            planet.economy = (planet.economy | 2)
        planet.techlevel = (msb(seed[1]) & 3) + (planet.economy ^ 7)
        planet.techlevel += planet.govtype >> 1
        if planet.govtype & 1 !=0
            planet.techlevel++
        planet.population = 4 * planet.techlevel + planet.economy
        planet.population += planet.govtype + 1
        planet.productivity = ((planet.economy ^ 7) + 3) * (planet.govtype + 4)
        planet.productivity *= planet.population * 8
        ubyte seed2_msb = msb(seed[2])
        planet.radius = mkword((seed2_msb & 15) + 11, planet.x)
        planet.species_is_alien = lsb(seed[2]) & 128 !=0      ; bit 7 of w2_lo
        if planet.species_is_alien {
            planet.species_size = (seed2_msb >> 2) & 7      ; bits 2-4 of w2_hi
            planet.species_color = seed2_msb >> 5           ; bits 5-7 of w2_hi
            planet.species_look = (seed2_msb ^ msb(seed[1])) & 7   ;bits 0-2 of (w0_hi EOR w1_hi)
            planet.species_kind = (planet.species_look + (seed2_msb & 3)) & 7      ;Add bits 0-1 of w2_hi to A from previous step, and take bits 0-2 of the result
        }

        planet.goatsoup_seed[0] = lsb(seed[1])
        planet.goatsoup_seed[1] = msb(seed[1])
        planet.goatsoup_seed[2] = lsb(seed[2])
        planet.goatsoup_seed[3] = seed2_msb
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

planet {
    str[] species_sizes = ["Large", "Fierce", "Small"]
    str[] species_colors = ["Green", "Red", "Yellow", "Blue", "Black", "Harmless"]
    str[] species_looks = ["Slimy", "Bug-Eyed", "Horned", "Bony", "Fat", "Furry"]
    str[] species_kinds = ["Rodents", "Frogs", "Lizards", "Lobsters", "Birds", "Humanoids", "Felines", "Insects"]
    str[] govnames = ["Anarchy", "Feudal", "Multi-gov", "Dictatorship", "Communist", "Confederacy", "Democracy", "Corporate State"]
    str[] econnames = ["Rich Industrial", "Average Industrial", "Poor Industrial", "Mainly Industrial",
                       "Mainly Agricultural", "Rich Agricultural", "Average Agricultural", "Poor Agricultural"]

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
        if compressed {
            print_name_uppercase()
            if distance!=0 {
                txt.print(" (")
                util.print_10s(distance)
                txt.print(" LY)")
            }
            txt.print("  Tech level:")
            txt.print_ub(techlevel+1)
            txt.print("\n    ")
            txt.print(econnames[economy])
            txt.spc()
            txt.print(govnames[govtype])
            txt.nl()
        } else {
            txt.print("\n\nSystem: ")
            print_name_uppercase()
            txt.print("\nPosition: ")
            txt.print_ub(x)
            txt.chrout('\'')
            txt.print_ub(y)
            txt.spc()
            txt.chrout('#')
            txt.print_ub(number)
            if distance!=0 {
                txt.print("\nDistance: ")
                util.print_10s(distance)
                txt.print(" LY")
            }
            txt.print("\nEconomy: ")
            txt.print(econnames[economy])
            txt.print("\nGovernment: ")
            txt.print(govnames[govtype])
            txt.print("\nTech Level: ")
            txt.print_ub(techlevel+1)
            txt.print("\nTurnover: ")
            txt.print_uw(productivity)
            txt.print("\nRadius: ")
            txt.print_uw(radius)
            txt.print("\nPopulation: ")
            txt.print_ub(population >> 3)
            txt.print(" Billion\nSpecies: ")
            if species_is_alien {
                if species_size < len(species_sizes) {
                    txt.print(species_sizes[species_size])
                    txt.spc()
                }
                if species_color < len(species_colors) {
                    txt.print(species_colors[species_color])
                    txt.spc()
                }
                if species_look < len(species_looks) {
                    txt.print(species_looks[species_look])
                    txt.spc()
                }
                if species_kind < len(species_kinds) {
                    txt.print(species_kinds[species_kind])
                }
            } else {
                txt.print("Human Colonials")
            }
            txt.nl()
            txt.print(soup())
            txt.nl()
        }
    }

    sub print_name_uppercase() {
        for cx16.r0L in name
            txt.chrout(strings.upperchar(cx16.r0L))
    }

    sub getword(ubyte listnum, ubyte wordidx) -> uword {
        uword list = wordlists[listnum-$81]
        return peekw(list + wordidx*2)
    }
}

util {
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

    sub print_right(ubyte width, uword s) {
        repeat width - strings.length(s) {
            txt.spc()
        }
        txt.print(s)
    }

    sub print_10s(uword value) {
        txt.print_uw(value/10)
        txt.chrout('.')
        txt.print_uw(value % 10)
    }
}
