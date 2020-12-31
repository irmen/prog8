%import textio
%import conv
%import diskio
%import test_stack
%option no_sysinit
%zeropage basicsafe

; Prog8 adaptation of the Text-Elite galaxy8 system trading simulation engine.
; Original C-version obtained from: http://www.elitehomepage.org/text/index.htm

; Note: this program is compatible with C64 and CX16.

perf8 {

    const ubyte numforLave = 7      ;  Lave is 7th generated planet8 in galaxy8 one
    const ubyte numforZaonce = 129
    const ubyte numforDiso = 147
    const ubyte numforRiedquat = 46

    sub start() {
        txt.lowercase()
        txt.print("\u000c\n --- TextElite v1.1 ---\n")

        galaxy8.travel_to(1, numforLave)
        market8.init(0)  ;  Lave's market8 is seeded with 0
        ship8.init()
        planet8.display(false)

        repeat {
            ; test_stack.test()

            str input = "????????"
            txt.print("\nCash: ")
            util8.print_10s(ship8.cash)
            txt.print("\nCommand (?=help): ")
            ubyte num_chars = txt.input_chars(input)
            txt.chrout('\n')
            if num_chars {
                when input[0] {
                    '?' -> {
                        txt.print("\nCommands are:\n"+
                            "buy   jump      info    cash   >=save\n"+
                            "sell  teleport  market8  hold   <=load\n"+
                            "fuel  galhyp    local          quit\n")
                    }
                    'q' -> break
                    'b' -> trader8.do_buy()
                    's' -> trader8.do_sell()
                    'f' -> trader8.do_fuel()
                    'j' -> trader8.do_jump()
                    't' -> trader8.do_teleport()
                    'g' -> trader8.do_next_galaxy8()
                    'i' -> trader8.do_info()
                    'm' -> trader8.do_show_market8()
                    'l' -> trader8.do_local()
                    'c' -> trader8.do_cash()
                    'h' -> trader8.do_hold()
                    '<' -> trader8.do_load()
                    '>' -> trader8.do_save()
                }
            }
        }
    }
}

