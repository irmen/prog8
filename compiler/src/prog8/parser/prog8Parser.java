// Generated from /home/irmen/Projects/prog8/compiler/antlr/prog8.g4 by ANTLR 4.7
package prog8.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

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
		T__101=102, T__102=103, T__103=104, LINECOMMENT=105, COMMENT=106, WS=107, 
		EOL=108, NAME=109, DEC_INTEGER=110, HEX_INTEGER=111, BIN_INTEGER=112, 
		FLOAT_NUMBER=113, STRING=114, INLINEASMBLOCK=115, SINGLECHAR=116;
	public static final int
		RULE_module = 0, RULE_modulestatement = 1, RULE_block = 2, RULE_statement = 3, 
		RULE_labeldef = 4, RULE_unconditionaljump = 5, RULE_directive = 6, RULE_directivearg = 7, 
		RULE_vardecl = 8, RULE_varinitializer = 9, RULE_constdecl = 10, RULE_memoryvardecl = 11, 
		RULE_datatype = 12, RULE_arrayspec = 13, RULE_assignment = 14, RULE_augassignment = 15, 
		RULE_assign_target = 16, RULE_postincrdecr = 17, RULE_expression = 18, 
		RULE_arrayindexed = 19, RULE_functioncall = 20, RULE_functioncall_stmt = 21, 
		RULE_expression_list = 22, RULE_returnstmt = 23, RULE_breakstmt = 24, 
		RULE_continuestmt = 25, RULE_identifier = 26, RULE_scoped_identifier = 27, 
		RULE_register = 28, RULE_statusregister = 29, RULE_integerliteral = 30, 
		RULE_wordsuffix = 31, RULE_booleanliteral = 32, RULE_arrayliteral = 33, 
		RULE_stringliteral = 34, RULE_charliteral = 35, RULE_floatliteral = 36, 
		RULE_literalvalue = 37, RULE_inlineasm = 38, RULE_subroutine = 39, RULE_sub_return_part = 40, 
		RULE_statement_block = 41, RULE_sub_params = 42, RULE_sub_param = 43, 
		RULE_sub_returns = 44, RULE_asmsubroutine = 45, RULE_asmsub_address = 46, 
		RULE_asmsub_params = 47, RULE_asmsub_param = 48, RULE_clobber = 49, RULE_asmsub_returns = 50, 
		RULE_asmsub_return = 51, RULE_if_stmt = 52, RULE_else_part = 53, RULE_branch_stmt = 54, 
		RULE_branchcondition = 55, RULE_forloop = 56, RULE_whileloop = 57, RULE_repeatloop = 58;
	public static final String[] ruleNames = {
		"module", "modulestatement", "block", "statement", "labeldef", "unconditionaljump", 
		"directive", "directivearg", "vardecl", "varinitializer", "constdecl", 
		"memoryvardecl", "datatype", "arrayspec", "assignment", "augassignment", 
		"assign_target", "postincrdecr", "expression", "arrayindexed", "functioncall", 
		"functioncall_stmt", "expression_list", "returnstmt", "breakstmt", "continuestmt", 
		"identifier", "scoped_identifier", "register", "statusregister", "integerliteral", 
		"wordsuffix", "booleanliteral", "arrayliteral", "stringliteral", "charliteral", 
		"floatliteral", "literalvalue", "inlineasm", "subroutine", "sub_return_part", 
		"statement_block", "sub_params", "sub_param", "sub_returns", "asmsubroutine", 
		"asmsub_address", "asmsub_params", "asmsub_param", "clobber", "asmsub_returns", 
		"asmsub_return", "if_stmt", "else_part", "branch_stmt", "branchcondition", 
		"forloop", "whileloop", "repeatloop"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'~'", "':'", "'goto'", "'%output'", "'%launcher'", "'%zeropage'", 
		"'%address'", "'%import'", "'%breakpoint'", "'%asminclude'", "'%asmbinary'", 
		"'%option'", "','", "'='", "'const'", "'memory'", "'byte'", "'word'", 
		"'float'", "'str'", "'str_p'", "'str_s'", "'str_ps'", "'['", "']'", "'+='", 
		"'-='", "'/='", "'//='", "'*='", "'**='", "'&='", "'|='", "'^='", "'++'", 
		"'--'", "'('", "')'", "'+'", "'-'", "'**'", "'*'", "'/'", "'//'", "'%'", 
		"'<'", "'>'", "'<='", "'>='", "'=='", "'!='", "'&'", "'^'", "'|'", "'to'", 
		"'step'", "'and'", "'or'", "'xor'", "'not'", "'return'", "'break'", "'continue'", 
		"'.'", "'A'", "'X'", "'Y'", "'AX'", "'AY'", "'XY'", "'Pc'", "'Pz'", "'Pn'", 
		"'Pv'", "'.w'", "'true'", "'false'", "'%asm'", "'sub'", "'->'", "'{'", 
		"'}'", "'asmsub'", "'clobbers'", "'@'", "'if'", "'else'", "'if_cs'", "'if_cc'", 
		"'if_eq'", "'if_z'", "'if_ne'", "'if_nz'", "'if_pl'", "'if_pos'", "'if_mi'", 
		"'if_neg'", "'if_vs'", "'if_vc'", "'for'", "'in'", "'while'", "'repeat'", 
		"'until'"
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
		null, null, null, null, null, null, null, null, null, "LINECOMMENT", "COMMENT", 
		"WS", "EOL", "NAME", "DEC_INTEGER", "HEX_INTEGER", "BIN_INTEGER", "FLOAT_NUMBER", 
		"STRING", "INLINEASMBLOCK", "SINGLECHAR"
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
			setState(122);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11))) != 0) || _la==EOL) {
				{
				setState(120);
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
					{
					setState(118);
					modulestatement();
					}
					break;
				case EOL:
					{
					setState(119);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(124);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(125);
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
			setState(129);
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
				enterOuterAlt(_localctx, 1);
				{
				setState(127);
				directive();
				}
				break;
			case T__0:
				enterOuterAlt(_localctx, 2);
				{
				setState(128);
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
			setState(131);
			match(T__0);
			setState(132);
			identifier();
			setState(134);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 110)) & ~0x3f) == 0 && ((1L << (_la - 110)) & ((1L << (DEC_INTEGER - 110)) | (1L << (HEX_INTEGER - 110)) | (1L << (BIN_INTEGER - 110)))) != 0)) {
				{
				setState(133);
				integerliteral();
				}
			}

			setState(136);
			statement_block();
			setState(137);
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
			setState(161);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,4,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(139);
				directive();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(140);
				varinitializer();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(141);
				vardecl();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(142);
				constdecl();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(143);
				memoryvardecl();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(144);
				assignment();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(145);
				augassignment();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(146);
				unconditionaljump();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(147);
				postincrdecr();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(148);
				functioncall_stmt();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(149);
				if_stmt();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(150);
				branch_stmt();
				}
				break;
			case 13:
				enterOuterAlt(_localctx, 13);
				{
				setState(151);
				subroutine();
				}
				break;
			case 14:
				enterOuterAlt(_localctx, 14);
				{
				setState(152);
				asmsubroutine();
				}
				break;
			case 15:
				enterOuterAlt(_localctx, 15);
				{
				setState(153);
				inlineasm();
				}
				break;
			case 16:
				enterOuterAlt(_localctx, 16);
				{
				setState(154);
				returnstmt();
				}
				break;
			case 17:
				enterOuterAlt(_localctx, 17);
				{
				setState(155);
				forloop();
				}
				break;
			case 18:
				enterOuterAlt(_localctx, 18);
				{
				setState(156);
				whileloop();
				}
				break;
			case 19:
				enterOuterAlt(_localctx, 19);
				{
				setState(157);
				repeatloop();
				}
				break;
			case 20:
				enterOuterAlt(_localctx, 20);
				{
				setState(158);
				breakstmt();
				}
				break;
			case 21:
				enterOuterAlt(_localctx, 21);
				{
				setState(159);
				continuestmt();
				}
				break;
			case 22:
				enterOuterAlt(_localctx, 22);
				{
				setState(160);
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
			setState(163);
			identifier();
			setState(164);
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
			setState(166);
			match(T__2);
			setState(170);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,5,_ctx) ) {
			case 1:
				{
				setState(167);
				integerliteral();
				}
				break;
			case 2:
				{
				setState(168);
				identifier();
				}
				break;
			case 3:
				{
				setState(169);
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
			setState(172);
			((DirectiveContext)_localctx).directivename = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11))) != 0)) ) {
				((DirectiveContext)_localctx).directivename = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(184);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				{
				setState(174);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
				case 1:
					{
					setState(173);
					directivearg();
					}
					break;
				}
				}
				break;
			case 2:
				{
				setState(176);
				directivearg();
				setState(181);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__12) {
					{
					{
					setState(177);
					match(T__12);
					setState(178);
					directivearg();
					}
					}
					setState(183);
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
			setState(189);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(186);
				stringliteral();
				}
				break;
			case NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(187);
				identifier();
				}
				break;
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(188);
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
			setState(191);
			datatype();
			setState(193);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__23) {
				{
				setState(192);
				arrayspec();
				}
			}

			setState(195);
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
			setState(197);
			datatype();
			setState(199);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__23) {
				{
				setState(198);
				arrayspec();
				}
			}

			setState(201);
			identifier();
			setState(202);
			match(T__13);
			setState(203);
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
			setState(205);
			match(T__14);
			setState(206);
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
			setState(208);
			match(T__15);
			setState(209);
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
			setState(211);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22))) != 0)) ) {
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
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public ArrayspecContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayspec; }
	}

	public final ArrayspecContext arrayspec() throws RecognitionException {
		ArrayspecContext _localctx = new ArrayspecContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_arrayspec);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(213);
			match(T__23);
			setState(214);
			expression(0);
			setState(217);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__12) {
				{
				setState(215);
				match(T__12);
				setState(216);
				expression(0);
				}
			}

			setState(219);
			match(T__24);
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
		public Assign_targetContext assign_target() {
			return getRuleContext(Assign_targetContext.class,0);
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
			assign_target();
			setState(222);
			match(T__13);
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
		enterRule(_localctx, 30, RULE_augassignment);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(225);
			assign_target();
			setState(226);
			((AugassignmentContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__25) | (1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33))) != 0)) ) {
				((AugassignmentContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(227);
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
		public Assign_targetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assign_target; }
	}

	public final Assign_targetContext assign_target() throws RecognitionException {
		Assign_targetContext _localctx = new Assign_targetContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_assign_target);
		try {
			setState(232);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,13,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(229);
				register();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(230);
				identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(231);
				scoped_identifier();
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
		enterRule(_localctx, 34, RULE_postincrdecr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(234);
			assign_target();
			setState(235);
			((PostincrdecrContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==T__34 || _la==T__35) ) {
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
		int _startState = 36;
		enterRecursionRule(_localctx, 36, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(252);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,14,_ctx) ) {
			case 1:
				{
				setState(238);
				match(T__36);
				setState(239);
				expression(0);
				setState(240);
				match(T__37);
				}
				break;
			case 2:
				{
				setState(242);
				functioncall();
				}
				break;
			case 3:
				{
				setState(243);
				((ExpressionContext)_localctx).prefix = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__38) | (1L << T__39))) != 0)) ) {
					((ExpressionContext)_localctx).prefix = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(244);
				expression(19);
				}
				break;
			case 4:
				{
				setState(245);
				((ExpressionContext)_localctx).prefix = match(T__59);
				setState(246);
				expression(6);
				}
				break;
			case 5:
				{
				setState(247);
				literalvalue();
				}
				break;
			case 6:
				{
				setState(248);
				register();
				}
				break;
			case 7:
				{
				setState(249);
				identifier();
				}
				break;
			case 8:
				{
				setState(250);
				scoped_identifier();
				}
				break;
			case 9:
				{
				setState(251);
				arrayindexed();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(296);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(294);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(254);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(255);
						((ExpressionContext)_localctx).bop = match(T__40);
						setState(256);
						((ExpressionContext)_localctx).right = expression(19);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(257);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(258);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__41) | (1L << T__42) | (1L << T__43) | (1L << T__44))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(259);
						((ExpressionContext)_localctx).right = expression(18);
						}
						break;
					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(260);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
						setState(261);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__38 || _la==T__39) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(262);
						((ExpressionContext)_localctx).right = expression(17);
						}
						break;
					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(263);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(264);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__45) | (1L << T__46) | (1L << T__47) | (1L << T__48))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(265);
						((ExpressionContext)_localctx).right = expression(16);
						}
						break;
					case 5:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(266);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(267);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__49 || _la==T__50) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(268);
						((ExpressionContext)_localctx).right = expression(15);
						}
						break;
					case 6:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(269);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(270);
						((ExpressionContext)_localctx).bop = match(T__51);
						setState(271);
						((ExpressionContext)_localctx).right = expression(14);
						}
						break;
					case 7:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(272);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(273);
						((ExpressionContext)_localctx).bop = match(T__52);
						setState(274);
						((ExpressionContext)_localctx).right = expression(13);
						}
						break;
					case 8:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(275);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(276);
						((ExpressionContext)_localctx).bop = match(T__53);
						setState(277);
						((ExpressionContext)_localctx).right = expression(12);
						}
						break;
					case 9:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(278);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(279);
						((ExpressionContext)_localctx).bop = match(T__56);
						setState(280);
						((ExpressionContext)_localctx).right = expression(10);
						}
						break;
					case 10:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(281);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(282);
						((ExpressionContext)_localctx).bop = match(T__57);
						setState(283);
						((ExpressionContext)_localctx).right = expression(9);
						}
						break;
					case 11:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(284);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(285);
						((ExpressionContext)_localctx).bop = match(T__58);
						setState(286);
						((ExpressionContext)_localctx).right = expression(8);
						}
						break;
					case 12:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.rangefrom = _prevctx;
						_localctx.rangefrom = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(287);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(288);
						match(T__54);
						setState(289);
						((ExpressionContext)_localctx).rangeto = expression(0);
						setState(292);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
						case 1:
							{
							setState(290);
							match(T__55);
							setState(291);
							((ExpressionContext)_localctx).rangestep = expression(0);
							}
							break;
						}
						}
						break;
					}
					} 
				}
				setState(298);
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
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
		}
		public ArrayindexedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayindexed; }
	}

	public final ArrayindexedContext arrayindexed() throws RecognitionException {
		ArrayindexedContext _localctx = new ArrayindexedContext(_ctx, getState());
		enterRule(_localctx, 38, RULE_arrayindexed);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(302);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				{
				setState(299);
				identifier();
				}
				break;
			case 2:
				{
				setState(300);
				scoped_identifier();
				}
				break;
			case 3:
				{
				setState(301);
				register();
				}
				break;
			}
			setState(304);
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
		enterRule(_localctx, 40, RULE_functioncall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(308);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				{
				setState(306);
				identifier();
				}
				break;
			case 2:
				{
				setState(307);
				scoped_identifier();
				}
				break;
			}
			setState(310);
			match(T__36);
			setState(312);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__23) | (1L << T__36) | (1L << T__38) | (1L << T__39) | (1L << T__59))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (T__64 - 65)) | (1L << (T__65 - 65)) | (1L << (T__66 - 65)) | (1L << (T__67 - 65)) | (1L << (T__68 - 65)) | (1L << (T__69 - 65)) | (1L << (T__75 - 65)) | (1L << (T__76 - 65)) | (1L << (NAME - 65)) | (1L << (DEC_INTEGER - 65)) | (1L << (HEX_INTEGER - 65)) | (1L << (BIN_INTEGER - 65)) | (1L << (FLOAT_NUMBER - 65)) | (1L << (STRING - 65)) | (1L << (SINGLECHAR - 65)))) != 0)) {
				{
				setState(311);
				expression_list();
				}
			}

			setState(314);
			match(T__37);
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
		enterRule(_localctx, 42, RULE_functioncall_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(318);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				{
				setState(316);
				identifier();
				}
				break;
			case 2:
				{
				setState(317);
				scoped_identifier();
				}
				break;
			}
			setState(320);
			match(T__36);
			setState(322);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__23) | (1L << T__36) | (1L << T__38) | (1L << T__39) | (1L << T__59))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (T__64 - 65)) | (1L << (T__65 - 65)) | (1L << (T__66 - 65)) | (1L << (T__67 - 65)) | (1L << (T__68 - 65)) | (1L << (T__69 - 65)) | (1L << (T__75 - 65)) | (1L << (T__76 - 65)) | (1L << (NAME - 65)) | (1L << (DEC_INTEGER - 65)) | (1L << (HEX_INTEGER - 65)) | (1L << (BIN_INTEGER - 65)) | (1L << (FLOAT_NUMBER - 65)) | (1L << (STRING - 65)) | (1L << (SINGLECHAR - 65)))) != 0)) {
				{
				setState(321);
				expression_list();
				}
			}

			setState(324);
			match(T__37);
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
		public Expression_listContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_expression_list; }
	}

	public final Expression_listContext expression_list() throws RecognitionException {
		Expression_listContext _localctx = new Expression_listContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_expression_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(326);
			expression(0);
			setState(331);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(327);
				match(T__12);
				setState(328);
				expression(0);
				}
				}
				setState(333);
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
		enterRule(_localctx, 46, RULE_returnstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(334);
			match(T__60);
			setState(336);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,24,_ctx) ) {
			case 1:
				{
				setState(335);
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
		enterRule(_localctx, 48, RULE_breakstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(338);
			match(T__61);
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
		enterRule(_localctx, 50, RULE_continuestmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(340);
			match(T__62);
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
		enterRule(_localctx, 52, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(342);
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
		enterRule(_localctx, 54, RULE_scoped_identifier);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(344);
			match(NAME);
			setState(347); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(345);
					match(T__63);
					setState(346);
					match(NAME);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(349); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,25,_ctx);
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
		enterRule(_localctx, 56, RULE_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(351);
			_la = _input.LA(1);
			if ( !(((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (T__64 - 65)) | (1L << (T__65 - 65)) | (1L << (T__66 - 65)) | (1L << (T__67 - 65)) | (1L << (T__68 - 65)) | (1L << (T__69 - 65)))) != 0)) ) {
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
		enterRule(_localctx, 58, RULE_statusregister);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(353);
			_la = _input.LA(1);
			if ( !(((((_la - 71)) & ~0x3f) == 0 && ((1L << (_la - 71)) & ((1L << (T__70 - 71)) | (1L << (T__71 - 71)) | (1L << (T__72 - 71)) | (1L << (T__73 - 71)))) != 0)) ) {
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
		enterRule(_localctx, 60, RULE_integerliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(355);
			((IntegerliteralContext)_localctx).intpart = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 110)) & ~0x3f) == 0 && ((1L << (_la - 110)) & ((1L << (DEC_INTEGER - 110)) | (1L << (HEX_INTEGER - 110)) | (1L << (BIN_INTEGER - 110)))) != 0)) ) {
				((IntegerliteralContext)_localctx).intpart = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(357);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,26,_ctx) ) {
			case 1:
				{
				setState(356);
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
		enterRule(_localctx, 62, RULE_wordsuffix);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(359);
			match(T__74);
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
		enterRule(_localctx, 64, RULE_booleanliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(361);
			_la = _input.LA(1);
			if ( !(_la==T__75 || _la==T__76) ) {
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
		public ArrayliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayliteral; }
	}

	public final ArrayliteralContext arrayliteral() throws RecognitionException {
		ArrayliteralContext _localctx = new ArrayliteralContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_arrayliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(363);
			match(T__23);
			setState(364);
			expression(0);
			setState(369);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(365);
				match(T__12);
				setState(366);
				expression(0);
				}
				}
				setState(371);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(372);
			match(T__24);
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
		enterRule(_localctx, 68, RULE_stringliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(374);
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
		enterRule(_localctx, 70, RULE_charliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(376);
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
		enterRule(_localctx, 72, RULE_floatliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(378);
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
		enterRule(_localctx, 74, RULE_literalvalue);
		try {
			setState(386);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 1);
				{
				setState(380);
				integerliteral();
				}
				break;
			case T__75:
			case T__76:
				enterOuterAlt(_localctx, 2);
				{
				setState(381);
				booleanliteral();
				}
				break;
			case T__23:
				enterOuterAlt(_localctx, 3);
				{
				setState(382);
				arrayliteral();
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 4);
				{
				setState(383);
				stringliteral();
				}
				break;
			case SINGLECHAR:
				enterOuterAlt(_localctx, 5);
				{
				setState(384);
				charliteral();
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 6);
				{
				setState(385);
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
		enterRule(_localctx, 76, RULE_inlineasm);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(388);
			match(T__77);
			setState(389);
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
		enterRule(_localctx, 78, RULE_subroutine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(391);
			match(T__78);
			setState(392);
			identifier();
			setState(393);
			match(T__36);
			setState(395);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(394);
				sub_params();
				}
			}

			setState(397);
			match(T__37);
			setState(399);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__79) {
				{
				setState(398);
				sub_return_part();
				}
			}

			{
			setState(401);
			statement_block();
			setState(402);
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
		enterRule(_localctx, 80, RULE_sub_return_part);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(404);
			match(T__79);
			setState(405);
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
		enterRule(_localctx, 82, RULE_statement_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(407);
			match(T__80);
			setState(408);
			match(EOL);
			setState(413);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__14) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__60) | (1L << T__61) | (1L << T__62))) != 0) || ((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (T__64 - 65)) | (1L << (T__65 - 65)) | (1L << (T__66 - 65)) | (1L << (T__67 - 65)) | (1L << (T__68 - 65)) | (1L << (T__69 - 65)) | (1L << (T__77 - 65)) | (1L << (T__78 - 65)) | (1L << (T__82 - 65)) | (1L << (T__85 - 65)) | (1L << (T__87 - 65)) | (1L << (T__88 - 65)) | (1L << (T__89 - 65)) | (1L << (T__90 - 65)) | (1L << (T__91 - 65)) | (1L << (T__92 - 65)) | (1L << (T__93 - 65)) | (1L << (T__94 - 65)) | (1L << (T__95 - 65)) | (1L << (T__96 - 65)) | (1L << (T__97 - 65)) | (1L << (T__98 - 65)) | (1L << (T__99 - 65)) | (1L << (T__101 - 65)) | (1L << (T__102 - 65)) | (1L << (EOL - 65)) | (1L << (NAME - 65)))) != 0)) {
				{
				setState(411);
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
				case T__14:
				case T__15:
				case T__16:
				case T__17:
				case T__18:
				case T__19:
				case T__20:
				case T__21:
				case T__22:
				case T__60:
				case T__61:
				case T__62:
				case T__64:
				case T__65:
				case T__66:
				case T__67:
				case T__68:
				case T__69:
				case T__77:
				case T__78:
				case T__82:
				case T__85:
				case T__87:
				case T__88:
				case T__89:
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
				case T__101:
				case T__102:
				case NAME:
					{
					setState(409);
					statement();
					}
					break;
				case EOL:
					{
					setState(410);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(415);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(416);
			match(T__81);
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
		public Sub_paramsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sub_params; }
	}

	public final Sub_paramsContext sub_params() throws RecognitionException {
		Sub_paramsContext _localctx = new Sub_paramsContext(_ctx, getState());
		enterRule(_localctx, 84, RULE_sub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(418);
			sub_param();
			setState(423);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(419);
				match(T__12);
				setState(420);
				sub_param();
				}
				}
				setState(425);
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
		enterRule(_localctx, 86, RULE_sub_param);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(426);
			identifier();
			setState(427);
			match(T__1);
			setState(428);
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
		public Sub_returnsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_sub_returns; }
	}

	public final Sub_returnsContext sub_returns() throws RecognitionException {
		Sub_returnsContext _localctx = new Sub_returnsContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_sub_returns);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(430);
			datatype();
			setState(435);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(431);
				match(T__12);
				setState(432);
				datatype();
				}
				}
				setState(437);
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
		enterRule(_localctx, 90, RULE_asmsubroutine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(438);
			match(T__82);
			setState(439);
			identifier();
			setState(440);
			match(T__36);
			setState(442);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==NAME) {
				{
				setState(441);
				asmsub_params();
				}
			}

			setState(444);
			match(T__37);
			setState(445);
			match(T__79);
			setState(446);
			match(T__83);
			setState(447);
			match(T__36);
			setState(449);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 65)) & ~0x3f) == 0 && ((1L << (_la - 65)) & ((1L << (T__64 - 65)) | (1L << (T__65 - 65)) | (1L << (T__66 - 65)) | (1L << (T__67 - 65)) | (1L << (T__68 - 65)) | (1L << (T__69 - 65)))) != 0)) {
				{
				setState(448);
				clobber();
				}
			}

			setState(451);
			match(T__37);
			setState(452);
			match(T__79);
			setState(453);
			match(T__36);
			setState(455);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22))) != 0)) {
				{
				setState(454);
				asmsub_returns();
				}
			}

			setState(457);
			match(T__37);
			setState(460);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__13:
				{
				setState(458);
				asmsub_address();
				}
				break;
			case T__80:
				{
				setState(459);
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
		enterRule(_localctx, 92, RULE_asmsub_address);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(462);
			match(T__13);
			setState(463);
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
		public Asmsub_paramsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_params; }
	}

	public final Asmsub_paramsContext asmsub_params() throws RecognitionException {
		Asmsub_paramsContext _localctx = new Asmsub_paramsContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_asmsub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(465);
			asmsub_param();
			setState(470);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(466);
				match(T__12);
				setState(467);
				asmsub_param();
				}
				}
				setState(472);
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
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
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
		enterRule(_localctx, 96, RULE_asmsub_param);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(473);
			identifier();
			setState(474);
			match(T__1);
			setState(475);
			datatype();
			setState(476);
			match(T__84);
			setState(479);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
				{
				setState(477);
				register();
				}
				break;
			case T__70:
			case T__71:
			case T__72:
			case T__73:
				{
				setState(478);
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
		enterRule(_localctx, 98, RULE_clobber);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(481);
			register();
			setState(486);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(482);
				match(T__12);
				setState(483);
				register();
				}
				}
				setState(488);
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
		public Asmsub_returnsContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_returns; }
	}

	public final Asmsub_returnsContext asmsub_returns() throws RecognitionException {
		Asmsub_returnsContext _localctx = new Asmsub_returnsContext(_ctx, getState());
		enterRule(_localctx, 100, RULE_asmsub_returns);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(489);
			asmsub_return();
			setState(494);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__12) {
				{
				{
				setState(490);
				match(T__12);
				setState(491);
				asmsub_return();
				}
				}
				setState(496);
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
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
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
		enterRule(_localctx, 102, RULE_asmsub_return);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(497);
			datatype();
			setState(498);
			match(T__84);
			setState(501);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
				{
				setState(499);
				register();
				}
				break;
			case T__70:
			case T__71:
			case T__72:
			case T__73:
				{
				setState(500);
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
		enterRule(_localctx, 104, RULE_if_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(503);
			match(T__85);
			setState(504);
			expression(0);
			setState(506);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(505);
				match(EOL);
				}
			}

			setState(510);
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
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__60:
			case T__61:
			case T__62:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__77:
			case T__78:
			case T__82:
			case T__85:
			case T__87:
			case T__88:
			case T__89:
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
			case T__101:
			case T__102:
			case NAME:
				{
				setState(508);
				statement();
				}
				break;
			case T__80:
				{
				setState(509);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(513);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,46,_ctx) ) {
			case 1:
				{
				setState(512);
				match(EOL);
				}
				break;
			}
			setState(516);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__86) {
				{
				setState(515);
				else_part();
				}
			}

			setState(518);
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
		enterRule(_localctx, 106, RULE_else_part);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(520);
			match(T__86);
			setState(522);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(521);
				match(EOL);
				}
			}

			setState(526);
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
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__60:
			case T__61:
			case T__62:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__77:
			case T__78:
			case T__82:
			case T__85:
			case T__87:
			case T__88:
			case T__89:
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
			case T__101:
			case T__102:
			case NAME:
				{
				setState(524);
				statement();
				}
				break;
			case T__80:
				{
				setState(525);
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
		enterRule(_localctx, 108, RULE_branch_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(528);
			branchcondition();
			setState(530);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(529);
				match(EOL);
				}
			}

			setState(534);
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
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__60:
			case T__61:
			case T__62:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__77:
			case T__78:
			case T__82:
			case T__85:
			case T__87:
			case T__88:
			case T__89:
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
			case T__101:
			case T__102:
			case NAME:
				{
				setState(532);
				statement();
				}
				break;
			case T__80:
				{
				setState(533);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(537);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
			case 1:
				{
				setState(536);
				match(EOL);
				}
				break;
			}
			setState(540);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__86) {
				{
				setState(539);
				else_part();
				}
			}

			setState(542);
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
		enterRule(_localctx, 110, RULE_branchcondition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(544);
			_la = _input.LA(1);
			if ( !(((((_la - 88)) & ~0x3f) == 0 && ((1L << (_la - 88)) & ((1L << (T__87 - 88)) | (1L << (T__88 - 88)) | (1L << (T__89 - 88)) | (1L << (T__90 - 88)) | (1L << (T__91 - 88)) | (1L << (T__92 - 88)) | (1L << (T__93 - 88)) | (1L << (T__94 - 88)) | (1L << (T__95 - 88)) | (1L << (T__96 - 88)) | (1L << (T__97 - 88)) | (1L << (T__98 - 88)))) != 0)) ) {
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
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public ForloopContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forloop; }
	}

	public final ForloopContext forloop() throws RecognitionException {
		ForloopContext _localctx = new ForloopContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_forloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(546);
			match(T__99);
			setState(549);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
				{
				setState(547);
				register();
				}
				break;
			case NAME:
				{
				setState(548);
				identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(551);
			match(T__100);
			setState(552);
			expression(0);
			setState(554);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(553);
				match(EOL);
				}
			}

			setState(556);
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
		enterRule(_localctx, 114, RULE_whileloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(558);
			match(T__101);
			setState(559);
			expression(0);
			setState(561);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(560);
				match(EOL);
				}
			}

			setState(565);
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
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__60:
			case T__61:
			case T__62:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__77:
			case T__78:
			case T__82:
			case T__85:
			case T__87:
			case T__88:
			case T__89:
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
			case T__101:
			case T__102:
			case NAME:
				{
				setState(563);
				statement();
				}
				break;
			case T__80:
				{
				setState(564);
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
		enterRule(_localctx, 116, RULE_repeatloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(567);
			match(T__102);
			setState(570);
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
			case T__14:
			case T__15:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__60:
			case T__61:
			case T__62:
			case T__64:
			case T__65:
			case T__66:
			case T__67:
			case T__68:
			case T__69:
			case T__77:
			case T__78:
			case T__82:
			case T__85:
			case T__87:
			case T__88:
			case T__89:
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
			case T__101:
			case T__102:
			case NAME:
				{
				setState(568);
				statement();
				}
				break;
			case T__80:
				{
				setState(569);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(573);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(572);
				match(EOL);
				}
			}

			setState(575);
			match(T__103);
			setState(576);
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
		case 18:
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
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3v\u0245\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\3\2\3"+
		"\2\7\2{\n\2\f\2\16\2~\13\2\3\2\3\2\3\3\3\3\5\3\u0084\n\3\3\4\3\4\3\4\5"+
		"\4\u0089\n\4\3\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3"+
		"\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\u00a4\n\5\3\6\3\6\3\6\3"+
		"\7\3\7\3\7\3\7\5\7\u00ad\n\7\3\b\3\b\5\b\u00b1\n\b\3\b\3\b\3\b\7\b\u00b6"+
		"\n\b\f\b\16\b\u00b9\13\b\5\b\u00bb\n\b\3\t\3\t\3\t\5\t\u00c0\n\t\3\n\3"+
		"\n\5\n\u00c4\n\n\3\n\3\n\3\13\3\13\5\13\u00ca\n\13\3\13\3\13\3\13\3\13"+
		"\3\f\3\f\3\f\3\r\3\r\3\r\3\16\3\16\3\17\3\17\3\17\3\17\5\17\u00dc\n\17"+
		"\3\17\3\17\3\20\3\20\3\20\3\20\3\21\3\21\3\21\3\21\3\22\3\22\3\22\5\22"+
		"\u00eb\n\22\3\23\3\23\3\23\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\3\24\3\24\3\24\5\24\u00ff\n\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24\3\24"+
		"\3\24\3\24\3\24\3\24\3\24\5\24\u0127\n\24\7\24\u0129\n\24\f\24\16\24\u012c"+
		"\13\24\3\25\3\25\3\25\5\25\u0131\n\25\3\25\3\25\3\26\3\26\5\26\u0137\n"+
		"\26\3\26\3\26\5\26\u013b\n\26\3\26\3\26\3\27\3\27\5\27\u0141\n\27\3\27"+
		"\3\27\5\27\u0145\n\27\3\27\3\27\3\30\3\30\3\30\7\30\u014c\n\30\f\30\16"+
		"\30\u014f\13\30\3\31\3\31\5\31\u0153\n\31\3\32\3\32\3\33\3\33\3\34\3\34"+
		"\3\35\3\35\3\35\6\35\u015e\n\35\r\35\16\35\u015f\3\36\3\36\3\37\3\37\3"+
		" \3 \5 \u0168\n \3!\3!\3\"\3\"\3#\3#\3#\3#\7#\u0172\n#\f#\16#\u0175\13"+
		"#\3#\3#\3$\3$\3%\3%\3&\3&\3\'\3\'\3\'\3\'\3\'\3\'\5\'\u0185\n\'\3(\3("+
		"\3(\3)\3)\3)\3)\5)\u018e\n)\3)\3)\5)\u0192\n)\3)\3)\3)\3*\3*\3*\3+\3+"+
		"\3+\3+\7+\u019e\n+\f+\16+\u01a1\13+\3+\3+\3,\3,\3,\7,\u01a8\n,\f,\16,"+
		"\u01ab\13,\3-\3-\3-\3-\3.\3.\3.\7.\u01b4\n.\f.\16.\u01b7\13.\3/\3/\3/"+
		"\3/\5/\u01bd\n/\3/\3/\3/\3/\3/\5/\u01c4\n/\3/\3/\3/\3/\5/\u01ca\n/\3/"+
		"\3/\3/\5/\u01cf\n/\3\60\3\60\3\60\3\61\3\61\3\61\7\61\u01d7\n\61\f\61"+
		"\16\61\u01da\13\61\3\62\3\62\3\62\3\62\3\62\3\62\5\62\u01e2\n\62\3\63"+
		"\3\63\3\63\7\63\u01e7\n\63\f\63\16\63\u01ea\13\63\3\64\3\64\3\64\7\64"+
		"\u01ef\n\64\f\64\16\64\u01f2\13\64\3\65\3\65\3\65\3\65\5\65\u01f8\n\65"+
		"\3\66\3\66\3\66\5\66\u01fd\n\66\3\66\3\66\5\66\u0201\n\66\3\66\5\66\u0204"+
		"\n\66\3\66\5\66\u0207\n\66\3\66\3\66\3\67\3\67\5\67\u020d\n\67\3\67\3"+
		"\67\5\67\u0211\n\67\38\38\58\u0215\n8\38\38\58\u0219\n8\38\58\u021c\n"+
		"8\38\58\u021f\n8\38\38\39\39\3:\3:\3:\5:\u0228\n:\3:\3:\3:\5:\u022d\n"+
		":\3:\3:\3;\3;\3;\5;\u0234\n;\3;\3;\5;\u0238\n;\3<\3<\3<\5<\u023d\n<\3"+
		"<\5<\u0240\n<\3<\3<\3<\3<\2\3&=\2\4\6\b\n\f\16\20\22\24\26\30\32\34\36"+
		" \"$&(*,.\60\62\64\668:<>@BDFHJLNPRTVXZ\\^`bdfhjlnprtv\2\20\3\2\6\16\3"+
		"\2\23\31\3\2\34$\3\2%&\4\2\3\3)*\3\2,/\3\2)*\3\2\60\63\3\2\64\65\3\2C"+
		"H\3\2IL\3\2pr\3\2NO\3\2Ze\2\u0272\2|\3\2\2\2\4\u0083\3\2\2\2\6\u0085\3"+
		"\2\2\2\b\u00a3\3\2\2\2\n\u00a5\3\2\2\2\f\u00a8\3\2\2\2\16\u00ae\3\2\2"+
		"\2\20\u00bf\3\2\2\2\22\u00c1\3\2\2\2\24\u00c7\3\2\2\2\26\u00cf\3\2\2\2"+
		"\30\u00d2\3\2\2\2\32\u00d5\3\2\2\2\34\u00d7\3\2\2\2\36\u00df\3\2\2\2 "+
		"\u00e3\3\2\2\2\"\u00ea\3\2\2\2$\u00ec\3\2\2\2&\u00fe\3\2\2\2(\u0130\3"+
		"\2\2\2*\u0136\3\2\2\2,\u0140\3\2\2\2.\u0148\3\2\2\2\60\u0150\3\2\2\2\62"+
		"\u0154\3\2\2\2\64\u0156\3\2\2\2\66\u0158\3\2\2\28\u015a\3\2\2\2:\u0161"+
		"\3\2\2\2<\u0163\3\2\2\2>\u0165\3\2\2\2@\u0169\3\2\2\2B\u016b\3\2\2\2D"+
		"\u016d\3\2\2\2F\u0178\3\2\2\2H\u017a\3\2\2\2J\u017c\3\2\2\2L\u0184\3\2"+
		"\2\2N\u0186\3\2\2\2P\u0189\3\2\2\2R\u0196\3\2\2\2T\u0199\3\2\2\2V\u01a4"+
		"\3\2\2\2X\u01ac\3\2\2\2Z\u01b0\3\2\2\2\\\u01b8\3\2\2\2^\u01d0\3\2\2\2"+
		"`\u01d3\3\2\2\2b\u01db\3\2\2\2d\u01e3\3\2\2\2f\u01eb\3\2\2\2h\u01f3\3"+
		"\2\2\2j\u01f9\3\2\2\2l\u020a\3\2\2\2n\u0212\3\2\2\2p\u0222\3\2\2\2r\u0224"+
		"\3\2\2\2t\u0230\3\2\2\2v\u0239\3\2\2\2x{\5\4\3\2y{\7n\2\2zx\3\2\2\2zy"+
		"\3\2\2\2{~\3\2\2\2|z\3\2\2\2|}\3\2\2\2}\177\3\2\2\2~|\3\2\2\2\177\u0080"+
		"\7\2\2\3\u0080\3\3\2\2\2\u0081\u0084\5\16\b\2\u0082\u0084\5\6\4\2\u0083"+
		"\u0081\3\2\2\2\u0083\u0082\3\2\2\2\u0084\5\3\2\2\2\u0085\u0086\7\3\2\2"+
		"\u0086\u0088\5\66\34\2\u0087\u0089\5> \2\u0088\u0087\3\2\2\2\u0088\u0089"+
		"\3\2\2\2\u0089\u008a\3\2\2\2\u008a\u008b\5T+\2\u008b\u008c\7n\2\2\u008c"+
		"\7\3\2\2\2\u008d\u00a4\5\16\b\2\u008e\u00a4\5\24\13\2\u008f\u00a4\5\22"+
		"\n\2\u0090\u00a4\5\26\f\2\u0091\u00a4\5\30\r\2\u0092\u00a4\5\36\20\2\u0093"+
		"\u00a4\5 \21\2\u0094\u00a4\5\f\7\2\u0095\u00a4\5$\23\2\u0096\u00a4\5,"+
		"\27\2\u0097\u00a4\5j\66\2\u0098\u00a4\5n8\2\u0099\u00a4\5P)\2\u009a\u00a4"+
		"\5\\/\2\u009b\u00a4\5N(\2\u009c\u00a4\5\60\31\2\u009d\u00a4\5r:\2\u009e"+
		"\u00a4\5t;\2\u009f\u00a4\5v<\2\u00a0\u00a4\5\62\32\2\u00a1\u00a4\5\64"+
		"\33\2\u00a2\u00a4\5\n\6\2\u00a3\u008d\3\2\2\2\u00a3\u008e\3\2\2\2\u00a3"+
		"\u008f\3\2\2\2\u00a3\u0090\3\2\2\2\u00a3\u0091\3\2\2\2\u00a3\u0092\3\2"+
		"\2\2\u00a3\u0093\3\2\2\2\u00a3\u0094\3\2\2\2\u00a3\u0095\3\2\2\2\u00a3"+
		"\u0096\3\2\2\2\u00a3\u0097\3\2\2\2\u00a3\u0098\3\2\2\2\u00a3\u0099\3\2"+
		"\2\2\u00a3\u009a\3\2\2\2\u00a3\u009b\3\2\2\2\u00a3\u009c\3\2\2\2\u00a3"+
		"\u009d\3\2\2\2\u00a3\u009e\3\2\2\2\u00a3\u009f\3\2\2\2\u00a3\u00a0\3\2"+
		"\2\2\u00a3\u00a1\3\2\2\2\u00a3\u00a2\3\2\2\2\u00a4\t\3\2\2\2\u00a5\u00a6"+
		"\5\66\34\2\u00a6\u00a7\7\4\2\2\u00a7\13\3\2\2\2\u00a8\u00ac\7\5\2\2\u00a9"+
		"\u00ad\5> \2\u00aa\u00ad\5\66\34\2\u00ab\u00ad\58\35\2\u00ac\u00a9\3\2"+
		"\2\2\u00ac\u00aa\3\2\2\2\u00ac\u00ab\3\2\2\2\u00ad\r\3\2\2\2\u00ae\u00ba"+
		"\t\2\2\2\u00af\u00b1\5\20\t\2\u00b0\u00af\3\2\2\2\u00b0\u00b1\3\2\2\2"+
		"\u00b1\u00bb\3\2\2\2\u00b2\u00b7\5\20\t\2\u00b3\u00b4\7\17\2\2\u00b4\u00b6"+
		"\5\20\t\2\u00b5\u00b3\3\2\2\2\u00b6\u00b9\3\2\2\2\u00b7\u00b5\3\2\2\2"+
		"\u00b7\u00b8\3\2\2\2\u00b8\u00bb\3\2\2\2\u00b9\u00b7\3\2\2\2\u00ba\u00b0"+
		"\3\2\2\2\u00ba\u00b2\3\2\2\2\u00bb\17\3\2\2\2\u00bc\u00c0\5F$\2\u00bd"+
		"\u00c0\5\66\34\2\u00be\u00c0\5> \2\u00bf\u00bc\3\2\2\2\u00bf\u00bd\3\2"+
		"\2\2\u00bf\u00be\3\2\2\2\u00c0\21\3\2\2\2\u00c1\u00c3\5\32\16\2\u00c2"+
		"\u00c4\5\34\17\2\u00c3\u00c2\3\2\2\2\u00c3\u00c4\3\2\2\2\u00c4\u00c5\3"+
		"\2\2\2\u00c5\u00c6\5\66\34\2\u00c6\23\3\2\2\2\u00c7\u00c9\5\32\16\2\u00c8"+
		"\u00ca\5\34\17\2\u00c9\u00c8\3\2\2\2\u00c9\u00ca\3\2\2\2\u00ca\u00cb\3"+
		"\2\2\2\u00cb\u00cc\5\66\34\2\u00cc\u00cd\7\20\2\2\u00cd\u00ce\5&\24\2"+
		"\u00ce\25\3\2\2\2\u00cf\u00d0\7\21\2\2\u00d0\u00d1\5\24\13\2\u00d1\27"+
		"\3\2\2\2\u00d2\u00d3\7\22\2\2\u00d3\u00d4\5\24\13\2\u00d4\31\3\2\2\2\u00d5"+
		"\u00d6\t\3\2\2\u00d6\33\3\2\2\2\u00d7\u00d8\7\32\2\2\u00d8\u00db\5&\24"+
		"\2\u00d9\u00da\7\17\2\2\u00da\u00dc\5&\24\2\u00db\u00d9\3\2\2\2\u00db"+
		"\u00dc\3\2\2\2\u00dc\u00dd\3\2\2\2\u00dd\u00de\7\33\2\2\u00de\35\3\2\2"+
		"\2\u00df\u00e0\5\"\22\2\u00e0\u00e1\7\20\2\2\u00e1\u00e2\5&\24\2\u00e2"+
		"\37\3\2\2\2\u00e3\u00e4\5\"\22\2\u00e4\u00e5\t\4\2\2\u00e5\u00e6\5&\24"+
		"\2\u00e6!\3\2\2\2\u00e7\u00eb\5:\36\2\u00e8\u00eb\5\66\34\2\u00e9\u00eb"+
		"\58\35\2\u00ea\u00e7\3\2\2\2\u00ea\u00e8\3\2\2\2\u00ea\u00e9\3\2\2\2\u00eb"+
		"#\3\2\2\2\u00ec\u00ed\5\"\22\2\u00ed\u00ee\t\5\2\2\u00ee%\3\2\2\2\u00ef"+
		"\u00f0\b\24\1\2\u00f0\u00f1\7\'\2\2\u00f1\u00f2\5&\24\2\u00f2\u00f3\7"+
		"(\2\2\u00f3\u00ff\3\2\2\2\u00f4\u00ff\5*\26\2\u00f5\u00f6\t\6\2\2\u00f6"+
		"\u00ff\5&\24\25\u00f7\u00f8\7>\2\2\u00f8\u00ff\5&\24\b\u00f9\u00ff\5L"+
		"\'\2\u00fa\u00ff\5:\36\2\u00fb\u00ff\5\66\34\2\u00fc\u00ff\58\35\2\u00fd"+
		"\u00ff\5(\25\2\u00fe\u00ef\3\2\2\2\u00fe\u00f4\3\2\2\2\u00fe\u00f5\3\2"+
		"\2\2\u00fe\u00f7\3\2\2\2\u00fe\u00f9\3\2\2\2\u00fe\u00fa\3\2\2\2\u00fe"+
		"\u00fb\3\2\2\2\u00fe\u00fc\3\2\2\2\u00fe\u00fd\3\2\2\2\u00ff\u012a\3\2"+
		"\2\2\u0100\u0101\f\24\2\2\u0101\u0102\7+\2\2\u0102\u0129\5&\24\25\u0103"+
		"\u0104\f\23\2\2\u0104\u0105\t\7\2\2\u0105\u0129\5&\24\24\u0106\u0107\f"+
		"\22\2\2\u0107\u0108\t\b\2\2\u0108\u0129\5&\24\23\u0109\u010a\f\21\2\2"+
		"\u010a\u010b\t\t\2\2\u010b\u0129\5&\24\22\u010c\u010d\f\20\2\2\u010d\u010e"+
		"\t\n\2\2\u010e\u0129\5&\24\21\u010f\u0110\f\17\2\2\u0110\u0111\7\66\2"+
		"\2\u0111\u0129\5&\24\20\u0112\u0113\f\16\2\2\u0113\u0114\7\67\2\2\u0114"+
		"\u0129\5&\24\17\u0115\u0116\f\r\2\2\u0116\u0117\78\2\2\u0117\u0129\5&"+
		"\24\16\u0118\u0119\f\13\2\2\u0119\u011a\7;\2\2\u011a\u0129\5&\24\f\u011b"+
		"\u011c\f\n\2\2\u011c\u011d\7<\2\2\u011d\u0129\5&\24\13\u011e\u011f\f\t"+
		"\2\2\u011f\u0120\7=\2\2\u0120\u0129\5&\24\n\u0121\u0122\f\f\2\2\u0122"+
		"\u0123\79\2\2\u0123\u0126\5&\24\2\u0124\u0125\7:\2\2\u0125\u0127\5&\24"+
		"\2\u0126\u0124\3\2\2\2\u0126\u0127\3\2\2\2\u0127\u0129\3\2\2\2\u0128\u0100"+
		"\3\2\2\2\u0128\u0103\3\2\2\2\u0128\u0106\3\2\2\2\u0128\u0109\3\2\2\2\u0128"+
		"\u010c\3\2\2\2\u0128\u010f\3\2\2\2\u0128\u0112\3\2\2\2\u0128\u0115\3\2"+
		"\2\2\u0128\u0118\3\2\2\2\u0128\u011b\3\2\2\2\u0128\u011e\3\2\2\2\u0128"+
		"\u0121\3\2\2\2\u0129\u012c\3\2\2\2\u012a\u0128\3\2\2\2\u012a\u012b\3\2"+
		"\2\2\u012b\'\3\2\2\2\u012c\u012a\3\2\2\2\u012d\u0131\5\66\34\2\u012e\u0131"+
		"\58\35\2\u012f\u0131\5:\36\2\u0130\u012d\3\2\2\2\u0130\u012e\3\2\2\2\u0130"+
		"\u012f\3\2\2\2\u0131\u0132\3\2\2\2\u0132\u0133\5\34\17\2\u0133)\3\2\2"+
		"\2\u0134\u0137\5\66\34\2\u0135\u0137\58\35\2\u0136\u0134\3\2\2\2\u0136"+
		"\u0135\3\2\2\2\u0137\u0138\3\2\2\2\u0138\u013a\7\'\2\2\u0139\u013b\5."+
		"\30\2\u013a\u0139\3\2\2\2\u013a\u013b\3\2\2\2\u013b\u013c\3\2\2\2\u013c"+
		"\u013d\7(\2\2\u013d+\3\2\2\2\u013e\u0141\5\66\34\2\u013f\u0141\58\35\2"+
		"\u0140\u013e\3\2\2\2\u0140\u013f\3\2\2\2\u0141\u0142\3\2\2\2\u0142\u0144"+
		"\7\'\2\2\u0143\u0145\5.\30\2\u0144\u0143\3\2\2\2\u0144\u0145\3\2\2\2\u0145"+
		"\u0146\3\2\2\2\u0146\u0147\7(\2\2\u0147-\3\2\2\2\u0148\u014d\5&\24\2\u0149"+
		"\u014a\7\17\2\2\u014a\u014c\5&\24\2\u014b\u0149\3\2\2\2\u014c\u014f\3"+
		"\2\2\2\u014d\u014b\3\2\2\2\u014d\u014e\3\2\2\2\u014e/\3\2\2\2\u014f\u014d"+
		"\3\2\2\2\u0150\u0152\7?\2\2\u0151\u0153\5.\30\2\u0152\u0151\3\2\2\2\u0152"+
		"\u0153\3\2\2\2\u0153\61\3\2\2\2\u0154\u0155\7@\2\2\u0155\63\3\2\2\2\u0156"+
		"\u0157\7A\2\2\u0157\65\3\2\2\2\u0158\u0159\7o\2\2\u0159\67\3\2\2\2\u015a"+
		"\u015d\7o\2\2\u015b\u015c\7B\2\2\u015c\u015e\7o\2\2\u015d\u015b\3\2\2"+
		"\2\u015e\u015f\3\2\2\2\u015f\u015d\3\2\2\2\u015f\u0160\3\2\2\2\u01609"+
		"\3\2\2\2\u0161\u0162\t\13\2\2\u0162;\3\2\2\2\u0163\u0164\t\f\2\2\u0164"+
		"=\3\2\2\2\u0165\u0167\t\r\2\2\u0166\u0168\5@!\2\u0167\u0166\3\2\2\2\u0167"+
		"\u0168\3\2\2\2\u0168?\3\2\2\2\u0169\u016a\7M\2\2\u016aA\3\2\2\2\u016b"+
		"\u016c\t\16\2\2\u016cC\3\2\2\2\u016d\u016e\7\32\2\2\u016e\u0173\5&\24"+
		"\2\u016f\u0170\7\17\2\2\u0170\u0172\5&\24\2\u0171\u016f\3\2\2\2\u0172"+
		"\u0175\3\2\2\2\u0173\u0171\3\2\2\2\u0173\u0174\3\2\2\2\u0174\u0176\3\2"+
		"\2\2\u0175\u0173\3\2\2\2\u0176\u0177\7\33\2\2\u0177E\3\2\2\2\u0178\u0179"+
		"\7t\2\2\u0179G\3\2\2\2\u017a\u017b\7v\2\2\u017bI\3\2\2\2\u017c\u017d\7"+
		"s\2\2\u017dK\3\2\2\2\u017e\u0185\5> \2\u017f\u0185\5B\"\2\u0180\u0185"+
		"\5D#\2\u0181\u0185\5F$\2\u0182\u0185\5H%\2\u0183\u0185\5J&\2\u0184\u017e"+
		"\3\2\2\2\u0184\u017f\3\2\2\2\u0184\u0180\3\2\2\2\u0184\u0181\3\2\2\2\u0184"+
		"\u0182\3\2\2\2\u0184\u0183\3\2\2\2\u0185M\3\2\2\2\u0186\u0187\7P\2\2\u0187"+
		"\u0188\7u\2\2\u0188O\3\2\2\2\u0189\u018a\7Q\2\2\u018a\u018b\5\66\34\2"+
		"\u018b\u018d\7\'\2\2\u018c\u018e\5V,\2\u018d\u018c\3\2\2\2\u018d\u018e"+
		"\3\2\2\2\u018e\u018f\3\2\2\2\u018f\u0191\7(\2\2\u0190\u0192\5R*\2\u0191"+
		"\u0190\3\2\2\2\u0191\u0192\3\2\2\2\u0192\u0193\3\2\2\2\u0193\u0194\5T"+
		"+\2\u0194\u0195\7n\2\2\u0195Q\3\2\2\2\u0196\u0197\7R\2\2\u0197\u0198\5"+
		"Z.\2\u0198S\3\2\2\2\u0199\u019a\7S\2\2\u019a\u019f\7n\2\2\u019b\u019e"+
		"\5\b\5\2\u019c\u019e\7n\2\2\u019d\u019b\3\2\2\2\u019d\u019c\3\2\2\2\u019e"+
		"\u01a1\3\2\2\2\u019f\u019d\3\2\2\2\u019f\u01a0\3\2\2\2\u01a0\u01a2\3\2"+
		"\2\2\u01a1\u019f\3\2\2\2\u01a2\u01a3\7T\2\2\u01a3U\3\2\2\2\u01a4\u01a9"+
		"\5X-\2\u01a5\u01a6\7\17\2\2\u01a6\u01a8\5X-\2\u01a7\u01a5\3\2\2\2\u01a8"+
		"\u01ab\3\2\2\2\u01a9\u01a7\3\2\2\2\u01a9\u01aa\3\2\2\2\u01aaW\3\2\2\2"+
		"\u01ab\u01a9\3\2\2\2\u01ac\u01ad\5\66\34\2\u01ad\u01ae\7\4\2\2\u01ae\u01af"+
		"\5\32\16\2\u01afY\3\2\2\2\u01b0\u01b5\5\32\16\2\u01b1\u01b2\7\17\2\2\u01b2"+
		"\u01b4\5\32\16\2\u01b3\u01b1\3\2\2\2\u01b4\u01b7\3\2\2\2\u01b5\u01b3\3"+
		"\2\2\2\u01b5\u01b6\3\2\2\2\u01b6[\3\2\2\2\u01b7\u01b5\3\2\2\2\u01b8\u01b9"+
		"\7U\2\2\u01b9\u01ba\5\66\34\2\u01ba\u01bc\7\'\2\2\u01bb\u01bd\5`\61\2"+
		"\u01bc\u01bb\3\2\2\2\u01bc\u01bd\3\2\2\2\u01bd\u01be\3\2\2\2\u01be\u01bf"+
		"\7(\2\2\u01bf\u01c0\7R\2\2\u01c0\u01c1\7V\2\2\u01c1\u01c3\7\'\2\2\u01c2"+
		"\u01c4\5d\63\2\u01c3\u01c2\3\2\2\2\u01c3\u01c4\3\2\2\2\u01c4\u01c5\3\2"+
		"\2\2\u01c5\u01c6\7(\2\2\u01c6\u01c7\7R\2\2\u01c7\u01c9\7\'\2\2\u01c8\u01ca"+
		"\5f\64\2\u01c9\u01c8\3\2\2\2\u01c9\u01ca\3\2\2\2\u01ca\u01cb\3\2\2\2\u01cb"+
		"\u01ce\7(\2\2\u01cc\u01cf\5^\60\2\u01cd\u01cf\5T+\2\u01ce\u01cc\3\2\2"+
		"\2\u01ce\u01cd\3\2\2\2\u01cf]\3\2\2\2\u01d0\u01d1\7\20\2\2\u01d1\u01d2"+
		"\5> \2\u01d2_\3\2\2\2\u01d3\u01d8\5b\62\2\u01d4\u01d5\7\17\2\2\u01d5\u01d7"+
		"\5b\62\2\u01d6\u01d4\3\2\2\2\u01d7\u01da\3\2\2\2\u01d8\u01d6\3\2\2\2\u01d8"+
		"\u01d9\3\2\2\2\u01d9a\3\2\2\2\u01da\u01d8\3\2\2\2\u01db\u01dc\5\66\34"+
		"\2\u01dc\u01dd\7\4\2\2\u01dd\u01de\5\32\16\2\u01de\u01e1\7W\2\2\u01df"+
		"\u01e2\5:\36\2\u01e0\u01e2\5<\37\2\u01e1\u01df\3\2\2\2\u01e1\u01e0\3\2"+
		"\2\2\u01e2c\3\2\2\2\u01e3\u01e8\5:\36\2\u01e4\u01e5\7\17\2\2\u01e5\u01e7"+
		"\5:\36\2\u01e6\u01e4\3\2\2\2\u01e7\u01ea\3\2\2\2\u01e8\u01e6\3\2\2\2\u01e8"+
		"\u01e9\3\2\2\2\u01e9e\3\2\2\2\u01ea\u01e8\3\2\2\2\u01eb\u01f0\5h\65\2"+
		"\u01ec\u01ed\7\17\2\2\u01ed\u01ef\5h\65\2\u01ee\u01ec\3\2\2\2\u01ef\u01f2"+
		"\3\2\2\2\u01f0\u01ee\3\2\2\2\u01f0\u01f1\3\2\2\2\u01f1g\3\2\2\2\u01f2"+
		"\u01f0\3\2\2\2\u01f3\u01f4\5\32\16\2\u01f4\u01f7\7W\2\2\u01f5\u01f8\5"+
		":\36\2\u01f6\u01f8\5<\37\2\u01f7\u01f5\3\2\2\2\u01f7\u01f6\3\2\2\2\u01f8"+
		"i\3\2\2\2\u01f9\u01fa\7X\2\2\u01fa\u01fc\5&\24\2\u01fb\u01fd\7n\2\2\u01fc"+
		"\u01fb\3\2\2\2\u01fc\u01fd\3\2\2\2\u01fd\u0200\3\2\2\2\u01fe\u0201\5\b"+
		"\5\2\u01ff\u0201\5T+\2\u0200\u01fe\3\2\2\2\u0200\u01ff\3\2\2\2\u0201\u0203"+
		"\3\2\2\2\u0202\u0204\7n\2\2\u0203\u0202\3\2\2\2\u0203\u0204\3\2\2\2\u0204"+
		"\u0206\3\2\2\2\u0205\u0207\5l\67\2\u0206\u0205\3\2\2\2\u0206\u0207\3\2"+
		"\2\2\u0207\u0208\3\2\2\2\u0208\u0209\7n\2\2\u0209k\3\2\2\2\u020a\u020c"+
		"\7Y\2\2\u020b\u020d\7n\2\2\u020c\u020b\3\2\2\2\u020c\u020d\3\2\2\2\u020d"+
		"\u0210\3\2\2\2\u020e\u0211\5\b\5\2\u020f\u0211\5T+\2\u0210\u020e\3\2\2"+
		"\2\u0210\u020f\3\2\2\2\u0211m\3\2\2\2\u0212\u0214\5p9\2\u0213\u0215\7"+
		"n\2\2\u0214\u0213\3\2\2\2\u0214\u0215\3\2\2\2\u0215\u0218\3\2\2\2\u0216"+
		"\u0219\5\b\5\2\u0217\u0219\5T+\2\u0218\u0216\3\2\2\2\u0218\u0217\3\2\2"+
		"\2\u0219\u021b\3\2\2\2\u021a\u021c\7n\2\2\u021b\u021a\3\2\2\2\u021b\u021c"+
		"\3\2\2\2\u021c\u021e\3\2\2\2\u021d\u021f\5l\67\2\u021e\u021d\3\2\2\2\u021e"+
		"\u021f\3\2\2\2\u021f\u0220\3\2\2\2\u0220\u0221\7n\2\2\u0221o\3\2\2\2\u0222"+
		"\u0223\t\17\2\2\u0223q\3\2\2\2\u0224\u0227\7f\2\2\u0225\u0228\5:\36\2"+
		"\u0226\u0228\5\66\34\2\u0227\u0225\3\2\2\2\u0227\u0226\3\2\2\2\u0228\u0229"+
		"\3\2\2\2\u0229\u022a\7g\2\2\u022a\u022c\5&\24\2\u022b\u022d\7n\2\2\u022c"+
		"\u022b\3\2\2\2\u022c\u022d\3\2\2\2\u022d\u022e\3\2\2\2\u022e\u022f\5T"+
		"+\2\u022fs\3\2\2\2\u0230\u0231\7h\2\2\u0231\u0233\5&\24\2\u0232\u0234"+
		"\7n\2\2\u0233\u0232\3\2\2\2\u0233\u0234\3\2\2\2\u0234\u0237\3\2\2\2\u0235"+
		"\u0238\5\b\5\2\u0236\u0238\5T+\2\u0237\u0235\3\2\2\2\u0237\u0236\3\2\2"+
		"\2\u0238u\3\2\2\2\u0239\u023c\7i\2\2\u023a\u023d\5\b\5\2\u023b\u023d\5"+
		"T+\2\u023c\u023a\3\2\2\2\u023c\u023b\3\2\2\2\u023d\u023f\3\2\2\2\u023e"+
		"\u0240\7n\2\2\u023f\u023e\3\2\2\2\u023f\u0240\3\2\2\2\u0240\u0241\3\2"+
		"\2\2\u0241\u0242\7j\2\2\u0242\u0243\5&\24\2\u0243w\3\2\2\2>z|\u0083\u0088"+
		"\u00a3\u00ac\u00b0\u00b7\u00ba\u00bf\u00c3\u00c9\u00db\u00ea\u00fe\u0126"+
		"\u0128\u012a\u0130\u0136\u013a\u0140\u0144\u014d\u0152\u015f\u0167\u0173"+
		"\u0184\u018d\u0191\u019d\u019f\u01a9\u01b5\u01bc\u01c3\u01c9\u01ce\u01d8"+
		"\u01e1\u01e8\u01f0\u01f7\u01fc\u0200\u0203\u0206\u020c\u0210\u0214\u0218"+
		"\u021b\u021e\u0227\u022c\u0233\u0237\u023c\u023f";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}