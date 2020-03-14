// Generated from prog8.g4 by ANTLR 4.8

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
	static { RuntimeMetaData.checkVersion("4.8", RuntimeMetaData.VERSION); }

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
		T__107=108, T__108=109, T__109=110, T__110=111, LINECOMMENT=112, COMMENT=113, 
		WS=114, EOL=115, VOID=116, NAME=117, DEC_INTEGER=118, HEX_INTEGER=119, 
		BIN_INTEGER=120, ADDRESS_OF=121, ALT_STRING_ENCODING=122, FLOAT_NUMBER=123, 
		STRING=124, INLINEASMBLOCK=125, SINGLECHAR=126, ZEROPAGE=127, ARRAYSIG=128;
	public static final int
		RULE_module = 0, RULE_block = 1, RULE_block_statement = 2, RULE_statement = 3, 
		RULE_variabledeclaration = 4, RULE_subroutinedeclaration = 5, RULE_labeldef = 6, 
		RULE_unconditionaljump = 7, RULE_directive = 8, RULE_directivearg = 9, 
		RULE_vardecl = 10, RULE_structvardecl = 11, RULE_varinitializer = 12, 
		RULE_structvarinitializer = 13, RULE_constdecl = 14, RULE_memoryvardecl = 15, 
		RULE_structdecl = 16, RULE_datatype = 17, RULE_arrayindex = 18, RULE_assignment = 19, 
		RULE_augassignment = 20, RULE_assign_target = 21, RULE_postincrdecr = 22, 
		RULE_expression = 23, RULE_typecast = 24, RULE_arrayindexed = 25, RULE_directmemory = 26, 
		RULE_addressof = 27, RULE_functioncall = 28, RULE_functioncall_stmt = 29, 
		RULE_expression_list = 30, RULE_returnstmt = 31, RULE_breakstmt = 32, 
		RULE_continuestmt = 33, RULE_identifier = 34, RULE_scoped_identifier = 35, 
		RULE_register = 36, RULE_registerorpair = 37, RULE_statusregister = 38, 
		RULE_integerliteral = 39, RULE_wordsuffix = 40, RULE_booleanliteral = 41, 
		RULE_arrayliteral = 42, RULE_structliteral = 43, RULE_stringliteral = 44, 
		RULE_charliteral = 45, RULE_floatliteral = 46, RULE_literalvalue = 47, 
		RULE_inlineasm = 48, RULE_subroutine = 49, RULE_sub_return_part = 50, 
		RULE_statement_block = 51, RULE_sub_params = 52, RULE_sub_returns = 53, 
		RULE_asmsubroutine = 54, RULE_romsubroutine = 55, RULE_asmsub_decl = 56, 
		RULE_asmsub_params = 57, RULE_asmsub_param = 58, RULE_asmsub_clobbers = 59, 
		RULE_clobber = 60, RULE_asmsub_returns = 61, RULE_asmsub_return = 62, 
		RULE_if_stmt = 63, RULE_else_part = 64, RULE_branch_stmt = 65, RULE_branchcondition = 66, 
		RULE_forloop = 67, RULE_whileloop = 68, RULE_repeatloop = 69, RULE_foreverloop = 70, 
		RULE_whenstmt = 71, RULE_when_choice = 72;
	private static String[] makeRuleNames() {
		return new String[] {
			"module", "block", "block_statement", "statement", "variabledeclaration", 
			"subroutinedeclaration", "labeldef", "unconditionaljump", "directive", 
			"directivearg", "vardecl", "structvardecl", "varinitializer", "structvarinitializer", 
			"constdecl", "memoryvardecl", "structdecl", "datatype", "arrayindex", 
			"assignment", "augassignment", "assign_target", "postincrdecr", "expression", 
			"typecast", "arrayindexed", "directmemory", "addressof", "functioncall", 
			"functioncall_stmt", "expression_list", "returnstmt", "breakstmt", "continuestmt", 
			"identifier", "scoped_identifier", "register", "registerorpair", "statusregister", 
			"integerliteral", "wordsuffix", "booleanliteral", "arrayliteral", "structliteral", 
			"stringliteral", "charliteral", "floatliteral", "literalvalue", "inlineasm", 
			"subroutine", "sub_return_part", "statement_block", "sub_params", "sub_returns", 
			"asmsubroutine", "romsubroutine", "asmsub_decl", "asmsub_params", "asmsub_param", 
			"asmsub_clobbers", "clobber", "asmsub_returns", "asmsub_return", "if_stmt", 
			"else_part", "branch_stmt", "branchcondition", "forloop", "whileloop", 
			"repeatloop", "foreverloop", "whenstmt", "when_choice"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'{'", "'}'", "':'", "'goto'", "'%output'", "'%launcher'", "'%zeropage'", 
			"'%zpreserved'", "'%address'", "'%import'", "'%breakpoint'", "'%asminclude'", 
			"'%asmbinary'", "'%option'", "','", "'='", "'const'", "'struct'", "'ubyte'", 
			"'byte'", "'uword'", "'word'", "'float'", "'str'", "'['", "']'", "'+='", 
			"'-='", "'/='", "'*='", "'**='", "'&='", "'|='", "'^='", "'%='", "'<<='", 
			"'>>='", "'++'", "'--'", "'+'", "'-'", "'~'", "'**'", "'*'", "'/'", "'%'", 
			"'<<'", "'>>'", "'<'", "'>'", "'<='", "'>='", "'=='", "'!='", "'^'", 
			"'|'", "'to'", "'downto'", "'step'", "'and'", "'or'", "'xor'", "'not'", 
			"'('", "')'", "'as'", "'return'", "'break'", "'continue'", "'.'", "'A'", 
			"'X'", "'Y'", "'AX'", "'AY'", "'XY'", "'Pc'", "'Pz'", "'Pn'", "'Pv'", 
			"'.w'", "'true'", "'false'", "'%asm'", "'sub'", "'->'", "'asmsub'", "'romsub'", 
			"'stack'", "'clobbers'", "'if'", "'else'", "'if_cs'", "'if_cc'", "'if_eq'", 
			"'if_z'", "'if_ne'", "'if_nz'", "'if_pl'", "'if_pos'", "'if_mi'", "'if_neg'", 
			"'if_vs'", "'if_vc'", "'for'", "'in'", "'while'", "'repeat'", "'until'", 
			"'forever'", "'when'", null, null, null, null, "'void'", null, null, 
			null, null, "'&'", "'@'", null, null, null, null, "'@zp'", "'[]'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, "LINECOMMENT", "COMMENT", "WS", "EOL", "VOID", 
			"NAME", "DEC_INTEGER", "HEX_INTEGER", "BIN_INTEGER", "ADDRESS_OF", "ALT_STRING_ENCODING", 
			"FLOAT_NUMBER", "STRING", "INLINEASMBLOCK", "SINGLECHAR", "ZEROPAGE", 
			"ARRAYSIG"
		};
	}
	private static final String[] _SYMBOLIC_NAMES = makeSymbolicNames();
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
		public List<DirectiveContext> directive() {
			return getRuleContexts(DirectiveContext.class);
		}
		public DirectiveContext directive(int i) {
			return getRuleContext(DirectiveContext.class,i);
		}
		public List<BlockContext> block() {
			return getRuleContexts(BlockContext.class);
		}
		public BlockContext block(int i) {
			return getRuleContext(BlockContext.class,i);
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
			setState(151);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13))) != 0) || _la==EOL || _la==NAME) {
				{
				setState(149);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__4:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
				case T__9:
				case T__10:
				case T__11:
				case T__12:
				case T__13:
					{
					setState(146);
					directive();
					}
					break;
				case NAME:
					{
					setState(147);
					block();
					}
					break;
				case EOL:
					{
					setState(148);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(153);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(154);
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

	public static class BlockContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public List<Block_statementContext> block_statement() {
			return getRuleContexts(Block_statementContext.class);
		}
		public Block_statementContext block_statement(int i) {
			return getRuleContext(Block_statementContext.class,i);
		}
		public BlockContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block; }
	}

	public final BlockContext block() throws RecognitionException {
		BlockContext _localctx = new BlockContext(_ctx, getState());
		enterRule(_localctx, 2, RULE_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(156);
			identifier();
			setState(158);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 118)) & ~0x3f) == 0 && ((1L << (_la - 118)) & ((1L << (DEC_INTEGER - 118)) | (1L << (HEX_INTEGER - 118)) | (1L << (BIN_INTEGER - 118)))) != 0)) {
				{
				setState(157);
				integerliteral();
				}
			}

			setState(160);
			match(T__0);
			setState(161);
			match(EOL);
			setState(166);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0) || ((((_la - 84)) & ~0x3f) == 0 && ((1L << (_la - 84)) & ((1L << (T__83 - 84)) | (1L << (T__84 - 84)) | (1L << (T__86 - 84)) | (1L << (T__87 - 84)) | (1L << (EOL - 84)) | (1L << (NAME - 84)) | (1L << (ADDRESS_OF - 84)))) != 0)) {
				{
				setState(164);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__4:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
				case T__9:
				case T__10:
				case T__11:
				case T__12:
				case T__13:
				case T__16:
				case T__17:
				case T__18:
				case T__19:
				case T__20:
				case T__21:
				case T__22:
				case T__23:
				case T__83:
				case T__84:
				case T__86:
				case T__87:
				case NAME:
				case ADDRESS_OF:
					{
					setState(162);
					block_statement();
					}
					break;
				case EOL:
					{
					setState(163);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(168);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(169);
			match(T__1);
			setState(170);
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

	public static class Block_statementContext extends ParserRuleContext {
		public DirectiveContext directive() {
			return getRuleContext(DirectiveContext.class,0);
		}
		public VariabledeclarationContext variabledeclaration() {
			return getRuleContext(VariabledeclarationContext.class,0);
		}
		public SubroutinedeclarationContext subroutinedeclaration() {
			return getRuleContext(SubroutinedeclarationContext.class,0);
		}
		public InlineasmContext inlineasm() {
			return getRuleContext(InlineasmContext.class,0);
		}
		public Block_statementContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_block_statement; }
	}

	public final Block_statementContext block_statement() throws RecognitionException {
		Block_statementContext _localctx = new Block_statementContext(_ctx, getState());
		enterRule(_localctx, 4, RULE_block_statement);
		try {
			setState(176);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__4:
			case T__5:
			case T__6:
			case T__7:
			case T__8:
			case T__9:
			case T__10:
			case T__11:
			case T__12:
			case T__13:
				enterOuterAlt(_localctx, 1);
				{
				setState(172);
				directive();
				}
				break;
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case NAME:
			case ADDRESS_OF:
				enterOuterAlt(_localctx, 2);
				{
				setState(173);
				variabledeclaration();
				}
				break;
			case T__84:
			case T__86:
			case T__87:
				enterOuterAlt(_localctx, 3);
				{
				setState(174);
				subroutinedeclaration();
				}
				break;
			case T__83:
				enterOuterAlt(_localctx, 4);
				{
				setState(175);
				inlineasm();
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

	public static class StatementContext extends ParserRuleContext {
		public DirectiveContext directive() {
			return getRuleContext(DirectiveContext.class,0);
		}
		public VariabledeclarationContext variabledeclaration() {
			return getRuleContext(VariabledeclarationContext.class,0);
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
		public SubroutinedeclarationContext subroutinedeclaration() {
			return getRuleContext(SubroutinedeclarationContext.class,0);
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
		public ForeverloopContext foreverloop() {
			return getRuleContext(ForeverloopContext.class,0);
		}
		public WhenstmtContext whenstmt() {
			return getRuleContext(WhenstmtContext.class,0);
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
			setState(198);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,6,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(178);
				directive();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(179);
				variabledeclaration();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(180);
				assignment();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(181);
				augassignment();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(182);
				unconditionaljump();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(183);
				postincrdecr();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(184);
				functioncall_stmt();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(185);
				if_stmt();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(186);
				branch_stmt();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(187);
				subroutinedeclaration();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(188);
				inlineasm();
				}
				break;
			case 12:
				enterOuterAlt(_localctx, 12);
				{
				setState(189);
				returnstmt();
				}
				break;
			case 13:
				enterOuterAlt(_localctx, 13);
				{
				setState(190);
				forloop();
				}
				break;
			case 14:
				enterOuterAlt(_localctx, 14);
				{
				setState(191);
				whileloop();
				}
				break;
			case 15:
				enterOuterAlt(_localctx, 15);
				{
				setState(192);
				repeatloop();
				}
				break;
			case 16:
				enterOuterAlt(_localctx, 16);
				{
				setState(193);
				foreverloop();
				}
				break;
			case 17:
				enterOuterAlt(_localctx, 17);
				{
				setState(194);
				whenstmt();
				}
				break;
			case 18:
				enterOuterAlt(_localctx, 18);
				{
				setState(195);
				breakstmt();
				}
				break;
			case 19:
				enterOuterAlt(_localctx, 19);
				{
				setState(196);
				continuestmt();
				}
				break;
			case 20:
				enterOuterAlt(_localctx, 20);
				{
				setState(197);
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

	public static class VariabledeclarationContext extends ParserRuleContext {
		public VarinitializerContext varinitializer() {
			return getRuleContext(VarinitializerContext.class,0);
		}
		public StructvarinitializerContext structvarinitializer() {
			return getRuleContext(StructvarinitializerContext.class,0);
		}
		public VardeclContext vardecl() {
			return getRuleContext(VardeclContext.class,0);
		}
		public StructvardeclContext structvardecl() {
			return getRuleContext(StructvardeclContext.class,0);
		}
		public ConstdeclContext constdecl() {
			return getRuleContext(ConstdeclContext.class,0);
		}
		public MemoryvardeclContext memoryvardecl() {
			return getRuleContext(MemoryvardeclContext.class,0);
		}
		public StructdeclContext structdecl() {
			return getRuleContext(StructdeclContext.class,0);
		}
		public VariabledeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_variabledeclaration; }
	}

	public final VariabledeclarationContext variabledeclaration() throws RecognitionException {
		VariabledeclarationContext _localctx = new VariabledeclarationContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_variabledeclaration);
		try {
			setState(207);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(200);
				varinitializer();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(201);
				structvarinitializer();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(202);
				vardecl();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(203);
				structvardecl();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(204);
				constdecl();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(205);
				memoryvardecl();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(206);
				structdecl();
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

	public static class SubroutinedeclarationContext extends ParserRuleContext {
		public SubroutineContext subroutine() {
			return getRuleContext(SubroutineContext.class,0);
		}
		public AsmsubroutineContext asmsubroutine() {
			return getRuleContext(AsmsubroutineContext.class,0);
		}
		public RomsubroutineContext romsubroutine() {
			return getRuleContext(RomsubroutineContext.class,0);
		}
		public SubroutinedeclarationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_subroutinedeclaration; }
	}

	public final SubroutinedeclarationContext subroutinedeclaration() throws RecognitionException {
		SubroutinedeclarationContext _localctx = new SubroutinedeclarationContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_subroutinedeclaration);
		try {
			setState(212);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__84:
				enterOuterAlt(_localctx, 1);
				{
				setState(209);
				subroutine();
				}
				break;
			case T__86:
				enterOuterAlt(_localctx, 2);
				{
				setState(210);
				asmsubroutine();
				}
				break;
			case T__87:
				enterOuterAlt(_localctx, 3);
				{
				setState(211);
				romsubroutine();
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
		enterRule(_localctx, 12, RULE_labeldef);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(214);
			identifier();
			setState(215);
			match(T__2);
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
		enterRule(_localctx, 14, RULE_unconditionaljump);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(217);
			match(T__3);
			setState(220);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				{
				setState(218);
				integerliteral();
				}
				break;
			case NAME:
				{
				setState(219);
				scoped_identifier();
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
		enterRule(_localctx, 16, RULE_directive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(222);
			((DirectiveContext)_localctx).directivename = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13))) != 0)) ) {
				((DirectiveContext)_localctx).directivename = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(234);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,12,_ctx) ) {
			case 1:
				{
				setState(224);
				_errHandler.sync(this);
				switch ( getInterpreter().adaptivePredict(_input,10,_ctx) ) {
				case 1:
					{
					setState(223);
					directivearg();
					}
					break;
				}
				}
				break;
			case 2:
				{
				setState(226);
				directivearg();
				setState(231);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__14) {
					{
					{
					setState(227);
					match(T__14);
					setState(228);
					directivearg();
					}
					}
					setState(233);
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
		enterRule(_localctx, 18, RULE_directivearg);
		try {
			setState(239);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case ALT_STRING_ENCODING:
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(236);
				stringliteral();
				}
				break;
			case NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(237);
				identifier();
				}
				break;
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(238);
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
		public IdentifierContext varname;
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public TerminalNode ZEROPAGE() { return getToken(prog8Parser.ZEROPAGE, 0); }
		public ArrayindexContext arrayindex() {
			return getRuleContext(ArrayindexContext.class,0);
		}
		public TerminalNode ARRAYSIG() { return getToken(prog8Parser.ARRAYSIG, 0); }
		public VardeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_vardecl; }
	}

	public final VardeclContext vardecl() throws RecognitionException {
		VardeclContext _localctx = new VardeclContext(_ctx, getState());
		enterRule(_localctx, 20, RULE_vardecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(241);
			datatype();
			setState(243);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ZEROPAGE) {
				{
				setState(242);
				match(ZEROPAGE);
				}
			}

			setState(247);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__24:
				{
				setState(245);
				arrayindex();
				}
				break;
			case ARRAYSIG:
				{
				setState(246);
				match(ARRAYSIG);
				}
				break;
			case NAME:
				break;
			default:
				break;
			}
			setState(249);
			((VardeclContext)_localctx).varname = identifier();
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

	public static class StructvardeclContext extends ParserRuleContext {
		public IdentifierContext structname;
		public IdentifierContext varname;
		public List<IdentifierContext> identifier() {
			return getRuleContexts(IdentifierContext.class);
		}
		public IdentifierContext identifier(int i) {
			return getRuleContext(IdentifierContext.class,i);
		}
		public StructvardeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structvardecl; }
	}

	public final StructvardeclContext structvardecl() throws RecognitionException {
		StructvardeclContext _localctx = new StructvardeclContext(_ctx, getState());
		enterRule(_localctx, 22, RULE_structvardecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(251);
			((StructvardeclContext)_localctx).structname = identifier();
			setState(252);
			((StructvardeclContext)_localctx).varname = identifier();
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
		public VardeclContext vardecl() {
			return getRuleContext(VardeclContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public VarinitializerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_varinitializer; }
	}

	public final VarinitializerContext varinitializer() throws RecognitionException {
		VarinitializerContext _localctx = new VarinitializerContext(_ctx, getState());
		enterRule(_localctx, 24, RULE_varinitializer);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(254);
			vardecl();
			setState(255);
			match(T__15);
			setState(256);
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

	public static class StructvarinitializerContext extends ParserRuleContext {
		public StructvardeclContext structvardecl() {
			return getRuleContext(StructvardeclContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public StructvarinitializerContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structvarinitializer; }
	}

	public final StructvarinitializerContext structvarinitializer() throws RecognitionException {
		StructvarinitializerContext _localctx = new StructvarinitializerContext(_ctx, getState());
		enterRule(_localctx, 26, RULE_structvarinitializer);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(258);
			structvardecl();
			setState(259);
			match(T__15);
			setState(260);
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
		enterRule(_localctx, 28, RULE_constdecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(262);
			match(T__16);
			setState(263);
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
		public TerminalNode ADDRESS_OF() { return getToken(prog8Parser.ADDRESS_OF, 0); }
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
		enterRule(_localctx, 30, RULE_memoryvardecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(265);
			match(ADDRESS_OF);
			setState(266);
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

	public static class StructdeclContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public List<VardeclContext> vardecl() {
			return getRuleContexts(VardeclContext.class);
		}
		public VardeclContext vardecl(int i) {
			return getRuleContext(VardeclContext.class,i);
		}
		public StructdeclContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structdecl; }
	}

	public final StructdeclContext structdecl() throws RecognitionException {
		StructdeclContext _localctx = new StructdeclContext(_ctx, getState());
		enterRule(_localctx, 32, RULE_structdecl);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(268);
			match(T__17);
			setState(269);
			identifier();
			setState(270);
			match(T__0);
			setState(271);
			match(EOL);
			setState(272);
			vardecl();
			setState(277);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,16,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(273);
					match(EOL);
					setState(274);
					vardecl();
					}
					} 
				}
				setState(279);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,16,_ctx);
			}
			setState(281);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(280);
				match(EOL);
				}
			}

			setState(283);
			match(T__1);
			setState(284);
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

	public static class DatatypeContext extends ParserRuleContext {
		public DatatypeContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_datatype; }
	}

	public final DatatypeContext datatype() throws RecognitionException {
		DatatypeContext _localctx = new DatatypeContext(_ctx, getState());
		enterRule(_localctx, 34, RULE_datatype);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(286);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) ) {
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

	public static class ArrayindexContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ArrayindexContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayindex; }
	}

	public final ArrayindexContext arrayindex() throws RecognitionException {
		ArrayindexContext _localctx = new ArrayindexContext(_ctx, getState());
		enterRule(_localctx, 36, RULE_arrayindex);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(288);
			match(T__24);
			setState(289);
			expression(0);
			setState(290);
			match(T__25);
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
		enterRule(_localctx, 38, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(292);
			assign_target();
			setState(293);
			match(T__15);
			setState(294);
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
		enterRule(_localctx, 40, RULE_augassignment);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(296);
			assign_target();
			setState(297);
			((AugassignmentContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36))) != 0)) ) {
				((AugassignmentContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(298);
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
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public ArrayindexedContext arrayindexed() {
			return getRuleContext(ArrayindexedContext.class,0);
		}
		public DirectmemoryContext directmemory() {
			return getRuleContext(DirectmemoryContext.class,0);
		}
		public Assign_targetContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_assign_target; }
	}

	public final Assign_targetContext assign_target() throws RecognitionException {
		Assign_targetContext _localctx = new Assign_targetContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_assign_target);
		try {
			setState(304);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(300);
				register();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(301);
				scoped_identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(302);
				arrayindexed();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(303);
				directmemory();
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
		enterRule(_localctx, 44, RULE_postincrdecr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(306);
			assign_target();
			setState(307);
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
		public Token rto;
		public ExpressionContext rangeto;
		public ExpressionContext rangestep;
		public FunctioncallContext functioncall() {
			return getRuleContext(FunctioncallContext.class,0);
		}
		public List<ExpressionContext> expression() {
			return getRuleContexts(ExpressionContext.class);
		}
		public ExpressionContext expression(int i) {
			return getRuleContext(ExpressionContext.class,i);
		}
		public LiteralvalueContext literalvalue() {
			return getRuleContext(LiteralvalueContext.class,0);
		}
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public ArrayindexedContext arrayindexed() {
			return getRuleContext(ArrayindexedContext.class,0);
		}
		public DirectmemoryContext directmemory() {
			return getRuleContext(DirectmemoryContext.class,0);
		}
		public AddressofContext addressof() {
			return getRuleContext(AddressofContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public TerminalNode ADDRESS_OF() { return getToken(prog8Parser.ADDRESS_OF, 0); }
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
		int _startState = 46;
		enterRecursionRule(_localctx, 46, RULE_expression, _p);
		int _la;
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(325);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,19,_ctx) ) {
			case 1:
				{
				setState(310);
				functioncall();
				}
				break;
			case 2:
				{
				setState(311);
				((ExpressionContext)_localctx).prefix = _input.LT(1);
				_la = _input.LA(1);
				if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__39) | (1L << T__40) | (1L << T__41))) != 0)) ) {
					((ExpressionContext)_localctx).prefix = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(312);
				expression(23);
				}
				break;
			case 3:
				{
				setState(313);
				((ExpressionContext)_localctx).prefix = match(T__62);
				setState(314);
				expression(9);
				}
				break;
			case 4:
				{
				setState(315);
				literalvalue();
				}
				break;
			case 5:
				{
				setState(316);
				register();
				}
				break;
			case 6:
				{
				setState(317);
				scoped_identifier();
				}
				break;
			case 7:
				{
				setState(318);
				arrayindexed();
				}
				break;
			case 8:
				{
				setState(319);
				directmemory();
				}
				break;
			case 9:
				{
				setState(320);
				addressof();
				}
				break;
			case 10:
				{
				setState(321);
				match(T__63);
				setState(322);
				expression(0);
				setState(323);
				match(T__64);
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(446);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(444);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,45,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(327);
						if (!(precpred(_ctx, 22))) throw new FailedPredicateException(this, "precpred(_ctx, 22)");
						setState(329);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(328);
							match(EOL);
							}
						}

						setState(331);
						((ExpressionContext)_localctx).bop = match(T__42);
						setState(333);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(332);
							match(EOL);
							}
						}

						setState(335);
						((ExpressionContext)_localctx).right = expression(23);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(336);
						if (!(precpred(_ctx, 21))) throw new FailedPredicateException(this, "precpred(_ctx, 21)");
						setState(338);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(337);
							match(EOL);
							}
						}

						setState(340);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__43) | (1L << T__44) | (1L << T__45))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
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
						((ExpressionContext)_localctx).right = expression(22);
						}
						break;
					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(345);
						if (!(precpred(_ctx, 20))) throw new FailedPredicateException(this, "precpred(_ctx, 20)");
						setState(347);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(346);
							match(EOL);
							}
						}

						setState(349);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__39 || _la==T__40) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(351);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(350);
							match(EOL);
							}
						}

						setState(353);
						((ExpressionContext)_localctx).right = expression(21);
						}
						break;
					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(354);
						if (!(precpred(_ctx, 19))) throw new FailedPredicateException(this, "precpred(_ctx, 19)");
						setState(356);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(355);
							match(EOL);
							}
						}

						setState(358);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__46 || _la==T__47) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(360);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(359);
							match(EOL);
							}
						}

						setState(362);
						((ExpressionContext)_localctx).right = expression(20);
						}
						break;
					case 5:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(363);
						if (!(precpred(_ctx, 18))) throw new FailedPredicateException(this, "precpred(_ctx, 18)");
						setState(365);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(364);
							match(EOL);
							}
						}

						setState(367);
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
						setState(369);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(368);
							match(EOL);
							}
						}

						setState(371);
						((ExpressionContext)_localctx).right = expression(19);
						}
						break;
					case 6:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(372);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(374);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(373);
							match(EOL);
							}
						}

						setState(376);
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
						((ExpressionContext)_localctx).right = expression(18);
						}
						break;
					case 7:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(381);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
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
						((ExpressionContext)_localctx).bop = match(ADDRESS_OF);
						setState(387);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(386);
							match(EOL);
							}
						}

						setState(389);
						((ExpressionContext)_localctx).right = expression(17);
						}
						break;
					case 8:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(390);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
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
						((ExpressionContext)_localctx).bop = match(T__54);
						setState(396);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(395);
							match(EOL);
							}
						}

						setState(398);
						((ExpressionContext)_localctx).right = expression(16);
						}
						break;
					case 9:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(399);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(401);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(400);
							match(EOL);
							}
						}

						setState(403);
						((ExpressionContext)_localctx).bop = match(T__55);
						setState(405);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(404);
							match(EOL);
							}
						}

						setState(407);
						((ExpressionContext)_localctx).right = expression(15);
						}
						break;
					case 10:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(408);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(410);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(409);
							match(EOL);
							}
						}

						setState(412);
						((ExpressionContext)_localctx).bop = match(T__59);
						setState(414);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(413);
							match(EOL);
							}
						}

						setState(416);
						((ExpressionContext)_localctx).right = expression(13);
						}
						break;
					case 11:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(417);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(419);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(418);
							match(EOL);
							}
						}

						setState(421);
						((ExpressionContext)_localctx).bop = match(T__60);
						setState(423);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(422);
							match(EOL);
							}
						}

						setState(425);
						((ExpressionContext)_localctx).right = expression(12);
						}
						break;
					case 12:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(426);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(428);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(427);
							match(EOL);
							}
						}

						setState(430);
						((ExpressionContext)_localctx).bop = match(T__61);
						setState(432);
						_errHandler.sync(this);
						_la = _input.LA(1);
						if (_la==EOL) {
							{
							setState(431);
							match(EOL);
							}
						}

						setState(434);
						((ExpressionContext)_localctx).right = expression(11);
						}
						break;
					case 13:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.rangefrom = _prevctx;
						_localctx.rangefrom = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(435);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(436);
						((ExpressionContext)_localctx).rto = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__56 || _la==T__57) ) {
							((ExpressionContext)_localctx).rto = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(437);
						((ExpressionContext)_localctx).rangeto = expression(0);
						setState(440);
						_errHandler.sync(this);
						switch ( getInterpreter().adaptivePredict(_input,44,_ctx) ) {
						case 1:
							{
							setState(438);
							match(T__58);
							setState(439);
							((ExpressionContext)_localctx).rangestep = expression(0);
							}
							break;
						}
						}
						break;
					case 14:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(442);
						if (!(precpred(_ctx, 2))) throw new FailedPredicateException(this, "precpred(_ctx, 2)");
						setState(443);
						typecast();
						}
						break;
					}
					} 
				}
				setState(448);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,46,_ctx);
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
		enterRule(_localctx, 48, RULE_typecast);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(449);
			match(T__65);
			setState(450);
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
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public ArrayindexContext arrayindex() {
			return getRuleContext(ArrayindexContext.class,0);
		}
		public ArrayindexedContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayindexed; }
	}

	public final ArrayindexedContext arrayindexed() throws RecognitionException {
		ArrayindexedContext _localctx = new ArrayindexedContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_arrayindexed);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(452);
			scoped_identifier();
			setState(453);
			arrayindex();
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

	public static class DirectmemoryContext extends ParserRuleContext {
		public TerminalNode ALT_STRING_ENCODING() { return getToken(prog8Parser.ALT_STRING_ENCODING, 0); }
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public DirectmemoryContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_directmemory; }
	}

	public final DirectmemoryContext directmemory() throws RecognitionException {
		DirectmemoryContext _localctx = new DirectmemoryContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_directmemory);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(455);
			match(ALT_STRING_ENCODING);
			setState(456);
			match(T__63);
			setState(457);
			expression(0);
			setState(458);
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

	public static class AddressofContext extends ParserRuleContext {
		public TerminalNode ADDRESS_OF() { return getToken(prog8Parser.ADDRESS_OF, 0); }
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public AddressofContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_addressof; }
	}

	public final AddressofContext addressof() throws RecognitionException {
		AddressofContext _localctx = new AddressofContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_addressof);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(460);
			match(ADDRESS_OF);
			setState(461);
			scoped_identifier();
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
		enterRule(_localctx, 56, RULE_functioncall);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(463);
			scoped_identifier();
			setState(464);
			match(T__63);
			setState(466);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__24) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (NAME - 64)) | (1L << (DEC_INTEGER - 64)) | (1L << (HEX_INTEGER - 64)) | (1L << (BIN_INTEGER - 64)) | (1L << (ADDRESS_OF - 64)) | (1L << (ALT_STRING_ENCODING - 64)) | (1L << (FLOAT_NUMBER - 64)) | (1L << (STRING - 64)) | (1L << (SINGLECHAR - 64)))) != 0)) {
				{
				setState(465);
				expression_list();
				}
			}

			setState(468);
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

	public static class Functioncall_stmtContext extends ParserRuleContext {
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public TerminalNode VOID() { return getToken(prog8Parser.VOID, 0); }
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
		enterRule(_localctx, 58, RULE_functioncall_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(471);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==VOID) {
				{
				setState(470);
				match(VOID);
				}
			}

			setState(473);
			scoped_identifier();
			setState(474);
			match(T__63);
			setState(476);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__24) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (NAME - 64)) | (1L << (DEC_INTEGER - 64)) | (1L << (HEX_INTEGER - 64)) | (1L << (BIN_INTEGER - 64)) | (1L << (ADDRESS_OF - 64)) | (1L << (ALT_STRING_ENCODING - 64)) | (1L << (FLOAT_NUMBER - 64)) | (1L << (STRING - 64)) | (1L << (SINGLECHAR - 64)))) != 0)) {
				{
				setState(475);
				expression_list();
				}
			}

			setState(478);
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
		enterRule(_localctx, 60, RULE_expression_list);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(480);
			expression(0);
			setState(488);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(481);
				match(T__14);
				setState(483);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(482);
					match(EOL);
					}
				}

				setState(485);
				expression(0);
				}
				}
				setState(490);
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
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public ReturnstmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_returnstmt; }
	}

	public final ReturnstmtContext returnstmt() throws RecognitionException {
		ReturnstmtContext _localctx = new ReturnstmtContext(_ctx, getState());
		enterRule(_localctx, 62, RULE_returnstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(491);
			match(T__66);
			setState(493);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,52,_ctx) ) {
			case 1:
				{
				setState(492);
				expression(0);
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
		enterRule(_localctx, 64, RULE_breakstmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(495);
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

	public static class ContinuestmtContext extends ParserRuleContext {
		public ContinuestmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_continuestmt; }
	}

	public final ContinuestmtContext continuestmt() throws RecognitionException {
		ContinuestmtContext _localctx = new ContinuestmtContext(_ctx, getState());
		enterRule(_localctx, 66, RULE_continuestmt);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(497);
			match(T__68);
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
		enterRule(_localctx, 68, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(499);
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
		enterRule(_localctx, 70, RULE_scoped_identifier);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(501);
			match(NAME);
			setState(506);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,53,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(502);
					match(T__69);
					setState(503);
					match(NAME);
					}
					} 
				}
				setState(508);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,53,_ctx);
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

	public static class RegisterContext extends ParserRuleContext {
		public RegisterContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_register; }
	}

	public final RegisterContext register() throws RecognitionException {
		RegisterContext _localctx = new RegisterContext(_ctx, getState());
		enterRule(_localctx, 72, RULE_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(509);
			_la = _input.LA(1);
			if ( !(((((_la - 71)) & ~0x3f) == 0 && ((1L << (_la - 71)) & ((1L << (T__70 - 71)) | (1L << (T__71 - 71)) | (1L << (T__72 - 71)))) != 0)) ) {
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
		enterRule(_localctx, 74, RULE_registerorpair);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(511);
			_la = _input.LA(1);
			if ( !(((((_la - 71)) & ~0x3f) == 0 && ((1L << (_la - 71)) & ((1L << (T__70 - 71)) | (1L << (T__71 - 71)) | (1L << (T__72 - 71)) | (1L << (T__73 - 71)) | (1L << (T__74 - 71)) | (1L << (T__75 - 71)))) != 0)) ) {
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
		enterRule(_localctx, 76, RULE_statusregister);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(513);
			_la = _input.LA(1);
			if ( !(((((_la - 77)) & ~0x3f) == 0 && ((1L << (_la - 77)) & ((1L << (T__76 - 77)) | (1L << (T__77 - 77)) | (1L << (T__78 - 77)) | (1L << (T__79 - 77)))) != 0)) ) {
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
		enterRule(_localctx, 78, RULE_integerliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(515);
			((IntegerliteralContext)_localctx).intpart = _input.LT(1);
			_la = _input.LA(1);
			if ( !(((((_la - 118)) & ~0x3f) == 0 && ((1L << (_la - 118)) & ((1L << (DEC_INTEGER - 118)) | (1L << (HEX_INTEGER - 118)) | (1L << (BIN_INTEGER - 118)))) != 0)) ) {
				((IntegerliteralContext)_localctx).intpart = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(517);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,54,_ctx) ) {
			case 1:
				{
				setState(516);
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
		enterRule(_localctx, 80, RULE_wordsuffix);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(519);
			match(T__80);
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
		enterRule(_localctx, 82, RULE_booleanliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(521);
			_la = _input.LA(1);
			if ( !(_la==T__81 || _la==T__82) ) {
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
		enterRule(_localctx, 84, RULE_arrayliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(523);
			match(T__24);
			setState(525);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(524);
				match(EOL);
				}
			}

			setState(527);
			expression(0);
			setState(535);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(528);
				match(T__14);
				setState(530);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(529);
					match(EOL);
					}
				}

				setState(532);
				expression(0);
				}
				}
				setState(537);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(539);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(538);
				match(EOL);
				}
			}

			setState(541);
			match(T__25);
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

	public static class StructliteralContext extends ParserRuleContext {
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
		public StructliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_structliteral; }
	}

	public final StructliteralContext structliteral() throws RecognitionException {
		StructliteralContext _localctx = new StructliteralContext(_ctx, getState());
		enterRule(_localctx, 86, RULE_structliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(543);
			match(T__0);
			setState(545);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(544);
				match(EOL);
				}
			}

			setState(547);
			expression(0);
			setState(555);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(548);
				match(T__14);
				setState(550);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(549);
					match(EOL);
					}
				}

				setState(552);
				expression(0);
				}
				}
				setState(557);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(559);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(558);
				match(EOL);
				}
			}

			setState(561);
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

	public static class StringliteralContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(prog8Parser.STRING, 0); }
		public TerminalNode ALT_STRING_ENCODING() { return getToken(prog8Parser.ALT_STRING_ENCODING, 0); }
		public StringliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stringliteral; }
	}

	public final StringliteralContext stringliteral() throws RecognitionException {
		StringliteralContext _localctx = new StringliteralContext(_ctx, getState());
		enterRule(_localctx, 88, RULE_stringliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(564);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ALT_STRING_ENCODING) {
				{
				setState(563);
				match(ALT_STRING_ENCODING);
				}
			}

			setState(566);
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
		public TerminalNode ALT_STRING_ENCODING() { return getToken(prog8Parser.ALT_STRING_ENCODING, 0); }
		public CharliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_charliteral; }
	}

	public final CharliteralContext charliteral() throws RecognitionException {
		CharliteralContext _localctx = new CharliteralContext(_ctx, getState());
		enterRule(_localctx, 90, RULE_charliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(569);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==ALT_STRING_ENCODING) {
				{
				setState(568);
				match(ALT_STRING_ENCODING);
				}
			}

			setState(571);
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
		enterRule(_localctx, 92, RULE_floatliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(573);
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
		public StructliteralContext structliteral() {
			return getRuleContext(StructliteralContext.class,0);
		}
		public LiteralvalueContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_literalvalue; }
	}

	public final LiteralvalueContext literalvalue() throws RecognitionException {
		LiteralvalueContext _localctx = new LiteralvalueContext(_ctx, getState());
		enterRule(_localctx, 94, RULE_literalvalue);
		try {
			setState(582);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,65,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(575);
				integerliteral();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(576);
				booleanliteral();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(577);
				arrayliteral();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(578);
				stringliteral();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(579);
				charliteral();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(580);
				floatliteral();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(581);
				structliteral();
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

	public static class InlineasmContext extends ParserRuleContext {
		public TerminalNode INLINEASMBLOCK() { return getToken(prog8Parser.INLINEASMBLOCK, 0); }
		public InlineasmContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inlineasm; }
	}

	public final InlineasmContext inlineasm() throws RecognitionException {
		InlineasmContext _localctx = new InlineasmContext(_ctx, getState());
		enterRule(_localctx, 96, RULE_inlineasm);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(584);
			match(T__83);
			setState(585);
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
		enterRule(_localctx, 98, RULE_subroutine);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(587);
			match(T__84);
			setState(588);
			identifier();
			setState(589);
			match(T__63);
			setState(591);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) {
				{
				setState(590);
				sub_params();
				}
			}

			setState(593);
			match(T__64);
			setState(595);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__85) {
				{
				setState(594);
				sub_return_part();
				}
			}

			{
			setState(597);
			statement_block();
			setState(598);
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
		enterRule(_localctx, 100, RULE_sub_return_part);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(600);
			match(T__85);
			setState(601);
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
		enterRule(_localctx, 102, RULE_statement_block);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(603);
			match(T__0);
			setState(604);
			match(EOL);
			setState(609);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__3) | (1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__13) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0) || ((((_la - 67)) & ~0x3f) == 0 && ((1L << (_la - 67)) & ((1L << (T__66 - 67)) | (1L << (T__67 - 67)) | (1L << (T__68 - 67)) | (1L << (T__70 - 67)) | (1L << (T__71 - 67)) | (1L << (T__72 - 67)) | (1L << (T__83 - 67)) | (1L << (T__84 - 67)) | (1L << (T__86 - 67)) | (1L << (T__87 - 67)) | (1L << (T__90 - 67)) | (1L << (T__92 - 67)) | (1L << (T__93 - 67)) | (1L << (T__94 - 67)) | (1L << (T__95 - 67)) | (1L << (T__96 - 67)) | (1L << (T__97 - 67)) | (1L << (T__98 - 67)) | (1L << (T__99 - 67)) | (1L << (T__100 - 67)) | (1L << (T__101 - 67)) | (1L << (T__102 - 67)) | (1L << (T__103 - 67)) | (1L << (T__104 - 67)) | (1L << (T__106 - 67)) | (1L << (T__107 - 67)) | (1L << (T__109 - 67)) | (1L << (T__110 - 67)) | (1L << (EOL - 67)) | (1L << (VOID - 67)) | (1L << (NAME - 67)) | (1L << (ADDRESS_OF - 67)) | (1L << (ALT_STRING_ENCODING - 67)))) != 0)) {
				{
				setState(607);
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
				case T__13:
				case T__16:
				case T__17:
				case T__18:
				case T__19:
				case T__20:
				case T__21:
				case T__22:
				case T__23:
				case T__66:
				case T__67:
				case T__68:
				case T__70:
				case T__71:
				case T__72:
				case T__83:
				case T__84:
				case T__86:
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
				case T__109:
				case T__110:
				case VOID:
				case NAME:
				case ADDRESS_OF:
				case ALT_STRING_ENCODING:
					{
					setState(605);
					statement();
					}
					break;
				case EOL:
					{
					setState(606);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(611);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(612);
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
		enterRule(_localctx, 104, RULE_sub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(614);
			vardecl();
			setState(622);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(615);
				match(T__14);
				setState(617);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(616);
					match(EOL);
					}
				}

				setState(619);
				vardecl();
				}
				}
				setState(624);
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
		enterRule(_localctx, 106, RULE_sub_returns);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(625);
			datatype();
			setState(633);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(626);
				match(T__14);
				setState(628);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(627);
					match(EOL);
					}
				}

				setState(630);
				datatype();
				}
				}
				setState(635);
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
		public Asmsub_declContext asmsub_decl() {
			return getRuleContext(Asmsub_declContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public AsmsubroutineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsubroutine; }
	}

	public final AsmsubroutineContext asmsubroutine() throws RecognitionException {
		AsmsubroutineContext _localctx = new AsmsubroutineContext(_ctx, getState());
		enterRule(_localctx, 108, RULE_asmsubroutine);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(636);
			match(T__86);
			setState(637);
			asmsub_decl();
			setState(638);
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

	public static class RomsubroutineContext extends ParserRuleContext {
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public Asmsub_declContext asmsub_decl() {
			return getRuleContext(Asmsub_declContext.class,0);
		}
		public RomsubroutineContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_romsubroutine; }
	}

	public final RomsubroutineContext romsubroutine() throws RecognitionException {
		RomsubroutineContext _localctx = new RomsubroutineContext(_ctx, getState());
		enterRule(_localctx, 110, RULE_romsubroutine);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(640);
			match(T__87);
			setState(641);
			integerliteral();
			setState(642);
			match(T__15);
			setState(643);
			asmsub_decl();
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

	public static class Asmsub_declContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Asmsub_paramsContext asmsub_params() {
			return getRuleContext(Asmsub_paramsContext.class,0);
		}
		public Asmsub_clobbersContext asmsub_clobbers() {
			return getRuleContext(Asmsub_clobbersContext.class,0);
		}
		public Asmsub_returnsContext asmsub_returns() {
			return getRuleContext(Asmsub_returnsContext.class,0);
		}
		public Asmsub_declContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_decl; }
	}

	public final Asmsub_declContext asmsub_decl() throws RecognitionException {
		Asmsub_declContext _localctx = new Asmsub_declContext(_ctx, getState());
		enterRule(_localctx, 112, RULE_asmsub_decl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(645);
			identifier();
			setState(646);
			match(T__63);
			setState(648);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) {
				{
				setState(647);
				asmsub_params();
				}
			}

			setState(650);
			match(T__64);
			setState(652);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__89) {
				{
				setState(651);
				asmsub_clobbers();
				}
			}

			setState(655);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__85) {
				{
				setState(654);
				asmsub_returns();
				}
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
		enterRule(_localctx, 114, RULE_asmsub_params);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(657);
			asmsub_param();
			setState(665);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(658);
				match(T__14);
				setState(660);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(659);
					match(EOL);
					}
				}

				setState(662);
				asmsub_param();
				}
				}
				setState(667);
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
		public Token stack;
		public VardeclContext vardecl() {
			return getRuleContext(VardeclContext.class,0);
		}
		public TerminalNode ALT_STRING_ENCODING() { return getToken(prog8Parser.ALT_STRING_ENCODING, 0); }
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
		enterRule(_localctx, 116, RULE_asmsub_param);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(668);
			vardecl();
			setState(669);
			match(ALT_STRING_ENCODING);
			setState(673);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__74:
			case T__75:
				{
				setState(670);
				registerorpair();
				}
				break;
			case T__76:
			case T__77:
			case T__78:
			case T__79:
				{
				setState(671);
				statusregister();
				}
				break;
			case T__88:
				{
				setState(672);
				((Asmsub_paramContext)_localctx).stack = match(T__88);
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

	public static class Asmsub_clobbersContext extends ParserRuleContext {
		public ClobberContext clobber() {
			return getRuleContext(ClobberContext.class,0);
		}
		public Asmsub_clobbersContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_asmsub_clobbers; }
	}

	public final Asmsub_clobbersContext asmsub_clobbers() throws RecognitionException {
		Asmsub_clobbersContext _localctx = new Asmsub_clobbersContext(_ctx, getState());
		enterRule(_localctx, 118, RULE_asmsub_clobbers);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(675);
			match(T__89);
			setState(676);
			match(T__63);
			setState(678);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 71)) & ~0x3f) == 0 && ((1L << (_la - 71)) & ((1L << (T__70 - 71)) | (1L << (T__71 - 71)) | (1L << (T__72 - 71)))) != 0)) {
				{
				setState(677);
				clobber();
				}
			}

			setState(680);
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
		enterRule(_localctx, 120, RULE_clobber);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(682);
			register();
			setState(687);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(683);
				match(T__14);
				setState(684);
				register();
				}
				}
				setState(689);
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
		enterRule(_localctx, 122, RULE_asmsub_returns);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(690);
			match(T__85);
			setState(691);
			asmsub_return();
			setState(699);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__14) {
				{
				{
				setState(692);
				match(T__14);
				setState(694);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (_la==EOL) {
					{
					setState(693);
					match(EOL);
					}
				}

				setState(696);
				asmsub_return();
				}
				}
				setState(701);
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
		public Token stack;
		public DatatypeContext datatype() {
			return getRuleContext(DatatypeContext.class,0);
		}
		public TerminalNode ALT_STRING_ENCODING() { return getToken(prog8Parser.ALT_STRING_ENCODING, 0); }
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
		enterRule(_localctx, 124, RULE_asmsub_return);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(702);
			datatype();
			setState(703);
			match(ALT_STRING_ENCODING);
			setState(707);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__70:
			case T__71:
			case T__72:
			case T__73:
			case T__74:
			case T__75:
				{
				setState(704);
				registerorpair();
				}
				break;
			case T__76:
			case T__77:
			case T__78:
			case T__79:
				{
				setState(705);
				statusregister();
				}
				break;
			case T__88:
				{
				setState(706);
				((Asmsub_returnContext)_localctx).stack = match(T__88);
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
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
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
		enterRule(_localctx, 126, RULE_if_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(709);
			match(T__90);
			setState(710);
			expression(0);
			setState(712);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(711);
				match(EOL);
				}
			}

			setState(716);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(714);
				statement();
				}
				break;
			case T__0:
				{
				setState(715);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(719);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,87,_ctx) ) {
			case 1:
				{
				setState(718);
				match(EOL);
				}
				break;
			}
			setState(722);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,88,_ctx) ) {
			case 1:
				{
				setState(721);
				else_part();
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
		enterRule(_localctx, 128, RULE_else_part);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(724);
			match(T__91);
			setState(726);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(725);
				match(EOL);
				}
			}

			setState(730);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(728);
				statement();
				}
				break;
			case T__0:
				{
				setState(729);
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
		enterRule(_localctx, 130, RULE_branch_stmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(732);
			branchcondition();
			setState(734);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(733);
				match(EOL);
				}
			}

			setState(738);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(736);
				statement();
				}
				break;
			case T__0:
				{
				setState(737);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(741);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,93,_ctx) ) {
			case 1:
				{
				setState(740);
				match(EOL);
				}
				break;
			}
			setState(744);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__91) {
				{
				setState(743);
				else_part();
				}
			}

			setState(746);
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
		enterRule(_localctx, 132, RULE_branchcondition);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(748);
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
		public RegisterContext register() {
			return getRuleContext(RegisterContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public ForloopContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_forloop; }
	}

	public final ForloopContext forloop() throws RecognitionException {
		ForloopContext _localctx = new ForloopContext(_ctx, getState());
		enterRule(_localctx, 134, RULE_forloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(750);
			match(T__104);
			setState(753);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__70:
			case T__71:
			case T__72:
				{
				setState(751);
				register();
				}
				break;
			case NAME:
				{
				setState(752);
				identifier();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(755);
			match(T__105);
			setState(756);
			expression(0);
			setState(758);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(757);
				match(EOL);
				}
			}

			setState(762);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(760);
				statement();
				}
				break;
			case T__0:
				{
				setState(761);
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
		enterRule(_localctx, 136, RULE_whileloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(764);
			match(T__106);
			setState(765);
			expression(0);
			setState(767);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(766);
				match(EOL);
				}
			}

			setState(771);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(769);
				statement();
				}
				break;
			case T__0:
				{
				setState(770);
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
		enterRule(_localctx, 138, RULE_repeatloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(773);
			match(T__107);
			setState(776);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(774);
				statement();
				}
				break;
			case T__0:
				{
				setState(775);
				statement_block();
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(779);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(778);
				match(EOL);
				}
			}

			setState(781);
			match(T__108);
			setState(782);
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

	public static class ForeverloopContext extends ParserRuleContext {
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public TerminalNode EOL() { return getToken(prog8Parser.EOL, 0); }
		public ForeverloopContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_foreverloop; }
	}

	public final ForeverloopContext foreverloop() throws RecognitionException {
		ForeverloopContext _localctx = new ForeverloopContext(_ctx, getState());
		enterRule(_localctx, 140, RULE_foreverloop);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(784);
			match(T__109);
			setState(786);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==EOL) {
				{
				setState(785);
				match(EOL);
				}
			}

			setState(790);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(788);
				statement();
				}
				break;
			case T__0:
				{
				setState(789);
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

	public static class WhenstmtContext extends ParserRuleContext {
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
		}
		public List<TerminalNode> EOL() { return getTokens(prog8Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(prog8Parser.EOL, i);
		}
		public List<When_choiceContext> when_choice() {
			return getRuleContexts(When_choiceContext.class);
		}
		public When_choiceContext when_choice(int i) {
			return getRuleContext(When_choiceContext.class,i);
		}
		public WhenstmtContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_whenstmt; }
	}

	public final WhenstmtContext whenstmt() throws RecognitionException {
		WhenstmtContext _localctx = new WhenstmtContext(_ctx, getState());
		enterRule(_localctx, 142, RULE_whenstmt);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(792);
			match(T__110);
			setState(793);
			expression(0);
			setState(794);
			match(T__0);
			setState(795);
			match(EOL);
			setState(800);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__24) | (1L << T__39) | (1L << T__40) | (1L << T__41) | (1L << T__62))) != 0) || ((((_la - 64)) & ~0x3f) == 0 && ((1L << (_la - 64)) & ((1L << (T__63 - 64)) | (1L << (T__70 - 64)) | (1L << (T__71 - 64)) | (1L << (T__72 - 64)) | (1L << (T__81 - 64)) | (1L << (T__82 - 64)) | (1L << (T__91 - 64)) | (1L << (EOL - 64)) | (1L << (NAME - 64)) | (1L << (DEC_INTEGER - 64)) | (1L << (HEX_INTEGER - 64)) | (1L << (BIN_INTEGER - 64)) | (1L << (ADDRESS_OF - 64)) | (1L << (ALT_STRING_ENCODING - 64)) | (1L << (FLOAT_NUMBER - 64)) | (1L << (STRING - 64)) | (1L << (SINGLECHAR - 64)))) != 0)) {
				{
				setState(798);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__24:
				case T__39:
				case T__40:
				case T__41:
				case T__62:
				case T__63:
				case T__70:
				case T__71:
				case T__72:
				case T__81:
				case T__82:
				case T__91:
				case NAME:
				case DEC_INTEGER:
				case HEX_INTEGER:
				case BIN_INTEGER:
				case ADDRESS_OF:
				case ALT_STRING_ENCODING:
				case FLOAT_NUMBER:
				case STRING:
				case SINGLECHAR:
					{
					setState(796);
					when_choice();
					}
					break;
				case EOL:
					{
					setState(797);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(802);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(803);
			match(T__1);
			setState(805);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,106,_ctx) ) {
			case 1:
				{
				setState(804);
				match(EOL);
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

	public static class When_choiceContext extends ParserRuleContext {
		public Expression_listContext expression_list() {
			return getRuleContext(Expression_listContext.class,0);
		}
		public StatementContext statement() {
			return getRuleContext(StatementContext.class,0);
		}
		public Statement_blockContext statement_block() {
			return getRuleContext(Statement_blockContext.class,0);
		}
		public When_choiceContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_when_choice; }
	}

	public final When_choiceContext when_choice() throws RecognitionException {
		When_choiceContext _localctx = new When_choiceContext(_ctx, getState());
		enterRule(_localctx, 144, RULE_when_choice);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(809);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case T__0:
			case T__24:
			case T__39:
			case T__40:
			case T__41:
			case T__62:
			case T__63:
			case T__70:
			case T__71:
			case T__72:
			case T__81:
			case T__82:
			case NAME:
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
			case FLOAT_NUMBER:
			case STRING:
			case SINGLECHAR:
				{
				setState(807);
				expression_list();
				}
				break;
			case T__91:
				{
				setState(808);
				match(T__91);
				}
				break;
			default:
				throw new NoViableAltException(this);
			}
			setState(811);
			match(T__85);
			setState(814);
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
			case T__13:
			case T__16:
			case T__17:
			case T__18:
			case T__19:
			case T__20:
			case T__21:
			case T__22:
			case T__23:
			case T__66:
			case T__67:
			case T__68:
			case T__70:
			case T__71:
			case T__72:
			case T__83:
			case T__84:
			case T__86:
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
			case T__109:
			case T__110:
			case VOID:
			case NAME:
			case ADDRESS_OF:
			case ALT_STRING_ENCODING:
				{
				setState(812);
				statement();
				}
				break;
			case T__0:
				{
				setState(813);
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

	public boolean sempred(RuleContext _localctx, int ruleIndex, int predIndex) {
		switch (ruleIndex) {
		case 23:
			return expression_sempred((ExpressionContext)_localctx, predIndex);
		}
		return true;
	}
	private boolean expression_sempred(ExpressionContext _localctx, int predIndex) {
		switch (predIndex) {
		case 0:
			return precpred(_ctx, 22);
		case 1:
			return precpred(_ctx, 21);
		case 2:
			return precpred(_ctx, 20);
		case 3:
			return precpred(_ctx, 19);
		case 4:
			return precpred(_ctx, 18);
		case 5:
			return precpred(_ctx, 17);
		case 6:
			return precpred(_ctx, 16);
		case 7:
			return precpred(_ctx, 15);
		case 8:
			return precpred(_ctx, 14);
		case 9:
			return precpred(_ctx, 12);
		case 10:
			return precpred(_ctx, 11);
		case 11:
			return precpred(_ctx, 10);
		case 12:
			return precpred(_ctx, 13);
		case 13:
			return precpred(_ctx, 2);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3\u0082\u0333\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\4\61\t\61\4\62\t\62\4\63\t\63\4\64\t"+
		"\64\4\65\t\65\4\66\t\66\4\67\t\67\48\t8\49\t9\4:\t:\4;\t;\4<\t<\4=\t="+
		"\4>\t>\4?\t?\4@\t@\4A\tA\4B\tB\4C\tC\4D\tD\4E\tE\4F\tF\4G\tG\4H\tH\4I"+
		"\tI\4J\tJ\3\2\3\2\3\2\7\2\u0098\n\2\f\2\16\2\u009b\13\2\3\2\3\2\3\3\3"+
		"\3\5\3\u00a1\n\3\3\3\3\3\3\3\3\3\7\3\u00a7\n\3\f\3\16\3\u00aa\13\3\3\3"+
		"\3\3\3\3\3\4\3\4\3\4\3\4\5\4\u00b3\n\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5"+
		"\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\3\5\5\5\u00c9\n\5\3\6\3\6"+
		"\3\6\3\6\3\6\3\6\3\6\5\6\u00d2\n\6\3\7\3\7\3\7\5\7\u00d7\n\7\3\b\3\b\3"+
		"\b\3\t\3\t\3\t\5\t\u00df\n\t\3\n\3\n\5\n\u00e3\n\n\3\n\3\n\3\n\7\n\u00e8"+
		"\n\n\f\n\16\n\u00eb\13\n\5\n\u00ed\n\n\3\13\3\13\3\13\5\13\u00f2\n\13"+
		"\3\f\3\f\5\f\u00f6\n\f\3\f\3\f\5\f\u00fa\n\f\3\f\3\f\3\r\3\r\3\r\3\16"+
		"\3\16\3\16\3\16\3\17\3\17\3\17\3\17\3\20\3\20\3\20\3\21\3\21\3\21\3\22"+
		"\3\22\3\22\3\22\3\22\3\22\3\22\7\22\u0116\n\22\f\22\16\22\u0119\13\22"+
		"\3\22\5\22\u011c\n\22\3\22\3\22\3\22\3\23\3\23\3\24\3\24\3\24\3\24\3\25"+
		"\3\25\3\25\3\25\3\26\3\26\3\26\3\26\3\27\3\27\3\27\3\27\5\27\u0133\n\27"+
		"\3\30\3\30\3\30\3\31\3\31\3\31\3\31\3\31\3\31\3\31\3\31\3\31\3\31\3\31"+
		"\3\31\3\31\3\31\3\31\3\31\5\31\u0148\n\31\3\31\3\31\5\31\u014c\n\31\3"+
		"\31\3\31\5\31\u0150\n\31\3\31\3\31\3\31\5\31\u0155\n\31\3\31\3\31\5\31"+
		"\u0159\n\31\3\31\3\31\3\31\5\31\u015e\n\31\3\31\3\31\5\31\u0162\n\31\3"+
		"\31\3\31\3\31\5\31\u0167\n\31\3\31\3\31\5\31\u016b\n\31\3\31\3\31\3\31"+
		"\5\31\u0170\n\31\3\31\3\31\5\31\u0174\n\31\3\31\3\31\3\31\5\31\u0179\n"+
		"\31\3\31\3\31\5\31\u017d\n\31\3\31\3\31\3\31\5\31\u0182\n\31\3\31\3\31"+
		"\5\31\u0186\n\31\3\31\3\31\3\31\5\31\u018b\n\31\3\31\3\31\5\31\u018f\n"+
		"\31\3\31\3\31\3\31\5\31\u0194\n\31\3\31\3\31\5\31\u0198\n\31\3\31\3\31"+
		"\3\31\5\31\u019d\n\31\3\31\3\31\5\31\u01a1\n\31\3\31\3\31\3\31\5\31\u01a6"+
		"\n\31\3\31\3\31\5\31\u01aa\n\31\3\31\3\31\3\31\5\31\u01af\n\31\3\31\3"+
		"\31\5\31\u01b3\n\31\3\31\3\31\3\31\3\31\3\31\3\31\5\31\u01bb\n\31\3\31"+
		"\3\31\7\31\u01bf\n\31\f\31\16\31\u01c2\13\31\3\32\3\32\3\32\3\33\3\33"+
		"\3\33\3\34\3\34\3\34\3\34\3\34\3\35\3\35\3\35\3\36\3\36\3\36\5\36\u01d5"+
		"\n\36\3\36\3\36\3\37\5\37\u01da\n\37\3\37\3\37\3\37\5\37\u01df\n\37\3"+
		"\37\3\37\3 \3 \3 \5 \u01e6\n \3 \7 \u01e9\n \f \16 \u01ec\13 \3!\3!\5"+
		"!\u01f0\n!\3\"\3\"\3#\3#\3$\3$\3%\3%\3%\7%\u01fb\n%\f%\16%\u01fe\13%\3"+
		"&\3&\3\'\3\'\3(\3(\3)\3)\5)\u0208\n)\3*\3*\3+\3+\3,\3,\5,\u0210\n,\3,"+
		"\3,\3,\5,\u0215\n,\3,\7,\u0218\n,\f,\16,\u021b\13,\3,\5,\u021e\n,\3,\3"+
		",\3-\3-\5-\u0224\n-\3-\3-\3-\5-\u0229\n-\3-\7-\u022c\n-\f-\16-\u022f\13"+
		"-\3-\5-\u0232\n-\3-\3-\3.\5.\u0237\n.\3.\3.\3/\5/\u023c\n/\3/\3/\3\60"+
		"\3\60\3\61\3\61\3\61\3\61\3\61\3\61\3\61\5\61\u0249\n\61\3\62\3\62\3\62"+
		"\3\63\3\63\3\63\3\63\5\63\u0252\n\63\3\63\3\63\5\63\u0256\n\63\3\63\3"+
		"\63\3\63\3\64\3\64\3\64\3\65\3\65\3\65\3\65\7\65\u0262\n\65\f\65\16\65"+
		"\u0265\13\65\3\65\3\65\3\66\3\66\3\66\5\66\u026c\n\66\3\66\7\66\u026f"+
		"\n\66\f\66\16\66\u0272\13\66\3\67\3\67\3\67\5\67\u0277\n\67\3\67\7\67"+
		"\u027a\n\67\f\67\16\67\u027d\13\67\38\38\38\38\39\39\39\39\39\3:\3:\3"+
		":\5:\u028b\n:\3:\3:\5:\u028f\n:\3:\5:\u0292\n:\3;\3;\3;\5;\u0297\n;\3"+
		";\7;\u029a\n;\f;\16;\u029d\13;\3<\3<\3<\3<\3<\5<\u02a4\n<\3=\3=\3=\5="+
		"\u02a9\n=\3=\3=\3>\3>\3>\7>\u02b0\n>\f>\16>\u02b3\13>\3?\3?\3?\3?\5?\u02b9"+
		"\n?\3?\7?\u02bc\n?\f?\16?\u02bf\13?\3@\3@\3@\3@\3@\5@\u02c6\n@\3A\3A\3"+
		"A\5A\u02cb\nA\3A\3A\5A\u02cf\nA\3A\5A\u02d2\nA\3A\5A\u02d5\nA\3B\3B\5"+
		"B\u02d9\nB\3B\3B\5B\u02dd\nB\3C\3C\5C\u02e1\nC\3C\3C\5C\u02e5\nC\3C\5"+
		"C\u02e8\nC\3C\5C\u02eb\nC\3C\3C\3D\3D\3E\3E\3E\5E\u02f4\nE\3E\3E\3E\5"+
		"E\u02f9\nE\3E\3E\5E\u02fd\nE\3F\3F\3F\5F\u0302\nF\3F\3F\5F\u0306\nF\3"+
		"G\3G\3G\5G\u030b\nG\3G\5G\u030e\nG\3G\3G\3G\3H\3H\5H\u0315\nH\3H\3H\5"+
		"H\u0319\nH\3I\3I\3I\3I\3I\3I\7I\u0321\nI\fI\16I\u0324\13I\3I\3I\5I\u0328"+
		"\nI\3J\3J\5J\u032c\nJ\3J\3J\3J\5J\u0331\nJ\3J\2\3\60K\2\4\6\b\n\f\16\20"+
		"\22\24\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>@BDFHJLNPRTVXZ\\^`bdfhj"+
		"lnprtvxz|~\u0080\u0082\u0084\u0086\u0088\u008a\u008c\u008e\u0090\u0092"+
		"\2\23\3\2\7\20\3\2\25\32\3\2\35\'\3\2()\3\2*,\3\2.\60\3\2*+\3\2\61\62"+
		"\3\2\63\66\3\2\678\3\2;<\3\2IK\3\2IN\3\2OR\3\2xz\3\2TU\3\2_j\2\u0390\2"+
		"\u0099\3\2\2\2\4\u009e\3\2\2\2\6\u00b2\3\2\2\2\b\u00c8\3\2\2\2\n\u00d1"+
		"\3\2\2\2\f\u00d6\3\2\2\2\16\u00d8\3\2\2\2\20\u00db\3\2\2\2\22\u00e0\3"+
		"\2\2\2\24\u00f1\3\2\2\2\26\u00f3\3\2\2\2\30\u00fd\3\2\2\2\32\u0100\3\2"+
		"\2\2\34\u0104\3\2\2\2\36\u0108\3\2\2\2 \u010b\3\2\2\2\"\u010e\3\2\2\2"+
		"$\u0120\3\2\2\2&\u0122\3\2\2\2(\u0126\3\2\2\2*\u012a\3\2\2\2,\u0132\3"+
		"\2\2\2.\u0134\3\2\2\2\60\u0147\3\2\2\2\62\u01c3\3\2\2\2\64\u01c6\3\2\2"+
		"\2\66\u01c9\3\2\2\28\u01ce\3\2\2\2:\u01d1\3\2\2\2<\u01d9\3\2\2\2>\u01e2"+
		"\3\2\2\2@\u01ed\3\2\2\2B\u01f1\3\2\2\2D\u01f3\3\2\2\2F\u01f5\3\2\2\2H"+
		"\u01f7\3\2\2\2J\u01ff\3\2\2\2L\u0201\3\2\2\2N\u0203\3\2\2\2P\u0205\3\2"+
		"\2\2R\u0209\3\2\2\2T\u020b\3\2\2\2V\u020d\3\2\2\2X\u0221\3\2\2\2Z\u0236"+
		"\3\2\2\2\\\u023b\3\2\2\2^\u023f\3\2\2\2`\u0248\3\2\2\2b\u024a\3\2\2\2"+
		"d\u024d\3\2\2\2f\u025a\3\2\2\2h\u025d\3\2\2\2j\u0268\3\2\2\2l\u0273\3"+
		"\2\2\2n\u027e\3\2\2\2p\u0282\3\2\2\2r\u0287\3\2\2\2t\u0293\3\2\2\2v\u029e"+
		"\3\2\2\2x\u02a5\3\2\2\2z\u02ac\3\2\2\2|\u02b4\3\2\2\2~\u02c0\3\2\2\2\u0080"+
		"\u02c7\3\2\2\2\u0082\u02d6\3\2\2\2\u0084\u02de\3\2\2\2\u0086\u02ee\3\2"+
		"\2\2\u0088\u02f0\3\2\2\2\u008a\u02fe\3\2\2\2\u008c\u0307\3\2\2\2\u008e"+
		"\u0312\3\2\2\2\u0090\u031a\3\2\2\2\u0092\u032b\3\2\2\2\u0094\u0098\5\22"+
		"\n\2\u0095\u0098\5\4\3\2\u0096\u0098\7u\2\2\u0097\u0094\3\2\2\2\u0097"+
		"\u0095\3\2\2\2\u0097\u0096\3\2\2\2\u0098\u009b\3\2\2\2\u0099\u0097\3\2"+
		"\2\2\u0099\u009a\3\2\2\2\u009a\u009c\3\2\2\2\u009b\u0099\3\2\2\2\u009c"+
		"\u009d\7\2\2\3\u009d\3\3\2\2\2\u009e\u00a0\5F$\2\u009f\u00a1\5P)\2\u00a0"+
		"\u009f\3\2\2\2\u00a0\u00a1\3\2\2\2\u00a1\u00a2\3\2\2\2\u00a2\u00a3\7\3"+
		"\2\2\u00a3\u00a8\7u\2\2\u00a4\u00a7\5\6\4\2\u00a5\u00a7\7u\2\2\u00a6\u00a4"+
		"\3\2\2\2\u00a6\u00a5\3\2\2\2\u00a7\u00aa\3\2\2\2\u00a8\u00a6\3\2\2\2\u00a8"+
		"\u00a9\3\2\2\2\u00a9\u00ab\3\2\2\2\u00aa\u00a8\3\2\2\2\u00ab\u00ac\7\4"+
		"\2\2\u00ac\u00ad\7u\2\2\u00ad\5\3\2\2\2\u00ae\u00b3\5\22\n\2\u00af\u00b3"+
		"\5\n\6\2\u00b0\u00b3\5\f\7\2\u00b1\u00b3\5b\62\2\u00b2\u00ae\3\2\2\2\u00b2"+
		"\u00af\3\2\2\2\u00b2\u00b0\3\2\2\2\u00b2\u00b1\3\2\2\2\u00b3\7\3\2\2\2"+
		"\u00b4\u00c9\5\22\n\2\u00b5\u00c9\5\n\6\2\u00b6\u00c9\5(\25\2\u00b7\u00c9"+
		"\5*\26\2\u00b8\u00c9\5\20\t\2\u00b9\u00c9\5.\30\2\u00ba\u00c9\5<\37\2"+
		"\u00bb\u00c9\5\u0080A\2\u00bc\u00c9\5\u0084C\2\u00bd\u00c9\5\f\7\2\u00be"+
		"\u00c9\5b\62\2\u00bf\u00c9\5@!\2\u00c0\u00c9\5\u0088E\2\u00c1\u00c9\5"+
		"\u008aF\2\u00c2\u00c9\5\u008cG\2\u00c3\u00c9\5\u008eH\2\u00c4\u00c9\5"+
		"\u0090I\2\u00c5\u00c9\5B\"\2\u00c6\u00c9\5D#\2\u00c7\u00c9\5\16\b\2\u00c8"+
		"\u00b4\3\2\2\2\u00c8\u00b5\3\2\2\2\u00c8\u00b6\3\2\2\2\u00c8\u00b7\3\2"+
		"\2\2\u00c8\u00b8\3\2\2\2\u00c8\u00b9\3\2\2\2\u00c8\u00ba\3\2\2\2\u00c8"+
		"\u00bb\3\2\2\2\u00c8\u00bc\3\2\2\2\u00c8\u00bd\3\2\2\2\u00c8\u00be\3\2"+
		"\2\2\u00c8\u00bf\3\2\2\2\u00c8\u00c0\3\2\2\2\u00c8\u00c1\3\2\2\2\u00c8"+
		"\u00c2\3\2\2\2\u00c8\u00c3\3\2\2\2\u00c8\u00c4\3\2\2\2\u00c8\u00c5\3\2"+
		"\2\2\u00c8\u00c6\3\2\2\2\u00c8\u00c7\3\2\2\2\u00c9\t\3\2\2\2\u00ca\u00d2"+
		"\5\32\16\2\u00cb\u00d2\5\34\17\2\u00cc\u00d2\5\26\f\2\u00cd\u00d2\5\30"+
		"\r\2\u00ce\u00d2\5\36\20\2\u00cf\u00d2\5 \21\2\u00d0\u00d2\5\"\22\2\u00d1"+
		"\u00ca\3\2\2\2\u00d1\u00cb\3\2\2\2\u00d1\u00cc\3\2\2\2\u00d1\u00cd\3\2"+
		"\2\2\u00d1\u00ce\3\2\2\2\u00d1\u00cf\3\2\2\2\u00d1\u00d0\3\2\2\2\u00d2"+
		"\13\3\2\2\2\u00d3\u00d7\5d\63\2\u00d4\u00d7\5n8\2\u00d5\u00d7\5p9\2\u00d6"+
		"\u00d3\3\2\2\2\u00d6\u00d4\3\2\2\2\u00d6\u00d5\3\2\2\2\u00d7\r\3\2\2\2"+
		"\u00d8\u00d9\5F$\2\u00d9\u00da\7\5\2\2\u00da\17\3\2\2\2\u00db\u00de\7"+
		"\6\2\2\u00dc\u00df\5P)\2\u00dd\u00df\5H%\2\u00de\u00dc\3\2\2\2\u00de\u00dd"+
		"\3\2\2\2\u00df\21\3\2\2\2\u00e0\u00ec\t\2\2\2\u00e1\u00e3\5\24\13\2\u00e2"+
		"\u00e1\3\2\2\2\u00e2\u00e3\3\2\2\2\u00e3\u00ed\3\2\2\2\u00e4\u00e9\5\24"+
		"\13\2\u00e5\u00e6\7\21\2\2\u00e6\u00e8\5\24\13\2\u00e7\u00e5\3\2\2\2\u00e8"+
		"\u00eb\3\2\2\2\u00e9\u00e7\3\2\2\2\u00e9\u00ea\3\2\2\2\u00ea\u00ed\3\2"+
		"\2\2\u00eb\u00e9\3\2\2\2\u00ec\u00e2\3\2\2\2\u00ec\u00e4\3\2\2\2\u00ed"+
		"\23\3\2\2\2\u00ee\u00f2\5Z.\2\u00ef\u00f2\5F$\2\u00f0\u00f2\5P)\2\u00f1"+
		"\u00ee\3\2\2\2\u00f1\u00ef\3\2\2\2\u00f1\u00f0\3\2\2\2\u00f2\25\3\2\2"+
		"\2\u00f3\u00f5\5$\23\2\u00f4\u00f6\7\u0081\2\2\u00f5\u00f4\3\2\2\2\u00f5"+
		"\u00f6\3\2\2\2\u00f6\u00f9\3\2\2\2\u00f7\u00fa\5&\24\2\u00f8\u00fa\7\u0082"+
		"\2\2\u00f9\u00f7\3\2\2\2\u00f9\u00f8\3\2\2\2\u00f9\u00fa\3\2\2\2\u00fa"+
		"\u00fb\3\2\2\2\u00fb\u00fc\5F$\2\u00fc\27\3\2\2\2\u00fd\u00fe\5F$\2\u00fe"+
		"\u00ff\5F$\2\u00ff\31\3\2\2\2\u0100\u0101\5\26\f\2\u0101\u0102\7\22\2"+
		"\2\u0102\u0103\5\60\31\2\u0103\33\3\2\2\2\u0104\u0105\5\30\r\2\u0105\u0106"+
		"\7\22\2\2\u0106\u0107\5\60\31\2\u0107\35\3\2\2\2\u0108\u0109\7\23\2\2"+
		"\u0109\u010a\5\32\16\2\u010a\37\3\2\2\2\u010b\u010c\7{\2\2\u010c\u010d"+
		"\5\32\16\2\u010d!\3\2\2\2\u010e\u010f\7\24\2\2\u010f\u0110\5F$\2\u0110"+
		"\u0111\7\3\2\2\u0111\u0112\7u\2\2\u0112\u0117\5\26\f\2\u0113\u0114\7u"+
		"\2\2\u0114\u0116\5\26\f\2\u0115\u0113\3\2\2\2\u0116\u0119\3\2\2\2\u0117"+
		"\u0115\3\2\2\2\u0117\u0118\3\2\2\2\u0118\u011b\3\2\2\2\u0119\u0117\3\2"+
		"\2\2\u011a\u011c\7u\2\2\u011b\u011a\3\2\2\2\u011b\u011c\3\2\2\2\u011c"+
		"\u011d\3\2\2\2\u011d\u011e\7\4\2\2\u011e\u011f\7u\2\2\u011f#\3\2\2\2\u0120"+
		"\u0121\t\3\2\2\u0121%\3\2\2\2\u0122\u0123\7\33\2\2\u0123\u0124\5\60\31"+
		"\2\u0124\u0125\7\34\2\2\u0125\'\3\2\2\2\u0126\u0127\5,\27\2\u0127\u0128"+
		"\7\22\2\2\u0128\u0129\5\60\31\2\u0129)\3\2\2\2\u012a\u012b\5,\27\2\u012b"+
		"\u012c\t\4\2\2\u012c\u012d\5\60\31\2\u012d+\3\2\2\2\u012e\u0133\5J&\2"+
		"\u012f\u0133\5H%\2\u0130\u0133\5\64\33\2\u0131\u0133\5\66\34\2\u0132\u012e"+
		"\3\2\2\2\u0132\u012f\3\2\2\2\u0132\u0130\3\2\2\2\u0132\u0131\3\2\2\2\u0133"+
		"-\3\2\2\2\u0134\u0135\5,\27\2\u0135\u0136\t\5\2\2\u0136/\3\2\2\2\u0137"+
		"\u0138\b\31\1\2\u0138\u0148\5:\36\2\u0139\u013a\t\6\2\2\u013a\u0148\5"+
		"\60\31\31\u013b\u013c\7A\2\2\u013c\u0148\5\60\31\13\u013d\u0148\5`\61"+
		"\2\u013e\u0148\5J&\2\u013f\u0148\5H%\2\u0140\u0148\5\64\33\2\u0141\u0148"+
		"\5\66\34\2\u0142\u0148\58\35\2\u0143\u0144\7B\2\2\u0144\u0145\5\60\31"+
		"\2\u0145\u0146\7C\2\2\u0146\u0148\3\2\2\2\u0147\u0137\3\2\2\2\u0147\u0139"+
		"\3\2\2\2\u0147\u013b\3\2\2\2\u0147\u013d\3\2\2\2\u0147\u013e\3\2\2\2\u0147"+
		"\u013f\3\2\2\2\u0147\u0140\3\2\2\2\u0147\u0141\3\2\2\2\u0147\u0142\3\2"+
		"\2\2\u0147\u0143\3\2\2\2\u0148\u01c0\3\2\2\2\u0149\u014b\f\30\2\2\u014a"+
		"\u014c\7u\2\2\u014b\u014a\3\2\2\2\u014b\u014c\3\2\2\2\u014c\u014d\3\2"+
		"\2\2\u014d\u014f\7-\2\2\u014e\u0150\7u\2\2\u014f\u014e\3\2\2\2\u014f\u0150"+
		"\3\2\2\2\u0150\u0151\3\2\2\2\u0151\u01bf\5\60\31\31\u0152\u0154\f\27\2"+
		"\2\u0153\u0155\7u\2\2\u0154\u0153\3\2\2\2\u0154\u0155\3\2\2\2\u0155\u0156"+
		"\3\2\2\2\u0156\u0158\t\7\2\2\u0157\u0159\7u\2\2\u0158\u0157\3\2\2\2\u0158"+
		"\u0159\3\2\2\2\u0159\u015a\3\2\2\2\u015a\u01bf\5\60\31\30\u015b\u015d"+
		"\f\26\2\2\u015c\u015e\7u\2\2\u015d\u015c\3\2\2\2\u015d\u015e\3\2\2\2\u015e"+
		"\u015f\3\2\2\2\u015f\u0161\t\b\2\2\u0160\u0162\7u\2\2\u0161\u0160\3\2"+
		"\2\2\u0161\u0162\3\2\2\2\u0162\u0163\3\2\2\2\u0163\u01bf\5\60\31\27\u0164"+
		"\u0166\f\25\2\2\u0165\u0167\7u\2\2\u0166\u0165\3\2\2\2\u0166\u0167\3\2"+
		"\2\2\u0167\u0168\3\2\2\2\u0168\u016a\t\t\2\2\u0169\u016b\7u\2\2\u016a"+
		"\u0169\3\2\2\2\u016a\u016b\3\2\2\2\u016b\u016c\3\2\2\2\u016c\u01bf\5\60"+
		"\31\26\u016d\u016f\f\24\2\2\u016e\u0170\7u\2\2\u016f\u016e\3\2\2\2\u016f"+
		"\u0170\3\2\2\2\u0170\u0171\3\2\2\2\u0171\u0173\t\n\2\2\u0172\u0174\7u"+
		"\2\2\u0173\u0172\3\2\2\2\u0173\u0174\3\2\2\2\u0174\u0175\3\2\2\2\u0175"+
		"\u01bf\5\60\31\25\u0176\u0178\f\23\2\2\u0177\u0179\7u\2\2\u0178\u0177"+
		"\3\2\2\2\u0178\u0179\3\2\2\2\u0179\u017a\3\2\2\2\u017a\u017c\t\13\2\2"+
		"\u017b\u017d\7u\2\2\u017c\u017b\3\2\2\2\u017c\u017d\3\2\2\2\u017d\u017e"+
		"\3\2\2\2\u017e\u01bf\5\60\31\24\u017f\u0181\f\22\2\2\u0180\u0182\7u\2"+
		"\2\u0181\u0180\3\2\2\2\u0181\u0182\3\2\2\2\u0182\u0183\3\2\2\2\u0183\u0185"+
		"\7{\2\2\u0184\u0186\7u\2\2\u0185\u0184\3\2\2\2\u0185\u0186\3\2\2\2\u0186"+
		"\u0187\3\2\2\2\u0187\u01bf\5\60\31\23\u0188\u018a\f\21\2\2\u0189\u018b"+
		"\7u\2\2\u018a\u0189\3\2\2\2\u018a\u018b\3\2\2\2\u018b\u018c\3\2\2\2\u018c"+
		"\u018e\79\2\2\u018d\u018f\7u\2\2\u018e\u018d\3\2\2\2\u018e\u018f\3\2\2"+
		"\2\u018f\u0190\3\2\2\2\u0190\u01bf\5\60\31\22\u0191\u0193\f\20\2\2\u0192"+
		"\u0194\7u\2\2\u0193\u0192\3\2\2\2\u0193\u0194\3\2\2\2\u0194\u0195\3\2"+
		"\2\2\u0195\u0197\7:\2\2\u0196\u0198\7u\2\2\u0197\u0196\3\2\2\2\u0197\u0198"+
		"\3\2\2\2\u0198\u0199\3\2\2\2\u0199\u01bf\5\60\31\21\u019a\u019c\f\16\2"+
		"\2\u019b\u019d\7u\2\2\u019c\u019b\3\2\2\2\u019c\u019d\3\2\2\2\u019d\u019e"+
		"\3\2\2\2\u019e\u01a0\7>\2\2\u019f\u01a1\7u\2\2\u01a0\u019f\3\2\2\2\u01a0"+
		"\u01a1\3\2\2\2\u01a1\u01a2\3\2\2\2\u01a2\u01bf\5\60\31\17\u01a3\u01a5"+
		"\f\r\2\2\u01a4\u01a6\7u\2\2\u01a5\u01a4\3\2\2\2\u01a5\u01a6\3\2\2\2\u01a6"+
		"\u01a7\3\2\2\2\u01a7\u01a9\7?\2\2\u01a8\u01aa\7u\2\2\u01a9\u01a8\3\2\2"+
		"\2\u01a9\u01aa\3\2\2\2\u01aa\u01ab\3\2\2\2\u01ab\u01bf\5\60\31\16\u01ac"+
		"\u01ae\f\f\2\2\u01ad\u01af\7u\2\2\u01ae\u01ad\3\2\2\2\u01ae\u01af\3\2"+
		"\2\2\u01af\u01b0\3\2\2\2\u01b0\u01b2\7@\2\2\u01b1\u01b3\7u\2\2\u01b2\u01b1"+
		"\3\2\2\2\u01b2\u01b3\3\2\2\2\u01b3\u01b4\3\2\2\2\u01b4\u01bf\5\60\31\r"+
		"\u01b5\u01b6\f\17\2\2\u01b6\u01b7\t\f\2\2\u01b7\u01ba\5\60\31\2\u01b8"+
		"\u01b9\7=\2\2\u01b9\u01bb\5\60\31\2\u01ba\u01b8\3\2\2\2\u01ba\u01bb\3"+
		"\2\2\2\u01bb\u01bf\3\2\2\2\u01bc\u01bd\f\4\2\2\u01bd\u01bf\5\62\32\2\u01be"+
		"\u0149\3\2\2\2\u01be\u0152\3\2\2\2\u01be\u015b\3\2\2\2\u01be\u0164\3\2"+
		"\2\2\u01be\u016d\3\2\2\2\u01be\u0176\3\2\2\2\u01be\u017f\3\2\2\2\u01be"+
		"\u0188\3\2\2\2\u01be\u0191\3\2\2\2\u01be\u019a\3\2\2\2\u01be\u01a3\3\2"+
		"\2\2\u01be\u01ac\3\2\2\2\u01be\u01b5\3\2\2\2\u01be\u01bc\3\2\2\2\u01bf"+
		"\u01c2\3\2\2\2\u01c0\u01be\3\2\2\2\u01c0\u01c1\3\2\2\2\u01c1\61\3\2\2"+
		"\2\u01c2\u01c0\3\2\2\2\u01c3\u01c4\7D\2\2\u01c4\u01c5\5$\23\2\u01c5\63"+
		"\3\2\2\2\u01c6\u01c7\5H%\2\u01c7\u01c8\5&\24\2\u01c8\65\3\2\2\2\u01c9"+
		"\u01ca\7|\2\2\u01ca\u01cb\7B\2\2\u01cb\u01cc\5\60\31\2\u01cc\u01cd\7C"+
		"\2\2\u01cd\67\3\2\2\2\u01ce\u01cf\7{\2\2\u01cf\u01d0\5H%\2\u01d09\3\2"+
		"\2\2\u01d1\u01d2\5H%\2\u01d2\u01d4\7B\2\2\u01d3\u01d5\5> \2\u01d4\u01d3"+
		"\3\2\2\2\u01d4\u01d5\3\2\2\2\u01d5\u01d6\3\2\2\2\u01d6\u01d7\7C\2\2\u01d7"+
		";\3\2\2\2\u01d8\u01da\7v\2\2\u01d9\u01d8\3\2\2\2\u01d9\u01da\3\2\2\2\u01da"+
		"\u01db\3\2\2\2\u01db\u01dc\5H%\2\u01dc\u01de\7B\2\2\u01dd\u01df\5> \2"+
		"\u01de\u01dd\3\2\2\2\u01de\u01df\3\2\2\2\u01df\u01e0\3\2\2\2\u01e0\u01e1"+
		"\7C\2\2\u01e1=\3\2\2\2\u01e2\u01ea\5\60\31\2\u01e3\u01e5\7\21\2\2\u01e4"+
		"\u01e6\7u\2\2\u01e5\u01e4\3\2\2\2\u01e5\u01e6\3\2\2\2\u01e6\u01e7\3\2"+
		"\2\2\u01e7\u01e9\5\60\31\2\u01e8\u01e3\3\2\2\2\u01e9\u01ec\3\2\2\2\u01ea"+
		"\u01e8\3\2\2\2\u01ea\u01eb\3\2\2\2\u01eb?\3\2\2\2\u01ec\u01ea\3\2\2\2"+
		"\u01ed\u01ef\7E\2\2\u01ee\u01f0\5\60\31\2\u01ef\u01ee\3\2\2\2\u01ef\u01f0"+
		"\3\2\2\2\u01f0A\3\2\2\2\u01f1\u01f2\7F\2\2\u01f2C\3\2\2\2\u01f3\u01f4"+
		"\7G\2\2\u01f4E\3\2\2\2\u01f5\u01f6\7w\2\2\u01f6G\3\2\2\2\u01f7\u01fc\7"+
		"w\2\2\u01f8\u01f9\7H\2\2\u01f9\u01fb\7w\2\2\u01fa\u01f8\3\2\2\2\u01fb"+
		"\u01fe\3\2\2\2\u01fc\u01fa\3\2\2\2\u01fc\u01fd\3\2\2\2\u01fdI\3\2\2\2"+
		"\u01fe\u01fc\3\2\2\2\u01ff\u0200\t\r\2\2\u0200K\3\2\2\2\u0201\u0202\t"+
		"\16\2\2\u0202M\3\2\2\2\u0203\u0204\t\17\2\2\u0204O\3\2\2\2\u0205\u0207"+
		"\t\20\2\2\u0206\u0208\5R*\2\u0207\u0206\3\2\2\2\u0207\u0208\3\2\2\2\u0208"+
		"Q\3\2\2\2\u0209\u020a\7S\2\2\u020aS\3\2\2\2\u020b\u020c\t\21\2\2\u020c"+
		"U\3\2\2\2\u020d\u020f\7\33\2\2\u020e\u0210\7u\2\2\u020f\u020e\3\2\2\2"+
		"\u020f\u0210\3\2\2\2\u0210\u0211\3\2\2\2\u0211\u0219\5\60\31\2\u0212\u0214"+
		"\7\21\2\2\u0213\u0215\7u\2\2\u0214\u0213\3\2\2\2\u0214\u0215\3\2\2\2\u0215"+
		"\u0216\3\2\2\2\u0216\u0218\5\60\31\2\u0217\u0212\3\2\2\2\u0218\u021b\3"+
		"\2\2\2\u0219\u0217\3\2\2\2\u0219\u021a\3\2\2\2\u021a\u021d\3\2\2\2\u021b"+
		"\u0219\3\2\2\2\u021c\u021e\7u\2\2\u021d\u021c\3\2\2\2\u021d\u021e\3\2"+
		"\2\2\u021e\u021f\3\2\2\2\u021f\u0220\7\34\2\2\u0220W\3\2\2\2\u0221\u0223"+
		"\7\3\2\2\u0222\u0224\7u\2\2\u0223\u0222\3\2\2\2\u0223\u0224\3\2\2\2\u0224"+
		"\u0225\3\2\2\2\u0225\u022d\5\60\31\2\u0226\u0228\7\21\2\2\u0227\u0229"+
		"\7u\2\2\u0228\u0227\3\2\2\2\u0228\u0229\3\2\2\2\u0229\u022a\3\2\2\2\u022a"+
		"\u022c\5\60\31\2\u022b\u0226\3\2\2\2\u022c\u022f\3\2\2\2\u022d\u022b\3"+
		"\2\2\2\u022d\u022e\3\2\2\2\u022e\u0231\3\2\2\2\u022f\u022d\3\2\2\2\u0230"+
		"\u0232\7u\2\2\u0231\u0230\3\2\2\2\u0231\u0232\3\2\2\2\u0232\u0233\3\2"+
		"\2\2\u0233\u0234\7\4\2\2\u0234Y\3\2\2\2\u0235\u0237\7|\2\2\u0236\u0235"+
		"\3\2\2\2\u0236\u0237\3\2\2\2\u0237\u0238\3\2\2\2\u0238\u0239\7~\2\2\u0239"+
		"[\3\2\2\2\u023a\u023c\7|\2\2\u023b\u023a\3\2\2\2\u023b\u023c\3\2\2\2\u023c"+
		"\u023d\3\2\2\2\u023d\u023e\7\u0080\2\2\u023e]\3\2\2\2\u023f\u0240\7}\2"+
		"\2\u0240_\3\2\2\2\u0241\u0249\5P)\2\u0242\u0249\5T+\2\u0243\u0249\5V,"+
		"\2\u0244\u0249\5Z.\2\u0245\u0249\5\\/\2\u0246\u0249\5^\60\2\u0247\u0249"+
		"\5X-\2\u0248\u0241\3\2\2\2\u0248\u0242\3\2\2\2\u0248\u0243\3\2\2\2\u0248"+
		"\u0244\3\2\2\2\u0248\u0245\3\2\2\2\u0248\u0246\3\2\2\2\u0248\u0247\3\2"+
		"\2\2\u0249a\3\2\2\2\u024a\u024b\7V\2\2\u024b\u024c\7\177\2\2\u024cc\3"+
		"\2\2\2\u024d\u024e\7W\2\2\u024e\u024f\5F$\2\u024f\u0251\7B\2\2\u0250\u0252"+
		"\5j\66\2\u0251\u0250\3\2\2\2\u0251\u0252\3\2\2\2\u0252\u0253\3\2\2\2\u0253"+
		"\u0255\7C\2\2\u0254\u0256\5f\64\2\u0255\u0254\3\2\2\2\u0255\u0256\3\2"+
		"\2\2\u0256\u0257\3\2\2\2\u0257\u0258\5h\65\2\u0258\u0259\7u\2\2\u0259"+
		"e\3\2\2\2\u025a\u025b\7X\2\2\u025b\u025c\5l\67\2\u025cg\3\2\2\2\u025d"+
		"\u025e\7\3\2\2\u025e\u0263\7u\2\2\u025f\u0262\5\b\5\2\u0260\u0262\7u\2"+
		"\2\u0261\u025f\3\2\2\2\u0261\u0260\3\2\2\2\u0262\u0265\3\2\2\2\u0263\u0261"+
		"\3\2\2\2\u0263\u0264\3\2\2\2\u0264\u0266\3\2\2\2\u0265\u0263\3\2\2\2\u0266"+
		"\u0267\7\4\2\2\u0267i\3\2\2\2\u0268\u0270\5\26\f\2\u0269\u026b\7\21\2"+
		"\2\u026a\u026c\7u\2\2\u026b\u026a\3\2\2\2\u026b\u026c\3\2\2\2\u026c\u026d"+
		"\3\2\2\2\u026d\u026f\5\26\f\2\u026e\u0269\3\2\2\2\u026f\u0272\3\2\2\2"+
		"\u0270\u026e\3\2\2\2\u0270\u0271\3\2\2\2\u0271k\3\2\2\2\u0272\u0270\3"+
		"\2\2\2\u0273\u027b\5$\23\2\u0274\u0276\7\21\2\2\u0275\u0277\7u\2\2\u0276"+
		"\u0275\3\2\2\2\u0276\u0277\3\2\2\2\u0277\u0278\3\2\2\2\u0278\u027a\5$"+
		"\23\2\u0279\u0274\3\2\2\2\u027a\u027d\3\2\2\2\u027b\u0279\3\2\2\2\u027b"+
		"\u027c\3\2\2\2\u027cm\3\2\2\2\u027d\u027b\3\2\2\2\u027e\u027f\7Y\2\2\u027f"+
		"\u0280\5r:\2\u0280\u0281\5h\65\2\u0281o\3\2\2\2\u0282\u0283\7Z\2\2\u0283"+
		"\u0284\5P)\2\u0284\u0285\7\22\2\2\u0285\u0286\5r:\2\u0286q\3\2\2\2\u0287"+
		"\u0288\5F$\2\u0288\u028a\7B\2\2\u0289\u028b\5t;\2\u028a\u0289\3\2\2\2"+
		"\u028a\u028b\3\2\2\2\u028b\u028c\3\2\2\2\u028c\u028e\7C\2\2\u028d\u028f"+
		"\5x=\2\u028e\u028d\3\2\2\2\u028e\u028f\3\2\2\2\u028f\u0291\3\2\2\2\u0290"+
		"\u0292\5|?\2\u0291\u0290\3\2\2\2\u0291\u0292\3\2\2\2\u0292s\3\2\2\2\u0293"+
		"\u029b\5v<\2\u0294\u0296\7\21\2\2\u0295\u0297\7u\2\2\u0296\u0295\3\2\2"+
		"\2\u0296\u0297\3\2\2\2\u0297\u0298\3\2\2\2\u0298\u029a\5v<\2\u0299\u0294"+
		"\3\2\2\2\u029a\u029d\3\2\2\2\u029b\u0299\3\2\2\2\u029b\u029c\3\2\2\2\u029c"+
		"u\3\2\2\2\u029d\u029b\3\2\2\2\u029e\u029f\5\26\f\2\u029f\u02a3\7|\2\2"+
		"\u02a0\u02a4\5L\'\2\u02a1\u02a4\5N(\2\u02a2\u02a4\7[\2\2\u02a3\u02a0\3"+
		"\2\2\2\u02a3\u02a1\3\2\2\2\u02a3\u02a2\3\2\2\2\u02a4w\3\2\2\2\u02a5\u02a6"+
		"\7\\\2\2\u02a6\u02a8\7B\2\2\u02a7\u02a9\5z>\2\u02a8\u02a7\3\2\2\2\u02a8"+
		"\u02a9\3\2\2\2\u02a9\u02aa\3\2\2\2\u02aa\u02ab\7C\2\2\u02aby\3\2\2\2\u02ac"+
		"\u02b1\5J&\2\u02ad\u02ae\7\21\2\2\u02ae\u02b0\5J&\2\u02af\u02ad\3\2\2"+
		"\2\u02b0\u02b3\3\2\2\2\u02b1\u02af\3\2\2\2\u02b1\u02b2\3\2\2\2\u02b2{"+
		"\3\2\2\2\u02b3\u02b1\3\2\2\2\u02b4\u02b5\7X\2\2\u02b5\u02bd\5~@\2\u02b6"+
		"\u02b8\7\21\2\2\u02b7\u02b9\7u\2\2\u02b8\u02b7\3\2\2\2\u02b8\u02b9\3\2"+
		"\2\2\u02b9\u02ba\3\2\2\2\u02ba\u02bc\5~@\2\u02bb\u02b6\3\2\2\2\u02bc\u02bf"+
		"\3\2\2\2\u02bd\u02bb\3\2\2\2\u02bd\u02be\3\2\2\2\u02be}\3\2\2\2\u02bf"+
		"\u02bd\3\2\2\2\u02c0\u02c1\5$\23\2\u02c1\u02c5\7|\2\2\u02c2\u02c6\5L\'"+
		"\2\u02c3\u02c6\5N(\2\u02c4\u02c6\7[\2\2\u02c5\u02c2\3\2\2\2\u02c5\u02c3"+
		"\3\2\2\2\u02c5\u02c4\3\2\2\2\u02c6\177\3\2\2\2\u02c7\u02c8\7]\2\2\u02c8"+
		"\u02ca\5\60\31\2\u02c9\u02cb\7u\2\2\u02ca\u02c9\3\2\2\2\u02ca\u02cb\3"+
		"\2\2\2\u02cb\u02ce\3\2\2\2\u02cc\u02cf\5\b\5\2\u02cd\u02cf\5h\65\2\u02ce"+
		"\u02cc\3\2\2\2\u02ce\u02cd\3\2\2\2\u02cf\u02d1\3\2\2\2\u02d0\u02d2\7u"+
		"\2\2\u02d1\u02d0\3\2\2\2\u02d1\u02d2\3\2\2\2\u02d2\u02d4\3\2\2\2\u02d3"+
		"\u02d5\5\u0082B\2\u02d4\u02d3\3\2\2\2\u02d4\u02d5\3\2\2\2\u02d5\u0081"+
		"\3\2\2\2\u02d6\u02d8\7^\2\2\u02d7\u02d9\7u\2\2\u02d8\u02d7\3\2\2\2\u02d8"+
		"\u02d9\3\2\2\2\u02d9\u02dc\3\2\2\2\u02da\u02dd\5\b\5\2\u02db\u02dd\5h"+
		"\65\2\u02dc\u02da\3\2\2\2\u02dc\u02db\3\2\2\2\u02dd\u0083\3\2\2\2\u02de"+
		"\u02e0\5\u0086D\2\u02df\u02e1\7u\2\2\u02e0\u02df\3\2\2\2\u02e0\u02e1\3"+
		"\2\2\2\u02e1\u02e4\3\2\2\2\u02e2\u02e5\5\b\5\2\u02e3\u02e5\5h\65\2\u02e4"+
		"\u02e2\3\2\2\2\u02e4\u02e3\3\2\2\2\u02e5\u02e7\3\2\2\2\u02e6\u02e8\7u"+
		"\2\2\u02e7\u02e6\3\2\2\2\u02e7\u02e8\3\2\2\2\u02e8\u02ea\3\2\2\2\u02e9"+
		"\u02eb\5\u0082B\2\u02ea\u02e9\3\2\2\2\u02ea\u02eb\3\2\2\2\u02eb\u02ec"+
		"\3\2\2\2\u02ec\u02ed\7u\2\2\u02ed\u0085\3\2\2\2\u02ee\u02ef\t\22\2\2\u02ef"+
		"\u0087\3\2\2\2\u02f0\u02f3\7k\2\2\u02f1\u02f4\5J&\2\u02f2\u02f4\5F$\2"+
		"\u02f3\u02f1\3\2\2\2\u02f3\u02f2\3\2\2\2\u02f4\u02f5\3\2\2\2\u02f5\u02f6"+
		"\7l\2\2\u02f6\u02f8\5\60\31\2\u02f7\u02f9\7u\2\2\u02f8\u02f7\3\2\2\2\u02f8"+
		"\u02f9\3\2\2\2\u02f9\u02fc\3\2\2\2\u02fa\u02fd\5\b\5\2\u02fb\u02fd\5h"+
		"\65\2\u02fc\u02fa\3\2\2\2\u02fc\u02fb\3\2\2\2\u02fd\u0089\3\2\2\2\u02fe"+
		"\u02ff\7m\2\2\u02ff\u0301\5\60\31\2\u0300\u0302\7u\2\2\u0301\u0300\3\2"+
		"\2\2\u0301\u0302\3\2\2\2\u0302\u0305\3\2\2\2\u0303\u0306\5\b\5\2\u0304"+
		"\u0306\5h\65\2\u0305\u0303\3\2\2\2\u0305\u0304\3\2\2\2\u0306\u008b\3\2"+
		"\2\2\u0307\u030a\7n\2\2\u0308\u030b\5\b\5\2\u0309\u030b\5h\65\2\u030a"+
		"\u0308\3\2\2\2\u030a\u0309\3\2\2\2\u030b\u030d\3\2\2\2\u030c\u030e\7u"+
		"\2\2\u030d\u030c\3\2\2\2\u030d\u030e\3\2\2\2\u030e\u030f\3\2\2\2\u030f"+
		"\u0310\7o\2\2\u0310\u0311\5\60\31\2\u0311\u008d\3\2\2\2\u0312\u0314\7"+
		"p\2\2\u0313\u0315\7u\2\2\u0314\u0313\3\2\2\2\u0314\u0315\3\2\2\2\u0315"+
		"\u0318\3\2\2\2\u0316\u0319\5\b\5\2\u0317\u0319\5h\65\2\u0318\u0316\3\2"+
		"\2\2\u0318\u0317\3\2\2\2\u0319\u008f\3\2\2\2\u031a\u031b\7q\2\2\u031b"+
		"\u031c\5\60\31\2\u031c\u031d\7\3\2\2\u031d\u0322\7u\2\2\u031e\u0321\5"+
		"\u0092J\2\u031f\u0321\7u\2\2\u0320\u031e\3\2\2\2\u0320\u031f\3\2\2\2\u0321"+
		"\u0324\3\2\2\2\u0322\u0320\3\2\2\2\u0322\u0323\3\2\2\2\u0323\u0325\3\2"+
		"\2\2\u0324\u0322\3\2\2\2\u0325\u0327\7\4\2\2\u0326\u0328\7u\2\2\u0327"+
		"\u0326\3\2\2\2\u0327\u0328\3\2\2\2\u0328\u0091\3\2\2\2\u0329\u032c\5>"+
		" \2\u032a\u032c\7^\2\2\u032b\u0329\3\2\2\2\u032b\u032a\3\2\2\2\u032c\u032d"+
		"\3\2\2\2\u032d\u0330\7X\2\2\u032e\u0331\5\b\5\2\u032f\u0331\5h\65\2\u0330"+
		"\u032e\3\2\2\2\u0330\u032f\3\2\2\2\u0331\u0093\3\2\2\2o\u0097\u0099\u00a0"+
		"\u00a6\u00a8\u00b2\u00c8\u00d1\u00d6\u00de\u00e2\u00e9\u00ec\u00f1\u00f5"+
		"\u00f9\u0117\u011b\u0132\u0147\u014b\u014f\u0154\u0158\u015d\u0161\u0166"+
		"\u016a\u016f\u0173\u0178\u017c\u0181\u0185\u018a\u018e\u0193\u0197\u019c"+
		"\u01a0\u01a5\u01a9\u01ae\u01b2\u01ba\u01be\u01c0\u01d4\u01d9\u01de\u01e5"+
		"\u01ea\u01ef\u01fc\u0207\u020f\u0214\u0219\u021d\u0223\u0228\u022d\u0231"+
		"\u0236\u023b\u0248\u0251\u0255\u0261\u0263\u026b\u0270\u0276\u027b\u028a"+
		"\u028e\u0291\u0296\u029b\u02a3\u02a8\u02b1\u02b8\u02bd\u02c5\u02ca\u02ce"+
		"\u02d1\u02d4\u02d8\u02dc\u02e0\u02e4\u02e7\u02ea\u02f3\u02f8\u02fc\u0301"+
		"\u0305\u030a\u030d\u0314\u0318\u0320\u0322\u0327\u032b\u0330";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}