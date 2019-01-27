c1541 -format examples,01 d64 examples.d64
for filename in *.prg; do
  c1541 examples.d64 -write $filename ${filename%.*}
done
c1541 examples.d64 -dir