trader8 {
    str Savegame = "↑commander.save"
    str input = "??????????"
    ubyte num_chars

    struct SaveData {
        ubyte galaxy8
        ubyte planet8
        ubyte cargo0
        ubyte cargo1
        ubyte cargo2
        ubyte cargo3
        ubyte cargo4
        ubyte cargo5
        ubyte cargo6
        ubyte cargo7
        ubyte cargo8
        ubyte cargo9
        ubyte cargo10
        ubyte cargo11
        ubyte cargo12
        ubyte cargo13
        ubyte cargo14
        ubyte cargo15
        ubyte cargo16
        uword cash
        ubyte max_cargo
        ubyte fuel
    }
    SaveData savedata

    sub do_load() {
        txt.print("\nLoading universe...")
        if diskio.load(8, Savegame, &savedata) {
            txt.print("ok\n")
        } else {
            txt.print("\ni/o error: ")
            txt.print(diskio.status(8))
            txt.chrout('\n')
            return
        }

        ship8.cash = savedata.cash
        ship8.Max_cargo = savedata.max_cargo
        ship8.fuel = savedata.fuel
        memcopy(&savedata.cargo0, ship8.cargohold, len(ship8.cargohold))
        galaxy8.travel_to(savedata.galaxy8, savedata.planet8)

        planet8.display(false)
    }

    sub do_save() {
        savedata.galaxy8 = galaxy8.number
        savedata.planet8 = planet8.number
        savedata.cash = ship8.cash
        savedata.max_cargo = ship8.Max_cargo
        savedata.fuel = ship8.fuel
        memcopy(ship8.cargohold, &savedata.cargo0, len(ship8.cargohold))

        txt.print("\nSaving universe...")
        diskio.delete(8, Savegame)
        if diskio.save(8, Savegame, &savedata, sizeof(savedata)) {
            txt.print("ok\n")
        } else {
            txt.print("\ni/o error: ")
            txt.print(diskio.status(8))
            txt.chrout('\n')
        }
    }

    sub do_jump() {
        txt.print("\nJump to what system? ")
        jump_to_system()
    }

    sub do_teleport() {
        txt.print("\nCheat! Teleport to what system? ")
        ubyte fuel = ship8.fuel
        ship8.fuel = 255
        jump_to_system()
        ship8.fuel = fuel
    }

    sub jump_to_system() {
        void txt.input_chars(input)
        ubyte current_planet8 = planet8.number
        ubyte x = planet8.x
        ubyte y = planet8.y
        if galaxy8.search_closest_planet8(input) {
            ubyte distance = planet8.distance(x, y)
            if distance <= ship8.fuel {
                galaxy8.init_market8_for_planet8()
                ship8.fuel -= distance
                txt.print("\n\nHyperspace jump! Arrived at:\n")
                planet8.display(true)
                return
            }
            txt.print("Insufficient fuel\n")
        } else {
            txt.print(" Not found!\n")
        }
        galaxy8.travel_to(galaxy8.number, current_planet8)
    }

    sub do_buy() {
        txt.print("\nBuy what commodity? ")
        str commodity = "???????????????"
        void txt.input_chars(commodity)
        ubyte ci = market8.match(commodity)
        if ci & 128 {
            txt.print("Unknown\n")
        } else {
            txt.print("\nHow much? ")
            void txt.input_chars(input)
            ubyte amount = conv.str2ubyte(input)
            if market8.current_quantity[ci] < amount {
                txt.print(" Insufficient supply!\n")
            } else {
                uword price = market8.current_price[ci] * amount
                txt.print(" Total price: ")
                util8.print_10s(price)
                if price > ship8.cash {
                    txt.print(" Not enough cash!\n")
                } else {
                    ship8.cash -= price
                    ship8.cargohold[ci] += amount
                    market8.current_quantity[ci] -= amount
                }
            }
        }
    }

    sub do_sell() {
        txt.print("\nSell what commodity? ")
        str commodity = "???????????????"
        void txt.input_chars(commodity)
        ubyte ci = market8.match(commodity)
        if ci & 128 {
            txt.print("Unknown\n")
        } else {
            txt.print("\nHow much? ")
            void txt.input_chars(input)
            ubyte amount = conv.str2ubyte(input)
            if ship8.cargohold[ci] < amount {
                txt.print(" Insufficient supply!\n")
            } else {
                uword price = market8.current_price[ci] * amount
                txt.print(" Total price: ")
                util8.print_10s(price)
                ship8.cash += price
                ship8.cargohold[ci] -= amount
                market8.current_quantity[ci] += amount
            }
        }
    }

    sub do_fuel() {
        txt.print("\nBuy fuel. Amount? ")
        void txt.input_chars(input)
        ubyte buy_fuel = 10*conv.str2ubyte(input)
        ubyte max_fuel = ship8.Max_fuel - ship8.fuel
        if buy_fuel > max_fuel
            buy_fuel = max_fuel
        uword price = buy_fuel as uword * ship8.Fuel_cost
        if price > ship8.cash {
            txt.print("Not enough cash!\n")
        } else {
            ship8.cash -= price
            ship8.fuel += buy_fuel
        }
    }

    sub do_cash() {
        txt.print("\nCheat! Set cash amount: ")
        void txt.input_chars(input)
        ship8.cash = conv.str2uword(input)
    }

    sub do_hold() {
        txt.print("\nCheat! Set cargohold size: ")
        void txt.input_chars(input)
        ship8.Max_cargo = conv.str2ubyte(input)
    }

    sub do_next_galaxy8() {
        galaxy8.travel_to(galaxy8.number+1, planet8.number)
        planet8.display(false)
    }

    sub do_info() {
        txt.print("\nSystem name (empty=current): ")
        num_chars = txt.input_chars(input)
        if num_chars {
            ubyte current_planet8 = planet8.number
            if galaxy8.search_closest_planet8(input) {
                planet8.display(false)
            } else {
                txt.print(" Not found!")
            }
            galaxy8.travel_to(galaxy8.number, current_planet8)
        } else {
            planet8.display(false)
        }
    }

    sub do_local() {
        galaxy8.local_area()
    }

    sub do_show_market8() {
        market8.display()
        txt.print("\nFuel: ")
        util8.print_10s(ship8.fuel)
        txt.print("   Cargohold space: ")
        txt.print_ub(ship8.cargo_free())
        txt.print("t\n")
    }
}

