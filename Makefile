# super simple Makefile to lauch the main gradle targets to build and/or test the prog8 compiler

.PHONY: all test

all:
	gradle installdist installshadowdist
	@echo "compiler launch script can be found here: compiler/build/install/compiler-shadow/bin/prog8c"

test:
	gradle build
	@echo "compiler launch script can be found here: compiler/build/install/compiler-shadow/bin/prog8c"

