import re

hashcode = open("perfecthash.c", "rt").read()

entries = hashcode.split("wordlist")[1].split("{")[1].split("}")[0].strip().split(",")

max_hash_value = int(re.search(r"MAX_HASH_VALUE = (\d+)", hashcode).group(1))

if len(entries) != max_hash_value+1:
    raise ValueError("inconsistent number of entries parsed")


entries = [e.strip() for e in entries]
entries = [None if e.endswith('0') else e.strip('"') for e in entries]

for ix, entry in enumerate(entries):
    print(ix, entry or "-")
