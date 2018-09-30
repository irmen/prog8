import cProfile
from il65.compile import PlyParser
from il65.optimize import optimize


def parse():
    parser = PlyParser(enable_floats=True)
    parsed_module = parser.parse_file("testsource/large.ill")
    optimize(parsed_module)


cProfile.run("parse()", filename="profile.dat")
