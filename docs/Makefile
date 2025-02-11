# Minimal makefile for Sphinx documentation
#

# You can set these variables from the command line.
SPHINXOPTS    =
SPHINXBUILD   = sphinx-build
SPHINXPROJ    = Prog8
SOURCEDIR     = source
BUILDDIR      = build

# Put it first so that "make" without argument is like "make help".
help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

.PHONY: help Makefile symboldumps


symboldumps:
	mkdir -p source/_static/symboldumps
	prog8c -target c64 import-all-c64.p8 -dumpsymbols > source/_static/symboldumps/skeletons-c64.txt
	prog8c -target c128 import-all-c128.p8 -dumpsymbols > source/_static/symboldumps/skeletons-c128.txt
	prog8c -target cx16 import-all-cx16.p8 -dumpsymbols > source/_static/symboldumps/skeletons-cx16.txt
	prog8c -target pet32 import-all-pet32.p8 -dumpsymbols > source/_static/symboldumps/skeletons-pet32.txt
	prog8c -target virtual import-all-virtual.p8 -dumpsymbols > source/_static/symboldumps/skeletons-virtual.txt


# Catch-all target: route all unknown targets to Sphinx using the new
# "make mode" option.  $(O) is meant as a shortcut for $(SPHINXOPTS).
%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)
