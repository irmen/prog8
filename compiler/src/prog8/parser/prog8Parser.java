// Generated from /home/irmen/Projects/prog8/compiler/antlr/prog8.g4 by ANTLR 4.7
package prog8.parser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.ATN;
import org.antlr.v4.runtime.atn.ATNDeserializer;
import org.antlr.v4.runtime.atn.ParserATNSimulator;
import org.antlr.v4.runtime.atn.PredictionContextCache;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.List;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class prog8Parser extends Parser {
	static { RuntimeMetaData.checkVersion("4.7", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9,
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17,
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24,
		T__24=25, T__25=26, T__26=27, T__27=28, T__28=29, T__29=30, T__30=31,
		T__31=32, T__32=33, T__33=34, T__34=35, T__35=36, T__36=37, T__37=38,
		T__38=39, T__39=40, T__40=41, T__41=42, T__42=43, T__43=44, T__44=45,
		T__45=46, T__46=47, T__47=48, T__48=49, T__49=50, T__50=51, T__51=52,
		T__52=53, T__53=54, T__54=55, T__55=56, T__56=57, T__57=58, T__58=59,
		T__59=60, T__60=61, T__61=62, T__62=63, T__63=64, T__64=65, T__65=66,
		T__66=67, T__67=68, T__68=69, T__69=70, T__70=71, T__71=72, T__72=73,
		T__73=74, T__74=75, T__75=76, T__76=77, T__77=78, T__78=79, T__79=80,
		T__80=81, T__81=82, T__82=83, T__83=84, T__84=85, T__85=86, T__86=87,
		T__87=88, T__88=89, T__89=90, T__90=91, T__91=92, T__92=93, T__93=94,
		T__94=95, T__95=96, T__96=97, T__97=98, T__98=99, T__99=100, T__100=101,
		T__101=102, T__102=103, T__103=104, T__104=105, T__105=106, T__106=107,
		LINECOMMENT=108, COMMENT=109, WS=110, EOL=111, NAME=112, DEC_INTEGER=113,
		HEX_INTEGER=114, BIN_INTEGER=115, FLOAT_NUMBER=116, STRING=117, INLINEASMBLOCK=118,
		SINGLECHAR=119;
	public static final int
		RULE_module = 0, RULE_modulestatement = 1, RULE_block = 2, RULE_statement = 3,
		RULE_labeldef = 4, RULE_unconditionaljump = 5, RULE_directive = 6, RULE_directivearg = 7,
		RULE_vardecl = 8, RULE_varinitializer = 9, RULE_constdecl = 10, RULE_memoryvardecl = 11,
		RULE_datatype = 12, RULE_arrayspec = 13, RULE_assignment = 14, RULE_assign_targets = 15,
		RULE_augassignment = 16, RULE_assign_target = 17, RULE_postincrdecr = 18,
		RULE_expression = 19, RULE_arrayindexed = 20, RULE_functioncall = 21,
		RULE_functioncall_stmt = 22, RULE_expression_list = 23, RULE_returnstmt = 24,
		RULE_breakstmt = 25, RULE_continuestmt = 26, RULE_identifier = 27, RULE_scoped_identifier = 28,
		RULE_register = 29, RULE_registerorpair = 30, RULE_statusregister = 31,
		RULE_integerliteral = 32, RULE_wordsuffix = 33, RULE_booleanliteral = 34,
		RULE_arrayliteral = 35, RULE_stringliteral = 36, RULE_charliteral = 37,
		RULE_floatliteral = 38, RULE_literalvalue = 39, RULE_inlineasm = 40, RULE_subroutine = 41,
		RULE_sub_return_part = 42, RULE_statement_block = 43, RULE_sub_params = 44,
		RULE_sub_param = 45, RULE_sub_returns = 46, RULE_asmsubroutine = 47, RULE_asmsub_address = 48,
		RULE_asmsub_params = 49, RULE_asmsub_param = 50, RULE_clobber = 51, RULE_asmsub_returns = 52,
		RULE_asmsub_return = 53, RULE_if_stmt = 54, RULE_else_part = 55, RULE_branch_stmt = 56,
		RULE_branchcondition = 57, RULE_forloop = 58, RULE_whileloop = 59, RULE_repeatloop = 60;
	public static final String[] ruleNames = {
		"module", "modulestatement", "block", "statement", "labeldef", "unconditionaljump",
		"directive", "directivearg", "vardecl", "varinitializer", "constdecl",
		"memoryvardecl", "datatype", "arrayspec", "assignment", "assign_targets",
		"augassignment", "assign_target", "postincrdecr", "expression", "arrayindexed",
		"functioncall", "functioncall_stmt", "expression_list", "returnstmt",
		"breakstmt", "continuestmt", "identifier", "scoped_identifier", "register",
		"registerorpair", "statusregister", "integerliteral", "wordsuffix", "booleanliteral",
		"arrayliteral", "stringliteral", "charliteral", "floatliteral", "literalvalue",
		"inlineasm", "subroutine", "sub_return_part", "statement_block", "sub_params",
		"sub_param", "sub_returns", "asmsubroutine", "asmsub_address", "asmsub_params",
		"asmsub_param", "clobber", "asmsub_returns", "asmsub_return", "if_stmt",
		"else_part", "branch_stmt", "branchcondition", "forloop", "whileloop",
		"repeatloop"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'~'", "':'", "'goto'", "'%output'", "'%launcher'", "'%zeropage'",
		"'%zpreserved'", "'%address'", "'%import'", "'%breakpoint'", "'%asminclude'",
		"'%asmbinary'", "'%option'", "','", "'='", "'const'", "'memory'", "'ubyte'",
		"'byte'", "'uword'", "'word'", "'float'", "'str'", "'str_p'", "'str_s'",
		"'str_ps'", "'['", "']'", "'+='", "'-='", "'/='", "'//='", "'*='", "'**='",
		"'&='", "'|='", "'^='", "'++'", "'--'", "'('", "')'", "'+'", "'-'", "'**'",
		"'*'", "'/'", "'//'", "'%'", "'<'", "'>'", "'<='", "'>='", "'=='", "'!='",
		"'&'", "'^'", "'|'", "'to'", "'step'", "'and'", "'or'", "'xor'", "'not'",
		"'return'", "'break'", "'continue'", "'.'", "'A'", "'X'", "'Y'", "'AX'",
		"'AY'", "'XY'", "'Pc'", "'Pz'", "'Pn'", "'Pv'", "'.w'", "'true'", "'false'",
		"'%asm'", "'sub'", "'->'", "'{'", "'}'", "'asmsub'", "'clobbers'", "'@'",
		"'if'", "'else'", "'if_cs'", "'if_cc'", "'if_eq'", "'if_z'", "'if_ne'",
		"'if_nz'", "'if_pl'", "'if_pos'", "'if_mi'", "'if_neg'", "'if_vs'", "'if_vc'",
		"'for'", "'in'", "'while'", "'repeat'", "'until'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null, null, null,
		"LINECOMMENT", "COMMENT", "WS", "EOL", "NAME", "DEC_INTEGER", "HEX_INTEGER",
		"BIN_INTEGER", "FLOAT_NUMBER", "STRING", "INLINEASMBLOCK", "SINGLECHAR"
	};
	public static final Vocabulary VOCABULARY = new VocabularyImpl(_LITERAL_NAMES, _SYMBOLIC_NAMES);

	/**
	 * @deprecated Use {@link #VOCABULARY} instead.
	 */
	@Deprecated
	public static final String[] tokenNames;
	static {
		tokenNames = new String[_SYMBOLIC_NAMES.length];
		for (int i = 0; i < tokenNames.length; i++) {
			tokenNames[i] = VOCABULARY.getLiteralName(i);
			if (tokenNames[i] == null) {
				tokenNames[i] = VOCABULARY.getSymbolicName(i);
			}

			if (tokenNames[i] == null) {
				tokenNames[i] = "<INVALID>";
			}
		}
	}

	@Override
	@Deprecated
	public String[] getTokenNames() {
		return tokenNames;
	}

	@Override

	public Vocabulary getVocabulary() {
		return VOCABULARY;
	}

	@Override
	public String getGrammarFileName() { return "prog8.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public prog8Parser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class ModuleContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(prog8Parser.EOF, 0); }
		public List<ModulestatementContext> modulestatement() {
			return getRuleContexts(ModulestatementContext.class);
		}
		public ModulestatementContext modulestatement(int i) {
			return getRuleContext(ModulestatementContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public ModuleContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_module; }
	}

	public final ModuleContext module() throws RecognitionException {
		ModuleContext _localctx = new ModuleContext(_ctx, getState());
		enterRule(_localctx, 0, RULE_module);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(126);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12))) != 0) || _la==EOL) {
				{
				setState(124);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__3:
				case T__4:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
				case T__9:
				case T__10:
				case T__11:
				case T__12:
					{
					setState(122);
					modulestatement();
					}
					break;
				case EOL:
					{
					setState(123);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(128);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(129);
			match(EOF);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ModulestatementContext extends ParserRuleContext {
		public DirectiveContext directive() {
			return getRuleContext(DirectiveContext.class,0);
		}
		public BlockContext block() {
			return getRuleContext(BlockContext.class,0);
		}
		public ModulestatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_modulestatement; }
	}

	public final ModulestatementContext modulestatement() throws RecognitionException {
		ModulestatementContext _localctx = new ModulestatementContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_modulestatement);
		try {
			setState(133);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__3:
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
				enterOuterAlt(_localctx, 1);
				{
				setState(131);
				directive();
				}
				break;
			case T__0:
				enterOuterAlt(_localctx, 2);
				{
				setState(132);
				block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BlockContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(135);
			match(T__0);
			setState(136);
			identifier();
			setState(138);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 113)) & ~0x3f) == 0 && ((1L << (_la - 113)) & ((1L << (DEC_INTEGER - 113)) | (1L << (HEX_INTEGER - 113)) | (1L << (BIN_INTEGER - 113)))) != 0)) {
				{
				setState(137);
				integerliteral();
				}
			}

			setState(140);
			statement_block();
			setState(141);
			match(EOL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatementContext extends ParserRuleContext {
		public DirectiveContext directive() {
			return getRuleContext(DirectiveContext.class,0);
		}
		public VarinitializerContext varinitializer() {
			return getRuleContext(VarinitializerContext.class,0);
		}
		public VardeclContext vardecl() {
			return getRuleContext(VardeclContext.class,0);
		}
		public ConstdeclContext constdecl() {
			return getRuleContext(ConstdeclContext.class,0);
		}
		public MemoryvardeclContext memoryvardecl() {
			return getRuleContext(MemoryvardeclContext.class,0);
		}
		public AssignmentContext assignment() {
			return getRuleContext(AssignmentContext.class,0);
		}
		public AugassignmentContext augassignment() {
			return getRuleContext(AugassignmentContext.class,0);
		}
		public UnconditionaljumpContext unconditionaljump() {
			return getRuleContext(UnconditionaljumpContext.class,0);
		}
		public PostincrdecrContext postincrdecr() {
			return getRuleContext(PostincrdecrContext.class,0);
		}
		public Functioncall_stmtContext functioncall_stmt() {
			return getRuleContext(Functioncall_stmtContext.class,0);
		}
		public If_stmtContext if_stmt() {
			return getRuleContext(If_stmtContext.class,0);
		}
		public Branch_stmtContext branch_stmt() {
			return getRuleContext(Branch_stmtContext.class,0);
		}
		public SubroutineContext subroutine() {
			return getRuleContext(SubroutineContext.class,0);
		}
		public AsmsubroutineContext asmsubroutine() {
			return getRuleContext(AsmsubroutineContext.class,0);
		}
		public InlineasmContext inlineasm() {
			return getRuleContext(InlineasmContext.class,0);
		}
		public ReturnstmtContext returnstmt() {
			return getRuleContext(ReturnstmtContext.class,0);
		}
		public ForloopContext forloop() {
			return getRuleContext(ForloopContext.class,0);
		}
		public WhileloopContext whileloop() {
			return getRuleContext(WhileloopContext.class,0);
		}
		public RepeatloopContext repeatloop() {
			return getRuleContext(RepeatloopContext.class,0);
		}
		public BreakstmtContext breakstmt() {
			return getRuleContext(BreakstmtContext.class,0);
		}
		public ContinuestmtContext continuestmt() {
			return getRuleContext(ContinuestmtContext.class,0);
		}
		public LabeldefContext labeldef() {
			return getRuleContext(LabeldefContext.class,0);
		}
		public StatementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement; }
	}

	public final StatementContext statement() throws RecognitionException {
		StatementContext _localctx = new StatementContext(_ctx, getState());
		enterRule(_localctx, 6, RULE_statement);
		try {
			setState(165);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(143);
				directive();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(144);
				varinitializer();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(145);
				vardecl();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(146);
				constdecl();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(147);
				memoryvardecl();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(148);
				assignment();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(149);
				augassignment();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(150);
				unconditionaljump();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(151);
				postincrdecr();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(152);
				functioncall_stmt();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(153);
				if_stmt();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(154);
				branch_stmt();
				}
				break;
			case 13:
				enterOuterAlt(_localctx, 13);
				{
				setState(155);
				subroutine();
				}
				break;
			case 14:
				enterOuterAlt(_localctx, 14);
				{
				setState(156);
				asmsubroutine();
				}
				break;
			case 15:
				enterOuterAlt(_localctx, 15);
				{
				setState(157);
				inlineasm();
				}
				break;
			case 16:
				enterOuterAlt(_localctx, 16);
				{
				setState(158);
				returnstmt();
				}
				break;
			case 17:
				enterOuterAlt(_localctx, 17);
				{
				setState(159);
				forloop();
				}
				break;
			case 18:
				enterOuterAlt(_localctx, 18);
				{
				setState(160);
				whileloop();
				}
				break;
			case 19:
				enterOuterAlt(_localctx, 19);
				{
				setState(161);
				repeatloop();
				}
				break;
			case 20:
				enterOuterAlt(_localctx, 20);
				{
				setState(162);
				breakstmt();
				}
				break;
			case 21:
				enterOuterAlt(_localctx, 21);
				{
				setState(163);
				continuestmt();
				}
				break;
			case 22:
				enterOuterAlt(_localctx, 22);
				{
				setState(164);
				labeldef();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LabeldefContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public LabeldefContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_labeldef; }
	}

	public final LabeldefContext labeldef() throws RecognitionException {
		LabeldefContext _localctx = new LabeldefContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_labeldef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(167);
			identifier();
			setState(168);
			match(T__1);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class UnconditionaljumpContext extends ParserRuleContext {
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public UnconditionaljumpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unconditionaljump; }
	}

	public final UnconditionaljumpContext unconditionaljump() throws RecognitionException {
		UnconditionaljumpContext _localctx = new UnconditionaljumpContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_unconditionaljump);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(170);
			match(T__2);
			setState(174);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				{
				setState(171);
				integerliteral();
				}
				break;
			case 2:
				{
				setState(172);
				identifier();
				}
				break;
			case 3:
				{
				setState(173);
				scoped_identifier();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DirectiveContext extends ParserRuleContext {
		public Token directivename;
		public List<DirectiveargContext> directivearg() {
			return getRuleContexts(DirectiveargContext.class);
		}
		public DirectiveargContext directivearg(int i) {
			return getRuleContext(DirectiveargContext.class,i);
		}
		public DirectiveContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directive; }
	}

	public final DirectiveContext directive() throws RecognitionException {
		DirectiveContext _localctx = new DirectiveContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_directive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(176);
			((DirectiveContext)_localctx).directivename = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12))) != 0)) ) {
				((DirectiveContext)_localctx).directivename = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(188);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				{
				setState(178);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
				case 1:
					{
					setState(177);
					directivearg();
					}
					break;
				}
				}
				break;
			case 2:
				{
				setState(180);
				directivearg();
				setState(185);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__13) {
					{
					{
					setState(181);
					match(T__13);
					setState(182);
					directivearg();
					}
					}
					setState(187);
					_errHandler.sync(this);
					_la = _input.LA(1);
				}
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DirectiveargContext extends ParserRuleContext {
		public StringliteralContext stringliteral() {
			return getRuleContext(StringliteralContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public DirectiveargContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directivearg; }
	}

	public final DirectiveargContext directivearg() throws RecognitionException {
		DirectiveargContext _localctx = new DirectiveargContext(_ctx, getState());
		enterRule(_localctx, 14, RULE_directivearg);
		try {
			setState(193);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(190);
				stringliteral();
				}
				break;
			case NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(191);
				identifier();
				}
				break;
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(192);
				integerliteral();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VardeclContext extends ParserRuleContext {
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ArrayspecContext arrayspec() {
			return getRuleContext(ArrayspecContext.class,0);
		}
		public VardeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vardecl; }
	}

	public final VardeclContext vardecl() throws RecognitionException {
		VardeclContext _localctx = new VardeclContext(_ctx, getState());
		enterRule(_localctx, 16, RULE_vardecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(195);
			datatype();
			setState(197);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__26) {
				{
				setState(196);
				arrayspec();
				}
			}

			setState(199);
			identifier();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class VarinitializerContext extends ParserRuleContext {
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ArrayspecContext arrayspec() {
			return getRuleContext(ArrayspecContext.class,0);
		}
		public VarinitializerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_varinitializer; }
	}

	public final VarinitializerContext varinitializer() throws RecognitionException {
		VarinitializerContext _localctx = new VarinitializerContext(_ctx, getState());
		enterRule(_localctx, 18, RULE_varinitializer);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(201);
			datatype();
			setState(203);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__26) {
				{
				setState(202);
				arrayspec();
				}
			}

			setState(205);
			identifier();
			setState(206);
			match(T__14);
			setState(207);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ConstdeclContext extends ParserRuleContext {
		public VarinitializerContext varinitializer() {
			return getRuleContext(VarinitializerContext.class,0);
		}
		public ConstdeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_constdecl; }
	}

	public final ConstdeclContext constdecl() throws RecognitionException {
		ConstdeclContext _localctx = new ConstdeclContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_constdecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(209);
			match(T__15);
			setState(210);
			varinitializer();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class MemoryvardeclContext extends ParserRuleContext {
		public VarinitializerContext varinitializer() {
			return getRuleContext(VarinitializerContext.class,0);
		}
		public MemoryvardeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_memoryvardecl; }
	}

	public final MemoryvardeclContext memoryvardecl() throws RecognitionException {
		MemoryvardeclContext _localctx = new MemoryvardeclContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_memoryvardecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(212);
			match(T__16);
			setState(213);
			varinitializer();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class DatatypeContext extends ParserRuleContext {
		public DatatypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_datatype; }
	}

	public final DatatypeContext datatype() throws RecognitionException {
		DatatypeContext _localctx = new DatatypeContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_datatype);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(215);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArrayspecContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ArrayspecContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayspec; }
	}

	public final ArrayspecContext arrayspec() throws RecognitionException {
		ArrayspecContext _localctx = new ArrayspecContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_arrayspec);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(217);
			match(T__26);
			setState(218);
			expression(0);
			setState(219);
			match(T__27);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AssignmentContext extends ParserRuleContext {
		public Assign_targetsContext assign_targets() {
			return getRuleContext(Assign_targetsContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AssignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assignment; }
	}

	public final AssignmentContext assignment() throws RecognitionException {
		AssignmentContext _localctx = new AssignmentContext(_ctx, getState());
		enterRule(_localctx, 28, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(221);
			assign_targets();
			setState(222);
			match(T__14);
			setState(223);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Assign_targetsContext extends ParserRuleContext {
		public List<Assign_targetContext> assign_target() {
			return getRuleContexts(Assign_targetContext.class);
		}
		public Assign_targetContext assign_target(int i) {
			return getRuleContext(Assign_targetContext.class,i);
		}
		public Assign_targetsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assign_targets; }
	}

	public final Assign_targetsContext assign_targets() throws RecognitionException {
		Assign_targetsContext _localctx = new Assign_targetsContext(_ctx, getState());
		enterRule(_localctx, 30, RULE_assign_targets);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(225);
			assign_target();
			setState(230);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(226);
				match(T__13);
				setState(227);
				assign_target();
				}
				}
				setState(232);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AugassignmentContext extends ParserRuleContext {
		public Token operator;
		public Assign_targetContext assign_target() {
			return getRuleContext(Assign_targetContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public AugassignmentContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_augassignment; }
	}

	public final AugassignmentContext augassignment() throws RecognitionException {
		AugassignmentContext _localctx = new AugassignmentContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_augassignment);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(233);
			assign_target();
			setState(234);
			((AugassignmentContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36))) != 0)) ) {
				((AugassignmentContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(235);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Assign_targetContext extends ParserRuleContext {
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public ArrayindexedContext arrayindexed() {
			return getRuleContext(ArrayindexedContext.class,0);
		}
		public Assign_targetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assign_target; }
	}

	public final Assign_targetContext assign_target() throws RecognitionException {
		Assign_targetContext _localctx = new Assign_targetContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_assign_target);
		try {
			setState(241);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(237);
				register();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(238);
				identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(239);
				scoped_identifier();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(240);
				arrayindexed();
				}
				break;
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class PostincrdecrContext extends ParserRuleContext {
		public Token operator;
		public Assign_targetContext assign_target() {
			return getRuleContext(Assign_targetContext.class,0);
		}
		public PostincrdecrContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_postincrdecr; }
	}

	public final PostincrdecrContext postincrdecr() throws RecognitionException {
		PostincrdecrContext _localctx = new PostincrdecrContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_postincrdecr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(243);
			assign_target();
			setState(244);
			((PostincrdecrContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==T__37 || _la==T__38) ) {
				((PostincrdecrContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ExpressionContext extends ParserRuleContext {
		public ExpressionContext left;
		public ExpressionContext rangefrom;
		public Token prefix;
		public Token bop;
		public ExpressionContext right;
		public ExpressionContext rangeto;
		public ExpressionContext rangestep;
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public FunctioncallContext functioncall() {
			return getRuleContext(FunctioncallContext.class,0);
		}
		public LiteralvalueContext literalvalue() {
			return getRuleContext(LiteralvalueContext.class,0);
		}
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public ArrayindexedContext arrayindexed() {
			return getRuleContext(ArrayindexedContext.class,0);
		}
		public ExpressionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression; }
	}

	public final ExpressionContext expression() throws RecognitionException {
		return expression(0);
	}

	private ExpressionContext expression(int _p) throws RecognitionException {
		ParserRuleContext _parentctx = _ctx;
		int _parentState = getState();
		ExpressionContext _localctx = new ExpressionContext(_ctx, _parentState);
		ExpressionContext _prevctx = _localctx;
		int _startState = 38;
		enterRecursionRule(_localctx, 38, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(261);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
			case 1:
				{
				setState(247);
				match(T__39);
				setState(248);
				expression(0);
				setState(249);
				match(T__40);
				}
				break;
			case 2:
				{
				setState(251);
				functioncall();
				}
				break;
			case 3:
				{
				setState(252);
				((ExpressionContext)_localctx).prefix = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__41) | (1L << T__42))) != 0)) ) {
					((ExpressionContext)_localctx).prefix = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(253);
				expression(19);
				}
				break;
			case 4:
				{
				setState(254);
				((ExpressionContext)_localctx).prefix = match(T__62);
				setState(255);
				expression(6);
				}
				break;
			case 5:
				{
				setState(256);
				literalvalue();
				}
				break;
			case 6:
				{
				setState(257);
				register();
				}
				break;
			case 7:
				{
				setState(258);
				identifier();
				}
				break;
			case 8:
				{
				setState(259);
				scoped_identifier();
				}
				break;
			case 9:
				{
				setState(260);
				arrayindexed();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(305);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(303);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(263);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(264);
						((ExpressionContext)_localctx).bop = match(T__43);
						setState(265);
						((ExpressionContext)_localctx).right = expression(19);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(266);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(267);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__44) | (1L << T__45) | (1L << T__46) | (1L << T__47))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(268);
						((ExpressionContext)_localctx).right = expression(18);
						}
						break;
					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(269);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
						setState(270);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__41 || _la==T__42) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(271);
						((ExpressionContext)_localctx).right = expression(17);
						}
						break;
					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(272);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(273);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__48) | (1L << T__49) | (1L << T__50) | (1L << T__51))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(274);
						((ExpressionContext)_localctx).right = expression(16);
						}
						break;
					case 5:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(275);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(276);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__52 || _la==T__53) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(277);
						((ExpressionContext)_localctx).right = expression(15);
						}
						break;
					case 6:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(278);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(279);
						((ExpressionContext)_localctx).bop = match(T__54);
						setState(280);
						((ExpressionContext)_localctx).right = expression(14);
						}
						break;
					case 7:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(281);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(282);
						((ExpressionContext)_localctx).bop = match(T__55);
						setState(283);
						((ExpressionContext)_localctx).right = expression(13);
						}
						break;
					case 8:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(284);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(285);
						((ExpressionContext)_localctx).bop = match(T__56);
						setState(286);
						((ExpressionContext)_localctx).right = expression(12);
						}
						break;
					case 9:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(287);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(288);
						((ExpressionContext)_localctx).bop = match(T__59);
						setState(289);
						((ExpressionContext)_localctx).right = expression(10);
						}
						break;
					case 10:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(290);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(291);
						((ExpressionContext)_localctx).bop = match(T__60);
						setState(292);
						((ExpressionContext)_localctx).right = expression(9);
						}
						break;
					case 11:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(293);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(294);
						((ExpressionContext)_localctx).bop = match(T__61);
						setState(295);
						((ExpressionContext)_localctx).right = expression(8);
						}
						break;
					case 12:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.rangefrom = _prevctx;
						_localctx.rangefrom = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(296);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(297);
						match(T__57);
						setState(298);
						((ExpressionContext)_localctx).rangeto = expression(0);
						setState(301);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
						case 1:
							{
							setState(299);
							match(T__58);
							setState(300);
							((ExpressionContext)_localctx).rangestep = expression(0);
							}
							break;
						}
						}
						break;
					}
					}
				}
				setState(307);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			unrollRecursionContexts(_parentctx);
		}
		return _localctx;
	}

	public static class ArrayindexedContext extends ParserRuleContext {
		public ArrayspecContext arrayspec() {
			return getRuleContext(ArrayspecContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public ArrayindexedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayindexed; }
	}

	public final ArrayindexedContext arrayindexed() throws RecognitionException {
		ArrayindexedContext _localctx = new ArrayindexedContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_arrayindexed);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(310);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				{
				setState(308);
				identifier();
				}
				break;
			case 2:
				{
				setState(309);
				scoped_identifier();
				}
				break;
			}
			setState(312);
			arrayspec();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FunctioncallContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public Expression_listContext expression_list() {
			return getRuleContext(Expression_listContext.class,0);
		}
		public FunctioncallContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functioncall; }
	}

	public final FunctioncallContext functioncall() throws RecognitionException {
		FunctioncallContext _localctx = new FunctioncallContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_functioncall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(316);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				{
				setState(314);
				identifier();
				}
				break;
			case 2:
				{
				setState(315);
				scoped_identifier();
				}
				break;
			}
			setState(318);
			match(T__39);
			setState(320);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__26) | (1L << T__39) | (1L << T__41) | (1L << T__42) | (1L << T__62))) != 0) || ((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & ((1L << (T__67 - 68)) | (1L << (T__68 - 68)) | (1L << (T__69 - 68)) | (1L << (T__78 - 68)) | (1L << (T__79 - 68)) | (1L << (NAME - 68)) | (1L << (DEC_INTEGER - 68)) | (1L << (HEX_INTEGER - 68)) | (1L << (BIN_INTEGER - 68)) | (1L << (FLOAT_NUMBER - 68)) | (1L << (STRING - 68)) | (1L << (SINGLECHAR - 68)))) != 0)) {
				{
				setState(319);
				expression_list();
				}
			}

			setState(322);
			match(T__40);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Functioncall_stmtContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public Expression_listContext expression_list() {
			return getRuleContext(Expression_listContext.class,0);
		}
		public Functioncall_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_functioncall_stmt; }
	}

	public final Functioncall_stmtContext functioncall_stmt() throws RecognitionException {
		Functioncall_stmtContext _localctx = new Functioncall_stmtContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_functioncall_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(326);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				{
				setState(324);
				identifier();
				}
				break;
			case 2:
				{
				setState(325);
				scoped_identifier();
				}
				break;
			}
			setState(328);
			match(T__39);
			setState(330);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__26) | (1L << T__39) | (1L << T__41) | (1L << T__42) | (1L << T__62))) != 0) || ((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & ((1L << (T__67 - 68)) | (1L << (T__68 - 68)) | (1L << (T__69 - 68)) | (1L << (T__78 - 68)) | (1L << (T__79 - 68)) | (1L << (NAME - 68)) | (1L << (DEC_INTEGER - 68)) | (1L << (HEX_INTEGER - 68)) | (1L << (BIN_INTEGER - 68)) | (1L << (FLOAT_NUMBER - 68)) | (1L << (STRING - 68)) | (1L << (SINGLECHAR - 68)))) != 0)) {
				{
				setState(329);
				expression_list();
				}
			}

			setState(332);
			match(T__40);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Expression_listContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public Expression_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_list; }
	}

	public final Expression_listContext expression_list() throws RecognitionException {
		Expression_listContext _localctx = new Expression_listContext(_ctx, getState());
		enterRule(_localctx, 46, RULE_expression_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(334);
			expression(0);
			setState(342);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(335);
				match(T__13);
				setState(337);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(336);
					match(EOL);
					}
				}

				setState(339);
				expression(0);
				}
				}
				setState(344);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ReturnstmtContext extends ParserRuleContext {
		public Expression_listContext expression_list() {
			return getRuleContext(Expression_listContext.class,0);
		}
		public ReturnstmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnstmt; }
	}

	public final ReturnstmtContext returnstmt() throws RecognitionException {
		ReturnstmtContext _localctx = new ReturnstmtContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_returnstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(345);
			match(T__63);
			setState(347);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,25,_ctx) ) {
			case 1:
				{
				setState(346);
				expression_list();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BreakstmtContext extends ParserRuleContext {
		public BreakstmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_breakstmt; }
	}

	public final BreakstmtContext breakstmt() throws RecognitionException {
		BreakstmtContext _localctx = new BreakstmtContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_breakstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(349);
			match(T__64);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ContinuestmtContext extends ParserRuleContext {
		public ContinuestmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continuestmt; }
	}

	public final ContinuestmtContext continuestmt() throws RecognitionException {
		ContinuestmtContext _localctx = new ContinuestmtContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_continuestmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(351);
			match(T__65);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IdentifierContext extends ParserRuleContext {
		public TerminalNode NAME() { return getToken(prog8Parser.NAME, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(353);
			match(NAME);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Scoped_identifierContext extends ParserRuleContext {
		public List<TerminalNode> NAME() { return getTokens(prog8Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(prog8Parser.NAME, i);
		}
		public Scoped_identifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_scoped_identifier; }
	}

	public final Scoped_identifierContext scoped_identifier() throws RecognitionException {
		Scoped_identifierContext _localctx = new Scoped_identifierContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_scoped_identifier);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(355);
			match(NAME);
			setState(358);
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(356);
					match(T__66);
					setState(357);
					match(NAME);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(360);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,26,_ctx);
			} while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER );
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RegisterContext extends ParserRuleContext {
		public RegisterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_register; }
	}

	public final RegisterContext register() throws RecognitionException {
		RegisterContext _localctx = new RegisterContext(_ctx, getState());
		enterRule(_localctx, 58, RULE_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(362);
			_la = _input.LA(1);
			if ( !(((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & ((1L << (T__67 - 68)) | (1L << (T__68 - 68)) | (1L << (T__69 - 68)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RegisterorpairContext extends ParserRuleContext {
		public RegisterorpairContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_registerorpair; }
	}

	public final RegisterorpairContext registerorpair() throws RecognitionException {
		RegisterorpairContext _localctx = new RegisterorpairContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_registerorpair);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(364);
			_la = _input.LA(1);
			if ( !(((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & ((1L << (T__67 - 68)) | (1L << (T__68 - 68)) | (1L << (T__69 - 68)) | (1L << (T__70 - 68)) | (1L << (T__71 - 68)) | (1L << (T__72 - 68)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StatusregisterContext extends ParserRuleContext {
		public StatusregisterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statusregister; }
	}

	public final StatusregisterContext statusregister() throws RecognitionException {
		StatusregisterContext _localctx = new StatusregisterContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_statusregister);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(366);
			_la = _input.LA(1);
			if ( !(((((_la - 74)) & ~0x3f) == 0 && ((1L << (_la - 74)) & ((1L << (T__73 - 74)) | (1L << (T__74 - 74)) | (1L << (T__75 - 74)) | (1L << (T__76 - 74)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class IntegerliteralContext extends ParserRuleContext {
		public Token intpart;
		public TerminalNode DEC_INTEGER() { return getToken(prog8Parser.DEC_INTEGER, 0); }
		public TerminalNode HEX_INTEGER() { return getToken(prog8Parser.HEX_INTEGER, 0); }
		public TerminalNode BIN_INTEGER() { return getToken(prog8Parser.BIN_INTEGER, 0); }
		public WordsuffixContext wordsuffix() {
			return getRuleContext(WordsuffixContext.class,0);
		}
		public IntegerliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_integerliteral; }
	}

	public final IntegerliteralContext integerliteral() throws RecognitionException {
		IntegerliteralContext _localctx = new IntegerliteralContext(_ctx, getState());
		enterRule(_localctx, 64, RULE_integerliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(368);
			((IntegerliteralContext)_localctx).intpart = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 113)) & ~0x3f) == 0 && ((1L << (_la - 113)) & ((1L << (DEC_INTEGER - 113)) | (1L << (HEX_INTEGER - 113)) | (1L << (BIN_INTEGER - 113)))) != 0)) ) {
				((IntegerliteralContext)_localctx).intpart = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(370);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,27,_ctx) ) {
			case 1:
				{
				setState(369);
				wordsuffix();
				}
				break;
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class WordsuffixContext extends ParserRuleContext {
		public WordsuffixContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_wordsuffix; }
	}

	public final WordsuffixContext wordsuffix() throws RecognitionException {
		WordsuffixContext _localctx = new WordsuffixContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_wordsuffix);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(372);
			match(T__77);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BooleanliteralContext extends ParserRuleContext {
		public BooleanliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_booleanliteral; }
	}

	public final BooleanliteralContext booleanliteral() throws RecognitionException {
		BooleanliteralContext _localctx = new BooleanliteralContext(_ctx, getState());
		enterRule(_localctx, 68, RULE_booleanliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(374);
			_la = _input.LA(1);
			if ( !(_la==T__78 || _la==T__79) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ArrayliteralContext extends ParserRuleContext {
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public ArrayliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayliteral; }
	}

	public final ArrayliteralContext arrayliteral() throws RecognitionException {
		ArrayliteralContext _localctx = new ArrayliteralContext(_ctx, getState());
		enterRule(_localctx, 70, RULE_arrayliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(376);
			match(T__26);
			setState(378);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(377);
				match(EOL);
				}
			}

			setState(380);
			expression(0);
			setState(388);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(381);
				match(T__13);
				setState(383);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(382);
					match(EOL);
					}
				}

				setState(385);
				expression(0);
				}
				}
				setState(390);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(392);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(391);
				match(EOL);
				}
			}

			setState(394);
			match(T__27);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class StringliteralContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(prog8Parser.STRING, 0); }
		public StringliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stringliteral; }
	}

	public final StringliteralContext stringliteral() throws RecognitionException {
		StringliteralContext _localctx = new StringliteralContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_stringliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(396);
			match(STRING);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class CharliteralContext extends ParserRuleContext {
		public TerminalNode SINGLECHAR() { return getToken(prog8Parser.SINGLECHAR, 0); }
		public CharliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_charliteral; }
	}

	public final CharliteralContext charliteral() throws RecognitionException {
		CharliteralContext _localctx = new CharliteralContext(_ctx, getState());
		enterRule(_localctx, 74, RULE_charliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(398);
			match(SINGLECHAR);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class FloatliteralContext extends ParserRuleContext {
		public TerminalNode FLOAT_NUMBER() { return getToken(prog8Parser.FLOAT_NUMBER, 0); }
		public FloatliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_floatliteral; }
	}

	public final FloatliteralContext floatliteral() throws RecognitionException {
		FloatliteralContext _localctx = new FloatliteralContext(_ctx, getState());
		enterRule(_localctx, 76, RULE_floatliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(400);
			match(FLOAT_NUMBER);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class LiteralvalueContext extends ParserRuleContext {
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public BooleanliteralContext booleanliteral() {
			return getRuleContext(BooleanliteralContext.class,0);
		}
		public ArrayliteralContext arrayliteral() {
			return getRuleContext(ArrayliteralContext.class,0);
		}
		public StringliteralContext stringliteral() {
			return getRuleContext(StringliteralContext.class,0);
		}
		public CharliteralContext charliteral() {
			return getRuleContext(CharliteralContext.class,0);
		}
		public FloatliteralContext floatliteral() {
			return getRuleContext(FloatliteralContext.class,0);
		}
		public LiteralvalueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literalvalue; }
	}

	public final LiteralvalueContext literalvalue() throws RecognitionException {
		LiteralvalueContext _localctx = new LiteralvalueContext(_ctx, getState());
		enterRule(_localctx, 78, RULE_literalvalue);
		try {
			setState(408);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 1);
				{
				setState(402);
				integerliteral();
				}
				break;
			case T__78:
			case T__79:
				enterOuterAlt(_localctx, 2);
				{
				setState(403);
				booleanliteral();
				}
				break;
			case T__26:
				enterOuterAlt(_localctx, 3);
				{
				setState(404);
				arrayliteral();
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 4);
				{
				setState(405);
				stringliteral();
				}
				break;
			case SINGLECHAR:
				enterOuterAlt(_localctx, 5);
				{
				setState(406);
				charliteral();
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 6);
				{
				setState(407);
				floatliteral();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class InlineasmContext extends ParserRuleContext {
		public TerminalNode INLINEASMBLOCK() { return getToken(prog8Parser.INLINEASMBLOCK, 0); }
		public InlineasmContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inlineasm; }
	}

	public final InlineasmContext inlineasm() throws RecognitionException {
		InlineasmContext _localctx = new InlineasmContext(_ctx, getState());
		enterRule(_localctx, 80, RULE_inlineasm);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(410);
			match(T__80);
			setState(411);
			match(INLINEASMBLOCK);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class SubroutineContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public Sub_paramsContext sub_params() {
			return getRuleContext(Sub_paramsContext.class,0);
		}
		public Sub_return_partContext sub_return_part() {
			return getRuleContext(Sub_return_partContext.class,0);
		}
		public SubroutineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subroutine; }
	}

	public final SubroutineContext subroutine() throws RecognitionException {
		SubroutineContext _localctx = new SubroutineContext(_ctx, getState());
		enterRule(_localctx, 82, RULE_subroutine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(413);
			match(T__81);
			setState(414);
			identifier();
			setState(415);
			match(T__39);
			setState(417);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(416);
				sub_params();
				}
			}

			setState(419);
			match(T__40);
			setState(421);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__82) {
				{
				setState(420);
				sub_return_part();
				}
			}

			{
			setState(423);
			statement_block();
			setState(424);
			match(EOL);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sub_return_partContext extends ParserRuleContext {
		public Sub_returnsContext sub_returns() {
			return getRuleContext(Sub_returnsContext.class,0);
		}
		public Sub_return_partContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sub_return_part; }
	}

	public final Sub_return_partContext sub_return_part() throws RecognitionException {
		Sub_return_partContext _localctx = new Sub_return_partContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_sub_return_part);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(426);
			match(T__82);
			setState(427);
			sub_returns();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Statement_blockContext extends ParserRuleContext {
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
		}
		public Statement_blockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_statement_block; }
	}

	public final Statement_blockContext statement_block() throws RecognitionException {
		Statement_blockContext _localctx = new Statement_blockContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_statement_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(429);
			match(T__83);
			setState(430);
			match(EOL);
			setState(435);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__64 - 64)) | (1L << (T__65 - 64)) | (1L << (T__67 - 64)) | (1L << (T__68 - 64)) | (1L << (T__69 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (T__85 - 64)) | (1L << (T__88 - 64)) | (1L << (T__90 - 64)) | (1L << (T__91 - 64)) | (1L << (T__92 - 64)) | (1L << (T__93 - 64)) | (1L << (T__94 - 64)) | (1L << (T__95 - 64)) | (1L << (T__96 - 64)) | (1L << (T__97 - 64)) | (1L << (T__98 - 64)) | (1L << (T__99 - 64)) | (1L << (T__100 - 64)) | (1L << (T__101 - 64)) | (1L << (T__102 - 64)) | (1L << (T__104 - 64)) | (1L << (T__105 - 64)) | (1L << (EOL - 64)) | (1L << (NAME - 64)))) != 0)) {
				{
				setState(433);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__2:
				case T__3:
				case T__4:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
				case T__9:
				case T__10:
				case T__11:
				case T__12:
				case T__15:
				case T__16:
				case T__17:
				case T__18:
				case T__19:
				case T__20:
				case T__21:
				case T__22:
				case T__23:
				case T__24:
				case T__25:
				case T__63:
				case T__64:
				case T__65:
				case T__67:
				case T__68:
				case T__69:
				case T__80:
				case T__81:
				case T__85:
				case T__88:
				case T__90:
				case T__91:
				case T__92:
				case T__93:
				case T__94:
				case T__95:
				case T__96:
				case T__97:
				case T__98:
				case T__99:
				case T__100:
				case T__101:
				case T__102:
				case T__104:
				case T__105:
				case NAME:
					{
					setState(431);
					statement();
					}
					break;
				case EOL:
					{
					setState(432);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(437);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(438);
			match(T__84);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sub_paramsContext extends ParserRuleContext {
		public List<Sub_paramContext> sub_param() {
			return getRuleContexts(Sub_paramContext.class);
		}
		public Sub_paramContext sub_param(int i) {
			return getRuleContext(Sub_paramContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public Sub_paramsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sub_params; }
	}

	public final Sub_paramsContext sub_params() throws RecognitionException {
		Sub_paramsContext _localctx = new Sub_paramsContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_sub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(440);
			sub_param();
			setState(448);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(441);
				match(T__13);
				setState(443);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(442);
					match(EOL);
					}
				}

				setState(445);
				sub_param();
				}
				}
				setState(450);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sub_paramContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public Sub_paramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sub_param; }
	}

	public final Sub_paramContext sub_param() throws RecognitionException {
		Sub_paramContext _localctx = new Sub_paramContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_sub_param);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(451);
			identifier();
			setState(452);
			match(T__1);
			setState(453);
			datatype();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Sub_returnsContext extends ParserRuleContext {
		public List<DatatypeContext> datatype() {
			return getRuleContexts(DatatypeContext.class);
		}
		public DatatypeContext datatype(int i) {
			return getRuleContext(DatatypeContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public Sub_returnsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sub_returns; }
	}

	public final Sub_returnsContext sub_returns() throws RecognitionException {
		Sub_returnsContext _localctx = new Sub_returnsContext(_ctx, getState());
		enterRule(_localctx, 92, RULE_sub_returns);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(455);
			datatype();
			setState(463);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(456);
				match(T__13);
				setState(458);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(457);
					match(EOL);
					}
				}

				setState(460);
				datatype();
				}
				}
				setState(465);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class AsmsubroutineContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Asmsub_addressContext asmsub_address() {
			return getRuleContext(Asmsub_addressContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public Asmsub_paramsContext asmsub_params() {
			return getRuleContext(Asmsub_paramsContext.class,0);
		}
		public ClobberContext clobber() {
			return getRuleContext(ClobberContext.class,0);
		}
		public Asmsub_returnsContext asmsub_returns() {
			return getRuleContext(Asmsub_returnsContext.class,0);
		}
		public AsmsubroutineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsubroutine; }
	}

	public final AsmsubroutineContext asmsubroutine() throws RecognitionException {
		AsmsubroutineContext _localctx = new AsmsubroutineContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_asmsubroutine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(466);
			match(T__85);
			setState(467);
			identifier();
			setState(468);
			match(T__39);
			setState(470);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(469);
				asmsub_params();
				}
			}

			setState(472);
			match(T__40);
			setState(473);
			match(T__82);
			setState(474);
			match(T__86);
			setState(475);
			match(T__39);
			setState(477);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 68)) & ~0x3f) == 0 && ((1L << (_la - 68)) & ((1L << (T__67 - 68)) | (1L << (T__68 - 68)) | (1L << (T__69 - 68)))) != 0)) {
				{
				setState(476);
				clobber();
				}
			}

			setState(479);
			match(T__40);
			setState(480);
			match(T__82);
			setState(481);
			match(T__39);
			setState(483);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) {
				{
				setState(482);
				asmsub_returns();
				}
			}

			setState(485);
			match(T__40);
			setState(488);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__14:
				{
				setState(486);
				asmsub_address();
				}
				break;
			case T__83:
				{
				setState(487);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Asmsub_addressContext extends ParserRuleContext {
		public IntegerliteralContext address;
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public Asmsub_addressContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_address; }
	}

	public final Asmsub_addressContext asmsub_address() throws RecognitionException {
		Asmsub_addressContext _localctx = new Asmsub_addressContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_asmsub_address);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(490);
			match(T__14);
			setState(491);
			((Asmsub_addressContext)_localctx).address = integerliteral();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Asmsub_paramsContext extends ParserRuleContext {
		public List<Asmsub_paramContext> asmsub_param() {
			return getRuleContexts(Asmsub_paramContext.class);
		}
		public Asmsub_paramContext asmsub_param(int i) {
			return getRuleContext(Asmsub_paramContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public Asmsub_paramsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_params; }
	}

	public final Asmsub_paramsContext asmsub_params() throws RecognitionException {
		Asmsub_paramsContext _localctx = new Asmsub_paramsContext(_ctx, getState());
		enterRule(_localctx, 98, RULE_asmsub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(493);
			asmsub_param();
			setState(501);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(494);
				match(T__13);
				setState(496);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(495);
					match(EOL);
					}
				}

				setState(498);
				asmsub_param();
				}
				}
				setState(503);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Asmsub_paramContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public RegisterorpairContext registerorpair() {
			return getRuleContext(RegisterorpairContext.class,0);
		}
		public StatusregisterContext statusregister() {
			return getRuleContext(StatusregisterContext.class,0);
		}
		public Asmsub_paramContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_param; }
	}

	public final Asmsub_paramContext asmsub_param() throws RecognitionException {
		Asmsub_paramContext _localctx = new Asmsub_paramContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_asmsub_param);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(504);
			identifier();
			setState(505);
			match(T__1);
			setState(506);
			datatype();
			setState(507);
			match(T__87);
			setState(510);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__67:
			case T__68:
			case T__69:
			case T__70:
			case T__71:
			case T__72:
				{
				setState(508);
				registerorpair();
				}
				break;
			case T__73:
			case T__74:
			case T__75:
			case T__76:
				{
				setState(509);
				statusregister();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ClobberContext extends ParserRuleContext {
		public List<RegisterContext> register() {
			return getRuleContexts(RegisterContext.class);
		}
		public RegisterContext register(int i) {
			return getRuleContext(RegisterContext.class,i);
		}
		public ClobberContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_clobber; }
	}

	public final ClobberContext clobber() throws RecognitionException {
		ClobberContext _localctx = new ClobberContext(_ctx, getState());
		enterRule(_localctx, 102, RULE_clobber);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(512);
			register();
			setState(517);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(513);
				match(T__13);
				setState(514);
				register();
				}
				}
				setState(519);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Asmsub_returnsContext extends ParserRuleContext {
		public List<Asmsub_returnContext> asmsub_return() {
			return getRuleContexts(Asmsub_returnContext.class);
		}
		public Asmsub_returnContext asmsub_return(int i) {
			return getRuleContext(Asmsub_returnContext.class,i);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public Asmsub_returnsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_returns; }
	}

	public final Asmsub_returnsContext asmsub_returns() throws RecognitionException {
		Asmsub_returnsContext _localctx = new Asmsub_returnsContext(_ctx, getState());
		enterRule(_localctx, 104, RULE_asmsub_returns);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(520);
			asmsub_return();
			setState(528);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(521);
				match(T__13);
				setState(523);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(522);
					match(EOL);
					}
				}

				setState(525);
				asmsub_return();
				}
				}
				setState(530);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Asmsub_returnContext extends ParserRuleContext {
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public RegisterorpairContext registerorpair() {
			return getRuleContext(RegisterorpairContext.class,0);
		}
		public StatusregisterContext statusregister() {
			return getRuleContext(StatusregisterContext.class,0);
		}
		public Asmsub_returnContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_return; }
	}

	public final Asmsub_returnContext asmsub_return() throws RecognitionException {
		Asmsub_returnContext _localctx = new Asmsub_returnContext(_ctx, getState());
		enterRule(_localctx, 106, RULE_asmsub_return);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(531);
			datatype();
			setState(532);
			match(T__87);
			setState(535);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__67:
			case T__68:
			case T__69:
			case T__70:
			case T__71:
			case T__72:
				{
				setState(533);
				registerorpair();
				}
				break;
			case T__73:
			case T__74:
			case T__75:
			case T__76:
				{
				setState(534);
				statusregister();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class If_stmtContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public Else_partContext else_part() {
			return getRuleContext(Else_partContext.class,0);
		}
		public If_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_if_stmt; }
	}

	public final If_stmtContext if_stmt() throws RecognitionException {
		If_stmtContext _localctx = new If_stmtContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_if_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(537);
			match(T__88);
			setState(538);
			expression(0);
			setState(540);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(539);
				match(EOL);
				}
			}

			setState(544);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__3:
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__24:
			case T__25:
			case T__63:
			case T__64:
			case T__65:
			case T__67:
			case T__68:
			case T__69:
			case T__80:
			case T__81:
			case T__85:
			case T__88:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
			case T__94:
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
			case T__102:
			case T__104:
			case T__105:
			case NAME:
				{
				setState(542);
				statement();
				}
				break;
			case T__83:
				{
				setState(543);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(547);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
			case 1:
				{
				setState(546);
				match(EOL);
				}
				break;
			}
			setState(550);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__89) {
				{
				setState(549);
				else_part();
				}
			}

			setState(552);
			match(EOL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Else_partContext extends ParserRuleContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public Else_partContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_else_part; }
	}

	public final Else_partContext else_part() throws RecognitionException {
		Else_partContext _localctx = new Else_partContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_else_part);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(554);
			match(T__89);
			setState(556);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(555);
				match(EOL);
				}
			}

			setState(560);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__3:
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__24:
			case T__25:
			case T__63:
			case T__64:
			case T__65:
			case T__67:
			case T__68:
			case T__69:
			case T__80:
			case T__81:
			case T__85:
			case T__88:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
			case T__94:
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
			case T__102:
			case T__104:
			case T__105:
			case NAME:
				{
				setState(558);
				statement();
				}
				break;
			case T__83:
				{
				setState(559);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class Branch_stmtContext extends ParserRuleContext {
		public BranchconditionContext branchcondition() {
			return getRuleContext(BranchconditionContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public Else_partContext else_part() {
			return getRuleContext(Else_partContext.class,0);
		}
		public Branch_stmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_branch_stmt; }
	}

	public final Branch_stmtContext branch_stmt() throws RecognitionException {
		Branch_stmtContext _localctx = new Branch_stmtContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_branch_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(562);
			branchcondition();
			setState(564);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(563);
				match(EOL);
				}
			}

			setState(568);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__3:
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__24:
			case T__25:
			case T__63:
			case T__64:
			case T__65:
			case T__67:
			case T__68:
			case T__69:
			case T__80:
			case T__81:
			case T__85:
			case T__88:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
			case T__94:
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
			case T__102:
			case T__104:
			case T__105:
			case NAME:
				{
				setState(566);
				statement();
				}
				break;
			case T__83:
				{
				setState(567);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(571);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,60,_ctx) ) {
			case 1:
				{
				setState(570);
				match(EOL);
				}
				break;
			}
			setState(574);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__89) {
				{
				setState(573);
				else_part();
				}
			}

			setState(576);
			match(EOL);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class BranchconditionContext extends ParserRuleContext {
		public BranchconditionContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_branchcondition; }
	}

	public final BranchconditionContext branchcondition() throws RecognitionException {
		BranchconditionContext _localctx = new BranchconditionContext(_ctx, getState());
		enterRule(_localctx, 114, RULE_branchcondition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(578);
			_la = _input.LA(1);
			if ( !(((((_la - 91)) & ~0x3f) == 0 && ((1L << (_la - 91)) & ((1L << (T__90 - 91)) | (1L << (T__91 - 91)) | (1L << (T__92 - 91)) | (1L << (T__93 - 91)) | (1L << (T__94 - 91)) | (1L << (T__95 - 91)) | (1L << (T__96 - 91)) | (1L << (T__97 - 91)) | (1L << (T__98 - 91)) | (1L << (T__99 - 91)) | (1L << (T__100 - 91)) | (1L << (T__101 - 91)))) != 0)) ) {
			_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class ForloopContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public ForloopContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forloop; }
	}

	public final ForloopContext forloop() throws RecognitionException {
		ForloopContext _localctx = new ForloopContext(_ctx, getState());
		enterRule(_localctx, 116, RULE_forloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(580);
			match(T__102);
			setState(582);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) {
				{
				setState(581);
				datatype();
				}
			}

			setState(586);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__67:
			case T__68:
			case T__69:
				{
				setState(584);
				register();
				}
				break;
			case NAME:
				{
				setState(585);
				identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(588);
			match(T__103);
			setState(589);
			expression(0);
			setState(591);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(590);
				match(EOL);
				}
			}

			setState(593);
			statement_block();
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class WhileloopContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public WhileloopContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whileloop; }
	}

	public final WhileloopContext whileloop() throws RecognitionException {
		WhileloopContext _localctx = new WhileloopContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_whileloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(595);
			match(T__104);
			setState(596);
			expression(0);
			setState(598);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(597);
				match(EOL);
				}
			}

			setState(602);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__3:
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__24:
			case T__25:
			case T__63:
			case T__64:
			case T__65:
			case T__67:
			case T__68:
			case T__69:
			case T__80:
			case T__81:
			case T__85:
			case T__88:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
			case T__94:
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
			case T__102:
			case T__104:
			case T__105:
			case NAME:
				{
				setState(600);
				statement();
				}
				break;
			case T__83:
				{
				setState(601);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public static class RepeatloopContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public RepeatloopContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_repeatloop; }
	}

	public final RepeatloopContext repeatloop() throws RecognitionException {
		RepeatloopContext _localctx = new RepeatloopContext(_ctx, getState());
		enterRule(_localctx, 120, RULE_repeatloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(604);
			match(T__105);
			setState(607);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__2:
			case T__3:
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__24:
			case T__25:
			case T__63:
			case T__64:
			case T__65:
			case T__67:
			case T__68:
			case T__69:
			case T__80:
			case T__81:
			case T__85:
			case T__88:
			case T__90:
			case T__91:
			case T__92:
			case T__93:
			case T__94:
			case T__95:
			case T__96:
			case T__97:
			case T__98:
			case T__99:
			case T__100:
			case T__101:
			case T__102:
			case T__104:
			case T__105:
			case NAME:
				{
				setState(605);
				statement();
				}
				break;
			case T__83:
				{
				setState(606);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(610);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(609);
				match(EOL);
				}
			}

			setState(612);
			match(T__106);
			setState(613);
			expression(0);
			}
		}
		catch (RecognitionException re) {
			_localctx.exception = re;
			_errHandler.reportError(this, re);
			_errHandler.recover(this, re);
		}
		finally {
			exitRule();
		}
		return _localctx;
	}

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 19:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 18);
		case 1:
			return precpred(_ctx, 17);
		case 2:
			return precpred(_ctx, 16);
		case 3:
			return precpred(_ctx, 15);
		case 4:
			return precpred(_ctx, 14);
		case 5:
			return precpred(_ctx, 13);
		case 6:
			return precpred(_ctx, 12);
		case 7:
			return precpred(_ctx, 11);
		case 8:
			return precpred(_ctx, 9);
		case 9:
			return precpred(_ctx, 8);
		case 10:
			return precpred(_ctx, 7);
		case 11:
			return precpred(_ctx, 10);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3y\u026a\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\3\2\3\2\7\2\177\n\2\f\2\16\2\u0082\13\2\3\2\3\2\3\3\3\3\5\3\u0088"+
		"\n\3\3\4\3\4\3\4\5\4\u008d\n\4\3\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5"+
		"\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\u00a8"+
		"\n\5\3\6\3\6\3\6\3\7\3\7\3\7\3\7\5\7\u00b1\n\7\3\b\3\b\5\b\u00b5\n\b\3"+
		"\b\3\b\3\b\7\b\u00ba\n\b\f\b\16\b\u00bd\13\b\5\b\u00bf\n\b\3\t\3\t\3\t"+
		"\5\t\u00c4\n\t\3\n\3\n\5\n\u00c8\n\n\3\n\3\n\3\13\3\13\5\13\u00ce\n\13"+
		"\3\13\3\13\3\13\3\13\3\f\3\f\3\f\3\r\3\r\3\r\3\16\3\16\3\17\3\17\3\17"+
		"\3\17\3\20\3\20\3\20\3\20\3\21\3\21\3\21\7\21\u00e7\n\21\f\21\16\21\u00ea"+
		"\13\21\3\22\3\22\3\22\3\22\3\23\3\23\3\23\3\23\5\23\u00f4\n\23\3\24\3"+
		"\24\3\24\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3"+
		"\25\3\25\3\25\5\25\u0108\n\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\5\25\u0130\n\25\7\25\u0132\n\25\f\25\16\25\u0135\13\25\3\26"+
		"\3\26\5\26\u0139\n\26\3\26\3\26\3\27\3\27\5\27\u013f\n\27\3\27\3\27\5"+
		"\27\u0143\n\27\3\27\3\27\3\30\3\30\5\30\u0149\n\30\3\30\3\30\5\30\u014d"+
		"\n\30\3\30\3\30\3\31\3\31\3\31\5\31\u0154\n\31\3\31\7\31\u0157\n\31\f"+
		"\31\16\31\u015a\13\31\3\32\3\32\5\32\u015e\n\32\3\33\3\33\3\34\3\34\3"+
		"\35\3\35\3\36\3\36\3\36\6\36\u0169\n\36\r\36\16\36\u016a\3\37\3\37\3 "+
		"\3 \3!\3!\3\"\3\"\5\"\u0175\n\"\3#\3#\3$\3$\3%\3%\5%\u017d\n%\3%\3%\3"+
		"%\5%\u0182\n%\3%\7%\u0185\n%\f%\16%\u0188\13%\3%\5%\u018b\n%\3%\3%\3&"+
		"\3&\3\'\3\'\3(\3(\3)\3)\3)\3)\3)\3)\5)\u019b\n)\3*\3*\3*\3+\3+\3+\3+\5"+
		"+\u01a4\n+\3+\3+\5+\u01a8\n+\3+\3+\3+\3,\3,\3,\3-\3-\3-\3-\7-\u01b4\n"+
		"-\f-\16-\u01b7\13-\3-\3-\3.\3.\3.\5.\u01be\n.\3.\7.\u01c1\n.\f.\16.\u01c4"+
		"\13.\3/\3/\3/\3/\3\60\3\60\3\60\5\60\u01cd\n\60\3\60\7\60\u01d0\n\60\f"+
		"\60\16\60\u01d3\13\60\3\61\3\61\3\61\3\61\5\61\u01d9\n\61\3\61\3\61\3"+
		"\61\3\61\3\61\5\61\u01e0\n\61\3\61\3\61\3\61\3\61\5\61\u01e6\n\61\3\61"+
		"\3\61\3\61\5\61\u01eb\n\61\3\62\3\62\3\62\3\63\3\63\3\63\5\63\u01f3\n"+
		"\63\3\63\7\63\u01f6\n\63\f\63\16\63\u01f9\13\63\3\64\3\64\3\64\3\64\3"+
		"\64\3\64\5\64\u0201\n\64\3\65\3\65\3\65\7\65\u0206\n\65\f\65\16\65\u0209"+
		"\13\65\3\66\3\66\3\66\5\66\u020e\n\66\3\66\7\66\u0211\n\66\f\66\16\66"+
		"\u0214\13\66\3\67\3\67\3\67\3\67\5\67\u021a\n\67\38\38\38\58\u021f\n8"+
		"\38\38\58\u0223\n8\38\58\u0226\n8\38\58\u0229\n8\38\38\39\39\59\u022f"+
		"\n9\39\39\59\u0233\n9\3:\3:\5:\u0237\n:\3:\3:\5:\u023b\n:\3:\5:\u023e"+
		"\n:\3:\5:\u0241\n:\3:\3:\3;\3;\3<\3<\5<\u0249\n<\3<\3<\5<\u024d\n<\3<"+
		"\3<\3<\5<\u0252\n<\3<\3<\3=\3=\3=\5=\u0259\n=\3=\3=\5=\u025d\n=\3>\3>"+
		"\3>\5>\u0262\n>\3>\5>\u0265\n>\3>\3>\3>\3>\2\3(?\2\4\6\b\n\f\16\20\22"+
		"\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJLNPRTVXZ\\^`bdfhjlnp"+
		"rtvxz\2\21\3\2\6\17\3\2\24\34\3\2\37\'\3\2()\4\2\3\3,-\3\2/\62\3\2,-\3"+
		"\2\63\66\3\2\678\3\2FH\3\2FK\3\2LO\3\2su\3\2QR\3\2]h\2\u029e\2\u0080\3"+
		"\2\2\2\4\u0087\3\2\2\2\6\u0089\3\2\2\2\b\u00a7\3\2\2\2\n\u00a9\3\2\2\2"+
		"\f\u00ac\3\2\2\2\16\u00b2\3\2\2\2\20\u00c3\3\2\2\2\22\u00c5\3\2\2\2\24"+
		"\u00cb\3\2\2\2\26\u00d3\3\2\2\2\30\u00d6\3\2\2\2\32\u00d9\3\2\2\2\34\u00db"+
		"\3\2\2\2\36\u00df\3\2\2\2 \u00e3\3\2\2\2\"\u00eb\3\2\2\2$\u00f3\3\2\2"+
		"\2&\u00f5\3\2\2\2(\u0107\3\2\2\2*\u0138\3\2\2\2,\u013e\3\2\2\2.\u0148"+
		"\3\2\2\2\60\u0150\3\2\2\2\62\u015b\3\2\2\2\64\u015f\3\2\2\2\66\u0161\3"+
		"\2\2\28\u0163\3\2\2\2:\u0165\3\2\2\2<\u016c\3\2\2\2>\u016e\3\2\2\2@\u0170"+
		"\3\2\2\2B\u0172\3\2\2\2D\u0176\3\2\2\2F\u0178\3\2\2\2H\u017a\3\2\2\2J"+
		"\u018e\3\2\2\2L\u0190\3\2\2\2N\u0192\3\2\2\2P\u019a\3\2\2\2R\u019c\3\2"+
		"\2\2T\u019f\3\2\2\2V\u01ac\3\2\2\2X\u01af\3\2\2\2Z\u01ba\3\2\2\2\\\u01c5"+
		"\3\2\2\2^\u01c9\3\2\2\2`\u01d4\3\2\2\2b\u01ec\3\2\2\2d\u01ef\3\2\2\2f"+
		"\u01fa\3\2\2\2h\u0202\3\2\2\2j\u020a\3\2\2\2l\u0215\3\2\2\2n\u021b\3\2"+
		"\2\2p\u022c\3\2\2\2r\u0234\3\2\2\2t\u0244\3\2\2\2v\u0246\3\2\2\2x\u0255"+
		"\3\2\2\2z\u025e\3\2\2\2|\177\5\4\3\2}\177\7q\2\2~|\3\2\2\2~}\3\2\2\2\177"+
		"\u0082\3\2\2\2\u0080~\3\2\2\2\u0080\u0081\3\2\2\2\u0081\u0083\3\2\2\2"+
		"\u0082\u0080\3\2\2\2\u0083\u0084\7\2\2\3\u0084\3\3\2\2\2\u0085\u0088\5"+
		"\16\b\2\u0086\u0088\5\6\4\2\u0087\u0085\3\2\2\2\u0087\u0086\3\2\2\2\u0088"+
		"\5\3\2\2\2\u0089\u008a\7\3\2\2\u008a\u008c\58\35\2\u008b\u008d\5B\"\2"+
		"\u008c\u008b\3\2\2\2\u008c\u008d\3\2\2\2\u008d\u008e\3\2\2\2\u008e\u008f"+
		"\5X-\2\u008f\u0090\7q\2\2\u0090\7\3\2\2\2\u0091\u00a8\5\16\b\2\u0092\u00a8"+
		"\5\24\13\2\u0093\u00a8\5\22\n\2\u0094\u00a8\5\26\f\2\u0095\u00a8\5\30"+
		"\r\2\u0096\u00a8\5\36\20\2\u0097\u00a8\5\"\22\2\u0098\u00a8\5\f\7\2\u0099"+
		"\u00a8\5&\24\2\u009a\u00a8\5.\30\2\u009b\u00a8\5n8\2\u009c\u00a8\5r:\2"+
		"\u009d\u00a8\5T+\2\u009e\u00a8\5`\61\2\u009f\u00a8\5R*\2\u00a0\u00a8\5"+
		"\62\32\2\u00a1\u00a8\5v<\2\u00a2\u00a8\5x=\2\u00a3\u00a8\5z>\2\u00a4\u00a8"+
		"\5\64\33\2\u00a5\u00a8\5\66\34\2\u00a6\u00a8\5\n\6\2\u00a7\u0091\3\2\2"+
		"\2\u00a7\u0092\3\2\2\2\u00a7\u0093\3\2\2\2\u00a7\u0094\3\2\2\2\u00a7\u0095"+
		"\3\2\2\2\u00a7\u0096\3\2\2\2\u00a7\u0097\3\2\2\2\u00a7\u0098\3\2\2\2\u00a7"+
		"\u0099\3\2\2\2\u00a7\u009a\3\2\2\2\u00a7\u009b\3\2\2\2\u00a7\u009c\3\2"+
		"\2\2\u00a7\u009d\3\2\2\2\u00a7\u009e\3\2\2\2\u00a7\u009f\3\2\2\2\u00a7"+
		"\u00a0\3\2\2\2\u00a7\u00a1\3\2\2\2\u00a7\u00a2\3\2\2\2\u00a7\u00a3\3\2"+
		"\2\2\u00a7\u00a4\3\2\2\2\u00a7\u00a5\3\2\2\2\u00a7\u00a6\3\2\2\2\u00a8"+
		"\t\3\2\2\2\u00a9\u00aa\58\35\2\u00aa\u00ab\7\4\2\2\u00ab\13\3\2\2\2\u00ac"+
		"\u00b0\7\5\2\2\u00ad\u00b1\5B\"\2\u00ae\u00b1\58\35\2\u00af\u00b1\5:\36"+
		"\2\u00b0\u00ad\3\2\2\2\u00b0\u00ae\3\2\2\2\u00b0\u00af\3\2\2\2\u00b1\r"+
		"\3\2\2\2\u00b2\u00be\t\2\2\2\u00b3\u00b5\5\20\t\2\u00b4\u00b3\3\2\2\2"+
		"\u00b4\u00b5\3\2\2\2\u00b5\u00bf\3\2\2\2\u00b6\u00bb\5\20\t\2\u00b7\u00b8"+
		"\7\20\2\2\u00b8\u00ba\5\20\t\2\u00b9\u00b7\3\2\2\2\u00ba\u00bd\3\2\2\2"+
		"\u00bb\u00b9\3\2\2\2\u00bb\u00bc\3\2\2\2\u00bc\u00bf\3\2\2\2\u00bd\u00bb"+
		"\3\2\2\2\u00be\u00b4\3\2\2\2\u00be\u00b6\3\2\2\2\u00bf\17\3\2\2\2\u00c0"+
		"\u00c4\5J&\2\u00c1\u00c4\58\35\2\u00c2\u00c4\5B\"\2\u00c3\u00c0\3\2\2"+
		"\2\u00c3\u00c1\3\2\2\2\u00c3\u00c2\3\2\2\2\u00c4\21\3\2\2\2\u00c5\u00c7"+
		"\5\32\16\2\u00c6\u00c8\5\34\17\2\u00c7\u00c6\3\2\2\2\u00c7\u00c8\3\2\2"+
		"\2\u00c8\u00c9\3\2\2\2\u00c9\u00ca\58\35\2\u00ca\23\3\2\2\2\u00cb\u00cd"+
		"\5\32\16\2\u00cc\u00ce\5\34\17\2\u00cd\u00cc\3\2\2\2\u00cd\u00ce\3\2\2"+
		"\2\u00ce\u00cf\3\2\2\2\u00cf\u00d0\58\35\2\u00d0\u00d1\7\21\2\2\u00d1"+
		"\u00d2\5(\25\2\u00d2\25\3\2\2\2\u00d3\u00d4\7\22\2\2\u00d4\u00d5\5\24"+
		"\13\2\u00d5\27\3\2\2\2\u00d6\u00d7\7\23\2\2\u00d7\u00d8\5\24\13\2\u00d8"+
		"\31\3\2\2\2\u00d9\u00da\t\3\2\2\u00da\33\3\2\2\2\u00db\u00dc\7\35\2\2"+
		"\u00dc\u00dd\5(\25\2\u00dd\u00de\7\36\2\2\u00de\35\3\2\2\2\u00df\u00e0"+
		"\5 \21\2\u00e0\u00e1\7\21\2\2\u00e1\u00e2\5(\25\2\u00e2\37\3\2\2\2\u00e3"+
		"\u00e8\5$\23\2\u00e4\u00e5\7\20\2\2\u00e5\u00e7\5$\23\2\u00e6\u00e4\3"+
		"\2\2\2\u00e7\u00ea\3\2\2\2\u00e8\u00e6\3\2\2\2\u00e8\u00e9\3\2\2\2\u00e9"+
		"!\3\2\2\2\u00ea\u00e8\3\2\2\2\u00eb\u00ec\5$\23\2\u00ec\u00ed\t\4\2\2"+
		"\u00ed\u00ee\5(\25\2\u00ee#\3\2\2\2\u00ef\u00f4\5<\37\2\u00f0\u00f4\5"+
		"8\35\2\u00f1\u00f4\5:\36\2\u00f2\u00f4\5*\26\2\u00f3\u00ef\3\2\2\2\u00f3"+
		"\u00f0\3\2\2\2\u00f3\u00f1\3\2\2\2\u00f3\u00f2\3\2\2\2\u00f4%\3\2\2\2"+
		"\u00f5\u00f6\5$\23\2\u00f6\u00f7\t\5\2\2\u00f7\'\3\2\2\2\u00f8\u00f9\b"+
		"\25\1\2\u00f9\u00fa\7*\2\2\u00fa\u00fb\5(\25\2\u00fb\u00fc\7+\2\2\u00fc"+
		"\u0108\3\2\2\2\u00fd\u0108\5,\27\2\u00fe\u00ff\t\6\2\2\u00ff\u0108\5("+
		"\25\25\u0100\u0101\7A\2\2\u0101\u0108\5(\25\b\u0102\u0108\5P)\2\u0103"+
		"\u0108\5<\37\2\u0104\u0108\58\35\2\u0105\u0108\5:\36\2\u0106\u0108\5*"+
		"\26\2\u0107\u00f8\3\2\2\2\u0107\u00fd\3\2\2\2\u0107\u00fe\3\2\2\2\u0107"+
		"\u0100\3\2\2\2\u0107\u0102\3\2\2\2\u0107\u0103\3\2\2\2\u0107\u0104\3\2"+
		"\2\2\u0107\u0105\3\2\2\2\u0107\u0106\3\2\2\2\u0108\u0133\3\2\2\2\u0109"+
		"\u010a\f\24\2\2\u010a\u010b\7.\2\2\u010b\u0132\5(\25\25\u010c\u010d\f"+
		"\23\2\2\u010d\u010e\t\7\2\2\u010e\u0132\5(\25\24\u010f\u0110\f\22\2\2"+
		"\u0110\u0111\t\b\2\2\u0111\u0132\5(\25\23\u0112\u0113\f\21\2\2\u0113\u0114"+
		"\t\t\2\2\u0114\u0132\5(\25\22\u0115\u0116\f\20\2\2\u0116\u0117\t\n\2\2"+
		"\u0117\u0132\5(\25\21\u0118\u0119\f\17\2\2\u0119\u011a\79\2\2\u011a\u0132"+
		"\5(\25\20\u011b\u011c\f\16\2\2\u011c\u011d\7:\2\2\u011d\u0132\5(\25\17"+
		"\u011e\u011f\f\r\2\2\u011f\u0120\7;\2\2\u0120\u0132\5(\25\16\u0121\u0122"+
		"\f\13\2\2\u0122\u0123\7>\2\2\u0123\u0132\5(\25\f\u0124\u0125\f\n\2\2\u0125"+
		"\u0126\7?\2\2\u0126\u0132\5(\25\13\u0127\u0128\f\t\2\2\u0128\u0129\7@"+
		"\2\2\u0129\u0132\5(\25\n\u012a\u012b\f\f\2\2\u012b\u012c\7<\2\2\u012c"+
		"\u012f\5(\25\2\u012d\u012e\7=\2\2\u012e\u0130\5(\25\2\u012f\u012d\3\2"+
		"\2\2\u012f\u0130\3\2\2\2\u0130\u0132\3\2\2\2\u0131\u0109\3\2\2\2\u0131"+
		"\u010c\3\2\2\2\u0131\u010f\3\2\2\2\u0131\u0112\3\2\2\2\u0131\u0115\3\2"+
		"\2\2\u0131\u0118\3\2\2\2\u0131\u011b\3\2\2\2\u0131\u011e\3\2\2\2\u0131"+
		"\u0121\3\2\2\2\u0131\u0124\3\2\2\2\u0131\u0127\3\2\2\2\u0131\u012a\3\2"+
		"\2\2\u0132\u0135\3\2\2\2\u0133\u0131\3\2\2\2\u0133\u0134\3\2\2\2\u0134"+
		")\3\2\2\2\u0135\u0133\3\2\2\2\u0136\u0139\58\35\2\u0137\u0139\5:\36\2"+
		"\u0138\u0136\3\2\2\2\u0138\u0137\3\2\2\2\u0139\u013a\3\2\2\2\u013a\u013b"+
		"\5\34\17\2\u013b+\3\2\2\2\u013c\u013f\58\35\2\u013d\u013f\5:\36\2\u013e"+
		"\u013c\3\2\2\2\u013e\u013d\3\2\2\2\u013f\u0140\3\2\2\2\u0140\u0142\7*"+
		"\2\2\u0141\u0143\5\60\31\2\u0142\u0141\3\2\2\2\u0142\u0143\3\2\2\2\u0143"+
		"\u0144\3\2\2\2\u0144\u0145\7+\2\2\u0145-\3\2\2\2\u0146\u0149\58\35\2\u0147"+
		"\u0149\5:\36\2\u0148\u0146\3\2\2\2\u0148\u0147\3\2\2\2\u0149\u014a\3\2"+
		"\2\2\u014a\u014c\7*\2\2\u014b\u014d\5\60\31\2\u014c\u014b\3\2\2\2\u014c"+
		"\u014d\3\2\2\2\u014d\u014e\3\2\2\2\u014e\u014f\7+\2\2\u014f/\3\2\2\2\u0150"+
		"\u0158\5(\25\2\u0151\u0153\7\20\2\2\u0152\u0154\7q\2\2\u0153\u0152\3\2"+
		"\2\2\u0153\u0154\3\2\2\2\u0154\u0155\3\2\2\2\u0155\u0157\5(\25\2\u0156"+
		"\u0151\3\2\2\2\u0157\u015a\3\2\2\2\u0158\u0156\3\2\2\2\u0158\u0159\3\2"+
		"\2\2\u0159\61\3\2\2\2\u015a\u0158\3\2\2\2\u015b\u015d\7B\2\2\u015c\u015e"+
		"\5\60\31\2\u015d\u015c\3\2\2\2\u015d\u015e\3\2\2\2\u015e\63\3\2\2\2\u015f"+
		"\u0160\7C\2\2\u0160\65\3\2\2\2\u0161\u0162\7D\2\2\u0162\67\3\2\2\2\u0163"+
		"\u0164\7r\2\2\u01649\3\2\2\2\u0165\u0168\7r\2\2\u0166\u0167\7E\2\2\u0167"+
		"\u0169\7r\2\2\u0168\u0166\3\2\2\2\u0169\u016a\3\2\2\2\u016a\u0168\3\2"+
		"\2\2\u016a\u016b\3\2\2\2\u016b;\3\2\2\2\u016c\u016d\t\13\2\2\u016d=\3"+
		"\2\2\2\u016e\u016f\t\f\2\2\u016f?\3\2\2\2\u0170\u0171\t\r\2\2\u0171A\3"+
		"\2\2\2\u0172\u0174\t\16\2\2\u0173\u0175\5D#\2\u0174\u0173\3\2\2\2\u0174"+
		"\u0175\3\2\2\2\u0175C\3\2\2\2\u0176\u0177\7P\2\2\u0177E\3\2\2\2\u0178"+
		"\u0179\t\17\2\2\u0179G\3\2\2\2\u017a\u017c\7\35\2\2\u017b\u017d\7q\2\2"+
		"\u017c\u017b\3\2\2\2\u017c\u017d\3\2\2\2\u017d\u017e\3\2\2\2\u017e\u0186"+
		"\5(\25\2\u017f\u0181\7\20\2\2\u0180\u0182\7q\2\2\u0181\u0180\3\2\2\2\u0181"+
		"\u0182\3\2\2\2\u0182\u0183\3\2\2\2\u0183\u0185\5(\25\2\u0184\u017f\3\2"+
		"\2\2\u0185\u0188\3\2\2\2\u0186\u0184\3\2\2\2\u0186\u0187\3\2\2\2\u0187"+
		"\u018a\3\2\2\2\u0188\u0186\3\2\2\2\u0189\u018b\7q\2\2\u018a\u0189\3\2"+
		"\2\2\u018a\u018b\3\2\2\2\u018b\u018c\3\2\2\2\u018c\u018d\7\36\2\2\u018d"+
		"I\3\2\2\2\u018e\u018f\7w\2\2\u018fK\3\2\2\2\u0190\u0191\7y\2\2\u0191M"+
		"\3\2\2\2\u0192\u0193\7v\2\2\u0193O\3\2\2\2\u0194\u019b\5B\"\2\u0195\u019b"+
		"\5F$\2\u0196\u019b\5H%\2\u0197\u019b\5J&\2\u0198\u019b\5L\'\2\u0199\u019b"+
		"\5N(\2\u019a\u0194\3\2\2\2\u019a\u0195\3\2\2\2\u019a\u0196\3\2\2\2\u019a"+
		"\u0197\3\2\2\2\u019a\u0198\3\2\2\2\u019a\u0199\3\2\2\2\u019bQ\3\2\2\2"+
		"\u019c\u019d\7S\2\2\u019d\u019e\7x\2\2\u019eS\3\2\2\2\u019f\u01a0\7T\2"+
		"\2\u01a0\u01a1\58\35\2\u01a1\u01a3\7*\2\2\u01a2\u01a4\5Z.\2\u01a3\u01a2"+
		"\3\2\2\2\u01a3\u01a4\3\2\2\2\u01a4\u01a5\3\2\2\2\u01a5\u01a7\7+\2\2\u01a6"+
		"\u01a8\5V,\2\u01a7\u01a6\3\2\2\2\u01a7\u01a8\3\2\2\2\u01a8\u01a9\3\2\2"+
		"\2\u01a9\u01aa\5X-\2\u01aa\u01ab\7q\2\2\u01abU\3\2\2\2\u01ac\u01ad\7U"+
		"\2\2\u01ad\u01ae\5^\60\2\u01aeW\3\2\2\2\u01af\u01b0\7V\2\2\u01b0\u01b5"+
		"\7q\2\2\u01b1\u01b4\5\b\5\2\u01b2\u01b4\7q\2\2\u01b3\u01b1\3\2\2\2\u01b3"+
		"\u01b2\3\2\2\2\u01b4\u01b7\3\2\2\2\u01b5\u01b3\3\2\2\2\u01b5\u01b6\3\2"+
		"\2\2\u01b6\u01b8\3\2\2\2\u01b7\u01b5\3\2\2\2\u01b8\u01b9\7W\2\2\u01b9"+
		"Y\3\2\2\2\u01ba\u01c2\5\\/\2\u01bb\u01bd\7\20\2\2\u01bc\u01be\7q\2\2\u01bd"+
		"\u01bc\3\2\2\2\u01bd\u01be\3\2\2\2\u01be\u01bf\3\2\2\2\u01bf\u01c1\5\\"+
		"/\2\u01c0\u01bb\3\2\2\2\u01c1\u01c4\3\2\2\2\u01c2\u01c0\3\2\2\2\u01c2"+
		"\u01c3\3\2\2\2\u01c3[\3\2\2\2\u01c4\u01c2\3\2\2\2\u01c5\u01c6\58\35\2"+
		"\u01c6\u01c7\7\4\2\2\u01c7\u01c8\5\32\16\2\u01c8]\3\2\2\2\u01c9\u01d1"+
		"\5\32\16\2\u01ca\u01cc\7\20\2\2\u01cb\u01cd\7q\2\2\u01cc\u01cb\3\2\2\2"+
		"\u01cc\u01cd\3\2\2\2\u01cd\u01ce\3\2\2\2\u01ce\u01d0\5\32\16\2\u01cf\u01ca"+
		"\3\2\2\2\u01d0\u01d3\3\2\2\2\u01d1\u01cf\3\2\2\2\u01d1\u01d2\3\2\2\2\u01d2"+
		"_\3\2\2\2\u01d3\u01d1\3\2\2\2\u01d4\u01d5\7X\2\2\u01d5\u01d6\58\35\2\u01d6"+
		"\u01d8\7*\2\2\u01d7\u01d9\5d\63\2\u01d8\u01d7\3\2\2\2\u01d8\u01d9\3\2"+
		"\2\2\u01d9\u01da\3\2\2\2\u01da\u01db\7+\2\2\u01db\u01dc\7U\2\2\u01dc\u01dd"+
		"\7Y\2\2\u01dd\u01df\7*\2\2\u01de\u01e0\5h\65\2\u01df\u01de\3\2\2\2\u01df"+
		"\u01e0\3\2\2\2\u01e0\u01e1\3\2\2\2\u01e1\u01e2\7+\2\2\u01e2\u01e3\7U\2"+
		"\2\u01e3\u01e5\7*\2\2\u01e4\u01e6\5j\66\2\u01e5\u01e4\3\2\2\2\u01e5\u01e6"+
		"\3\2\2\2\u01e6\u01e7\3\2\2\2\u01e7\u01ea\7+\2\2\u01e8\u01eb\5b\62\2\u01e9"+
		"\u01eb\5X-\2\u01ea\u01e8\3\2\2\2\u01ea\u01e9\3\2\2\2\u01eba\3\2\2\2\u01ec"+
		"\u01ed\7\21\2\2\u01ed\u01ee\5B\"\2\u01eec\3\2\2\2\u01ef\u01f7\5f\64\2"+
		"\u01f0\u01f2\7\20\2\2\u01f1\u01f3\7q\2\2\u01f2\u01f1\3\2\2\2\u01f2\u01f3"+
		"\3\2\2\2\u01f3\u01f4\3\2\2\2\u01f4\u01f6\5f\64\2\u01f5\u01f0\3\2\2\2\u01f6"+
		"\u01f9\3\2\2\2\u01f7\u01f5\3\2\2\2\u01f7\u01f8\3\2\2\2\u01f8e\3\2\2\2"+
		"\u01f9\u01f7\3\2\2\2\u01fa\u01fb\58\35\2\u01fb\u01fc\7\4\2\2\u01fc\u01fd"+
		"\5\32\16\2\u01fd\u0200\7Z\2\2\u01fe\u0201\5> \2\u01ff\u0201\5@!\2\u0200"+
		"\u01fe\3\2\2\2\u0200\u01ff\3\2\2\2\u0201g\3\2\2\2\u0202\u0207\5<\37\2"+
		"\u0203\u0204\7\20\2\2\u0204\u0206\5<\37\2\u0205\u0203\3\2\2\2\u0206\u0209"+
		"\3\2\2\2\u0207\u0205\3\2\2\2\u0207\u0208\3\2\2\2\u0208i\3\2\2\2\u0209"+
		"\u0207\3\2\2\2\u020a\u0212\5l\67\2\u020b\u020d\7\20\2\2\u020c\u020e\7"+
		"q\2\2\u020d\u020c\3\2\2\2\u020d\u020e\3\2\2\2\u020e\u020f\3\2\2\2\u020f"+
		"\u0211\5l\67\2\u0210\u020b\3\2\2\2\u0211\u0214\3\2\2\2\u0212\u0210\3\2"+
		"\2\2\u0212\u0213\3\2\2\2\u0213k\3\2\2\2\u0214\u0212\3\2\2\2\u0215\u0216"+
		"\5\32\16\2\u0216\u0219\7Z\2\2\u0217\u021a\5> \2\u0218\u021a\5@!\2\u0219"+
		"\u0217\3\2\2\2\u0219\u0218\3\2\2\2\u021am\3\2\2\2\u021b\u021c\7[\2\2\u021c"+
		"\u021e\5(\25\2\u021d\u021f\7q\2\2\u021e\u021d\3\2\2\2\u021e\u021f\3\2"+
		"\2\2\u021f\u0222\3\2\2\2\u0220\u0223\5\b\5\2\u0221\u0223\5X-\2\u0222\u0220"+
		"\3\2\2\2\u0222\u0221\3\2\2\2\u0223\u0225\3\2\2\2\u0224\u0226\7q\2\2\u0225"+
		"\u0224\3\2\2\2\u0225\u0226\3\2\2\2\u0226\u0228\3\2\2\2\u0227\u0229\5p"+
		"9\2\u0228\u0227\3\2\2\2\u0228\u0229\3\2\2\2\u0229\u022a\3\2\2\2\u022a"+
		"\u022b\7q\2\2\u022bo\3\2\2\2\u022c\u022e\7\\\2\2\u022d\u022f\7q\2\2\u022e"+
		"\u022d\3\2\2\2\u022e\u022f\3\2\2\2\u022f\u0232\3\2\2\2\u0230\u0233\5\b"+
		"\5\2\u0231\u0233\5X-\2\u0232\u0230\3\2\2\2\u0232\u0231\3\2\2\2\u0233q"+
		"\3\2\2\2\u0234\u0236\5t;\2\u0235\u0237\7q\2\2\u0236\u0235\3\2\2\2\u0236"+
		"\u0237\3\2\2\2\u0237\u023a\3\2\2\2\u0238\u023b\5\b\5\2\u0239\u023b\5X"+
		"-\2\u023a\u0238\3\2\2\2\u023a\u0239\3\2\2\2\u023b\u023d\3\2\2\2\u023c"+
		"\u023e\7q\2\2\u023d\u023c\3\2\2\2\u023d\u023e\3\2\2\2\u023e\u0240\3\2"+
		"\2\2\u023f\u0241\5p9\2\u0240\u023f\3\2\2\2\u0240\u0241\3\2\2\2\u0241\u0242"+
		"\3\2\2\2\u0242\u0243\7q\2\2\u0243s\3\2\2\2\u0244\u0245\t\20\2\2\u0245"+
		"u\3\2\2\2\u0246\u0248\7i\2\2\u0247\u0249\5\32\16\2\u0248\u0247\3\2\2\2"+
		"\u0248\u0249\3\2\2\2\u0249\u024c\3\2\2\2\u024a\u024d\5<\37\2\u024b\u024d"+
		"\58\35\2\u024c\u024a\3\2\2\2\u024c\u024b\3\2\2\2\u024d\u024e\3\2\2\2\u024e"+
		"\u024f\7j\2\2\u024f\u0251\5(\25\2\u0250\u0252\7q\2\2\u0251\u0250\3\2\2"+
		"\2\u0251\u0252\3\2\2\2\u0252\u0253\3\2\2\2\u0253\u0254\5X-\2\u0254w\3"+
		"\2\2\2\u0255\u0256\7k\2\2\u0256\u0258\5(\25\2\u0257\u0259\7q\2\2\u0258"+
		"\u0257\3\2\2\2\u0258\u0259\3\2\2\2\u0259\u025c\3\2\2\2\u025a\u025d\5\b"+
		"\5\2\u025b\u025d\5X-\2\u025c\u025a\3\2\2\2\u025c\u025b\3\2\2\2\u025dy"+
		"\3\2\2\2\u025e\u0261\7l\2\2\u025f\u0262\5\b\5\2\u0260\u0262\5X-\2\u0261"+
		"\u025f\3\2\2\2\u0261\u0260\3\2\2\2\u0262\u0264\3\2\2\2\u0263\u0265\7q"+
		"\2\2\u0264\u0263\3\2\2\2\u0264\u0265\3\2\2\2\u0265\u0266\3\2\2\2\u0266"+
		"\u0267\7m\2\2\u0267\u0268\5(\25\2\u0268{\3\2\2\2G~\u0080\u0087\u008c\u00a7"+
		"\u00b0\u00b4\u00bb\u00be\u00c3\u00c7\u00cd\u00e8\u00f3\u0107\u012f\u0131"+
		"\u0133\u0138\u013e\u0142\u0148\u014c\u0153\u0158\u015d\u016a\u0174\u017c"+
		"\u0181\u0186\u018a\u019a\u01a3\u01a7\u01b3\u01b5\u01bd\u01c2\u01cc\u01d1"+
		"\u01d8\u01df\u01e5\u01ea\u01f2\u01f7\u0200\u0207\u020d\u0212\u0219\u021e"+
		"\u0222\u0225\u0228\u022e\u0232\u0236\u023a\u023d\u0240\u0248\u024c\u0251"+
		"\u0258\u025c\u0261\u0264";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}