ship8 {
    const ubyte Max_fuel = 70
    const ubyte Fuel_cost = 2
    ubyte Max_cargo = 20

    ubyte fuel = Max_fuel
    uword cash = 1000               ; actually has to be 4 bytes for the ultra rich....
    ubyte[17] cargohold = 0

    sub init() {
        memset(cargohold, len(cargohold), 0)
    }

    sub cargo_free() -> ubyte {
        ubyte ci
        ubyte total = 0
        for ci in 0 to len(cargohold)-1 {
            if market8.units[ci]==0      ; tonnes only
                total += cargohold[ci]
        }
        return Max_cargo - total
    }
}

market8 {
    ubyte[17] baseprices = [$13, $14, $41, $28, $53, $C4, $EB, $9A, $75, $4E, $7C, $B0, $20, $61, $AB, $2D, $35]
    byte[17] gradients = [-$02, -$01, -$03, -$05, -$05, $08, $1D, $0E, $06, $01, $0d, -$09, -$01, -$01, -$02, -$01, $0F]
    ubyte[17] basequants = [$06, $0A, $02, $E2, $FB, $36, $08, $38, $28, $11, $1D, $DC, $35, $42, $37, $FA, $C0]
    ubyte[17] maskbytes = [$01, $03, $07, $1F, $0F, $03, $78, $03, $07, $1F, $07, $3F, $03, $07, $1F, $0F, $07]
    ubyte[17] units = [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 2, 0]
    str[17] names = ["Food", "Textiles", "Radioactives", "Slaves", "Liquor/Wines", "Luxuries", "Narcotics", "Computers",
                     "Machinery", "Alloys", "Firearms", "Furs", "Minerals", "Gold", "Platinum", "Gem-Stones", "Alien Items"]

    ubyte[17] current_quantity = 0
    uword[17] current_price = 0

    sub init(ubyte fluct) {
        ; Prices and availabilities are influenced by the planet8's economy type
        ; (0-7) and a random "fluctuation" byte that was kept within the saved
        ; commander position to keep the market8 prices constant over gamesaves.
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
            product = planet8.economy as word * gradients[ci]
            changing = fluct & maskbytes[ci]  as byte
            ubyte q = (basequants[ci] as word + changing - product) as ubyte
            if q & $80
                q = 0  ; clip to positive 8-bit
            current_quantity[ci] = q & $3f
            q = (baseprices[ci] + changing + product) as ubyte
            current_price[ci] = q * $0004
        }
        current_quantity[16] = 0        ; force nonavailability of Alien Items
    }

    sub display() {
        ubyte ci
        txt.chrout('\n')
        planet8.print_name_uppercase()
        txt.print(" trade market8:\n    COMMODITY / PRICE / AVAIL / IN HOLD\n")
        for ci in 0 to len(names)-1 {
            util8.print_right(13, names[ci])
            txt.print("   ")
            util8.print_10s(current_price[ci])
            txt.print("  ")
            txt.print_ub(current_quantity[ci])
            when units[ci] {
                0 -> txt.chrout('t')
                1 -> txt.print("kg")
                2 -> txt.chrout('g')
            }
            txt.print("   ")
            txt.print_ub(ship8.cargohold[ci])
            txt.chrout('\n')
        }
    }

    sub match(uword nameptr) -> ubyte {
        ubyte ci
        for ci in 0 to len(names)-1 {
            if util8.prefix_matches(nameptr, names[ci])
                return ci
        }
        return 255
    }
}

