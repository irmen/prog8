# old deprecated code, in the process of moving this to the new emit/... modules

class CodeGenerator:
    BREAKPOINT_COMMENT_SIGNATURE = "~~~BREAKPOINT~~~"
    BREAKPOINT_COMMENT_DETECTOR = r".(?P<address>\w+)\s+ea\s+nop\s+;\s+{:s}.*".format(BREAKPOINT_COMMENT_SIGNATURE)

    def generate_call(self, stmt: CallStmt) -> None:
        self.p("\t\t\t\t\t; " + stmt.lineref)
        if stmt.condition:
            assert stmt.is_goto
            if stmt.condition.lvalue:
                if stmt.condition.comparison_op:
                    self._generate_goto_conditional_comparison(stmt)
                else:
                    self._generate_goto_conditional_truthvalue(stmt)
            else:
                self._generate_goto_conditional_if(stmt)
        else:
            # unconditional goto or subroutine call.
            def branch_emitter(targetstr: str, is_goto: bool, target_indirect: bool) -> None:
                if is_goto:
                    if target_indirect:
                        self.p("\t\tjmp  ({:s})".format(targetstr))
                    else:
                        self.p("\t\tjmp  {:s}".format(targetstr))
                else:
                    assert not target_indirect
                    self.p("\t\tjsr  " + targetstr)
            self._generate_call_or_goto(stmt, branch_emitter)

    def _generate_goto_conditional_if(self, stmt):
        # a goto with just an if-condition, no condition expression
        def branch_emitter(targetstr: str, is_goto: bool, target_indirect: bool) -> None:
            assert is_goto and not stmt.condition.comparison_op
            ifs = stmt.condition.ifstatus
            if target_indirect:
                if ifs == "true":
                    self.p("\t\tbeq  +")
                    self.p("\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                elif ifs in ("not", "zero"):
                    self.p("\t\tbne  +")
                    self.p("\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                elif ifs in ("cc", "cs", "vc", "vs", "eq", "ne"):
                    if ifs == "cc":
                        self.p("\t\tbcs  +")
                    elif ifs == "cs":
                        self.p("\t\tbcc  +")
                    elif ifs == "vc":
                        self.p("\t\tbvs  +")
                    elif ifs == "vs":
                        self.p("\t\tbvc  +")
                    elif ifs == "eq":
                        self.p("\t\tbne  +")
                    elif ifs == "ne":
                        self.p("\t\tbeq  +")
                    self.p("\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                elif ifs == "lt":
                    self.p("\t\tbcs  +")
                    self.p("\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                elif ifs == "gt":
                    self.p("\t\tbcc  +")
                    self.p("\t\tbeq  +")
                    self.p("\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                elif ifs == "ge":
                    self.p("\t\tbcc  +")
                    self.p("\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                elif ifs == "le":
                    self.p("\t\tbeq  +")
                    self.p("\t\tbcs  ++")
                    self.p("+\t\tjmp  ({:s})".format(targetstr))
                    self.p("+")
                else:
                    raise CodeError("invalid if status " + ifs)
            else:
                if ifs == "true":
                    self.p("\t\tbne  " + targetstr)
                elif ifs in ("not", "zero"):
                    self.p("\t\tbeq  " + targetstr)
                elif ifs in ("cc", "cs", "vc", "vs", "eq", "ne"):
                    self.p("\t\tb{:s}  {:s}".format(ifs, targetstr))
                elif ifs == "pos":
                    self.p("\t\tbpl  " + targetstr)
                elif ifs == "neg":
                    self.p("\t\tbmi  " + targetstr)
                elif ifs == "lt":
                    self.p("\t\tbcc  " + targetstr)
                elif ifs == "gt":
                    self.p("\t\tbeq  +")
                    self.p("\t\tbcs  " + targetstr)
                    self.p("+")
                elif ifs == "ge":
                    self.p("\t\tbcs  " + targetstr)
                elif ifs == "le":
                    self.p("\t\tbcc  " + targetstr)
                    self.p("\t\tbeq  " + targetstr)
                else:
                    raise CodeError("invalid if status " + ifs)
        self._generate_call_or_goto(stmt, branch_emitter)

    def _generate_goto_conditional_truthvalue(self, stmt: CallStmt) -> None:
        # the condition is just the 'truth value' of the single value,
        # this is translated into assembly by comparing the argument to zero.
        def branch_emitter_mmap(targetstr: str, is_goto: bool, target_indirect: bool) -> None:
            assert is_goto and not stmt.condition.comparison_op
            assert stmt.condition.lvalue and not stmt.condition.rvalue
            assert not target_indirect
            assert stmt.condition.ifstatus in ("true", "not", "zero")
            branch, inverse_branch = ("bne", "beq") if stmt.condition.ifstatus == "true" else ("beq", "bne")
            cv = stmt.condition.lvalue
            assert isinstance(cv, MemMappedValue)
            cv_str = cv.name or Parser.to_hex(cv.address)
            if cv.datatype == DataType.BYTE:
                self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                self.p("\t\tlda  " + cv_str)
                self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                self.p("\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
            elif cv.datatype == DataType.WORD:
                self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                self.p("\t\tlda  " + cv_str)
                if stmt.condition.ifstatus == "true":
                    self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                    self.p("\t\tlda  {:s}+1".format(cv_str))
                    self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                    self.p("\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
                else:
                    self.p("\t\t{:s}  +".format(inverse_branch, targetstr))
                    self.p("\t\tlda  {:s}+1".format(cv_str))
                    self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                    self.p("+\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
            else:
                raise CodeError("conditions cannot yet use other types than byte or word",  # @todo comparisons of other types
                                cv.datatype, str(cv), stmt.sourceref)

        def branch_emitter_reg(targetstr: str, is_goto: bool, target_indirect: bool) -> None:
            assert is_goto and not stmt.condition.comparison_op
            assert stmt.condition.lvalue and not stmt.condition.rvalue
            assert not target_indirect
            assert stmt.condition.ifstatus in ("true", "not", "zero")
            branch, inverse_branch = ("bne", "beq") if stmt.condition.ifstatus == "true" else ("beq", "bne")
            line_after_branch = ""
            cv = stmt.condition.lvalue
            assert isinstance(cv, RegisterValue)
            if cv.register == 'A':
                self.p("\t\tcmp  #0")
            elif cv.register == 'X':
                self.p("\t\tcpx  #0")
            elif cv.register == 'Y':
                self.p("\t\tcpy  #0")
            else:
                if cv.register == 'AX':
                    line_after_branch = "+"
                    self.p("\t\tcmp  #0")
                    self.p("\t\t{:s}  {:s}".format(inverse_branch, line_after_branch))
                    self.p("\t\tcpx  #0")
                elif cv.register == 'AY':
                    line_after_branch = "+"
                    self.p("\t\tcmp  #0")
                    self.p("\t\t{:s}  {:s}".format(inverse_branch, line_after_branch))
                    self.p("\t\tcpy  #0")
                elif cv.register == 'XY':
                    line_after_branch = "+"
                    self.p("\t\tcpx  #0")
                    self.p("\t\t{:s}  {:s}".format(inverse_branch, line_after_branch))
                    self.p("\t\tcpy  #0")
                else:
                    raise CodeError("invalid register", cv.register)
            self.p("\t\t{:s}  {:s}".format(branch, targetstr))
            if line_after_branch:
                self.p(line_after_branch)

        def branch_emitter_indirect_cond(targetstr: str, is_goto: bool, target_indirect: bool) -> None:
            assert is_goto and not stmt.condition.comparison_op
            assert stmt.condition.lvalue and not stmt.condition.rvalue
            assert stmt.condition.ifstatus in ("true", "not", "zero")
            assert not target_indirect
            cv = stmt.condition.lvalue.value   # type: ignore
            if isinstance(cv, RegisterValue):
                branch = "bne" if stmt.condition.ifstatus == "true" else "beq"
                self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                if cv.register == 'Y':
                    self.p("\t\tlda  ($00),y")
                elif cv.register == 'X':
                    self.p("\t\tstx  *+2\t; self-modify")
                    self.p("\t\tlda  $ff")
                elif cv.register == 'A':
                    self.p("\t\tsta  *+2\t; self-modify")
                    self.p("\t\tlda  $ff")
                else:
                    self.p("\t\tst{:s}  (+)+1\t; self-modify".format(cv.register[0].lower()))
                    self.p("\t\tst{:s}  (+)+2\t; self-modify".format(cv.register[1].lower()))
                    self.p("+\t\tlda  $ffff")
                self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                self.p("\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
            elif isinstance(cv, MemMappedValue):
                raise CodeError("memmapped indirect should not occur, use the variable without indirection")
            elif isinstance(cv, IntegerValue) and cv.constant:
                branch, inverse_branch = ("bne", "beq") if stmt.condition.ifstatus == "true" else ("beq", "bne")
                cv_str = cv.name or Parser.to_hex(cv.value)
                if cv.datatype == DataType.BYTE:
                    self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                    self.p("\t\tlda  " + cv_str)
                    self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                    self.p("\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
                elif cv.datatype == DataType.WORD:
                    self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                    self.p("\t\tlda  " + cv_str)
                    if stmt.condition.ifstatus == "true":
                        self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                        self.p("\t\tlda  {:s}+1".format(cv_str))
                        self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                        self.p("\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
                    else:
                        self.p("\t\t{:s}  +".format(inverse_branch, targetstr))
                        self.p("\t\tlda  {:s}+1".format(cv_str))
                        self.p("\t\t{:s}  {:s}".format(branch, targetstr))
                        self.p("+\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # restore A
                else:
                    raise CodeError("conditions cannot yet use other types than byte or word",  # @todo comparisons of other types
                                    cv.datatype, str(cv), stmt.sourceref)
            else:
                raise CodeError("weird indirect type", str(cv))

        cv = stmt.condition.lvalue
        if isinstance(cv, RegisterValue):
            self._generate_call_or_goto(stmt, branch_emitter_reg)
        elif isinstance(cv, MemMappedValue):
            self._generate_call_or_goto(stmt, branch_emitter_mmap)
        elif isinstance(cv, IndirectValue):
            if isinstance(cv.value, RegisterValue):
                self._generate_call_or_goto(stmt, branch_emitter_indirect_cond)
            elif isinstance(cv.value, MemMappedValue):
                self._generate_call_or_goto(stmt, branch_emitter_indirect_cond)
            elif isinstance(cv.value, IntegerValue) and cv.value.constant:
                self._generate_call_or_goto(stmt, branch_emitter_indirect_cond)
            else:
                raise CodeError("weird indirect type", str(cv))
        else:
            raise CodeError("need register, memmapped or indirect value", str(cv))

    def _generate_goto_conditional_comparison(self, stmt: CallStmt) -> None:
        # the condition is lvalue operator rvalue
        raise NotImplementedError("no comparisons yet")  # XXX comparisons
        assert stmt.condition.ifstatus in ("true", "not", "zero")
        assert stmt.condition.lvalue != stmt.condition.rvalue  # so we know we actually have to compare different things
        lv, compare_operator, rv = stmt.condition.lvalue, stmt.condition.comparison_op, stmt.condition.rvalue
        if lv.constant and not rv.constant:
            # if lv is a constant, swap the whole thing around so the constant is on the right
            lv, compare_operator, rv = stmt.condition.swap()
        if isinstance(rv, RegisterValue):
            # if rv is a register, make sure it comes first instead
            lv, compare_operator, rv = stmt.condition.swap()
        if lv.datatype != DataType.BYTE or rv.datatype != DataType.BYTE:
            raise CodeError("can only generate comparison code for byte values for now")  # @todo compare non-bytes
        if isinstance(lv, RegisterValue):
            if isinstance(rv, RegisterValue):
                self.p("\t\tst{:s}  {:s}".format(rv.register.lower(), Parser.to_hex(Zeropage.SCRATCH_B1)))
                if lv.register == "A":
                    self.p("\t\tcmp  " + Parser.to_hex(Zeropage.SCRATCH_B1))
                elif lv.register == "X":
                    self.p("\t\tcpx  " + Parser.to_hex(Zeropage.SCRATCH_B1))
                elif lv.register == "Y":
                    self.p("\t\tcpy  " + Parser.to_hex(Zeropage.SCRATCH_B1))
                else:
                    raise CodeError("wrong lvalue register")
            elif isinstance(rv, IntegerValue):
                rvstr = rv.name or Parser.to_hex(rv.value)
                if lv.register == "A":
                    self.p("\t\tcmp  #" + rvstr)
                elif lv.register == "X":
                    self.p("\t\tcpx  #" + rvstr)
                elif lv.register == "Y":
                    self.p("\t\tcpy  #" + rvstr)
                else:
                    raise CodeError("wrong lvalue register")
            elif isinstance(rv, MemMappedValue):
                rvstr = rv.name or Parser.to_hex(rv.address)
                if lv.register == "A":
                    self.p("\t\tcmp  " + rvstr)
                elif lv.register == "X":
                    self.p("\t\tcpx  #" + rvstr)
                elif lv.register == "Y":
                    self.p("\t\tcpy  #" + rvstr)
                else:
                    raise CodeError("wrong lvalue register")
            else:
                raise CodeError("invalid rvalue type in comparison", rv)
        elif isinstance(lv, MemMappedValue):
            assert not isinstance(rv, RegisterValue), "registers as rvalue should have been swapped with lvalue"
            if isinstance(rv, IntegerValue):
                self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                self.p("\t\tlda  " + (lv.name or Parser.to_hex(lv.address)))
                self.p("\t\tcmp  #" + (rv.name or Parser.to_hex(rv.value)))
                line_after_goto = "\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1)  # restore A
            elif isinstance(rv, MemMappedValue):
                rvstr = rv.name or Parser.to_hex(rv.address)
                self.p("\t\tsta  " + Parser.to_hex(Zeropage.SCRATCH_B1))  # need to save A, because the goto may not be taken
                self.p("\t\tlda  " + (lv.name or Parser.to_hex(lv.address)))
                self.p("\t\tcmp  " + rvstr)
                line_after_goto = "\t\tlda  " + Parser.to_hex(Zeropage.SCRATCH_B1)  # restore A
            else:
                raise CodeError("invalid rvalue type in comparison", rv)
        else:
            raise CodeError("invalid lvalue type in comparison", lv)

    def _generate_call_or_goto(self, stmt: CallStmt, branch_emitter: Callable[[str, bool, bool], None]) -> None:
        def generate_param_assignments() -> None:
            for assign_stmt in stmt.desugared_call_arguments:
                self.generate_assignment(assign_stmt)

        def generate_result_assignments() -> None:
            for assign_stmt in stmt.desugared_output_assignments:
                self.generate_assignment(assign_stmt)

        def params_load_a() -> bool:
            for assign_stmt in stmt.desugared_call_arguments:
                for lv in assign_stmt.leftvalues:
                    if isinstance(lv, RegisterValue):
                        if lv.register == 'A':
                            return True
            return False

        def unclobber_result_registers(registers: Set[str], output_assignments: List[AssignmentStmt]) -> Set[str]:
            result = registers.copy()
            for a in output_assignments:
                for lv in a.leftvalues:
                    if isinstance(lv, RegisterValue):
                        if len(lv.register) == 1:
                            result.discard(lv.register)
                        else:
                            for r in lv.register:
                                result.discard(r)
            return result

        if stmt.target.name:
            symblock, targetdef = self.cur_block.lookup(stmt.target.name)
        else:
            symblock = None
            targetdef = None
        if isinstance(targetdef, SubroutineDef):
            if isinstance(stmt.target, MemMappedValue):
                targetstr = stmt.target.name or Parser.to_hex(stmt.address)
            else:
                raise CodeError("call sub target must be mmapped")
            if stmt.is_goto:
                generate_param_assignments()
                branch_emitter(targetstr, True, False)
                # no result assignments because it's a goto
                return
            clobbered = set()  # type: Set[str]
            if targetdef.clobbered_registers:
                if stmt.preserve_regs is not None:
                    clobbered = targetdef.clobbered_registers & stmt.preserve_regs
                    clobbered = unclobber_result_registers(clobbered, stmt.desugared_output_assignments)
            with self.preserving_registers(clobbered, loads_a_within=params_load_a(), always_preserve=stmt.preserve_regs is not None):
                generate_param_assignments()
                branch_emitter(targetstr, False, False)
                generate_result_assignments()
            return
        if isinstance(stmt.target, IndirectValue):
            if stmt.target.name:
                targetstr = stmt.target.name
            elif stmt.address is not None:
                targetstr = Parser.to_hex(stmt.address)
            elif stmt.target.value.name:
                targetstr = stmt.target.value.name
            elif isinstance(stmt.target.value, RegisterValue):
                targetstr = stmt.target.value.register
            elif isinstance(stmt.target.value, IntegerValue):
                targetstr = stmt.target.value.name or Parser.to_hex(stmt.target.value.value)
            else:
                raise CodeError("missing name", stmt.target.value)
            if stmt.is_goto:
                # no need to preserve registers for a goto
                generate_param_assignments()
                if targetstr in REGISTER_WORDS:
                    self.p("\t\tst{:s}  {:s}".format(targetstr[0].lower(), Parser.to_hex(Zeropage.SCRATCH_B1)))
                    self.p("\t\tst{:s}  {:s}".format(targetstr[1].lower(), Parser.to_hex(Zeropage.SCRATCH_B2)))
                    branch_emitter(Parser.to_hex(Zeropage.SCRATCH_B1), True, True)
                else:
                    branch_emitter(targetstr, True, True)
                # no result assignments because it's a goto
            else:
                # indirect call to subroutine
                preserve_regs = unclobber_result_registers(stmt.preserve_regs or set(), stmt.desugared_output_assignments)
                with self.preserving_registers(preserve_regs, loads_a_within=params_load_a(),
                                               always_preserve=stmt.preserve_regs is not None):
                    generate_param_assignments()
                    if targetstr in REGISTER_WORDS:
                        print("warning: {}: indirect register pair call is quite inefficient, use a jump table in memory instead?"
                              .format(stmt.sourceref))
                        if stmt.preserve_regs is not None:
                            # cannot use zp scratch because it may be used by the register backup. This is very inefficient code!
                            self.p("\t\tjsr  il65_lib.jsr_indirect_nozpuse_"+targetstr)

                        else:
                            self.p("\t\tjsr  il65_lib.jsr_indirect_"+targetstr)
                    else:
                        self.p("\t\tjsr  +")
                        self.p("\t\tjmp  ++")
                        self.p("+\t\tjmp  ({:s})".format(targetstr))
                        self.p("+")
                    generate_result_assignments()
        else:
            # call to a label or immediate address
            if stmt.target.name:
                targetstr = stmt.target.name
            elif stmt.address is not None:
                targetstr = Parser.to_hex(stmt.address)
            elif isinstance(stmt.target, IntegerValue):
                targetstr = stmt.target.name or Parser.to_hex(stmt.target.value)
            else:
                raise CodeError("missing name", stmt.target)
            if stmt.is_goto:
                # no need to preserve registers for a goto
                generate_param_assignments()
                branch_emitter(targetstr, True, False)
                # no result assignments because it's a goto
            else:
                preserve_regs = unclobber_result_registers(stmt.preserve_regs or set(), stmt.desugared_output_assignments)
                with self.preserving_registers(preserve_regs, loads_a_within=params_load_a(),
                                               always_preserve=stmt.preserve_regs is not None):
                    generate_param_assignments()
                    branch_emitter(targetstr, False, False)
                    generate_result_assignments()



    def _generate_aug_reg_mem(self, lvalue: RegisterValue, operator: str, rvalue: MemMappedValue) -> None:
        r_str = rvalue.name or Parser.to_hex(rvalue.address)
        if operator == "+=":
            if lvalue.register == "A":
                self.p("\t\tclc")
                self.p("\t\tadc  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\tclc")
                    self.p("\t\tadc  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\tclc")
                    self.p("\t\tadc  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo +=.word
        elif operator == "-=":
            if lvalue.register == "A":
                self.p("\t\tsec")
                self.p("\t\tsbc  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\tsec")
                    self.p("\t\tsbc  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\tsec")
                    self.p("\t\tsbc  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo -=.word
        elif operator == "&=":
            if lvalue.register == "A":
                self.p("\t\tand  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\tand  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\tand  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo &=.word
        elif operator == "|=":
            if lvalue.register == "A":
                self.p("\t\tora  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\tora  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\tora  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo |=.word
        elif operator == "^=":
            if lvalue.register == "A":
                self.p("\t\teor  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\teor  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\teor  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo ^=.word
        elif operator == ">>=":
            if rvalue.datatype != DataType.BYTE:
                raise CodeError("can only shift by a byte value", str(rvalue))
            r_str = rvalue.name or Parser.to_hex(rvalue.address)
            if lvalue.register == "A":
                self.p("\t\tlsr  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\tlsr  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\tlsr  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported lvalue register for shift", str(lvalue))  # @todo >>=.word
        elif operator == "<<=":
            if rvalue.datatype != DataType.BYTE:
                raise CodeError("can only shift by a byte value", str(rvalue))
            r_str = rvalue.name or Parser.to_hex(rvalue.address)
            if lvalue.register == "A":
                self.p("\t\tasl  " + r_str)
            elif lvalue.register == "X":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttxa")
                    self.p("\t\tasl  " + r_str)
                    self.p("\t\ttax")
            elif lvalue.register == "Y":
                with self.preserving_registers({'A'}):
                    self.p("\t\ttya")
                    self.p("\t\tasl  " + r_str)
                    self.p("\t\ttay")
            else:
                raise CodeError("unsupported lvalue register for shift", str(lvalue))  # @todo >>=.word



    def generate_assignment(self, stmt: AssignmentStmt) -> None:
        def unwrap_indirect(iv: IndirectValue) -> MemMappedValue:
            if isinstance(iv.value, MemMappedValue):
                return iv.value
            elif iv.value.constant and isinstance(iv.value, IntegerValue):
                return MemMappedValue(iv.value.value, iv.datatype, 1, stmt.sourceref, iv.name)
            else:
                raise CodeError("cannot yet generate code for assignment: non-constant and non-memmapped indirect")  # XXX

        rvalue = stmt.right
        if isinstance(rvalue, IndirectValue):
            rvalue = unwrap_indirect(rvalue)
        self.p("\t\t\t\t\t; " + stmt.lineref)
        if isinstance(rvalue, IntegerValue):
            for lv in stmt.leftvalues:
                if isinstance(lv, RegisterValue):
                    self.generate_assign_integer_to_reg(lv.register, rvalue)
                elif isinstance(lv, MemMappedValue):
                    self.generate_assign_integer_to_mem(lv, rvalue)
                elif isinstance(lv, IndirectValue):
                    lv = unwrap_indirect(lv)
                    self.generate_assign_integer_to_mem(lv, rvalue)
                else:
                    raise CodeError("invalid assignment target (1)", str(stmt))
        elif isinstance(rvalue, RegisterValue):
            for lv in stmt.leftvalues:
                if isinstance(lv, RegisterValue):
                    self.generate_assign_reg_to_reg(lv, rvalue.register)
                elif isinstance(lv, MemMappedValue):
                    self.generate_assign_reg_to_memory(lv, rvalue.register)
                elif isinstance(lv, IndirectValue):
                    lv = unwrap_indirect(lv)
                    self.generate_assign_reg_to_memory(lv, rvalue.register)
                else:
                    raise CodeError("invalid assignment target (2)", str(stmt))
        elif isinstance(rvalue, StringValue):
            r_str = self.output_string(rvalue.value, True)
            for lv in stmt.leftvalues:
                if isinstance(lv, RegisterValue):
                    if len(rvalue.value) == 1:
                        self.generate_assign_char_to_reg(lv, r_str)
                    else:
                        self.generate_assign_string_to_reg(lv, rvalue)
                elif isinstance(lv, MemMappedValue):
                    if len(rvalue.value) == 1:
                        self.generate_assign_char_to_memory(lv, r_str)
                    else:
                        self.generate_assign_string_to_memory(lv, rvalue)
                elif isinstance(lv, IndirectValue):
                    lv = unwrap_indirect(lv)
                    if len(rvalue.value) == 1:
                        self.generate_assign_char_to_memory(lv, r_str)
                    else:
                        self.generate_assign_string_to_memory(lv, rvalue)
                else:
                    raise CodeError("invalid assignment target (2)", str(stmt))
        elif isinstance(rvalue, MemMappedValue):
            for lv in stmt.leftvalues:
                if isinstance(lv, RegisterValue):
                    self.generate_assign_mem_to_reg(lv.register, rvalue)
                elif isinstance(lv, MemMappedValue):
                    self.generate_assign_mem_to_mem(lv, rvalue)
                elif isinstance(lv, IndirectValue):
                    lv = unwrap_indirect(lv)
                    self.generate_assign_mem_to_mem(lv, rvalue)
                else:
                    raise CodeError("invalid assignment target (4)", str(stmt))
        elif isinstance(rvalue, FloatValue):
            for lv in stmt.leftvalues:
                if isinstance(lv, MemMappedValue) and lv.datatype == DataType.FLOAT:
                    self.generate_assign_float_to_mem(lv, rvalue)
                elif isinstance(lv, IndirectValue):
                    lv = unwrap_indirect(lv)
                    assert lv.datatype == DataType.FLOAT
                    self.generate_assign_float_to_mem(lv, rvalue)
                else:
                    raise CodeError("cannot assign float to ", str(lv))
        else:
            raise CodeError("invalid assignment value type", str(stmt))

    def generate_assign_float_to_mem(self, mmv: MemMappedValue,
                                     rvalue: Union[FloatValue, IntegerValue]) -> None:
        floatvalue = float(rvalue.value)
        mflpt = self.to_mflpt5(floatvalue)
        target = mmv.name or Parser.to_hex(mmv.address)
        with self.preserving_registers({'A'}):
            self.p("\t\t\t\t\t; {:s} = {}".format(target, rvalue.name or floatvalue))
            a_reg_value = None
            for i, byte in enumerate(mflpt):
                if byte != a_reg_value:
                    self.p("\t\tlda  #${:02x}".format(byte))
                    a_reg_value = byte
                self.p("\t\tsta  {:s}+{:d}".format(target, i))

    def generate_assign_reg_to_memory(self, lv: MemMappedValue, r_register: str) -> None:
        # Memory = Register
        lv_string = lv.name or Parser.to_hex(lv.address)
        if lv.datatype == DataType.BYTE:
            if len(r_register) > 1:
                raise CodeError("cannot assign register pair to single byte memory")
            self.p("\t\tst{:s}  {}".format(r_register.lower(), lv_string))
        elif lv.datatype == DataType.WORD:
            if len(r_register) == 1:
                self.p("\t\tst{:s}  {}".format(r_register.lower(), lv_string))  # lsb
                with self.preserving_registers({'A'}, loads_a_within=True):
                    self.p("\t\tlda  #0")
                    self.p("\t\tsta  {:s}+1".format(lv_string))  # msb
            else:
                self.p("\t\tst{:s}  {}".format(r_register[0].lower(), lv_string))
                self.p("\t\tst{:s}  {}+1".format(r_register[1].lower(), lv_string))
        elif lv.datatype == DataType.FLOAT:
            # assigning a register to a float requires c64 ROM routines
            if r_register in REGISTER_WORDS:
                def do_rom_calls():
                    self.p("\t\tjsr  c64flt.GIVUAYF")  # uword AY -> fac1
                    self.p("\t\tldx  #<" + lv_string)
                    self.p("\t\tldy  #>" + lv_string)
                    self.p("\t\tjsr  c64.FTOMEMXY")  # fac1 -> memory XY
                if r_register == "AY":
                    with self.preserving_registers({'A', 'X', 'Y'}):
                        do_rom_calls()
                elif r_register == "AX":
                    with self.preserving_registers({'A', 'X', 'Y'}):
                        self.p("\t\tpha\n\t\ttxa\n\t\ttay\n\t\tpla")    # X->Y (so we have AY now)
                        do_rom_calls()
                else:  # XY
                    with self.preserving_registers({'A', 'X', 'Y'}, loads_a_within=True):
                        self.p("\t\ttxa")    # X->A (so we have AY now)
                        do_rom_calls()
            elif r_register in "AXY":

                def do_rom_calls():
                    self.p("\t\tjsr  c64.FREADUY")  # ubyte Y -> fac1
                    self.p("\t\tldx  #<" + lv_string)
                    self.p("\t\tldy  #>" + lv_string)
                    self.p("\t\tjsr  c64.FTOMEMXY")  # fac1 -> memory XY

                if r_register == "A":
                    with self.preserving_registers({'A', 'X', 'Y'}):
                        self.p("\t\ttay")
                        do_rom_calls()
                elif r_register == "X":
                    with self.preserving_registers({'A', 'X', 'Y'}, loads_a_within=True):
                        self.p("\t\ttxa")
                        self.p("\t\ttay")
                        do_rom_calls()
                elif r_register == "Y":
                    with self.preserving_registers({'A', 'X', 'Y'}):
                        do_rom_calls()
            else:
                raise CodeError("invalid register to assign to float", r_register)
        else:
            raise CodeError("invalid lvalue type", lv.datatype)

    def generate_assign_reg_to_reg(self, lv: RegisterValue, r_register: str) -> None:
        if lv.register != r_register:
            if lv.register == 'A':  # x/y -> a
                self.p("\t\tt{:s}a".format(r_register.lower()))
            elif lv.register == 'Y':
                if r_register == 'A':
                    # a -> y
                    self.p("\t\ttay")
                else:
                    # x -> y, 6502 doesn't have txy
                    self.p("\t\tstx  ${0:02x}\n\t\tldy  ${0:02x}".format(Zeropage.SCRATCH_B1))
            elif lv.register == 'X':
                if r_register == 'A':
                    # a -> x
                    self.p("\t\ttax")
                else:
                    # y -> x, 6502 doesn't have tyx
                    self.p("\t\tsty  ${0:02x}\n\t\tldx  ${0:02x}".format(Zeropage.SCRATCH_B1))
            elif lv.register in REGISTER_WORDS:
                if len(r_register) == 1:
                    # assign one register to a pair, so the hi byte is zero.
                    if lv.register == "AX" and r_register == "A":
                        self.p("\t\tldx  #0")
                    elif lv.register == "AX" and r_register == "X":
                        self.p("\t\ttxa\n\t\tldx  #0")
                    elif lv.register == "AX" and r_register == "Y":
                        self.p("\t\ttya\n\t\tldx  #0")
                    elif lv.register == "AY" and r_register == "A":
                        self.p("\t\tldy  #0")
                    elif lv.register == "AY" and r_register == "X":
                        self.p("\t\ttxa\n\t\tldy  #0")
                    elif lv.register == "AY" and r_register == "Y":
                        self.p("\t\ttya\n\t\tldy  #0")
                    elif lv.register == "XY" and r_register == "A":
                        self.p("\t\ttax\n\t\tldy  #0")
                    elif lv.register == "XY" and r_register == "X":
                        self.p("\t\tldy  #0")
                    elif lv.register == "XY" and r_register == "Y":
                        self.p("\t\ttyx\n\t\tldy  #0")
                    else:
                        raise CodeError("invalid register combination", lv.register, r_register)
                elif lv.register == "AX" and r_register == "AY":
                    # y -> x, 6502 doesn't have tyx
                    self.p("\t\tsty  ${0:02x}\n\t\tldx  ${0:02x}".format(Zeropage.SCRATCH_B1))
                elif lv.register == "AX" and r_register == "XY":
                    # x -> a, y -> x, 6502 doesn't have tyx
                    self.p("\t\ttxa")
                    self.p("\t\tsty  ${0:02x}\n\t\tldx  ${0:02x}".format(Zeropage.SCRATCH_B1))
                elif lv.register == "AY" and r_register == "AX":
                    # x -> y, 6502 doesn't have txy
                    self.p("\t\tstx  ${0:02x}\n\t\tldy  ${0:02x}".format(Zeropage.SCRATCH_B1))
                elif lv.register == "AY" and r_register == "XY":
                    # x -> a
                    self.p("\t\ttxa")
                elif lv.register == "XY" and r_register == "AX":
                    # x -> y, a -> x, 6502 doesn't have txy
                    self.p("\t\tstx  ${0:02x}\n\t\tldy  ${0:02x}".format(Zeropage.SCRATCH_B1))
                    self.p("\t\ttax")
                elif lv.register == "XY" and r_register == "AY":
                    # a -> x
                    self.p("\t\ttax")
                else:
                    raise CodeError("invalid register combination", lv.register, r_register)
            else:
                raise CodeError("invalid register " + lv.register)

    def generate_assign_integer_to_mem(self, lv: MemMappedValue, rvalue: IntegerValue) -> None:
        if lv.name:
            symblock, sym = self.cur_block.lookup(lv.name)
            if not isinstance(sym, VariableDef):
                raise CodeError("invalid lvalue type " + str(sym))
            assign_target = symblock.label + '.' + sym.name if symblock is not self.cur_block else lv.name
            lvdatatype = sym.type
        else:
            assign_target = Parser.to_hex(lv.address)
            lvdatatype = lv.datatype
        r_str = rvalue.name if rvalue.name else "${:x}".format(rvalue.value)
        if lvdatatype == DataType.BYTE:
            if rvalue.value is not None and not lv.assignable_from(rvalue) or rvalue.datatype != DataType.BYTE:
                raise OverflowError("value doesn't fit in a byte")
            with self.preserving_registers({'A'}, loads_a_within=True):
                self.p("\t\tlda  #" + r_str)
                self.p("\t\tsta  " + assign_target)
        elif lvdatatype == DataType.WORD:
            if rvalue.value is not None and not lv.assignable_from(rvalue):
                raise OverflowError("value doesn't fit in a word")
            with self.preserving_registers({'A'}, loads_a_within=True):
                self.p("\t\tlda  #<" + r_str)
                self.p("\t\tsta  " + assign_target)
                self.p("\t\tlda  #>" + r_str)
                self.p("\t\tsta  {}+1".format(assign_target))
        elif lvdatatype == DataType.FLOAT:
            if rvalue.value is not None and not DataType.FLOAT.assignable_from_value(rvalue.value):
                raise CodeError("value cannot be assigned to a float")
            self.generate_assign_float_to_mem(lv, rvalue)
        else:
            raise CodeError("invalid lvalue type " + str(lvdatatype))

    def generate_assign_mem_to_reg(self, l_register: str, rvalue: MemMappedValue) -> None:
        r_str = rvalue.name if rvalue.name else "${:x}".format(rvalue.address)
        if len(l_register) == 1:
            if rvalue.datatype != DataType.BYTE:
                raise CodeError("can only assign a byte to a register")
            self.p("\t\tld{:s}  {:s}".format(l_register.lower(), r_str))
        else:
            if rvalue.datatype == DataType.BYTE:
                self.p("\t\tld{:s}  {:s}".format(l_register[0].lower(), r_str))
                self.p("\t\tld{:s}  #0".format(l_register[1].lower()))
            elif rvalue.datatype == DataType.WORD:
                self.p("\t\tld{:s}  {:s}".format(l_register[0].lower(), r_str))
                self.p("\t\tld{:s}  {:s}+1".format(l_register[1].lower(), r_str))
            else:
                raise CodeError("can only assign a byte or word to a register pair")

    def generate_assign_mem_to_mem(self, lv: MemMappedValue, rvalue: MemMappedValue) -> None:
        r_str = rvalue.name or Parser.to_hex(rvalue.address)
        l_str = lv.name or Parser.to_hex(lv.address)
        if lv.datatype == DataType.BYTE:
            if rvalue.datatype != DataType.BYTE:
                raise CodeError("can only assign a byte to a byte", str(rvalue))
            with self.preserving_registers({'A'}, loads_a_within=True):
                self.p("\t\tlda  " + r_str)
                self.p("\t\tsta  " + l_str)
        elif lv.datatype == DataType.WORD:
            if rvalue.datatype == DataType.BYTE:
                with self.preserving_registers({'A'}, loads_a_within=True):
                    self.p("\t\tlda  " + r_str)
                    self.p("\t\tsta  " + l_str)
                    self.p("\t\tlda  #0")
                    self.p("\t\tsta  {:s}+1".format(l_str))
            elif rvalue.datatype == DataType.WORD:
                with self.preserving_registers({'A'}, loads_a_within=True):
                    self.p("\t\tlda  {:s}".format(r_str))
                    self.p("\t\tsta  {:s}".format(l_str))
                    self.p("\t\tlda  {:s}+1".format(r_str))
                    self.p("\t\tsta  {:s}+1".format(l_str))
            else:
                raise CodeError("can only assign a byte or word to a word", str(rvalue))
        elif lv.datatype == DataType.FLOAT:
            if rvalue.datatype == DataType.FLOAT:
                with self.preserving_registers({'A', 'X', 'Y'}, loads_a_within=True):
                    self.p("\t\tlda  #<" + r_str)
                    self.p("\t\tsta  c64.SCRATCH_ZPWORD1")
                    self.p("\t\tlda  #>" + r_str)
                    self.p("\t\tsta  c64.SCRATCH_ZPWORD1+1")
                    self.p("\t\tldx  #<" + l_str)
                    self.p("\t\tldy  #>" + l_str)
                    self.p("\t\tjsr  c64flt.copy_mflt")
            elif rvalue.datatype == DataType.BYTE:
                with self.preserving_registers({'A', 'X', 'Y'}):
                    self.p("\t\tldy  " + r_str)
                    self.p("\t\tjsr  c64.FREADUY")  # ubyte Y -> fac1
                    self.p("\t\tldx  #<" + l_str)
                    self.p("\t\tldy  #>" + l_str)
                    self.p("\t\tjsr  c64.FTOMEMXY")  # fac1 -> memory XY
            elif rvalue.datatype == DataType.WORD:
                with self.preserving_registers({'A', 'X', 'Y'}, loads_a_within=True):
                    self.p("\t\tlda  " + r_str)
                    self.p("\t\tldy  {:s}+1".format(r_str))
                    self.p("\t\tjsr  c64flt.GIVUAYF")  # uword AY -> fac1
                    self.p("\t\tldx  #<" + l_str)
                    self.p("\t\tldy  #>" + l_str)
                    self.p("\t\tjsr  c64.FTOMEMXY")  # fac1 -> memory XY
            else:
                raise CodeError("unsupported rvalue to memfloat", str(rvalue))
        else:
            raise CodeError("invalid lvalue memmapped datatype", str(lv))

    def generate_assign_char_to_memory(self, lv: MemMappedValue, char_str: str) -> None:
        # Memory = Character
        with self.preserving_registers({'A'}, loads_a_within=True):
            self.p("\t\tlda  #" + char_str)
            if not lv.name:
                self.p("\t\tsta  " + Parser.to_hex(lv.address))
                return
            # assign char value to a memory location by symbol name
            symblock, sym = self.cur_block.lookup(lv.name)
            if isinstance(sym, VariableDef):
                assign_target = lv.name
                if symblock is not self.cur_block:
                    assign_target = symblock.label + '.' + sym.name
                if sym.type == DataType.BYTE:
                    self.p("\t\tsta  " + assign_target)
                elif sym.type == DataType.WORD:
                    self.p("\t\tsta  " + assign_target)
                    self.p("\t\tlda  #0")
                    self.p("\t\tsta  {}+1".format(assign_target))
                else:
                    raise CodeError("invalid lvalue type " + str(sym))
            else:
                raise CodeError("invalid lvalue type " + str(sym))

    def generate_assign_integer_to_reg(self, l_register: str, rvalue: IntegerValue) -> None:
        r_str = rvalue.name if rvalue.name else "${:x}".format(rvalue.value)
        if l_register in ('A', 'X', 'Y'):
            self.p("\t\tld{:s}  #{:s}".format(l_register.lower(), r_str))
        elif l_register in REGISTER_WORDS:
            self.p("\t\tld{:s}  #<{:s}".format(l_register[0].lower(), r_str))
            self.p("\t\tld{:s}  #>{:s}".format(l_register[1].lower(), r_str))
        elif l_register == "SC":
            # set/clear S carry bit
            if rvalue.value:
                self.p("\t\tsec")
            else:
                self.p("\t\tclc")
        elif l_register == "SI":
            # interrupt disable bit
            if rvalue.value:
                self.p("\t\tsei")
            else:
                self.p("\t\tcli")
        else:
            raise CodeError("invalid register in immediate integer assignment", l_register, rvalue.value)

    def generate_assign_char_to_reg(self, lv: RegisterValue, char_str: str) -> None:
        # Register = Char (string of length 1)
        if lv.register not in ('A', 'X', 'Y'):
            raise CodeError("invalid register for char assignment", lv.register)
        self.p("\t\tld{:s}  #{:s}".format(lv.register.lower(), char_str))

    def generate_assign_string_to_reg(self, lv: RegisterValue, rvalue: StringValue) -> None:
        if lv.register not in ("AX", "AY", "XY"):
            raise CodeError("need register pair AX, AY or XY for string address assignment", lv.register)
        if rvalue.name:
            self.p("\t\tld{:s}  #<{:s}".format(lv.register[0].lower(), rvalue.name))
            self.p("\t\tld{:s}  #>{:s}".format(lv.register[1].lower(), rvalue.name))
        else:
            raise CodeError("cannot assign immediate string, it must be a string variable")

    def generate_assign_string_to_memory(self, lv: MemMappedValue, rvalue: StringValue) -> None:
        if lv.datatype != DataType.WORD:
            raise CodeError("need word memory type for string address assignment")
        if rvalue.name:
            assign_target = lv.name if lv.name else Parser.to_hex(lv.address)
            self.p("\t\tlda  #<{:s}".format(rvalue.name))
            self.p("\t\tsta  " + assign_target)
            self.p("\t\tlda  #>{:s}".format(rvalue.name))
            self.p("\t\tsta  {}+1".format(assign_target))
        else:
            raise CodeError("cannot assign immediate string, it must be a string variable")
