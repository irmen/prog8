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
		T__101=102, T__102=103, T__103=104, T__104=105, T__105=106, T__106=107, 
		T__107=108, T__108=109, LINECOMMENT=110, COMMENT=111, WS=112, EOL=113, 
		NAME=114, DEC_INTEGER=115, HEX_INTEGER=116, BIN_INTEGER=117, FLOAT_NUMBER=118, 
		STRING=119, INLINEASMBLOCK=120, SINGLECHAR=121;
	public static final int
		RULE_module = 0, RULE_modulestatement = 1, RULE_block = 2, RULE_statement = 3, 
		RULE_labeldef = 4, RULE_unconditionaljump = 5, RULE_directive = 6, RULE_directivearg = 7, 
		RULE_vardecl = 8, RULE_varinitializer = 9, RULE_constdecl = 10, RULE_memoryvardecl = 11, 
		RULE_datatype = 12, RULE_arrayspec = 13, RULE_assignment = 14, RULE_assign_targets = 15, 
		RULE_augassignment = 16, RULE_assign_target = 17, RULE_postincrdecr = 18, 
		RULE_expression = 19, RULE_typecast = 20, RULE_arrayindexed = 21, RULE_functioncall = 22, 
		RULE_functioncall_stmt = 23, RULE_expression_list = 24, RULE_returnstmt = 25, 
		RULE_breakstmt = 26, RULE_continuestmt = 27, RULE_identifier = 28, RULE_scoped_identifier = 29, 
		RULE_register = 30, RULE_registerorpair = 31, RULE_statusregister = 32, 
		RULE_integerliteral = 33, RULE_wordsuffix = 34, RULE_booleanliteral = 35, 
		RULE_arrayliteral = 36, RULE_stringliteral = 37, RULE_charliteral = 38, 
		RULE_floatliteral = 39, RULE_literalvalue = 40, RULE_inlineasm = 41, RULE_subroutine = 42, 
		RULE_sub_return_part = 43, RULE_statement_block = 44, RULE_sub_params = 45, 
		RULE_sub_returns = 46, RULE_asmsubroutine = 47, RULE_asmsub_address = 48, 
		RULE_asmsub_params = 49, RULE_asmsub_param = 50, RULE_clobber = 51, RULE_asmsub_returns = 52, 
		RULE_asmsub_return = 53, RULE_if_stmt = 54, RULE_else_part = 55, RULE_branch_stmt = 56, 
		RULE_branchcondition = 57, RULE_forloop = 58, RULE_whileloop = 59, RULE_repeatloop = 60;
	public static final String[] ruleNames = {
		"module", "modulestatement", "block", "statement", "labeldef", "unconditionaljump", 
		"directive", "directivearg", "vardecl", "varinitializer", "constdecl", 
		"memoryvardecl", "datatype", "arrayspec", "assignment", "assign_targets", 
		"augassignment", "assign_target", "postincrdecr", "expression", "typecast", 
		"arrayindexed", "functioncall", "functioncall_stmt", "expression_list", 
		"returnstmt", "breakstmt", "continuestmt", "identifier", "scoped_identifier", 
		"register", "registerorpair", "statusregister", "integerliteral", "wordsuffix", 
		"booleanliteral", "arrayliteral", "stringliteral", "charliteral", "floatliteral", 
		"literalvalue", "inlineasm", "subroutine", "sub_return_part", "statement_block", 
		"sub_params", "sub_returns", "asmsubroutine", "asmsub_address", "asmsub_params", 
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
		"'&='", "'|='", "'^='", "'%='", "'++'", "'--'", "'('", "')'", "'+'", "'-'", 
		"'**'", "'*'", "'/'", "'//'", "'%'", "'<'", "'>'", "'<='", "'>='", "'=='", 
		"'!='", "'&'", "'^'", "'|'", "'to'", "'step'", "'and'", "'or'", "'xor'", 
		"'not'", "'as'", "'return'", "'break'", "'continue'", "'.'", "'A'", "'X'", 
		"'Y'", "'AX'", "'AY'", "'XY'", "'Pc'", "'Pz'", "'Pn'", "'Pv'", "'.w'", 
		"'true'", "'false'", "'%asm'", "'sub'", "'->'", "'{'", "'}'", "'asmsub'", 
		"'clobbers'", "'@'", "'if'", "'else'", "'if_cs'", "'if_cc'", "'if_eq'", 
		"'if_z'", "'if_ne'", "'if_nz'", "'if_pl'", "'if_pos'", "'if_mi'", "'if_neg'", 
		"'if_vs'", "'if_vc'", "'for'", "'in'", "'while'", "'repeat'", "'until'"
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
		null, null, "LINECOMMENT", "COMMENT", "WS", "EOL", "NAME", "DEC_INTEGER", 
		"HEX_INTEGER", "BIN_INTEGER", "FLOAT_NUMBER", "STRING", "INLINEASMBLOCK", 
		"SINGLECHAR"
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
			if (((((_la - 115)) & ~0x3f) == 0 && ((1L << (_la - 115)) & ((1L << (DEC_INTEGER - 115)) | (1L << (HEX_INTEGER - 115)) | (1L << (BIN_INTEGER - 115)))) != 0)) {
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
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37))) != 0)) ) {
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
			if ( !(_la==T__38 || _la==T__39) ) {
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
		public TypecastContext typecast() {
			return getRuleContext(TypecastContext.class,0);
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
				match(T__40);
				setState(248);
				expression(0);
				setState(249);
				match(T__41);
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
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__42) | (1L << T__43))) != 0)) ) {
					((ExpressionContext)_localctx).prefix = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(253);
				expression(20);
				}
				break;
			case 4:
				{
				setState(254);
				((ExpressionContext)_localctx).prefix = match(T__63);
				setState(255);
				expression(7);
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
			setState(307);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,17,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(305);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(263);
						if (!(precpred(_ctx, 19))) throw new FailedPredicateException(this, "precpred(_ctx, 19)");
						setState(264);
						((ExpressionContext)_localctx).bop = match(T__44);
						setState(265);
						((ExpressionContext)_localctx).right = expression(20);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(266);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(267);
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
						setState(268);
						((ExpressionContext)_localctx).right = expression(19);
						}
						break;
					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(269);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(270);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__42 || _la==T__43) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(271);
						((ExpressionContext)_localctx).right = expression(18);
						}
						break;
					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(272);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
						setState(273);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__49) | (1L << T__50) | (1L << T__51) | (1L << T__52))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(274);
						((ExpressionContext)_localctx).right = expression(17);
						}
						break;
					case 5:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(275);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(276);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__53 || _la==T__54) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(277);
						((ExpressionContext)_localctx).right = expression(16);
						}
						break;
					case 6:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(278);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(279);
						((ExpressionContext)_localctx).bop = match(T__55);
						setState(280);
						((ExpressionContext)_localctx).right = expression(15);
						}
						break;
					case 7:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(281);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(282);
						((ExpressionContext)_localctx).bop = match(T__56);
						setState(283);
						((ExpressionContext)_localctx).right = expression(14);
						}
						break;
					case 8:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(284);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(285);
						((ExpressionContext)_localctx).bop = match(T__57);
						setState(286);
						((ExpressionContext)_localctx).right = expression(13);
						}
						break;
					case 9:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(287);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(288);
						((ExpressionContext)_localctx).bop = match(T__60);
						setState(289);
						((ExpressionContext)_localctx).right = expression(11);
						}
						break;
					case 10:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(290);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(291);
						((ExpressionContext)_localctx).bop = match(T__61);
						setState(292);
						((ExpressionContext)_localctx).right = expression(10);
						}
						break;
					case 11:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(293);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(294);
						((ExpressionContext)_localctx).bop = match(T__62);
						setState(295);
						((ExpressionContext)_localctx).right = expression(9);
						}
						break;
					case 12:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.rangefrom = _prevctx;
						_localctx.rangefrom = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(296);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(297);
						match(T__58);
						setState(298);
						((ExpressionContext)_localctx).rangeto = expression(0);
						setState(301);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,15,_ctx) ) {
						case 1:
							{
							setState(299);
							match(T__59);
							setState(300);
							((ExpressionContext)_localctx).rangestep = expression(0);
							}
							break;
						}
						}
						break;
					case 13:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(303);
						if (!(precpred(_ctx, 1))) throw new FailedPredicateException(this, "precpred(_ctx, 1)");
						setState(304);
						typecast();
						}
						break;
					}
					} 
				}
				setState(309);
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

	public static class TypecastContext extends ParserRuleContext {
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public TypecastContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_typecast; }
	}

	public final TypecastContext typecast() throws RecognitionException {
		TypecastContext _localctx = new TypecastContext(_ctx, getState());
		enterRule(_localctx, 40, RULE_typecast);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(310);
			match(T__64);
			setState(311);
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
		enterRule(_localctx, 42, RULE_arrayindexed);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(315);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				{
				setState(313);
				identifier();
				}
				break;
			case 2:
				{
				setState(314);
				scoped_identifier();
				}
				break;
			}
			setState(317);
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
		enterRule(_localctx, 44, RULE_functioncall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(321);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				{
				setState(319);
				identifier();
				}
				break;
			case 2:
				{
				setState(320);
				scoped_identifier();
				}
				break;
			}
			setState(323);
			match(T__40);
			setState(325);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__26) | (1L << T__40) | (1L << T__42) | (1L << T__43))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (NAME - 64)) | (1L << (DEC_INTEGER - 64)) | (1L << (HEX_INTEGER - 64)) | (1L << (BIN_INTEGER - 64)) | (1L << (FLOAT_NUMBER - 64)) | (1L << (STRING - 64)) | (1L << (SINGLECHAR - 64)))) != 0)) {
				{
				setState(324);
				expression_list();
				}
			}

			setState(327);
			match(T__41);
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
		enterRule(_localctx, 46, RULE_functioncall_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(331);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,21,_ctx) ) {
			case 1:
				{
				setState(329);
				identifier();
				}
				break;
			case 2:
				{
				setState(330);
				scoped_identifier();
				}
				break;
			}
			setState(333);
			match(T__40);
			setState(335);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__26) | (1L << T__40) | (1L << T__42) | (1L << T__43))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__69 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__80 - 64)) | (1L << (T__81 - 64)) | (1L << (NAME - 64)) | (1L << (DEC_INTEGER - 64)) | (1L << (HEX_INTEGER - 64)) | (1L << (BIN_INTEGER - 64)) | (1L << (FLOAT_NUMBER - 64)) | (1L << (STRING - 64)) | (1L << (SINGLECHAR - 64)))) != 0)) {
				{
				setState(334);
				expression_list();
				}
			}

			setState(337);
			match(T__41);
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
		enterRule(_localctx, 48, RULE_expression_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(339);
			expression(0);
			setState(347);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(340);
				match(T__13);
				setState(342);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(341);
					match(EOL);
					}
				}

				setState(344);
				expression(0);
				}
				}
				setState(349);
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
		enterRule(_localctx, 50, RULE_returnstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(350);
			match(T__65);
			setState(352);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,25,_ctx) ) {
			case 1:
				{
				setState(351);
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
		enterRule(_localctx, 52, RULE_breakstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(354);
			match(T__66);
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
		enterRule(_localctx, 54, RULE_continuestmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(356);
			match(T__67);
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
		enterRule(_localctx, 56, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(358);
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
		enterRule(_localctx, 58, RULE_scoped_identifier);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(360);
			match(NAME);
			setState(363); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(361);
					match(T__68);
					setState(362);
					match(NAME);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(365); 
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
		enterRule(_localctx, 60, RULE_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(367);
			_la = _input.LA(1);
			if ( !(((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & ((1L << (T__69 - 70)) | (1L << (T__70 - 70)) | (1L << (T__71 - 70)))) != 0)) ) {
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
		enterRule(_localctx, 62, RULE_registerorpair);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(369);
			_la = _input.LA(1);
			if ( !(((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & ((1L << (T__69 - 70)) | (1L << (T__70 - 70)) | (1L << (T__71 - 70)) | (1L << (T__72 - 70)) | (1L << (T__73 - 70)) | (1L << (T__74 - 70)))) != 0)) ) {
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
		enterRule(_localctx, 64, RULE_statusregister);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(371);
			_la = _input.LA(1);
			if ( !(((((_la - 76)) & ~0x3f) == 0 && ((1L << (_la - 76)) & ((1L << (T__75 - 76)) | (1L << (T__76 - 76)) | (1L << (T__77 - 76)) | (1L << (T__78 - 76)))) != 0)) ) {
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
		enterRule(_localctx, 66, RULE_integerliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(373);
			((IntegerliteralContext)_localctx).intpart = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 115)) & ~0x3f) == 0 && ((1L << (_la - 115)) & ((1L << (DEC_INTEGER - 115)) | (1L << (HEX_INTEGER - 115)) | (1L << (BIN_INTEGER - 115)))) != 0)) ) {
				((IntegerliteralContext)_localctx).intpart = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(375);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,27,_ctx) ) {
			case 1:
				{
				setState(374);
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
		enterRule(_localctx, 68, RULE_wordsuffix);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(377);
			match(T__79);
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
		enterRule(_localctx, 70, RULE_booleanliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(379);
			_la = _input.LA(1);
			if ( !(_la==T__80 || _la==T__81) ) {
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
		enterRule(_localctx, 72, RULE_arrayliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(381);
			match(T__26);
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
			setState(393);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(386);
				match(T__13);
				setState(388);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(387);
					match(EOL);
					}
				}

				setState(390);
				expression(0);
				}
				}
				setState(395);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(397);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(396);
				match(EOL);
				}
			}

			setState(399);
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
		enterRule(_localctx, 74, RULE_stringliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(401);
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
		enterRule(_localctx, 76, RULE_charliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(403);
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
		enterRule(_localctx, 78, RULE_floatliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(405);
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
		enterRule(_localctx, 80, RULE_literalvalue);
		try {
			setState(413);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 1);
				{
				setState(407);
				integerliteral();
				}
				break;
			case T__80:
			case T__81:
				enterOuterAlt(_localctx, 2);
				{
				setState(408);
				booleanliteral();
				}
				break;
			case T__26:
				enterOuterAlt(_localctx, 3);
				{
				setState(409);
				arrayliteral();
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 4);
				{
				setState(410);
				stringliteral();
				}
				break;
			case SINGLECHAR:
				enterOuterAlt(_localctx, 5);
				{
				setState(411);
				charliteral();
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 6);
				{
				setState(412);
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
		enterRule(_localctx, 82, RULE_inlineasm);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(415);
			match(T__82);
			setState(416);
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
		enterRule(_localctx, 84, RULE_subroutine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(418);
			match(T__83);
			setState(419);
			identifier();
			setState(420);
			match(T__40);
			setState(422);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) {
				{
				setState(421);
				sub_params();
				}
			}

			setState(424);
			match(T__41);
			setState(426);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__84) {
				{
				setState(425);
				sub_return_part();
				}
			}

			{
			setState(428);
			statement_block();
			setState(429);
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
		enterRule(_localctx, 86, RULE_sub_return_part);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(431);
			match(T__84);
			setState(432);
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
		enterRule(_localctx, 88, RULE_statement_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(434);
			match(T__85);
			setState(435);
			match(EOL);
			setState(440);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__2) | (1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0) || ((((_la - 66)) & ~0x3f) == 0 && ((1L << (_la - 66)) & ((1L << (T__65 - 66)) | (1L << (T__66 - 66)) | (1L << (T__67 - 66)) | (1L << (T__69 - 66)) | (1L << (T__70 - 66)) | (1L << (T__71 - 66)) | (1L << (T__82 - 66)) | (1L << (T__83 - 66)) | (1L << (T__87 - 66)) | (1L << (T__90 - 66)) | (1L << (T__92 - 66)) | (1L << (T__93 - 66)) | (1L << (T__94 - 66)) | (1L << (T__95 - 66)) | (1L << (T__96 - 66)) | (1L << (T__97 - 66)) | (1L << (T__98 - 66)) | (1L << (T__99 - 66)) | (1L << (T__100 - 66)) | (1L << (T__101 - 66)) | (1L << (T__102 - 66)) | (1L << (T__103 - 66)) | (1L << (T__104 - 66)) | (1L << (T__106 - 66)) | (1L << (T__107 - 66)) | (1L << (EOL - 66)) | (1L << (NAME - 66)))) != 0)) {
				{
				setState(438);
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
				case T__65:
				case T__66:
				case T__67:
				case T__69:
				case T__70:
				case T__71:
				case T__82:
				case T__83:
				case T__87:
				case T__90:
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
				case T__103:
				case T__104:
				case T__106:
				case T__107:
				case NAME:
					{
					setState(436);
					statement();
					}
					break;
				case EOL:
					{
					setState(437);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(442);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(443);
			match(T__86);
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
		public List<VardeclContext> vardecl() {
			return getRuleContexts(VardeclContext.class);
		}
		public VardeclContext vardecl(int i) {
			return getRuleContext(VardeclContext.class,i);
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
		enterRule(_localctx, 90, RULE_sub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(445);
			vardecl();
			setState(453);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(446);
				match(T__13);
				setState(448);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(447);
					match(EOL);
					}
				}

				setState(450);
				vardecl();
				}
				}
				setState(455);
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
			setState(456);
			datatype();
			setState(464);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(457);
				match(T__13);
				setState(459);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(458);
					match(EOL);
					}
				}

				setState(461);
				datatype();
				}
				}
				setState(466);
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
			setState(467);
			match(T__87);
			setState(468);
			identifier();
			setState(469);
			match(T__40);
			setState(471);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) {
				{
				setState(470);
				asmsub_params();
				}
			}

			setState(473);
			match(T__41);
			setState(474);
			match(T__84);
			setState(475);
			match(T__88);
			setState(476);
			match(T__40);
			setState(478);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & ((1L << (T__69 - 70)) | (1L << (T__70 - 70)) | (1L << (T__71 - 70)))) != 0)) {
				{
				setState(477);
				clobber();
				}
			}

			setState(480);
			match(T__41);
			setState(481);
			match(T__84);
			setState(482);
			match(T__40);
			setState(484);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) {
				{
				setState(483);
				asmsub_returns();
				}
			}

			setState(486);
			match(T__41);
			setState(489);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__14:
				{
				setState(487);
				asmsub_address();
				}
				break;
			case T__85:
				{
				setState(488);
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
			setState(491);
			match(T__14);
			setState(492);
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
			setState(494);
			asmsub_param();
			setState(502);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(495);
				match(T__13);
				setState(497);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(496);
					match(EOL);
					}
				}

				setState(499);
				asmsub_param();
				}
				}
				setState(504);
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
		public VardeclContext vardecl() {
			return getRuleContext(VardeclContext.class,0);
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
			setState(505);
			vardecl();
			setState(506);
			match(T__89);
			setState(509);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__69:
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__74:
				{
				setState(507);
				registerorpair();
				}
				break;
			case T__75:
			case T__76:
			case T__77:
			case T__78:
				{
				setState(508);
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
			setState(511);
			register();
			setState(516);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(512);
				match(T__13);
				setState(513);
				register();
				}
				}
				setState(518);
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
			setState(519);
			asmsub_return();
			setState(527);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(520);
				match(T__13);
				setState(522);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(521);
					match(EOL);
					}
				}

				setState(524);
				asmsub_return();
				}
				}
				setState(529);
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
			setState(530);
			datatype();
			setState(531);
			match(T__89);
			setState(534);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__69:
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__74:
				{
				setState(532);
				registerorpair();
				}
				break;
			case T__75:
			case T__76:
			case T__77:
			case T__78:
				{
				setState(533);
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
			setState(536);
			match(T__90);
			setState(537);
			expression(0);
			setState(539);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(538);
				match(EOL);
				}
			}

			setState(543);
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
			case T__65:
			case T__66:
			case T__67:
			case T__69:
			case T__70:
			case T__71:
			case T__82:
			case T__83:
			case T__87:
			case T__90:
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
			case T__103:
			case T__104:
			case T__106:
			case T__107:
			case NAME:
				{
				setState(541);
				statement();
				}
				break;
			case T__85:
				{
				setState(542);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(546);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
			case 1:
				{
				setState(545);
				match(EOL);
				}
				break;
			}
			setState(549);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__91) {
				{
				setState(548);
				else_part();
				}
			}

			setState(551);
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
			setState(553);
			match(T__91);
			setState(555);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(554);
				match(EOL);
				}
			}

			setState(559);
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
			case T__65:
			case T__66:
			case T__67:
			case T__69:
			case T__70:
			case T__71:
			case T__82:
			case T__83:
			case T__87:
			case T__90:
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
			case T__103:
			case T__104:
			case T__106:
			case T__107:
			case NAME:
				{
				setState(557);
				statement();
				}
				break;
			case T__85:
				{
				setState(558);
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
			setState(561);
			branchcondition();
			setState(563);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(562);
				match(EOL);
				}
			}

			setState(567);
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
			case T__65:
			case T__66:
			case T__67:
			case T__69:
			case T__70:
			case T__71:
			case T__82:
			case T__83:
			case T__87:
			case T__90:
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
			case T__103:
			case T__104:
			case T__106:
			case T__107:
			case NAME:
				{
				setState(565);
				statement();
				}
				break;
			case T__85:
				{
				setState(566);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(570);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,60,_ctx) ) {
			case 1:
				{
				setState(569);
				match(EOL);
				}
				break;
			}
			setState(573);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__91) {
				{
				setState(572);
				else_part();
				}
			}

			setState(575);
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
			setState(577);
			_la = _input.LA(1);
			if ( !(((((_la - 93)) & ~0x3f) == 0 && ((1L << (_la - 93)) & ((1L << (T__92 - 93)) | (1L << (T__93 - 93)) | (1L << (T__94 - 93)) | (1L << (T__95 - 93)) | (1L << (T__96 - 93)) | (1L << (T__97 - 93)) | (1L << (T__98 - 93)) | (1L << (T__99 - 93)) | (1L << (T__100 - 93)) | (1L << (T__101 - 93)) | (1L << (T__102 - 93)) | (1L << (T__103 - 93)))) != 0)) ) {
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
			setState(579);
			match(T__104);
			setState(581);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23) | (1L << T__24) | (1L << T__25))) != 0)) {
				{
				setState(580);
				datatype();
				}
			}

			setState(585);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__69:
			case T__70:
			case T__71:
				{
				setState(583);
				register();
				}
				break;
			case NAME:
				{
				setState(584);
				identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(587);
			match(T__105);
			setState(588);
			expression(0);
			setState(590);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(589);
				match(EOL);
				}
			}

			setState(592);
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
			setState(594);
			match(T__106);
			setState(595);
			expression(0);
			setState(597);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(596);
				match(EOL);
				}
			}

			setState(601);
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
			case T__65:
			case T__66:
			case T__67:
			case T__69:
			case T__70:
			case T__71:
			case T__82:
			case T__83:
			case T__87:
			case T__90:
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
			case T__103:
			case T__104:
			case T__106:
			case T__107:
			case NAME:
				{
				setState(599);
				statement();
				}
				break;
			case T__85:
				{
				setState(600);
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
			setState(603);
			match(T__107);
			setState(606);
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
			case T__65:
			case T__66:
			case T__67:
			case T__69:
			case T__70:
			case T__71:
			case T__82:
			case T__83:
			case T__87:
			case T__90:
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
			case T__103:
			case T__104:
			case T__106:
			case T__107:
			case NAME:
				{
				setState(604);
				statement();
				}
				break;
			case T__85:
				{
				setState(605);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(609);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(608);
				match(EOL);
				}
			}

			setState(611);
			match(T__108);
			setState(612);
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
			return precpred(_ctx, 19);
		case 1:
			return precpred(_ctx, 18);
		case 2:
			return precpred(_ctx, 17);
		case 3:
			return precpred(_ctx, 16);
		case 4:
			return precpred(_ctx, 15);
		case 5:
			return precpred(_ctx, 14);
		case 6:
			return precpred(_ctx, 13);
		case 7:
			return precpred(_ctx, 12);
		case 8:
			return precpred(_ctx, 10);
		case 9:
			return precpred(_ctx, 9);
		case 10:
			return precpred(_ctx, 8);
		case 11:
			return precpred(_ctx, 11);
		case 12:
			return precpred(_ctx, 1);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3{\u0269\4\2\t\2\4"+
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
		"\3\25\3\25\5\25\u0130\n\25\3\25\3\25\7\25\u0134\n\25\f\25\16\25\u0137"+
		"\13\25\3\26\3\26\3\26\3\27\3\27\5\27\u013e\n\27\3\27\3\27\3\30\3\30\5"+
		"\30\u0144\n\30\3\30\3\30\5\30\u0148\n\30\3\30\3\30\3\31\3\31\5\31\u014e"+
		"\n\31\3\31\3\31\5\31\u0152\n\31\3\31\3\31\3\32\3\32\3\32\5\32\u0159\n"+
		"\32\3\32\7\32\u015c\n\32\f\32\16\32\u015f\13\32\3\33\3\33\5\33\u0163\n"+
		"\33\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37\3\37\6\37\u016e\n\37\r\37"+
		"\16\37\u016f\3 \3 \3!\3!\3\"\3\"\3#\3#\5#\u017a\n#\3$\3$\3%\3%\3&\3&\5"+
		"&\u0182\n&\3&\3&\3&\5&\u0187\n&\3&\7&\u018a\n&\f&\16&\u018d\13&\3&\5&"+
		"\u0190\n&\3&\3&\3\'\3\'\3(\3(\3)\3)\3*\3*\3*\3*\3*\3*\5*\u01a0\n*\3+\3"+
		"+\3+\3,\3,\3,\3,\5,\u01a9\n,\3,\3,\5,\u01ad\n,\3,\3,\3,\3-\3-\3-\3.\3"+
		".\3.\3.\7.\u01b9\n.\f.\16.\u01bc\13.\3.\3.\3/\3/\3/\5/\u01c3\n/\3/\7/"+
		"\u01c6\n/\f/\16/\u01c9\13/\3\60\3\60\3\60\5\60\u01ce\n\60\3\60\7\60\u01d1"+
		"\n\60\f\60\16\60\u01d4\13\60\3\61\3\61\3\61\3\61\5\61\u01da\n\61\3\61"+
		"\3\61\3\61\3\61\3\61\5\61\u01e1\n\61\3\61\3\61\3\61\3\61\5\61\u01e7\n"+
		"\61\3\61\3\61\3\61\5\61\u01ec\n\61\3\62\3\62\3\62\3\63\3\63\3\63\5\63"+
		"\u01f4\n\63\3\63\7\63\u01f7\n\63\f\63\16\63\u01fa\13\63\3\64\3\64\3\64"+
		"\3\64\5\64\u0200\n\64\3\65\3\65\3\65\7\65\u0205\n\65\f\65\16\65\u0208"+
		"\13\65\3\66\3\66\3\66\5\66\u020d\n\66\3\66\7\66\u0210\n\66\f\66\16\66"+
		"\u0213\13\66\3\67\3\67\3\67\3\67\5\67\u0219\n\67\38\38\38\58\u021e\n8"+
		"\38\38\58\u0222\n8\38\58\u0225\n8\38\58\u0228\n8\38\38\39\39\59\u022e"+
		"\n9\39\39\59\u0232\n9\3:\3:\5:\u0236\n:\3:\3:\5:\u023a\n:\3:\5:\u023d"+
		"\n:\3:\5:\u0240\n:\3:\3:\3;\3;\3<\3<\5<\u0248\n<\3<\3<\5<\u024c\n<\3<"+
		"\3<\3<\5<\u0251\n<\3<\3<\3=\3=\3=\5=\u0258\n=\3=\3=\5=\u025c\n=\3>\3>"+
		"\3>\5>\u0261\n>\3>\5>\u0264\n>\3>\3>\3>\3>\2\3(?\2\4\6\b\n\f\16\20\22"+
		"\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJLNPRTVXZ\\^`bdfhjlnp"+
		"rtvxz\2\21\3\2\6\17\3\2\24\34\3\2\37(\3\2)*\4\2\3\3-.\3\2\60\63\3\2-."+
		"\3\2\64\67\3\289\3\2HJ\3\2HM\3\2NQ\3\2uw\3\2ST\3\2_j\2\u029e\2\u0080\3"+
		"\2\2\2\4\u0087\3\2\2\2\6\u0089\3\2\2\2\b\u00a7\3\2\2\2\n\u00a9\3\2\2\2"+
		"\f\u00ac\3\2\2\2\16\u00b2\3\2\2\2\20\u00c3\3\2\2\2\22\u00c5\3\2\2\2\24"+
		"\u00cb\3\2\2\2\26\u00d3\3\2\2\2\30\u00d6\3\2\2\2\32\u00d9\3\2\2\2\34\u00db"+
		"\3\2\2\2\36\u00df\3\2\2\2 \u00e3\3\2\2\2\"\u00eb\3\2\2\2$\u00f3\3\2\2"+
		"\2&\u00f5\3\2\2\2(\u0107\3\2\2\2*\u0138\3\2\2\2,\u013d\3\2\2\2.\u0143"+
		"\3\2\2\2\60\u014d\3\2\2\2\62\u0155\3\2\2\2\64\u0160\3\2\2\2\66\u0164\3"+
		"\2\2\28\u0166\3\2\2\2:\u0168\3\2\2\2<\u016a\3\2\2\2>\u0171\3\2\2\2@\u0173"+
		"\3\2\2\2B\u0175\3\2\2\2D\u0177\3\2\2\2F\u017b\3\2\2\2H\u017d\3\2\2\2J"+
		"\u017f\3\2\2\2L\u0193\3\2\2\2N\u0195\3\2\2\2P\u0197\3\2\2\2R\u019f\3\2"+
		"\2\2T\u01a1\3\2\2\2V\u01a4\3\2\2\2X\u01b1\3\2\2\2Z\u01b4\3\2\2\2\\\u01bf"+
		"\3\2\2\2^\u01ca\3\2\2\2`\u01d5\3\2\2\2b\u01ed\3\2\2\2d\u01f0\3\2\2\2f"+
		"\u01fb\3\2\2\2h\u0201\3\2\2\2j\u0209\3\2\2\2l\u0214\3\2\2\2n\u021a\3\2"+
		"\2\2p\u022b\3\2\2\2r\u0233\3\2\2\2t\u0243\3\2\2\2v\u0245\3\2\2\2x\u0254"+
		"\3\2\2\2z\u025d\3\2\2\2|\177\5\4\3\2}\177\7s\2\2~|\3\2\2\2~}\3\2\2\2\177"+
		"\u0082\3\2\2\2\u0080~\3\2\2\2\u0080\u0081\3\2\2\2\u0081\u0083\3\2\2\2"+
		"\u0082\u0080\3\2\2\2\u0083\u0084\7\2\2\3\u0084\3\3\2\2\2\u0085\u0088\5"+
		"\16\b\2\u0086\u0088\5\6\4\2\u0087\u0085\3\2\2\2\u0087\u0086\3\2\2\2\u0088"+
		"\5\3\2\2\2\u0089\u008a\7\3\2\2\u008a\u008c\5:\36\2\u008b\u008d\5D#\2\u008c"+
		"\u008b\3\2\2\2\u008c\u008d\3\2\2\2\u008d\u008e\3\2\2\2\u008e\u008f\5Z"+
		".\2\u008f\u0090\7s\2\2\u0090\7\3\2\2\2\u0091\u00a8\5\16\b\2\u0092\u00a8"+
		"\5\24\13\2\u0093\u00a8\5\22\n\2\u0094\u00a8\5\26\f\2\u0095\u00a8\5\30"+
		"\r\2\u0096\u00a8\5\36\20\2\u0097\u00a8\5\"\22\2\u0098\u00a8\5\f\7\2\u0099"+
		"\u00a8\5&\24\2\u009a\u00a8\5\60\31\2\u009b\u00a8\5n8\2\u009c\u00a8\5r"+
		":\2\u009d\u00a8\5V,\2\u009e\u00a8\5`\61\2\u009f\u00a8\5T+\2\u00a0\u00a8"+
		"\5\64\33\2\u00a1\u00a8\5v<\2\u00a2\u00a8\5x=\2\u00a3\u00a8\5z>\2\u00a4"+
		"\u00a8\5\66\34\2\u00a5\u00a8\58\35\2\u00a6\u00a8\5\n\6\2\u00a7\u0091\3"+
		"\2\2\2\u00a7\u0092\3\2\2\2\u00a7\u0093\3\2\2\2\u00a7\u0094\3\2\2\2\u00a7"+
		"\u0095\3\2\2\2\u00a7\u0096\3\2\2\2\u00a7\u0097\3\2\2\2\u00a7\u0098\3\2"+
		"\2\2\u00a7\u0099\3\2\2\2\u00a7\u009a\3\2\2\2\u00a7\u009b\3\2\2\2\u00a7"+
		"\u009c\3\2\2\2\u00a7\u009d\3\2\2\2\u00a7\u009e\3\2\2\2\u00a7\u009f\3\2"+
		"\2\2\u00a7\u00a0\3\2\2\2\u00a7\u00a1\3\2\2\2\u00a7\u00a2\3\2\2\2\u00a7"+
		"\u00a3\3\2\2\2\u00a7\u00a4\3\2\2\2\u00a7\u00a5\3\2\2\2\u00a7\u00a6\3\2"+
		"\2\2\u00a8\t\3\2\2\2\u00a9\u00aa\5:\36\2\u00aa\u00ab\7\4\2\2\u00ab\13"+
		"\3\2\2\2\u00ac\u00b0\7\5\2\2\u00ad\u00b1\5D#\2\u00ae\u00b1\5:\36\2\u00af"+
		"\u00b1\5<\37\2\u00b0\u00ad\3\2\2\2\u00b0\u00ae\3\2\2\2\u00b0\u00af\3\2"+
		"\2\2\u00b1\r\3\2\2\2\u00b2\u00be\t\2\2\2\u00b3\u00b5\5\20\t\2\u00b4\u00b3"+
		"\3\2\2\2\u00b4\u00b5\3\2\2\2\u00b5\u00bf\3\2\2\2\u00b6\u00bb\5\20\t\2"+
		"\u00b7\u00b8\7\20\2\2\u00b8\u00ba\5\20\t\2\u00b9\u00b7\3\2\2\2\u00ba\u00bd"+
		"\3\2\2\2\u00bb\u00b9\3\2\2\2\u00bb\u00bc\3\2\2\2\u00bc\u00bf\3\2\2\2\u00bd"+
		"\u00bb\3\2\2\2\u00be\u00b4\3\2\2\2\u00be\u00b6\3\2\2\2\u00bf\17\3\2\2"+
		"\2\u00c0\u00c4\5L\'\2\u00c1\u00c4\5:\36\2\u00c2\u00c4\5D#\2\u00c3\u00c0"+
		"\3\2\2\2\u00c3\u00c1\3\2\2\2\u00c3\u00c2\3\2\2\2\u00c4\21\3\2\2\2\u00c5"+
		"\u00c7\5\32\16\2\u00c6\u00c8\5\34\17\2\u00c7\u00c6\3\2\2\2\u00c7\u00c8"+
		"\3\2\2\2\u00c8\u00c9\3\2\2\2\u00c9\u00ca\5:\36\2\u00ca\23\3\2\2\2\u00cb"+
		"\u00cd\5\32\16\2\u00cc\u00ce\5\34\17\2\u00cd\u00cc\3\2\2\2\u00cd\u00ce"+
		"\3\2\2\2\u00ce\u00cf\3\2\2\2\u00cf\u00d0\5:\36\2\u00d0\u00d1\7\21\2\2"+
		"\u00d1\u00d2\5(\25\2\u00d2\25\3\2\2\2\u00d3\u00d4\7\22\2\2\u00d4\u00d5"+
		"\5\24\13\2\u00d5\27\3\2\2\2\u00d6\u00d7\7\23\2\2\u00d7\u00d8\5\24\13\2"+
		"\u00d8\31\3\2\2\2\u00d9\u00da\t\3\2\2\u00da\33\3\2\2\2\u00db\u00dc\7\35"+
		"\2\2\u00dc\u00dd\5(\25\2\u00dd\u00de\7\36\2\2\u00de\35\3\2\2\2\u00df\u00e0"+
		"\5 \21\2\u00e0\u00e1\7\21\2\2\u00e1\u00e2\5(\25\2\u00e2\37\3\2\2\2\u00e3"+
		"\u00e8\5$\23\2\u00e4\u00e5\7\20\2\2\u00e5\u00e7\5$\23\2\u00e6\u00e4\3"+
		"\2\2\2\u00e7\u00ea\3\2\2\2\u00e8\u00e6\3\2\2\2\u00e8\u00e9\3\2\2\2\u00e9"+
		"!\3\2\2\2\u00ea\u00e8\3\2\2\2\u00eb\u00ec\5$\23\2\u00ec\u00ed\t\4\2\2"+
		"\u00ed\u00ee\5(\25\2\u00ee#\3\2\2\2\u00ef\u00f4\5> \2\u00f0\u00f4\5:\36"+
		"\2\u00f1\u00f4\5<\37\2\u00f2\u00f4\5,\27\2\u00f3\u00ef\3\2\2\2\u00f3\u00f0"+
		"\3\2\2\2\u00f3\u00f1\3\2\2\2\u00f3\u00f2\3\2\2\2\u00f4%\3\2\2\2\u00f5"+
		"\u00f6\5$\23\2\u00f6\u00f7\t\5\2\2\u00f7\'\3\2\2\2\u00f8\u00f9\b\25\1"+
		"\2\u00f9\u00fa\7+\2\2\u00fa\u00fb\5(\25\2\u00fb\u00fc\7,\2\2\u00fc\u0108"+
		"\3\2\2\2\u00fd\u0108\5.\30\2\u00fe\u00ff\t\6\2\2\u00ff\u0108\5(\25\26"+
		"\u0100\u0101\7B\2\2\u0101\u0108\5(\25\t\u0102\u0108\5R*\2\u0103\u0108"+
		"\5> \2\u0104\u0108\5:\36\2\u0105\u0108\5<\37\2\u0106\u0108\5,\27\2\u0107"+
		"\u00f8\3\2\2\2\u0107\u00fd\3\2\2\2\u0107\u00fe\3\2\2\2\u0107\u0100\3\2"+
		"\2\2\u0107\u0102\3\2\2\2\u0107\u0103\3\2\2\2\u0107\u0104\3\2\2\2\u0107"+
		"\u0105\3\2\2\2\u0107\u0106\3\2\2\2\u0108\u0135\3\2\2\2\u0109\u010a\f\25"+
		"\2\2\u010a\u010b\7/\2\2\u010b\u0134\5(\25\26\u010c\u010d\f\24\2\2\u010d"+
		"\u010e\t\7\2\2\u010e\u0134\5(\25\25\u010f\u0110\f\23\2\2\u0110\u0111\t"+
		"\b\2\2\u0111\u0134\5(\25\24\u0112\u0113\f\22\2\2\u0113\u0114\t\t\2\2\u0114"+
		"\u0134\5(\25\23\u0115\u0116\f\21\2\2\u0116\u0117\t\n\2\2\u0117\u0134\5"+
		"(\25\22\u0118\u0119\f\20\2\2\u0119\u011a\7:\2\2\u011a\u0134\5(\25\21\u011b"+
		"\u011c\f\17\2\2\u011c\u011d\7;\2\2\u011d\u0134\5(\25\20\u011e\u011f\f"+
		"\16\2\2\u011f\u0120\7<\2\2\u0120\u0134\5(\25\17\u0121\u0122\f\f\2\2\u0122"+
		"\u0123\7?\2\2\u0123\u0134\5(\25\r\u0124\u0125\f\13\2\2\u0125\u0126\7@"+
		"\2\2\u0126\u0134\5(\25\f\u0127\u0128\f\n\2\2\u0128\u0129\7A\2\2\u0129"+
		"\u0134\5(\25\13\u012a\u012b\f\r\2\2\u012b\u012c\7=\2\2\u012c\u012f\5("+
		"\25\2\u012d\u012e\7>\2\2\u012e\u0130\5(\25\2\u012f\u012d\3\2\2\2\u012f"+
		"\u0130\3\2\2\2\u0130\u0134\3\2\2\2\u0131\u0132\f\3\2\2\u0132\u0134\5*"+
		"\26\2\u0133\u0109\3\2\2\2\u0133\u010c\3\2\2\2\u0133\u010f\3\2\2\2\u0133"+
		"\u0112\3\2\2\2\u0133\u0115\3\2\2\2\u0133\u0118\3\2\2\2\u0133\u011b\3\2"+
		"\2\2\u0133\u011e\3\2\2\2\u0133\u0121\3\2\2\2\u0133\u0124\3\2\2\2\u0133"+
		"\u0127\3\2\2\2\u0133\u012a\3\2\2\2\u0133\u0131\3\2\2\2\u0134\u0137\3\2"+
		"\2\2\u0135\u0133\3\2\2\2\u0135\u0136\3\2\2\2\u0136)\3\2\2\2\u0137\u0135"+
		"\3\2\2\2\u0138\u0139\7C\2\2\u0139\u013a\5\32\16\2\u013a+\3\2\2\2\u013b"+
		"\u013e\5:\36\2\u013c\u013e\5<\37\2\u013d\u013b\3\2\2\2\u013d\u013c\3\2"+
		"\2\2\u013e\u013f\3\2\2\2\u013f\u0140\5\34\17\2\u0140-\3\2\2\2\u0141\u0144"+
		"\5:\36\2\u0142\u0144\5<\37\2\u0143\u0141\3\2\2\2\u0143\u0142\3\2\2\2\u0144"+
		"\u0145\3\2\2\2\u0145\u0147\7+\2\2\u0146\u0148\5\62\32\2\u0147\u0146\3"+
		"\2\2\2\u0147\u0148\3\2\2\2\u0148\u0149\3\2\2\2\u0149\u014a\7,\2\2\u014a"+
		"/\3\2\2\2\u014b\u014e\5:\36\2\u014c\u014e\5<\37\2\u014d\u014b\3\2\2\2"+
		"\u014d\u014c\3\2\2\2\u014e\u014f\3\2\2\2\u014f\u0151\7+\2\2\u0150\u0152"+
		"\5\62\32\2\u0151\u0150\3\2\2\2\u0151\u0152\3\2\2\2\u0152\u0153\3\2\2\2"+
		"\u0153\u0154\7,\2\2\u0154\61\3\2\2\2\u0155\u015d\5(\25\2\u0156\u0158\7"+
		"\20\2\2\u0157\u0159\7s\2\2\u0158\u0157\3\2\2\2\u0158\u0159\3\2\2\2\u0159"+
		"\u015a\3\2\2\2\u015a\u015c\5(\25\2\u015b\u0156\3\2\2\2\u015c\u015f\3\2"+
		"\2\2\u015d\u015b\3\2\2\2\u015d\u015e\3\2\2\2\u015e\63\3\2\2\2\u015f\u015d"+
		"\3\2\2\2\u0160\u0162\7D\2\2\u0161\u0163\5\62\32\2\u0162\u0161\3\2\2\2"+
		"\u0162\u0163\3\2\2\2\u0163\65\3\2\2\2\u0164\u0165\7E\2\2\u0165\67\3\2"+
		"\2\2\u0166\u0167\7F\2\2\u01679\3\2\2\2\u0168\u0169\7t\2\2\u0169;\3\2\2"+
		"\2\u016a\u016d\7t\2\2\u016b\u016c\7G\2\2\u016c\u016e\7t\2\2\u016d\u016b"+
		"\3\2\2\2\u016e\u016f\3\2\2\2\u016f\u016d\3\2\2\2\u016f\u0170\3\2\2\2\u0170"+
		"=\3\2\2\2\u0171\u0172\t\13\2\2\u0172?\3\2\2\2\u0173\u0174\t\f\2\2\u0174"+
		"A\3\2\2\2\u0175\u0176\t\r\2\2\u0176C\3\2\2\2\u0177\u0179\t\16\2\2\u0178"+
		"\u017a\5F$\2\u0179\u0178\3\2\2\2\u0179\u017a\3\2\2\2\u017aE\3\2\2\2\u017b"+
		"\u017c\7R\2\2\u017cG\3\2\2\2\u017d\u017e\t\17\2\2\u017eI\3\2\2\2\u017f"+
		"\u0181\7\35\2\2\u0180\u0182\7s\2\2\u0181\u0180\3\2\2\2\u0181\u0182\3\2"+
		"\2\2\u0182\u0183\3\2\2\2\u0183\u018b\5(\25\2\u0184\u0186\7\20\2\2\u0185"+
		"\u0187\7s\2\2\u0186\u0185\3\2\2\2\u0186\u0187\3\2\2\2\u0187\u0188\3\2"+
		"\2\2\u0188\u018a\5(\25\2\u0189\u0184\3\2\2\2\u018a\u018d\3\2\2\2\u018b"+
		"\u0189\3\2\2\2\u018b\u018c\3\2\2\2\u018c\u018f\3\2\2\2\u018d\u018b\3\2"+
		"\2\2\u018e\u0190\7s\2\2\u018f\u018e\3\2\2\2\u018f\u0190\3\2\2\2\u0190"+
		"\u0191\3\2\2\2\u0191\u0192\7\36\2\2\u0192K\3\2\2\2\u0193\u0194\7y\2\2"+
		"\u0194M\3\2\2\2\u0195\u0196\7{\2\2\u0196O\3\2\2\2\u0197\u0198\7x\2\2\u0198"+
		"Q\3\2\2\2\u0199\u01a0\5D#\2\u019a\u01a0\5H%\2\u019b\u01a0\5J&\2\u019c"+
		"\u01a0\5L\'\2\u019d\u01a0\5N(\2\u019e\u01a0\5P)\2\u019f\u0199\3\2\2\2"+
		"\u019f\u019a\3\2\2\2\u019f\u019b\3\2\2\2\u019f\u019c\3\2\2\2\u019f\u019d"+
		"\3\2\2\2\u019f\u019e\3\2\2\2\u01a0S\3\2\2\2\u01a1\u01a2\7U\2\2\u01a2\u01a3"+
		"\7z\2\2\u01a3U\3\2\2\2\u01a4\u01a5\7V\2\2\u01a5\u01a6\5:\36\2\u01a6\u01a8"+
		"\7+\2\2\u01a7\u01a9\5\\/\2\u01a8\u01a7\3\2\2\2\u01a8\u01a9\3\2\2\2\u01a9"+
		"\u01aa\3\2\2\2\u01aa\u01ac\7,\2\2\u01ab\u01ad\5X-\2\u01ac\u01ab\3\2\2"+
		"\2\u01ac\u01ad\3\2\2\2\u01ad\u01ae\3\2\2\2\u01ae\u01af\5Z.\2\u01af\u01b0"+
		"\7s\2\2\u01b0W\3\2\2\2\u01b1\u01b2\7W\2\2\u01b2\u01b3\5^\60\2\u01b3Y\3"+
		"\2\2\2\u01b4\u01b5\7X\2\2\u01b5\u01ba\7s\2\2\u01b6\u01b9\5\b\5\2\u01b7"+
		"\u01b9\7s\2\2\u01b8\u01b6\3\2\2\2\u01b8\u01b7\3\2\2\2\u01b9\u01bc\3\2"+
		"\2\2\u01ba\u01b8\3\2\2\2\u01ba\u01bb\3\2\2\2\u01bb\u01bd\3\2\2\2\u01bc"+
		"\u01ba\3\2\2\2\u01bd\u01be\7Y\2\2\u01be[\3\2\2\2\u01bf\u01c7\5\22\n\2"+
		"\u01c0\u01c2\7\20\2\2\u01c1\u01c3\7s\2\2\u01c2\u01c1\3\2\2\2\u01c2\u01c3"+
		"\3\2\2\2\u01c3\u01c4\3\2\2\2\u01c4\u01c6\5\22\n\2\u01c5\u01c0\3\2\2\2"+
		"\u01c6\u01c9\3\2\2\2\u01c7\u01c5\3\2\2\2\u01c7\u01c8\3\2\2\2\u01c8]\3"+
		"\2\2\2\u01c9\u01c7\3\2\2\2\u01ca\u01d2\5\32\16\2\u01cb\u01cd\7\20\2\2"+
		"\u01cc\u01ce\7s\2\2\u01cd\u01cc\3\2\2\2\u01cd\u01ce\3\2\2\2\u01ce\u01cf"+
		"\3\2\2\2\u01cf\u01d1\5\32\16\2\u01d0\u01cb\3\2\2\2\u01d1\u01d4\3\2\2\2"+
		"\u01d2\u01d0\3\2\2\2\u01d2\u01d3\3\2\2\2\u01d3_\3\2\2\2\u01d4\u01d2\3"+
		"\2\2\2\u01d5\u01d6\7Z\2\2\u01d6\u01d7\5:\36\2\u01d7\u01d9\7+\2\2\u01d8"+
		"\u01da\5d\63\2\u01d9\u01d8\3\2\2\2\u01d9\u01da\3\2\2\2\u01da\u01db\3\2"+
		"\2\2\u01db\u01dc\7,\2\2\u01dc\u01dd\7W\2\2\u01dd\u01de\7[\2\2\u01de\u01e0"+
		"\7+\2\2\u01df\u01e1\5h\65\2\u01e0\u01df\3\2\2\2\u01e0\u01e1\3\2\2\2\u01e1"+
		"\u01e2\3\2\2\2\u01e2\u01e3\7,\2\2\u01e3\u01e4\7W\2\2\u01e4\u01e6\7+\2"+
		"\2\u01e5\u01e7\5j\66\2\u01e6\u01e5\3\2\2\2\u01e6\u01e7\3\2\2\2\u01e7\u01e8"+
		"\3\2\2\2\u01e8\u01eb\7,\2\2\u01e9\u01ec\5b\62\2\u01ea\u01ec\5Z.\2\u01eb"+
		"\u01e9\3\2\2\2\u01eb\u01ea\3\2\2\2\u01eca\3\2\2\2\u01ed\u01ee\7\21\2\2"+
		"\u01ee\u01ef\5D#\2\u01efc\3\2\2\2\u01f0\u01f8\5f\64\2\u01f1\u01f3\7\20"+
		"\2\2\u01f2\u01f4\7s\2\2\u01f3\u01f2\3\2\2\2\u01f3\u01f4\3\2\2\2\u01f4"+
		"\u01f5\3\2\2\2\u01f5\u01f7\5f\64\2\u01f6\u01f1\3\2\2\2\u01f7\u01fa\3\2"+
		"\2\2\u01f8\u01f6\3\2\2\2\u01f8\u01f9\3\2\2\2\u01f9e\3\2\2\2\u01fa\u01f8"+
		"\3\2\2\2\u01fb\u01fc\5\22\n\2\u01fc\u01ff\7\\\2\2\u01fd\u0200\5@!\2\u01fe"+
		"\u0200\5B\"\2\u01ff\u01fd\3\2\2\2\u01ff\u01fe\3\2\2\2\u0200g\3\2\2\2\u0201"+
		"\u0206\5> \2\u0202\u0203\7\20\2\2\u0203\u0205\5> \2\u0204\u0202\3\2\2"+
		"\2\u0205\u0208\3\2\2\2\u0206\u0204\3\2\2\2\u0206\u0207\3\2\2\2\u0207i"+
		"\3\2\2\2\u0208\u0206\3\2\2\2\u0209\u0211\5l\67\2\u020a\u020c\7\20\2\2"+
		"\u020b\u020d\7s\2\2\u020c\u020b\3\2\2\2\u020c\u020d\3\2\2\2\u020d\u020e"+
		"\3\2\2\2\u020e\u0210\5l\67\2\u020f\u020a\3\2\2\2\u0210\u0213\3\2\2\2\u0211"+
		"\u020f\3\2\2\2\u0211\u0212\3\2\2\2\u0212k\3\2\2\2\u0213\u0211\3\2\2\2"+
		"\u0214\u0215\5\32\16\2\u0215\u0218\7\\\2\2\u0216\u0219\5@!\2\u0217\u0219"+
		"\5B\"\2\u0218\u0216\3\2\2\2\u0218\u0217\3\2\2\2\u0219m\3\2\2\2\u021a\u021b"+
		"\7]\2\2\u021b\u021d\5(\25\2\u021c\u021e\7s\2\2\u021d\u021c\3\2\2\2\u021d"+
		"\u021e\3\2\2\2\u021e\u0221\3\2\2\2\u021f\u0222\5\b\5\2\u0220\u0222\5Z"+
		".\2\u0221\u021f\3\2\2\2\u0221\u0220\3\2\2\2\u0222\u0224\3\2\2\2\u0223"+
		"\u0225\7s\2\2\u0224\u0223\3\2\2\2\u0224\u0225\3\2\2\2\u0225\u0227\3\2"+
		"\2\2\u0226\u0228\5p9\2\u0227\u0226\3\2\2\2\u0227\u0228\3\2\2\2\u0228\u0229"+
		"\3\2\2\2\u0229\u022a\7s\2\2\u022ao\3\2\2\2\u022b\u022d\7^\2\2\u022c\u022e"+
		"\7s\2\2\u022d\u022c\3\2\2\2\u022d\u022e\3\2\2\2\u022e\u0231\3\2\2\2\u022f"+
		"\u0232\5\b\5\2\u0230\u0232\5Z.\2\u0231\u022f\3\2\2\2\u0231\u0230\3\2\2"+
		"\2\u0232q\3\2\2\2\u0233\u0235\5t;\2\u0234\u0236\7s\2\2\u0235\u0234\3\2"+
		"\2\2\u0235\u0236\3\2\2\2\u0236\u0239\3\2\2\2\u0237\u023a\5\b\5\2\u0238"+
		"\u023a\5Z.\2\u0239\u0237\3\2\2\2\u0239\u0238\3\2\2\2\u023a\u023c\3\2\2"+
		"\2\u023b\u023d\7s\2\2\u023c\u023b\3\2\2\2\u023c\u023d\3\2\2\2\u023d\u023f"+
		"\3\2\2\2\u023e\u0240\5p9\2\u023f\u023e\3\2\2\2\u023f\u0240\3\2\2\2\u0240"+
		"\u0241\3\2\2\2\u0241\u0242\7s\2\2\u0242s\3\2\2\2\u0243\u0244\t\20\2\2"+
		"\u0244u\3\2\2\2\u0245\u0247\7k\2\2\u0246\u0248\5\32\16\2\u0247\u0246\3"+
		"\2\2\2\u0247\u0248\3\2\2\2\u0248\u024b\3\2\2\2\u0249\u024c\5> \2\u024a"+
		"\u024c\5:\36\2\u024b\u0249\3\2\2\2\u024b\u024a\3\2\2\2\u024c\u024d\3\2"+
		"\2\2\u024d\u024e\7l\2\2\u024e\u0250\5(\25\2\u024f\u0251\7s\2\2\u0250\u024f"+
		"\3\2\2\2\u0250\u0251\3\2\2\2\u0251\u0252\3\2\2\2\u0252\u0253\5Z.\2\u0253"+
		"w\3\2\2\2\u0254\u0255\7m\2\2\u0255\u0257\5(\25\2\u0256\u0258\7s\2\2\u0257"+
		"\u0256\3\2\2\2\u0257\u0258\3\2\2\2\u0258\u025b\3\2\2\2\u0259\u025c\5\b"+
		"\5\2\u025a\u025c\5Z.\2\u025b\u0259\3\2\2\2\u025b\u025a\3\2\2\2\u025cy"+
		"\3\2\2\2\u025d\u0260\7n\2\2\u025e\u0261\5\b\5\2\u025f\u0261\5Z.\2\u0260"+
		"\u025e\3\2\2\2\u0260\u025f\3\2\2\2\u0261\u0263\3\2\2\2\u0262\u0264\7s"+
		"\2\2\u0263\u0262\3\2\2\2\u0263\u0264\3\2\2\2\u0264\u0265\3\2\2\2\u0265"+
		"\u0266\7o\2\2\u0266\u0267\5(\25\2\u0267{\3\2\2\2G~\u0080\u0087\u008c\u00a7"+
		"\u00b0\u00b4\u00bb\u00be\u00c3\u00c7\u00cd\u00e8\u00f3\u0107\u012f\u0133"+
		"\u0135\u013d\u0143\u0147\u014d\u0151\u0158\u015d\u0162\u016f\u0179\u0181"+
		"\u0186\u018b\u018f\u019f\u01a8\u01ac\u01b8\u01ba\u01c2\u01c7\u01cd\u01d2"+
		"\u01d9\u01e0\u01e6\u01eb\u01f3\u01f8\u01ff\u0206\u020c\u0211\u0218\u021d"+
		"\u0221\u0224\u0227\u022d\u0231\u0235\u0239\u023c\u023f\u0247\u024b\u0250"+
		"\u0257\u025b\u0260\u0263";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}