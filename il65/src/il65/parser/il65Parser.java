// Generated from /home/irmen/Projects/IL65/il65/antlr/il65.g4 by ANTLR 4.7
package il65.parser;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.*;
import org.antlr.v4.runtime.tree.*;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class il65Parser extends Parser {
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
		T__80=81, COMMENT=82, WS=83, EOL=84, NAME=85, DEC_INTEGER=86, HEX_INTEGER=87, 
		BIN_INTEGER=88, FLOAT_NUMBER=89, STRING=90, INLINEASMBLOCK=91;
	public static final int
		RULE_module = 0, RULE_modulestatement = 1, RULE_block = 2, RULE_statement = 3, 
		RULE_label = 4, RULE_call_location = 5, RULE_unconditionaljump = 6, RULE_directive = 7, 
		RULE_directivearg = 8, RULE_vardecl = 9, RULE_varinitializer = 10, RULE_constdecl = 11, 
		RULE_memoryvardecl = 12, RULE_datatype = 13, RULE_arrayspec = 14, RULE_assignment = 15, 
		RULE_augassignment = 16, RULE_assign_target = 17, RULE_postincrdecr = 18, 
		RULE_expression = 19, RULE_functioncall = 20, RULE_identifier = 21, RULE_scoped_identifier = 22, 
		RULE_register = 23, RULE_integerliteral = 24, RULE_booleanliteral = 25, 
		RULE_arrayliteral = 26, RULE_stringliteral = 27, RULE_floatliteral = 28, 
		RULE_literalvalue = 29, RULE_inlineasm = 30;
	public static final String[] ruleNames = {
		"module", "modulestatement", "block", "statement", "label", "call_location", 
		"unconditionaljump", "directive", "directivearg", "vardecl", "varinitializer", 
		"constdecl", "memoryvardecl", "datatype", "arrayspec", "assignment", "augassignment", 
		"assign_target", "postincrdecr", "expression", "functioncall", "identifier", 
		"scoped_identifier", "register", "integerliteral", "booleanliteral", "arrayliteral", 
		"stringliteral", "floatliteral", "literalvalue", "inlineasm"
	};

	private static final String[] _LITERAL_NAMES = {
		null, "'~'", "'{'", "'}'", "':'", "'goto'", "'%output'", "'%launcher'", 
		"'%zp'", "'%address'", "'%import'", "'%breakpoint'", "'%asminclude'", 
		"'%asmbinary'", "','", "'='", "'const'", "'memory'", "'byte'", "'word'", 
		"'float'", "'str'", "'str_p'", "'str_s'", "'str_ps'", "'['", "']'", "'+='", 
		"'-='", "'/='", "'//='", "'*='", "'**='", "'<<='", "'>>='", "'<<@='", 
		"'>>@='", "'&='", "'|='", "'^='", "'++'", "'--'", "'('", "')'", "'+'", 
		"'-'", "'not'", "'**'", "'*'", "'/'", "'//'", "'%'", "'<<'", "'>>'", "'<<@'", 
		"'>>@'", "'<'", "'>'", "'<='", "'>='", "'=='", "'!='", "'&'", "'^'", "'|'", 
		"'and'", "'or'", "'xor'", "'to'", "'.'", "'A'", "'X'", "'Y'", "'AX'", 
		"'AY'", "'XY'", "'SC'", "'SI'", "'SZ'", "'true'", "'false'", "'%asm'"
	};
	private static final String[] _SYMBOLIC_NAMES = {
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, null, null, 
		null, null, null, null, null, null, null, null, null, null, "COMMENT", 
		"WS", "EOL", "NAME", "DEC_INTEGER", "HEX_INTEGER", "BIN_INTEGER", "FLOAT_NUMBER", 
		"STRING", "INLINEASMBLOCK"
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
	public String getGrammarFileName() { return "il65.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public ATN getATN() { return _ATN; }

	public il65Parser(TokenStream input) {
		super(input);
		_interp = new ParserATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}
	public static class ModuleContext extends ParserRuleContext {
		public TerminalNode EOF() { return getToken(il65Parser.EOF, 0); }
		public List<TerminalNode> EOL() { return getTokens(il65Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(il65Parser.EOL, i);
		}
		public List<ModulestatementContext> modulestatement() {
			return getRuleContexts(ModulestatementContext.class);
		}
		public ModulestatementContext modulestatement(int i) {
			return getRuleContext(ModulestatementContext.class,i);
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
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(65);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					{
					{
					setState(62);
					match(EOL);
					}
					} 
				}
				setState(67);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,0,_ctx);
			}
			setState(72);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12))) != 0) || _la==EOL) {
				{
				setState(70);
				_errHandler.sync(this);
				switch (_input.LA(1)) {
				case T__0:
				case T__5:
				case T__6:
				case T__7:
				case T__8:
				case T__9:
				case T__10:
				case T__11:
				case T__12:
					{
					setState(68);
					modulestatement();
					}
					break;
				case EOL:
					{
					setState(69);
					match(EOL);
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				}
				setState(74);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(75);
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
			setState(79);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
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
				setState(77);
				directive();
				}
				break;
			case T__0:
				enterOuterAlt(_localctx, 2);
				{
				setState(78);
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
		public List<TerminalNode> EOL() { return getTokens(il65Parser.EOL); }
		public TerminalNode EOL(int i) {
			return getToken(il65Parser.EOL, i);
		}
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public List<StatementContext> statement() {
			return getRuleContexts(StatementContext.class);
		}
		public StatementContext statement(int i) {
			return getRuleContext(StatementContext.class,i);
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
			setState(81);
			match(T__0);
			setState(82);
			identifier();
			setState(84);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (((((_la - 86)) & ~0x3f) == 0 && ((1L << (_la - 86)) & ((1L << (DEC_INTEGER - 86)) | (1L << (HEX_INTEGER - 86)) | (1L << (BIN_INTEGER - 86)))) != 0)) {
				{
				setState(83);
				integerliteral();
				}
			}

			setState(86);
			match(T__1);
			setState(87);
			match(EOL);
			setState(93);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__4) | (1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12) | (1L << T__15) | (1L << T__16) | (1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0) || ((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & ((1L << (T__69 - 70)) | (1L << (T__70 - 70)) | (1L << (T__71 - 70)) | (1L << (T__72 - 70)) | (1L << (T__73 - 70)) | (1L << (T__74 - 70)) | (1L << (T__75 - 70)) | (1L << (T__76 - 70)) | (1L << (T__77 - 70)) | (1L << (T__80 - 70)) | (1L << (NAME - 70)))) != 0)) {
				{
				{
				setState(88);
				statement();
				setState(89);
				match(EOL);
				}
				}
				setState(95);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(99);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==EOL) {
				{
				{
				setState(96);
				match(EOL);
				}
				}
				setState(101);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(102);
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
		public InlineasmContext inlineasm() {
			return getRuleContext(InlineasmContext.class,0);
		}
		public LabelContext label() {
			return getRuleContext(LabelContext.class,0);
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
			setState(115);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,7,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(104);
				directive();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(105);
				varinitializer();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(106);
				vardecl();
				}
				break;
			case 4:
				enterOuterAlt(_localctx, 4);
				{
				setState(107);
				constdecl();
				}
				break;
			case 5:
				enterOuterAlt(_localctx, 5);
				{
				setState(108);
				memoryvardecl();
				}
				break;
			case 6:
				enterOuterAlt(_localctx, 6);
				{
				setState(109);
				assignment();
				}
				break;
			case 7:
				enterOuterAlt(_localctx, 7);
				{
				setState(110);
				augassignment();
				}
				break;
			case 8:
				enterOuterAlt(_localctx, 8);
				{
				setState(111);
				unconditionaljump();
				}
				break;
			case 9:
				enterOuterAlt(_localctx, 9);
				{
				setState(112);
				postincrdecr();
				}
				break;
			case 10:
				enterOuterAlt(_localctx, 10);
				{
				setState(113);
				inlineasm();
				}
				break;
			case 11:
				enterOuterAlt(_localctx, 11);
				{
				setState(114);
				label();
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

	public static class LabelContext extends ParserRuleContext {
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public LabelContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_label; }
	}

	public final LabelContext label() throws RecognitionException {
		LabelContext _localctx = new LabelContext(_ctx, getState());
		enterRule(_localctx, 8, RULE_label);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(117);
			identifier();
			setState(118);
			match(T__3);
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

	public static class Call_locationContext extends ParserRuleContext {
		public IntegerliteralContext integerliteral() {
			return getRuleContext(IntegerliteralContext.class,0);
		}
		public IdentifierContext identifier() {
			return getRuleContext(IdentifierContext.class,0);
		}
		public Scoped_identifierContext scoped_identifier() {
			return getRuleContext(Scoped_identifierContext.class,0);
		}
		public Call_locationContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_call_location; }
	}

	public final Call_locationContext call_location() throws RecognitionException {
		Call_locationContext _localctx = new Call_locationContext(_ctx, getState());
		enterRule(_localctx, 10, RULE_call_location);
		try {
			setState(123);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,8,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(120);
				integerliteral();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(121);
				identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(122);
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

	public static class UnconditionaljumpContext extends ParserRuleContext {
		public Call_locationContext call_location() {
			return getRuleContext(Call_locationContext.class,0);
		}
		public UnconditionaljumpContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_unconditionaljump; }
	}

	public final UnconditionaljumpContext unconditionaljump() throws RecognitionException {
		UnconditionaljumpContext _localctx = new UnconditionaljumpContext(_ctx, getState());
		enterRule(_localctx, 12, RULE_unconditionaljump);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(125);
			match(T__4);
			setState(126);
			call_location();
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
		enterRule(_localctx, 14, RULE_directive);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(128);
			((DirectiveContext)_localctx).directivename = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__5) | (1L << T__6) | (1L << T__7) | (1L << T__8) | (1L << T__9) | (1L << T__10) | (1L << T__11) | (1L << T__12))) != 0)) ) {
				((DirectiveContext)_localctx).directivename = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(140);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,11,_ctx) ) {
			case 1:
				{
				setState(130);
				_errHandler.sync(this);
				_la = _input.LA(1);
				if (((((_la - 85)) & ~0x3f) == 0 && ((1L << (_la - 85)) & ((1L << (NAME - 85)) | (1L << (DEC_INTEGER - 85)) | (1L << (HEX_INTEGER - 85)) | (1L << (BIN_INTEGER - 85)) | (1L << (STRING - 85)))) != 0)) {
					{
					setState(129);
					directivearg();
					}
				}

				}
				break;
			case 2:
				{
				setState(132);
				directivearg();
				setState(137);
				_errHandler.sync(this);
				_la = _input.LA(1);
				while (_la==T__13) {
					{
					{
					setState(133);
					match(T__13);
					setState(134);
					directivearg();
					}
					}
					setState(139);
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
		enterRule(_localctx, 16, RULE_directivearg);
		try {
			setState(145);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case STRING:
				enterOuterAlt(_localctx, 1);
				{
				setState(142);
				stringliteral();
				}
				break;
			case NAME:
				enterOuterAlt(_localctx, 2);
				{
				setState(143);
				identifier();
				}
				break;
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 3);
				{
				setState(144);
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
		enterRule(_localctx, 18, RULE_vardecl);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(147);
			datatype();
			setState(149);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__24) {
				{
				setState(148);
				arrayspec();
				}
			}

			setState(151);
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
		enterRule(_localctx, 20, RULE_varinitializer);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(153);
			datatype();
			setState(155);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__24) {
				{
				setState(154);
				arrayspec();
				}
			}

			setState(157);
			identifier();
			setState(158);
			match(T__14);
			setState(159);
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
		enterRule(_localctx, 22, RULE_constdecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(161);
			match(T__15);
			setState(162);
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
		enterRule(_localctx, 24, RULE_memoryvardecl);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(164);
			match(T__16);
			setState(165);
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
		enterRule(_localctx, 26, RULE_datatype);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(167);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__17) | (1L << T__18) | (1L << T__19) | (1L << T__20) | (1L << T__21) | (1L << T__22) | (1L << T__23))) != 0)) ) {
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
		enterRule(_localctx, 28, RULE_arrayspec);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(169);
			match(T__24);
			setState(170);
			expression(0);
			setState(173);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if (_la==T__13) {
				{
				setState(171);
				match(T__13);
				setState(172);
				expression(0);
				}
			}

			setState(175);
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
		enterRule(_localctx, 30, RULE_assignment);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(177);
			assign_target();
			setState(178);
			match(T__14);
			setState(179);
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
		enterRule(_localctx, 32, RULE_augassignment);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(181);
			assign_target();
			setState(182);
			((AugassignmentContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__26) | (1L << T__27) | (1L << T__28) | (1L << T__29) | (1L << T__30) | (1L << T__31) | (1L << T__32) | (1L << T__33) | (1L << T__34) | (1L << T__35) | (1L << T__36) | (1L << T__37) | (1L << T__38))) != 0)) ) {
				((AugassignmentContext)_localctx).operator = (Token)_errHandler.recoverInline(this);
			}
			else {
				if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
				_errHandler.reportMatch(this);
				consume();
			}
			setState(183);
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
		enterRule(_localctx, 34, RULE_assign_target);
		try {
			setState(188);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,16,_ctx) ) {
			case 1:
				enterOuterAlt(_localctx, 1);
				{
				setState(185);
				register();
				}
				break;
			case 2:
				enterOuterAlt(_localctx, 2);
				{
				setState(186);
				identifier();
				}
				break;
			case 3:
				enterOuterAlt(_localctx, 3);
				{
				setState(187);
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
		enterRule(_localctx, 36, RULE_postincrdecr);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(190);
			assign_target();
			setState(191);
			((PostincrdecrContext)_localctx).operator = _input.LT(1);
			_la = _input.LA(1);
			if ( !(_la==T__39 || _la==T__40) ) {
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
		public ArrayspecContext arrayspec() {
			return getRuleContext(ArrayspecContext.class,0);
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
			setState(207);
			_errHandler.sync(this);
			switch ( getInterpreter().adaptivePredict(_input,17,_ctx) ) {
			case 1:
				{
				setState(194);
				match(T__41);
				setState(195);
				expression(0);
				setState(196);
				match(T__42);
				}
				break;
			case 2:
				{
				setState(198);
				functioncall();
				}
				break;
			case 3:
				{
				setState(199);
				((ExpressionContext)_localctx).prefix = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__43 || _la==T__44) ) {
					((ExpressionContext)_localctx).prefix = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(200);
				expression(19);
				}
				break;
			case 4:
				{
				setState(201);
				((ExpressionContext)_localctx).prefix = _input.LT(1);
				_la = _input.LA(1);
				if ( !(_la==T__0 || _la==T__45) ) {
					((ExpressionContext)_localctx).prefix = (Token)_errHandler.recoverInline(this);
				}
				else {
					if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
					_errHandler.reportMatch(this);
					consume();
				}
				setState(202);
				expression(18);
				}
				break;
			case 5:
				{
				setState(203);
				literalvalue();
				}
				break;
			case 6:
				{
				setState(204);
				register();
				}
				break;
			case 7:
				{
				setState(205);
				identifier();
				}
				break;
			case 8:
				{
				setState(206);
				scoped_identifier();
				}
				break;
			}
			_ctx.stop = _input.LT(-1);
			setState(252);
			_errHandler.sync(this);
			_alt = getInterpreter().adaptivePredict(_input,19,_ctx);
			while ( _alt!=2 && _alt!=org.antlr.v4.runtime.atn.ATN.INVALID_ALT_NUMBER ) {
				if ( _alt==1 ) {
					if ( _parseListeners!=null ) triggerExitRuleEvent();
					_prevctx = _localctx;
					{
					setState(250);
					_errHandler.sync(this);
					switch ( getInterpreter().adaptivePredict(_input,18,_ctx) ) {
					case 1:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(209);
						if (!(precpred(_ctx, 17))) throw new FailedPredicateException(this, "precpred(_ctx, 17)");
						setState(210);
						((ExpressionContext)_localctx).bop = match(T__46);
						setState(211);
						((ExpressionContext)_localctx).right = expression(18);
						}
						break;
					case 2:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(212);
						if (!(precpred(_ctx, 16))) throw new FailedPredicateException(this, "precpred(_ctx, 16)");
						setState(213);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__47) | (1L << T__48) | (1L << T__49) | (1L << T__50))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(214);
						((ExpressionContext)_localctx).right = expression(17);
						}
						break;
					case 3:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(215);
						if (!(precpred(_ctx, 15))) throw new FailedPredicateException(this, "precpred(_ctx, 15)");
						setState(216);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__43 || _la==T__44) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(217);
						((ExpressionContext)_localctx).right = expression(16);
						}
						break;
					case 4:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(218);
						if (!(precpred(_ctx, 14))) throw new FailedPredicateException(this, "precpred(_ctx, 14)");
						setState(219);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__51) | (1L << T__52) | (1L << T__53) | (1L << T__54))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(220);
						((ExpressionContext)_localctx).right = expression(15);
						}
						break;
					case 5:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(221);
						if (!(precpred(_ctx, 13))) throw new FailedPredicateException(this, "precpred(_ctx, 13)");
						setState(222);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__55) | (1L << T__56) | (1L << T__57) | (1L << T__58))) != 0)) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(223);
						((ExpressionContext)_localctx).right = expression(14);
						}
						break;
					case 6:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(224);
						if (!(precpred(_ctx, 12))) throw new FailedPredicateException(this, "precpred(_ctx, 12)");
						setState(225);
						((ExpressionContext)_localctx).bop = _input.LT(1);
						_la = _input.LA(1);
						if ( !(_la==T__59 || _la==T__60) ) {
							((ExpressionContext)_localctx).bop = (Token)_errHandler.recoverInline(this);
						}
						else {
							if ( _input.LA(1)==Token.EOF ) matchedEOF = true;
							_errHandler.reportMatch(this);
							consume();
						}
						setState(226);
						((ExpressionContext)_localctx).right = expression(13);
						}
						break;
					case 7:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(227);
						if (!(precpred(_ctx, 11))) throw new FailedPredicateException(this, "precpred(_ctx, 11)");
						setState(228);
						((ExpressionContext)_localctx).bop = match(T__61);
						setState(229);
						((ExpressionContext)_localctx).right = expression(12);
						}
						break;
					case 8:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(230);
						if (!(precpred(_ctx, 10))) throw new FailedPredicateException(this, "precpred(_ctx, 10)");
						setState(231);
						((ExpressionContext)_localctx).bop = match(T__62);
						setState(232);
						((ExpressionContext)_localctx).right = expression(11);
						}
						break;
					case 9:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(233);
						if (!(precpred(_ctx, 9))) throw new FailedPredicateException(this, "precpred(_ctx, 9)");
						setState(234);
						((ExpressionContext)_localctx).bop = match(T__63);
						setState(235);
						((ExpressionContext)_localctx).right = expression(10);
						}
						break;
					case 10:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(236);
						if (!(precpred(_ctx, 8))) throw new FailedPredicateException(this, "precpred(_ctx, 8)");
						setState(237);
						((ExpressionContext)_localctx).bop = match(T__64);
						setState(238);
						((ExpressionContext)_localctx).right = expression(9);
						}
						break;
					case 11:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(239);
						if (!(precpred(_ctx, 7))) throw new FailedPredicateException(this, "precpred(_ctx, 7)");
						setState(240);
						((ExpressionContext)_localctx).bop = match(T__65);
						setState(241);
						((ExpressionContext)_localctx).right = expression(8);
						}
						break;
					case 12:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.left = _prevctx;
						_localctx.left = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(242);
						if (!(precpred(_ctx, 6))) throw new FailedPredicateException(this, "precpred(_ctx, 6)");
						setState(243);
						((ExpressionContext)_localctx).bop = match(T__66);
						setState(244);
						((ExpressionContext)_localctx).right = expression(7);
						}
						break;
					case 13:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						_localctx.rangefrom = _prevctx;
						_localctx.rangefrom = _prevctx;
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(245);
						if (!(precpred(_ctx, 5))) throw new FailedPredicateException(this, "precpred(_ctx, 5)");
						setState(246);
						match(T__67);
						setState(247);
						((ExpressionContext)_localctx).rangeto = expression(6);
						}
						break;
					case 14:
						{
						_localctx = new ExpressionContext(_parentctx, _parentState);
						pushNewRecursionContext(_localctx, _startState, RULE_expression);
						setState(248);
						if (!(precpred(_ctx, 21))) throw new FailedPredicateException(this, "precpred(_ctx, 21)");
						setState(249);
						arrayspec();
						}
						break;
					}
					} 
				}
				setState(254);
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,19,_ctx);
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

	public static class FunctioncallContext extends ParserRuleContext {
		public Call_locationContext call_location() {
			return getRuleContext(Call_locationContext.class,0);
		}
		public ExpressionContext expression() {
			return getRuleContext(ExpressionContext.class,0);
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
			setState(255);
			call_location();
			setState(256);
			match(T__41);
			setState(258);
			_errHandler.sync(this);
			_la = _input.LA(1);
			if ((((_la) & ~0x3f) == 0 && ((1L << _la) & ((1L << T__0) | (1L << T__24) | (1L << T__41) | (1L << T__43) | (1L << T__44) | (1L << T__45))) != 0) || ((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & ((1L << (T__69 - 70)) | (1L << (T__70 - 70)) | (1L << (T__71 - 70)) | (1L << (T__72 - 70)) | (1L << (T__73 - 70)) | (1L << (T__74 - 70)) | (1L << (T__75 - 70)) | (1L << (T__76 - 70)) | (1L << (T__77 - 70)) | (1L << (T__78 - 70)) | (1L << (T__79 - 70)) | (1L << (NAME - 70)) | (1L << (DEC_INTEGER - 70)) | (1L << (HEX_INTEGER - 70)) | (1L << (BIN_INTEGER - 70)) | (1L << (FLOAT_NUMBER - 70)) | (1L << (STRING - 70)))) != 0)) {
				{
				setState(257);
				expression(0);
				}
			}

			setState(260);
			match(T__42);
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
		public TerminalNode NAME() { return getToken(il65Parser.NAME, 0); }
		public IdentifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_identifier; }
	}

	public final IdentifierContext identifier() throws RecognitionException {
		IdentifierContext _localctx = new IdentifierContext(_ctx, getState());
		enterRule(_localctx, 42, RULE_identifier);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(262);
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
		public List<TerminalNode> NAME() { return getTokens(il65Parser.NAME); }
		public TerminalNode NAME(int i) {
			return getToken(il65Parser.NAME, i);
		}
		public Scoped_identifierContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_scoped_identifier; }
	}

	public final Scoped_identifierContext scoped_identifier() throws RecognitionException {
		Scoped_identifierContext _localctx = new Scoped_identifierContext(_ctx, getState());
		enterRule(_localctx, 44, RULE_scoped_identifier);
		try {
			int _alt;
			enterOuterAlt(_localctx, 1);
			{
			setState(264);
			match(NAME);
			setState(267); 
			_errHandler.sync(this);
			_alt = 1;
			do {
				switch (_alt) {
				case 1:
					{
					{
					setState(265);
					match(T__68);
					setState(266);
					match(NAME);
					}
					}
					break;
				default:
					throw new NoViableAltException(this);
				}
				setState(269); 
				_errHandler.sync(this);
				_alt = getInterpreter().adaptivePredict(_input,21,_ctx);
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
		enterRule(_localctx, 46, RULE_register);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(271);
			_la = _input.LA(1);
			if ( !(((((_la - 70)) & ~0x3f) == 0 && ((1L << (_la - 70)) & ((1L << (T__69 - 70)) | (1L << (T__70 - 70)) | (1L << (T__71 - 70)) | (1L << (T__72 - 70)) | (1L << (T__73 - 70)) | (1L << (T__74 - 70)) | (1L << (T__75 - 70)) | (1L << (T__76 - 70)) | (1L << (T__77 - 70)))) != 0)) ) {
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
		public TerminalNode DEC_INTEGER() { return getToken(il65Parser.DEC_INTEGER, 0); }
		public TerminalNode HEX_INTEGER() { return getToken(il65Parser.HEX_INTEGER, 0); }
		public TerminalNode BIN_INTEGER() { return getToken(il65Parser.BIN_INTEGER, 0); }
		public IntegerliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_integerliteral; }
	}

	public final IntegerliteralContext integerliteral() throws RecognitionException {
		IntegerliteralContext _localctx = new IntegerliteralContext(_ctx, getState());
		enterRule(_localctx, 48, RULE_integerliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(273);
			_la = _input.LA(1);
			if ( !(((((_la - 86)) & ~0x3f) == 0 && ((1L << (_la - 86)) & ((1L << (DEC_INTEGER - 86)) | (1L << (HEX_INTEGER - 86)) | (1L << (BIN_INTEGER - 86)))) != 0)) ) {
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

	public static class BooleanliteralContext extends ParserRuleContext {
		public BooleanliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_booleanliteral; }
	}

	public final BooleanliteralContext booleanliteral() throws RecognitionException {
		BooleanliteralContext _localctx = new BooleanliteralContext(_ctx, getState());
		enterRule(_localctx, 50, RULE_booleanliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(275);
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
		public ArrayliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_arrayliteral; }
	}

	public final ArrayliteralContext arrayliteral() throws RecognitionException {
		ArrayliteralContext _localctx = new ArrayliteralContext(_ctx, getState());
		enterRule(_localctx, 52, RULE_arrayliteral);
		int _la;
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(277);
			match(T__24);
			setState(278);
			expression(0);
			setState(283);
			_errHandler.sync(this);
			_la = _input.LA(1);
			while (_la==T__13) {
				{
				{
				setState(279);
				match(T__13);
				setState(280);
				expression(0);
				}
				}
				setState(285);
				_errHandler.sync(this);
				_la = _input.LA(1);
			}
			setState(286);
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

	public static class StringliteralContext extends ParserRuleContext {
		public TerminalNode STRING() { return getToken(il65Parser.STRING, 0); }
		public StringliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_stringliteral; }
	}

	public final StringliteralContext stringliteral() throws RecognitionException {
		StringliteralContext _localctx = new StringliteralContext(_ctx, getState());
		enterRule(_localctx, 54, RULE_stringliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(288);
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

	public static class FloatliteralContext extends ParserRuleContext {
		public TerminalNode FLOAT_NUMBER() { return getToken(il65Parser.FLOAT_NUMBER, 0); }
		public FloatliteralContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_floatliteral; }
	}

	public final FloatliteralContext floatliteral() throws RecognitionException {
		FloatliteralContext _localctx = new FloatliteralContext(_ctx, getState());
		enterRule(_localctx, 56, RULE_floatliteral);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(290);
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
		enterRule(_localctx, 58, RULE_literalvalue);
		try {
			setState(297);
			_errHandler.sync(this);
			switch (_input.LA(1)) {
			case DEC_INTEGER:
			case HEX_INTEGER:
			case BIN_INTEGER:
				enterOuterAlt(_localctx, 1);
				{
				setState(292);
				integerliteral();
				}
				break;
			case T__78:
			case T__79:
				enterOuterAlt(_localctx, 2);
				{
				setState(293);
				booleanliteral();
				}
				break;
			case T__24:
				enterOuterAlt(_localctx, 3);
				{
				setState(294);
				arrayliteral();
				}
				break;
			case STRING:
				enterOuterAlt(_localctx, 4);
				{
				setState(295);
				stringliteral();
				}
				break;
			case FLOAT_NUMBER:
				enterOuterAlt(_localctx, 5);
				{
				setState(296);
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
		public TerminalNode INLINEASMBLOCK() { return getToken(il65Parser.INLINEASMBLOCK, 0); }
		public InlineasmContext(ParserRuleContext parent, int invokingState) {
			super(parent, invokingState);
		}
		@Override public int getRuleIndex() { return RULE_inlineasm; }
	}

	public final InlineasmContext inlineasm() throws RecognitionException {
		InlineasmContext _localctx = new InlineasmContext(_ctx, getState());
		enterRule(_localctx, 60, RULE_inlineasm);
		try {
			enterOuterAlt(_localctx, 1);
			{
			setState(299);
			match(T__80);
			setState(300);
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
			return precpred(_ctx, 17);
		case 1:
			return precpred(_ctx, 16);
		case 2:
			return precpred(_ctx, 15);
		case 3:
			return precpred(_ctx, 14);
		case 4:
			return precpred(_ctx, 13);
		case 5:
			return precpred(_ctx, 12);
		case 6:
			return precpred(_ctx, 11);
		case 7:
			return precpred(_ctx, 10);
		case 8:
			return precpred(_ctx, 9);
		case 9:
			return precpred(_ctx, 8);
		case 10:
			return precpred(_ctx, 7);
		case 11:
			return precpred(_ctx, 6);
		case 12:
			return precpred(_ctx, 5);
		case 13:
			return precpred(_ctx, 21);
		}
		return true;
	}

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\3]\u0131\4\2\t\2\4"+
		"\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13\t"+
		"\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \3\2"+
		"\7\2B\n\2\f\2\16\2E\13\2\3\2\3\2\7\2I\n\2\f\2\16\2L\13\2\3\2\3\2\3\3\3"+
		"\3\5\3R\n\3\3\4\3\4\3\4\5\4W\n\4\3\4\3\4\3\4\3\4\3\4\7\4^\n\4\f\4\16\4"+
		"a\13\4\3\4\7\4d\n\4\f\4\16\4g\13\4\3\4\3\4\3\5\3\5\3\5\3\5\3\5\3\5\3\5"+
		"\3\5\3\5\3\5\3\5\5\5v\n\5\3\6\3\6\3\6\3\7\3\7\3\7\5\7~\n\7\3\b\3\b\3\b"+
		"\3\t\3\t\5\t\u0085\n\t\3\t\3\t\3\t\7\t\u008a\n\t\f\t\16\t\u008d\13\t\5"+
		"\t\u008f\n\t\3\n\3\n\3\n\5\n\u0094\n\n\3\13\3\13\5\13\u0098\n\13\3\13"+
		"\3\13\3\f\3\f\5\f\u009e\n\f\3\f\3\f\3\f\3\f\3\r\3\r\3\r\3\16\3\16\3\16"+
		"\3\17\3\17\3\20\3\20\3\20\3\20\5\20\u00b0\n\20\3\20\3\20\3\21\3\21\3\21"+
		"\3\21\3\22\3\22\3\22\3\22\3\23\3\23\3\23\5\23\u00bf\n\23\3\24\3\24\3\24"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\5\25\u00d2\n\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25\3\25"+
		"\3\25\3\25\7\25\u00fd\n\25\f\25\16\25\u0100\13\25\3\26\3\26\3\26\5\26"+
		"\u0105\n\26\3\26\3\26\3\27\3\27\3\30\3\30\3\30\6\30\u010e\n\30\r\30\16"+
		"\30\u010f\3\31\3\31\3\32\3\32\3\33\3\33\3\34\3\34\3\34\3\34\7\34\u011c"+
		"\n\34\f\34\16\34\u011f\13\34\3\34\3\34\3\35\3\35\3\36\3\36\3\37\3\37\3"+
		"\37\3\37\3\37\5\37\u012c\n\37\3 \3 \3 \3 \2\3(!\2\4\6\b\n\f\16\20\22\24"+
		"\26\30\32\34\36 \"$&(*,.\60\62\64\668:<>\2\17\3\2\b\17\3\2\24\32\3\2\35"+
		")\3\2*+\3\2./\4\2\3\3\60\60\3\2\62\65\3\2\669\3\2:=\3\2>?\3\2HP\3\2XZ"+
		"\3\2QR\2\u014a\2C\3\2\2\2\4Q\3\2\2\2\6S\3\2\2\2\bu\3\2\2\2\nw\3\2\2\2"+
		"\f}\3\2\2\2\16\177\3\2\2\2\20\u0082\3\2\2\2\22\u0093\3\2\2\2\24\u0095"+
		"\3\2\2\2\26\u009b\3\2\2\2\30\u00a3\3\2\2\2\32\u00a6\3\2\2\2\34\u00a9\3"+
		"\2\2\2\36\u00ab\3\2\2\2 \u00b3\3\2\2\2\"\u00b7\3\2\2\2$\u00be\3\2\2\2"+
		"&\u00c0\3\2\2\2(\u00d1\3\2\2\2*\u0101\3\2\2\2,\u0108\3\2\2\2.\u010a\3"+
		"\2\2\2\60\u0111\3\2\2\2\62\u0113\3\2\2\2\64\u0115\3\2\2\2\66\u0117\3\2"+
		"\2\28\u0122\3\2\2\2:\u0124\3\2\2\2<\u012b\3\2\2\2>\u012d\3\2\2\2@B\7V"+
		"\2\2A@\3\2\2\2BE\3\2\2\2CA\3\2\2\2CD\3\2\2\2DJ\3\2\2\2EC\3\2\2\2FI\5\4"+
		"\3\2GI\7V\2\2HF\3\2\2\2HG\3\2\2\2IL\3\2\2\2JH\3\2\2\2JK\3\2\2\2KM\3\2"+
		"\2\2LJ\3\2\2\2MN\7\2\2\3N\3\3\2\2\2OR\5\20\t\2PR\5\6\4\2QO\3\2\2\2QP\3"+
		"\2\2\2R\5\3\2\2\2ST\7\3\2\2TV\5,\27\2UW\5\62\32\2VU\3\2\2\2VW\3\2\2\2"+
		"WX\3\2\2\2XY\7\4\2\2Y_\7V\2\2Z[\5\b\5\2[\\\7V\2\2\\^\3\2\2\2]Z\3\2\2\2"+
		"^a\3\2\2\2_]\3\2\2\2_`\3\2\2\2`e\3\2\2\2a_\3\2\2\2bd\7V\2\2cb\3\2\2\2"+
		"dg\3\2\2\2ec\3\2\2\2ef\3\2\2\2fh\3\2\2\2ge\3\2\2\2hi\7\5\2\2i\7\3\2\2"+
		"\2jv\5\20\t\2kv\5\26\f\2lv\5\24\13\2mv\5\30\r\2nv\5\32\16\2ov\5 \21\2"+
		"pv\5\"\22\2qv\5\16\b\2rv\5&\24\2sv\5> \2tv\5\n\6\2uj\3\2\2\2uk\3\2\2\2"+
		"ul\3\2\2\2um\3\2\2\2un\3\2\2\2uo\3\2\2\2up\3\2\2\2uq\3\2\2\2ur\3\2\2\2"+
		"us\3\2\2\2ut\3\2\2\2v\t\3\2\2\2wx\5,\27\2xy\7\6\2\2y\13\3\2\2\2z~\5\62"+
		"\32\2{~\5,\27\2|~\5.\30\2}z\3\2\2\2}{\3\2\2\2}|\3\2\2\2~\r\3\2\2\2\177"+
		"\u0080\7\7\2\2\u0080\u0081\5\f\7\2\u0081\17\3\2\2\2\u0082\u008e\t\2\2"+
		"\2\u0083\u0085\5\22\n\2\u0084\u0083\3\2\2\2\u0084\u0085\3\2\2\2\u0085"+
		"\u008f\3\2\2\2\u0086\u008b\5\22\n\2\u0087\u0088\7\20\2\2\u0088\u008a\5"+
		"\22\n\2\u0089\u0087\3\2\2\2\u008a\u008d\3\2\2\2\u008b\u0089\3\2\2\2\u008b"+
		"\u008c\3\2\2\2\u008c\u008f\3\2\2\2\u008d\u008b\3\2\2\2\u008e\u0084\3\2"+
		"\2\2\u008e\u0086\3\2\2\2\u008f\21\3\2\2\2\u0090\u0094\58\35\2\u0091\u0094"+
		"\5,\27\2\u0092\u0094\5\62\32\2\u0093\u0090\3\2\2\2\u0093\u0091\3\2\2\2"+
		"\u0093\u0092\3\2\2\2\u0094\23\3\2\2\2\u0095\u0097\5\34\17\2\u0096\u0098"+
		"\5\36\20\2\u0097\u0096\3\2\2\2\u0097\u0098\3\2\2\2\u0098\u0099\3\2\2\2"+
		"\u0099\u009a\5,\27\2\u009a\25\3\2\2\2\u009b\u009d\5\34\17\2\u009c\u009e"+
		"\5\36\20\2\u009d\u009c\3\2\2\2\u009d\u009e\3\2\2\2\u009e\u009f\3\2\2\2"+
		"\u009f\u00a0\5,\27\2\u00a0\u00a1\7\21\2\2\u00a1\u00a2\5(\25\2\u00a2\27"+
		"\3\2\2\2\u00a3\u00a4\7\22\2\2\u00a4\u00a5\5\26\f\2\u00a5\31\3\2\2\2\u00a6"+
		"\u00a7\7\23\2\2\u00a7\u00a8\5\26\f\2\u00a8\33\3\2\2\2\u00a9\u00aa\t\3"+
		"\2\2\u00aa\35\3\2\2\2\u00ab\u00ac\7\33\2\2\u00ac\u00af\5(\25\2\u00ad\u00ae"+
		"\7\20\2\2\u00ae\u00b0\5(\25\2\u00af\u00ad\3\2\2\2\u00af\u00b0\3\2\2\2"+
		"\u00b0\u00b1\3\2\2\2\u00b1\u00b2\7\34\2\2\u00b2\37\3\2\2\2\u00b3\u00b4"+
		"\5$\23\2\u00b4\u00b5\7\21\2\2\u00b5\u00b6\5(\25\2\u00b6!\3\2\2\2\u00b7"+
		"\u00b8\5$\23\2\u00b8\u00b9\t\4\2\2\u00b9\u00ba\5(\25\2\u00ba#\3\2\2\2"+
		"\u00bb\u00bf\5\60\31\2\u00bc\u00bf\5,\27\2\u00bd\u00bf\5.\30\2\u00be\u00bb"+
		"\3\2\2\2\u00be\u00bc\3\2\2\2\u00be\u00bd\3\2\2\2\u00bf%\3\2\2\2\u00c0"+
		"\u00c1\5$\23\2\u00c1\u00c2\t\5\2\2\u00c2\'\3\2\2\2\u00c3\u00c4\b\25\1"+
		"\2\u00c4\u00c5\7,\2\2\u00c5\u00c6\5(\25\2\u00c6\u00c7\7-\2\2\u00c7\u00d2"+
		"\3\2\2\2\u00c8\u00d2\5*\26\2\u00c9\u00ca\t\6\2\2\u00ca\u00d2\5(\25\25"+
		"\u00cb\u00cc\t\7\2\2\u00cc\u00d2\5(\25\24\u00cd\u00d2\5<\37\2\u00ce\u00d2"+
		"\5\60\31\2\u00cf\u00d2\5,\27\2\u00d0\u00d2\5.\30\2\u00d1\u00c3\3\2\2\2"+
		"\u00d1\u00c8\3\2\2\2\u00d1\u00c9\3\2\2\2\u00d1\u00cb\3\2\2\2\u00d1\u00cd"+
		"\3\2\2\2\u00d1\u00ce\3\2\2\2\u00d1\u00cf\3\2\2\2\u00d1\u00d0\3\2\2\2\u00d2"+
		"\u00fe\3\2\2\2\u00d3\u00d4\f\23\2\2\u00d4\u00d5\7\61\2\2\u00d5\u00fd\5"+
		"(\25\24\u00d6\u00d7\f\22\2\2\u00d7\u00d8\t\b\2\2\u00d8\u00fd\5(\25\23"+
		"\u00d9\u00da\f\21\2\2\u00da\u00db\t\6\2\2\u00db\u00fd\5(\25\22\u00dc\u00dd"+
		"\f\20\2\2\u00dd\u00de\t\t\2\2\u00de\u00fd\5(\25\21\u00df\u00e0\f\17\2"+
		"\2\u00e0\u00e1\t\n\2\2\u00e1\u00fd\5(\25\20\u00e2\u00e3\f\16\2\2\u00e3"+
		"\u00e4\t\13\2\2\u00e4\u00fd\5(\25\17\u00e5\u00e6\f\r\2\2\u00e6\u00e7\7"+
		"@\2\2\u00e7\u00fd\5(\25\16\u00e8\u00e9\f\f\2\2\u00e9\u00ea\7A\2\2\u00ea"+
		"\u00fd\5(\25\r\u00eb\u00ec\f\13\2\2\u00ec\u00ed\7B\2\2\u00ed\u00fd\5("+
		"\25\f\u00ee\u00ef\f\n\2\2\u00ef\u00f0\7C\2\2\u00f0\u00fd\5(\25\13\u00f1"+
		"\u00f2\f\t\2\2\u00f2\u00f3\7D\2\2\u00f3\u00fd\5(\25\n\u00f4\u00f5\f\b"+
		"\2\2\u00f5\u00f6\7E\2\2\u00f6\u00fd\5(\25\t\u00f7\u00f8\f\7\2\2\u00f8"+
		"\u00f9\7F\2\2\u00f9\u00fd\5(\25\b\u00fa\u00fb\f\27\2\2\u00fb\u00fd\5\36"+
		"\20\2\u00fc\u00d3\3\2\2\2\u00fc\u00d6\3\2\2\2\u00fc\u00d9\3\2\2\2\u00fc"+
		"\u00dc\3\2\2\2\u00fc\u00df\3\2\2\2\u00fc\u00e2\3\2\2\2\u00fc\u00e5\3\2"+
		"\2\2\u00fc\u00e8\3\2\2\2\u00fc\u00eb\3\2\2\2\u00fc\u00ee\3\2\2\2\u00fc"+
		"\u00f1\3\2\2\2\u00fc\u00f4\3\2\2\2\u00fc\u00f7\3\2\2\2\u00fc\u00fa\3\2"+
		"\2\2\u00fd\u0100\3\2\2\2\u00fe\u00fc\3\2\2\2\u00fe\u00ff\3\2\2\2\u00ff"+
		")\3\2\2\2\u0100\u00fe\3\2\2\2\u0101\u0102\5\f\7\2\u0102\u0104\7,\2\2\u0103"+
		"\u0105\5(\25\2\u0104\u0103\3\2\2\2\u0104\u0105\3\2\2\2\u0105\u0106\3\2"+
		"\2\2\u0106\u0107\7-\2\2\u0107+\3\2\2\2\u0108\u0109\7W\2\2\u0109-\3\2\2"+
		"\2\u010a\u010d\7W\2\2\u010b\u010c\7G\2\2\u010c\u010e\7W\2\2\u010d\u010b"+
		"\3\2\2\2\u010e\u010f\3\2\2\2\u010f\u010d\3\2\2\2\u010f\u0110\3\2\2\2\u0110"+
		"/\3\2\2\2\u0111\u0112\t\f\2\2\u0112\61\3\2\2\2\u0113\u0114\t\r\2\2\u0114"+
		"\63\3\2\2\2\u0115\u0116\t\16\2\2\u0116\65\3\2\2\2\u0117\u0118\7\33\2\2"+
		"\u0118\u011d\5(\25\2\u0119\u011a\7\20\2\2\u011a\u011c\5(\25\2\u011b\u0119"+
		"\3\2\2\2\u011c\u011f\3\2\2\2\u011d\u011b\3\2\2\2\u011d\u011e\3\2\2\2\u011e"+
		"\u0120\3\2\2\2\u011f\u011d\3\2\2\2\u0120\u0121\7\34\2\2\u0121\67\3\2\2"+
		"\2\u0122\u0123\7\\\2\2\u01239\3\2\2\2\u0124\u0125\7[\2\2\u0125;\3\2\2"+
		"\2\u0126\u012c\5\62\32\2\u0127\u012c\5\64\33\2\u0128\u012c\5\66\34\2\u0129"+
		"\u012c\58\35\2\u012a\u012c\5:\36\2\u012b\u0126\3\2\2\2\u012b\u0127\3\2"+
		"\2\2\u012b\u0128\3\2\2\2\u012b\u0129\3\2\2\2\u012b\u012a\3\2\2\2\u012c"+
		"=\3\2\2\2\u012d\u012e\7S\2\2\u012e\u012f\7]\2\2\u012f?\3\2\2\2\32CHJQ"+
		"V_eu}\u0084\u008b\u008e\u0093\u0097\u009d\u00af\u00be\u00d1\u00fc\u00fe"+
		"\u0104\u010f\u011d\u012b";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}