galaxy8 {
    const uword GALSIZE = 256
    const uword base0 = $5A4A       ; seeds for the first galaxy8
    const uword base1 = $0248
    const uword base2 = $B753

    str pn_pairs = "..lexegezacebisousesarmaindirea.eratenberalavetiedorquanteisrion"

    ubyte number

    uword[3] seed

    sub init(ubyte galaxy8num) {
        number = 1
        planet8.number = 255
        seed = [base0, base1, base2]
        repeat galaxy8num-1 {
            nextgalaxy8()
        }
    }

    sub nextgalaxy8() {
        seed = [twist(seed[0]), twist(seed[1]), twist(seed[2])]
        number++
        if number==9
            number = 1
    }

    sub travel_to(ubyte galaxy8num, ubyte system) {
        init(galaxy8num)
        generate_next_planet8()   ; always at least planet8 0  (separate to avoid repeat ubyte overflow)
        repeat system {
            generate_next_planet8()
        }
        planet8.name = make_current_planet8_name()
        init_market8_for_planet8()
    }

    sub init_market8_for_planet8() {
        market8.init(lsb(seed[0])+msb(seed[2]))
    }

    sub search_closest_planet8(uword nameptr) -> ubyte {
        ubyte x = planet8.x
        ubyte y = planet8.y
        ubyte current_planet8_num = planet8.number

        init(number)
        ubyte found = false
        ubyte current_closest_pi
        ubyte current_distance = 127
        ubyte pi
        for pi in 0 to 255 {
            generate_next_planet8()
            planet8.name = make_current_planet8_name()
            if util8.prefix_matches(nameptr, planet8.name) {
                ubyte distance = planet8.distance(x, y)
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
            travel_to(number, current_planet8_num)

        return found
    }

    sub local_area() {
        ubyte current_planet8 = planet8.number
        ubyte px = planet8.x
        ubyte py = planet8.y
        ubyte pn = 0

        init(number)
        txt.print("\nGalaxy #")
        txt.print_ub(number)
        txt.print(" - systems in vicinity:\n")
        do {
            generate_next_planet8()
            ubyte distance = planet8.distance(px, py)
            if distance <= ship8.Max_fuel {
                if distance <= ship8.fuel
                    txt.chrout('*')
                else
                    txt.chrout('-')
                txt.chrout(' ')
                planet8.name = make_current_planet8_name()
                planet8.display(true)
                txt.print(" (")
                util8.print_10s(distance)
                txt.print(" LY)\n")
            }
            pn++
        } until pn==0

        travel_to(number, current_planet8)
    }

    ubyte pn_pair1
    ubyte pn_pair2
    ubyte pn_pair3
    ubyte pn_pair4
    ubyte longname

    sub generate_next_planet8() {
        determine_planet8_properties()
        longname = lsb(seed[0]) & 64

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

    sub make_current_planet8_name() -> str {
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

    sub determine_planet8_properties() {
        ; create the planet8's characteristics
        planet8.number++
        planet8.x = msb(seed[1])
        planet8.y = msb(seed[0])
        planet8.govtype = lsb(seed[1]) >> 3 & 7  ; bits 3,4 &5 of w1
        planet8.economy = msb(seed[0]) & 7  ; bits 8,9 &A of w0
        if planet8.govtype <= 1
            planet8.economy = (planet8.economy | 2)
        planet8.techlevel = (msb(seed[1]) & 3) + (planet8.economy ^ 7)
        planet8.techlevel += planet8.govtype >> 1
        if planet8.govtype & 1
            planet8.techlevel++
        planet8.population = 4 * planet8.techlevel + planet8.economy
        planet8.population += planet8.govtype + 1
        planet8.productivity = ((planet8.economy ^ 7) + 3) * (planet8.govtype + 4)
        planet8.productivity *= planet8.population * 8
        ubyte seed2_msb = msb(seed[2])
        planet8.radius = mkword((seed2_msb & 15) + 11, planet8.x)
        planet8.species_is_alien = lsb(seed[2]) & 128       ; bit 7 of w2_lo
        if planet8.species_is_alien {
            planet8.species_size = (seed2_msb >> 2) & 7      ; bits 2-4 of w2_hi
            planet8.species_color = seed2_msb >> 5           ; bits 5-7 of w2_hi
            planet8.species_look = (seed2_msb ^ msb(seed[1])) & 7   ;bits 0-2 of (w0_hi EOR w1_hi)
            planet8.species_kind = (planet8.species_look + (seed2_msb & 3)) & 7      ;Add bits 0-1 of w2_hi to A from previous step, and take bits 0-2 of the result
        }

        planet8.goatsoup_seed = [lsb(seed[1]), msb(seed[1]), lsb(seed[2]), seed2_msb]
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
        rol(xh)
        rol(xl)
        return mkword(xh, xl)
    }

    sub debug_seed() {
        txt.print("\ngalaxy8 #")
        txt.print_ub(number)
        txt.print("\ngalaxy8 seed0=")
        txt.print_uwhex(galaxy8.seed[0], true)
        txt.print("\ngalaxy8 seed1=")
        txt.print_uwhex(galaxy8.seed[1], true)
        txt.print("\ngalaxy8 seed2=")
        txt.print_uwhex(galaxy8.seed[2], true)
        txt.chrout('\n')
    }
}

planet8 {
    %option force_output

    str[] species_sizes = ["Large", "Fierce", "Small"]
    str[] species_colors = ["Green", "Red", "Yellow", "Blue", "Black", "Harmless"]
    str[] species_looks = ["Slimy", "Bug-Eyed", "Horned", "Bony", "Fat", "Furry"]
    str[] species_kinds = ["Rodents", "Frogs", "Lizards", "Lobsters", "Birds", "Humanoids", "Felines", "Insects"]
    str[] govnames = ["Anarchy", "Feudal", "Multi-gov", "Dictatorship8", "Communist", "Confederacy", "Democracy", "Corporate State"]
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
    str[] words8F = ["\xB0", "The planet8 \xB0", "The world \xB0", "This planet8", "This world"]
    str[] words90 = ["n unremarkable", " boring", " dull", " tedious", " revolting"]
    str[] words91 = ["planet8", "world", "place", "little planet8", "dump"]
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

    uword[] wordlists = [
        words81, words82, words83, words84, words85, words86, words87, words88,
        words89, words8A, words8B, words8C, words8D, words8E, words8F, words90,
        words91, words92, words93, words94, words95, words96, words97, words98,
        words99, words9A, words9B, words9C, words9D, words9E, words9F, wordsA0,
        wordsA1, wordsA2, wordsA3, wordsA4]

    str pairs0 = "abouseitiletstonlonuthnoallexegezacebisousesarmaindirea.eratenbe"

    ubyte[4] goatsoup_rnd = [0, 0, 0, 0]
    ubyte[4] goatsoup_seed = [0, 0, 0, 0]

    str name = "        "       ; 8 max
    ubyte number                ; starts at 0 in new galaxy8, then increases by 1 for each generated planet8
    ubyte x
    ubyte y
    ubyte economy
    ubyte govtype
    ubyte techlevel
    ubyte population
    uword productivity
    uword radius
    ubyte species_is_alien      ; otherwise "Human Colonials"
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
        str name = "        "       ; 8 chars max
        ubyte nx = 0
        for ii in 0 to goatsoup_rnd_number() & 3 {
            ubyte x = goatsoup_rnd_number() & $3e
            if pairs0[x] != '.' {
                name[nx] = pairs0[x]
                nx++
            }
            x++
            if pairs0[x] != '.' {
                name[nx] = pairs0[x]
                nx++
            }
        }
        name[nx] = 0
        name[0] |= 32       ; uppercase first letter
        return name
    }

    sub goatsoup_rnd_number() -> ubyte {
        ubyte x = goatsoup_rnd[0] * 2
        uword a = x as uword + goatsoup_rnd[2]
        if goatsoup_rnd[0] > 127
            a ++
        goatsoup_rnd[0] = lsb(a)
        goatsoup_rnd[2] = x
        x = goatsoup_rnd[1]
        ubyte ac = x + goatsoup_rnd[3] + msb(a)
        goatsoup_rnd[1] = ac
        goatsoup_rnd[3] = x
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
        ubyte d = sqrt16(ax*ax + ay*ay)
        if d>63
            return 255
        return d*4
    }

    sub soup() -> str {
        str planet8_result = " " * 160
        uword[6] source_stack
        ubyte stack_ptr = 0
        str start_source = "\x8F is \x97."
        uword source_ptr = &start_source
        uword result_ptr = &planet8_result

        reset_rnd()
        recursive_soup()
        return planet8_result

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
                        ubyte wordNr = (rnr >= $33) + (rnr >= $66) + (rnr >= $99) + (rnr >= $CC)
                        source_stack[stack_ptr] = source_ptr
                        stack_ptr++
                        source_ptr = getword(c, wordNr)
                        recursive_soup()    ; RECURSIVE CALL - ignore the warning message from the compiler; we don't use local variables or parameters so we're safe in this case
                        stack_ptr--
                        source_ptr = source_stack[stack_ptr]
                    } else {
                        if c == $b0 {
                            @(result_ptr) = name[0] | 32
                            result_ptr++
                            concat_string(&name + 1)
                        }
                        else if c == $b1 {
                            @(result_ptr) = name[0] | 32
                            result_ptr++
                            ubyte ni
                            for ni in 1 to len(name) {
                                ubyte cc = name[ni]
                                if cc=='e' or cc=='o' or cc==0
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

    sub display(ubyte compressed) {
        if compressed {
            print_name_uppercase()
            txt.print(" TL:")
            txt.print_ub(techlevel+1)
            txt.chrout(' ')
            txt.print(econnames[economy])
            txt.chrout(' ')
            txt.print(govnames[govtype])
        } else {
            txt.print("\n\nSystem: ")
            print_name_uppercase()
            txt.print("\nPosition: ")
            txt.print_ub(x)
            txt.chrout('\'')
            txt.print_ub(y)
            txt.chrout(' ')
            txt.chrout('#')
            txt.print_ub(number)
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
                    txt.chrout(' ')
                }
                if species_color < len(species_colors) {
                    txt.print(species_colors[species_color])
                    txt.chrout(' ')
                }
                if species_look < len(species_looks) {
                    txt.print(species_looks[species_look])
                    txt.chrout(' ')
                }
                if species_kind < len(species_kinds) {
                    txt.print(species_kinds[species_kind])
                }
            } else {
                txt.print("Human Colonials")
            }
            txt.chrout('\n')
            txt.print(soup())
            txt.chrout('\n')
        }
    }

    sub print_name_uppercase() {
        ubyte c
        for c in name
            txt.chrout(c | 32)
    }

    asmsub getword(ubyte list @A, ubyte wordidx @Y) -> uword @AY {
        %asm {{
            sty  P8ZP_SCRATCH_REG
            sec
            sbc  #$81
            asl  a
            tay
            lda  wordlists,y
            sta  P8ZP_SCRATCH_W1
            lda  wordlists+1,y
            sta  P8ZP_SCRATCH_W1+1
            lda  P8ZP_SCRATCH_REG
            asl  a
            tay
            lda  (P8ZP_SCRATCH_W1),y
            pha
            iny
            lda  (P8ZP_SCRATCH_W1),y
            tay
            pla
            rts
        }}
    }
}

util8 {
    sub prefix_matches(uword prefixptr, uword stringptr) -> ubyte {
        repeat {
            ubyte pc = @(prefixptr)
            ubyte sc = @(stringptr)
            if pc == 0
                return true
            ; to lowercase for case insensitive compare:
            pc &= 127
            sc &= 127
            if pc != sc
                return false
            prefixptr++
            stringptr++
        }
        return false
    }

    sub print_right(ubyte width, uword string) {
        repeat width - strlen(string) {
            txt.chrout(' ')
        }
        txt.print(string)
    }

    asmsub print_10s(uword value @AY) clobbers(A, X, Y) {
        %asm {{
            jsr  conv.uword2decimal
            lda  conv.uword2decimal.decTenThousands
            ldy  #0     ; have we started printing?
            cmp  #'0'
            beq  +
            jsr  c64.CHROUT
            iny
+           lda  conv.uword2decimal.decThousands
		    cmp  #'0'
            bne  +
            cpy  #0
            beq  ++
+           jsr  c64.CHROUT
            iny
+           lda  conv.uword2decimal.decHundreds
		    cmp  #'0'
		    bne  +
		    cpy  #0
            beq  ++
+           jsr  c64.CHROUT
            iny
+           lda  conv.uword2decimal.decTens
            jsr  c64.CHROUT
            lda  #'.'
            jsr  c64.CHROUT
            lda  conv.uword2decimal.decOnes
            jsr  c64.CHROUT
            rts
        }}
    }

}
