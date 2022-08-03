// Generated from src/top/origami404/ssyc/frontend/SysY.g4 by ANTLR 4.9.3

package top.origami404.ssyc.frontend;

import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.atn.*;
import org.antlr.v4.runtime.dfa.DFA;
import org.antlr.v4.runtime.misc.*;

@SuppressWarnings({"all", "warnings", "unchecked", "unused", "cast"})
public class SysYLexer extends Lexer {
	static { RuntimeMetaData.checkVersion("4.9.3", RuntimeMetaData.VERSION); }

	protected static final DFA[] _decisionToDFA;
	protected static final PredictionContextCache _sharedContextCache =
		new PredictionContextCache();
	public static final int
		T__0=1, T__1=2, T__2=3, T__3=4, T__4=5, T__5=6, T__6=7, T__7=8, T__8=9, 
		T__9=10, T__10=11, T__11=12, T__12=13, T__13=14, T__14=15, T__15=16, T__16=17, 
		T__17=18, T__18=19, T__19=20, T__20=21, T__21=22, T__22=23, T__23=24, 
		T__24=25, T__25=26, T__26=27, Const=28, Break=29, Continue=30, Return=31, 
		BType=32, Ident=33, FloatConst=34, IntConst=35, StrConst=36, WS=37, LineComments=38, 
		BlockComments=39;
	public static String[] channelNames = {
		"DEFAULT_TOKEN_CHANNEL", "HIDDEN"
	};

	public static String[] modeNames = {
		"DEFAULT_MODE"
	};

	private static String[] makeRuleNames() {
		return new String[] {
			"T__0", "T__1", "T__2", "T__3", "T__4", "T__5", "T__6", "T__7", "T__8", 
			"T__9", "T__10", "T__11", "T__12", "T__13", "T__14", "T__15", "T__16", 
			"T__17", "T__18", "T__19", "T__20", "T__21", "T__22", "T__23", "T__24", 
			"T__25", "T__26", "Const", "Break", "Continue", "Return", "BType", "Ident", 
			"FloatConst", "FloatDec", "FloatDecExp", "FloatHex", "IntConst", "IntDec", 
			"IntOct", "IntHex", "DEC", "HEX", "StrConst", "WS", "LineComments", "BlockComments"
		};
	}
	public static final String[] ruleNames = makeRuleNames();

	private static String[] makeLiteralNames() {
		return new String[] {
			null, "'('", "')'", "','", "';'", "'='", "'{'", "'}'", "'['", "']'", 
			"'if'", "'else'", "'while'", "'putf'", "'||'", "'&&'", "'=='", "'!='", 
			"'<'", "'>'", "'<='", "'>='", "'+'", "'-'", "'*'", "'/'", "'%'", "'!'", 
			"'const'", "'break'", "'continue'", "'return'"
		};
	}
	private static final String[] _LITERAL_NAMES = makeLiteralNames();
	private static String[] makeSymbolicNames() {
		return new String[] {
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, null, null, null, null, null, null, null, null, 
			null, null, null, null, "Const", "Break", "Continue", "Return", "BType", 
			"Ident", "FloatConst", "IntConst", "StrConst", "WS", "LineComments", 
			"BlockComments"
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


	public SysYLexer(CharStream input) {
		super(input);
		_interp = new LexerATNSimulator(this,_ATN,_decisionToDFA,_sharedContextCache);
	}

	@Override
	public String getGrammarFileName() { return "SysY.g4"; }

	@Override
	public String[] getRuleNames() { return ruleNames; }

	@Override
	public String getSerializedATN() { return _serializedATN; }

	@Override
	public String[] getChannelNames() { return channelNames; }

	@Override
	public String[] getModeNames() { return modeNames; }

	@Override
	public ATN getATN() { return _ATN; }

	public static final String _serializedATN =
		"\3\u608b\ua72a\u8133\ub9ed\u417c\u3be7\u7786\u5964\2)\u0187\b\1\4\2\t"+
		"\2\4\3\t\3\4\4\t\4\4\5\t\5\4\6\t\6\4\7\t\7\4\b\t\b\4\t\t\t\4\n\t\n\4\13"+
		"\t\13\4\f\t\f\4\r\t\r\4\16\t\16\4\17\t\17\4\20\t\20\4\21\t\21\4\22\t\22"+
		"\4\23\t\23\4\24\t\24\4\25\t\25\4\26\t\26\4\27\t\27\4\30\t\30\4\31\t\31"+
		"\4\32\t\32\4\33\t\33\4\34\t\34\4\35\t\35\4\36\t\36\4\37\t\37\4 \t \4!"+
		"\t!\4\"\t\"\4#\t#\4$\t$\4%\t%\4&\t&\4\'\t\'\4(\t(\4)\t)\4*\t*\4+\t+\4"+
		",\t,\4-\t-\4.\t.\4/\t/\4\60\t\60\3\2\3\2\3\3\3\3\3\4\3\4\3\5\3\5\3\6\3"+
		"\6\3\7\3\7\3\b\3\b\3\t\3\t\3\n\3\n\3\13\3\13\3\13\3\f\3\f\3\f\3\f\3\f"+
		"\3\r\3\r\3\r\3\r\3\r\3\r\3\16\3\16\3\16\3\16\3\16\3\17\3\17\3\17\3\20"+
		"\3\20\3\20\3\21\3\21\3\21\3\22\3\22\3\22\3\23\3\23\3\24\3\24\3\25\3\25"+
		"\3\25\3\26\3\26\3\26\3\27\3\27\3\30\3\30\3\31\3\31\3\32\3\32\3\33\3\33"+
		"\3\34\3\34\3\35\3\35\3\35\3\35\3\35\3\35\3\36\3\36\3\36\3\36\3\36\3\36"+
		"\3\37\3\37\3\37\3\37\3\37\3\37\3\37\3\37\3\37\3 \3 \3 \3 \3 \3 \3 \3!"+
		"\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\3!\5!\u00d1\n!\3\"\3\"\7\"\u00d5\n\"\f"+
		"\"\16\"\u00d8\13\"\3#\3#\5#\u00dc\n#\3$\6$\u00df\n$\r$\16$\u00e0\3$\3"+
		"$\3$\3$\6$\u00e7\n$\r$\16$\u00e8\3$\6$\u00ec\n$\r$\16$\u00ed\3$\3$\6$"+
		"\u00f2\n$\r$\16$\u00f3\5$\u00f6\n$\3$\5$\u00f9\n$\3$\6$\u00fc\n$\r$\16"+
		"$\u00fd\3$\3$\5$\u0102\n$\3%\3%\5%\u0106\n%\3%\6%\u0109\n%\r%\16%\u010a"+
		"\3&\3&\3&\3&\5&\u0111\n&\3&\6&\u0114\n&\r&\16&\u0115\3&\6&\u0119\n&\r"+
		"&\16&\u011a\3&\3&\3&\3&\6&\u0121\n&\r&\16&\u0122\3&\6&\u0126\n&\r&\16"+
		"&\u0127\3&\3&\6&\u012c\n&\r&\16&\u012d\5&\u0130\n&\3&\3&\5&\u0134\n&\3"+
		"&\6&\u0137\n&\r&\16&\u0138\3\'\3\'\3\'\5\'\u013e\n\'\3(\3(\7(\u0142\n"+
		"(\f(\16(\u0145\13(\3)\3)\7)\u0149\n)\f)\16)\u014c\13)\3*\3*\3*\3*\5*\u0152"+
		"\n*\3*\6*\u0155\n*\r*\16*\u0156\3+\3+\3,\3,\3-\3-\7-\u015f\n-\f-\16-\u0162"+
		"\13-\3-\3-\3.\6.\u0167\n.\r.\16.\u0168\3.\3.\3/\3/\3/\3/\7/\u0171\n/\f"+
		"/\16/\u0174\13/\3/\3/\3/\3/\3\60\3\60\3\60\3\60\7\60\u017e\n\60\f\60\16"+
		"\60\u0181\13\60\3\60\3\60\3\60\3\60\3\60\4\u0172\u017f\2\61\3\3\5\4\7"+
		"\5\t\6\13\7\r\b\17\t\21\n\23\13\25\f\27\r\31\16\33\17\35\20\37\21!\22"+
		"#\23%\24\'\25)\26+\27-\30/\31\61\32\63\33\65\34\67\359\36;\37= ?!A\"C"+
		"#E$G\2I\2K\2M%O\2Q\2S\2U\2W\2Y&[\'](_)\3\2\n\5\2C\\aac|\6\2\62;C\\aac"+
		"|\4\2GGgg\4\2--//\4\2RRrr\5\2\62;CHch\4\2\f\f$$\5\2\13\f\17\17\"\"\2\u01a2"+
		"\2\3\3\2\2\2\2\5\3\2\2\2\2\7\3\2\2\2\2\t\3\2\2\2\2\13\3\2\2\2\2\r\3\2"+
		"\2\2\2\17\3\2\2\2\2\21\3\2\2\2\2\23\3\2\2\2\2\25\3\2\2\2\2\27\3\2\2\2"+
		"\2\31\3\2\2\2\2\33\3\2\2\2\2\35\3\2\2\2\2\37\3\2\2\2\2!\3\2\2\2\2#\3\2"+
		"\2\2\2%\3\2\2\2\2\'\3\2\2\2\2)\3\2\2\2\2+\3\2\2\2\2-\3\2\2\2\2/\3\2\2"+
		"\2\2\61\3\2\2\2\2\63\3\2\2\2\2\65\3\2\2\2\2\67\3\2\2\2\29\3\2\2\2\2;\3"+
		"\2\2\2\2=\3\2\2\2\2?\3\2\2\2\2A\3\2\2\2\2C\3\2\2\2\2E\3\2\2\2\2M\3\2\2"+
		"\2\2Y\3\2\2\2\2[\3\2\2\2\2]\3\2\2\2\2_\3\2\2\2\3a\3\2\2\2\5c\3\2\2\2\7"+
		"e\3\2\2\2\tg\3\2\2\2\13i\3\2\2\2\rk\3\2\2\2\17m\3\2\2\2\21o\3\2\2\2\23"+
		"q\3\2\2\2\25s\3\2\2\2\27v\3\2\2\2\31{\3\2\2\2\33\u0081\3\2\2\2\35\u0086"+
		"\3\2\2\2\37\u0089\3\2\2\2!\u008c\3\2\2\2#\u008f\3\2\2\2%\u0092\3\2\2\2"+
		"\'\u0094\3\2\2\2)\u0096\3\2\2\2+\u0099\3\2\2\2-\u009c\3\2\2\2/\u009e\3"+
		"\2\2\2\61\u00a0\3\2\2\2\63\u00a2\3\2\2\2\65\u00a4\3\2\2\2\67\u00a6\3\2"+
		"\2\29\u00a8\3\2\2\2;\u00ae\3\2\2\2=\u00b4\3\2\2\2?\u00bd\3\2\2\2A\u00d0"+
		"\3\2\2\2C\u00d2\3\2\2\2E\u00db\3\2\2\2G\u0101\3\2\2\2I\u0103\3\2\2\2K"+
		"\u0110\3\2\2\2M\u013d\3\2\2\2O\u013f\3\2\2\2Q\u0146\3\2\2\2S\u0151\3\2"+
		"\2\2U\u0158\3\2\2\2W\u015a\3\2\2\2Y\u015c\3\2\2\2[\u0166\3\2\2\2]\u016c"+
		"\3\2\2\2_\u0179\3\2\2\2ab\7*\2\2b\4\3\2\2\2cd\7+\2\2d\6\3\2\2\2ef\7.\2"+
		"\2f\b\3\2\2\2gh\7=\2\2h\n\3\2\2\2ij\7?\2\2j\f\3\2\2\2kl\7}\2\2l\16\3\2"+
		"\2\2mn\7\177\2\2n\20\3\2\2\2op\7]\2\2p\22\3\2\2\2qr\7_\2\2r\24\3\2\2\2"+
		"st\7k\2\2tu\7h\2\2u\26\3\2\2\2vw\7g\2\2wx\7n\2\2xy\7u\2\2yz\7g\2\2z\30"+
		"\3\2\2\2{|\7y\2\2|}\7j\2\2}~\7k\2\2~\177\7n\2\2\177\u0080\7g\2\2\u0080"+
		"\32\3\2\2\2\u0081\u0082\7r\2\2\u0082\u0083\7w\2\2\u0083\u0084\7v\2\2\u0084"+
		"\u0085\7h\2\2\u0085\34\3\2\2\2\u0086\u0087\7~\2\2\u0087\u0088\7~\2\2\u0088"+
		"\36\3\2\2\2\u0089\u008a\7(\2\2\u008a\u008b\7(\2\2\u008b \3\2\2\2\u008c"+
		"\u008d\7?\2\2\u008d\u008e\7?\2\2\u008e\"\3\2\2\2\u008f\u0090\7#\2\2\u0090"+
		"\u0091\7?\2\2\u0091$\3\2\2\2\u0092\u0093\7>\2\2\u0093&\3\2\2\2\u0094\u0095"+
		"\7@\2\2\u0095(\3\2\2\2\u0096\u0097\7>\2\2\u0097\u0098\7?\2\2\u0098*\3"+
		"\2\2\2\u0099\u009a\7@\2\2\u009a\u009b\7?\2\2\u009b,\3\2\2\2\u009c\u009d"+
		"\7-\2\2\u009d.\3\2\2\2\u009e\u009f\7/\2\2\u009f\60\3\2\2\2\u00a0\u00a1"+
		"\7,\2\2\u00a1\62\3\2\2\2\u00a2\u00a3\7\61\2\2\u00a3\64\3\2\2\2\u00a4\u00a5"+
		"\7\'\2\2\u00a5\66\3\2\2\2\u00a6\u00a7\7#\2\2\u00a78\3\2\2\2\u00a8\u00a9"+
		"\7e\2\2\u00a9\u00aa\7q\2\2\u00aa\u00ab\7p\2\2\u00ab\u00ac\7u\2\2\u00ac"+
		"\u00ad\7v\2\2\u00ad:\3\2\2\2\u00ae\u00af\7d\2\2\u00af\u00b0\7t\2\2\u00b0"+
		"\u00b1\7g\2\2\u00b1\u00b2\7c\2\2\u00b2\u00b3\7m\2\2\u00b3<\3\2\2\2\u00b4"+
		"\u00b5\7e\2\2\u00b5\u00b6\7q\2\2\u00b6\u00b7\7p\2\2\u00b7\u00b8\7v\2\2"+
		"\u00b8\u00b9\7k\2\2\u00b9\u00ba\7p\2\2\u00ba\u00bb\7w\2\2\u00bb\u00bc"+
		"\7g\2\2\u00bc>\3\2\2\2\u00bd\u00be\7t\2\2\u00be\u00bf\7g\2\2\u00bf\u00c0"+
		"\7v\2\2\u00c0\u00c1\7w\2\2\u00c1\u00c2\7t\2\2\u00c2\u00c3\7p\2\2\u00c3"+
		"@\3\2\2\2\u00c4\u00c5\7k\2\2\u00c5\u00c6\7p\2\2\u00c6\u00d1\7v\2\2\u00c7"+
		"\u00c8\7h\2\2\u00c8\u00c9\7n\2\2\u00c9\u00ca\7q\2\2\u00ca\u00cb\7c\2\2"+
		"\u00cb\u00d1\7v\2\2\u00cc\u00cd\7x\2\2\u00cd\u00ce\7q\2\2\u00ce\u00cf"+
		"\7k\2\2\u00cf\u00d1\7f\2\2\u00d0\u00c4\3\2\2\2\u00d0\u00c7\3\2\2\2\u00d0"+
		"\u00cc\3\2\2\2\u00d1B\3\2\2\2\u00d2\u00d6\t\2\2\2\u00d3\u00d5\t\3\2\2"+
		"\u00d4\u00d3\3\2\2\2\u00d5\u00d8\3\2\2\2\u00d6\u00d4\3\2\2\2\u00d6\u00d7"+
		"\3\2\2\2\u00d7D\3\2\2\2\u00d8\u00d6\3\2\2\2\u00d9\u00dc\5G$\2\u00da\u00dc"+
		"\5K&\2\u00db\u00d9\3\2\2\2\u00db\u00da\3\2\2\2\u00dcF\3\2\2\2\u00dd\u00df"+
		"\5U+\2\u00de\u00dd\3\2\2\2\u00df\u00e0\3\2\2\2\u00e0\u00de\3\2\2\2\u00e0"+
		"\u00e1\3\2\2\2\u00e1\u00e2\3\2\2\2\u00e2\u00e3\7\60\2\2\u00e3\u00f6\3"+
		"\2\2\2\u00e4\u00e6\7\60\2\2\u00e5\u00e7\5U+\2\u00e6\u00e5\3\2\2\2\u00e7"+
		"\u00e8\3\2\2\2\u00e8\u00e6\3\2\2\2\u00e8\u00e9\3\2\2\2\u00e9\u00f6\3\2"+
		"\2\2\u00ea\u00ec\5U+\2\u00eb\u00ea\3\2\2\2\u00ec\u00ed\3\2\2\2\u00ed\u00eb"+
		"\3\2\2\2\u00ed\u00ee\3\2\2\2\u00ee\u00ef\3\2\2\2\u00ef\u00f1\7\60\2\2"+
		"\u00f0\u00f2\5U+\2\u00f1\u00f0\3\2\2\2\u00f2\u00f3\3\2\2\2\u00f3\u00f1"+
		"\3\2\2\2\u00f3\u00f4\3\2\2\2\u00f4\u00f6\3\2\2\2\u00f5\u00de\3\2\2\2\u00f5"+
		"\u00e4\3\2\2\2\u00f5\u00eb\3\2\2\2\u00f6\u00f8\3\2\2\2\u00f7\u00f9\5I"+
		"%\2\u00f8\u00f7\3\2\2\2\u00f8\u00f9\3\2\2\2\u00f9\u0102\3\2\2\2\u00fa"+
		"\u00fc\5U+\2\u00fb\u00fa\3\2\2\2\u00fc\u00fd\3\2\2\2\u00fd\u00fb\3\2\2"+
		"\2\u00fd\u00fe\3\2\2\2\u00fe\u00ff\3\2\2\2\u00ff\u0100\5I%\2\u0100\u0102"+
		"\3\2\2\2\u0101\u00f5\3\2\2\2\u0101\u00fb\3\2\2\2\u0102H\3\2\2\2\u0103"+
		"\u0105\t\4\2\2\u0104\u0106\t\5\2\2\u0105\u0104\3\2\2\2\u0105\u0106\3\2"+
		"\2\2\u0106\u0108\3\2\2\2\u0107\u0109\5U+\2\u0108\u0107\3\2\2\2\u0109\u010a"+
		"\3\2\2\2\u010a\u0108\3\2\2\2\u010a\u010b\3\2\2\2\u010bJ\3\2\2\2\u010c"+
		"\u010d\7\62\2\2\u010d\u0111\7z\2\2\u010e\u010f\7\62\2\2\u010f\u0111\7"+
		"Z\2\2\u0110\u010c\3\2\2\2\u0110\u010e\3\2\2\2\u0111\u012f\3\2\2\2\u0112"+
		"\u0114\5W,\2\u0113\u0112\3\2\2\2\u0114\u0115\3\2\2\2\u0115\u0113\3\2\2"+
		"\2\u0115\u0116\3\2\2\2\u0116\u0130\3\2\2\2\u0117\u0119\5W,\2\u0118\u0117"+
		"\3\2\2\2\u0119\u011a\3\2\2\2\u011a\u0118\3\2\2\2\u011a\u011b\3\2\2\2\u011b"+
		"\u011c\3\2\2\2\u011c\u011d\7\60\2\2\u011d\u0130\3\2\2\2\u011e\u0120\7"+
		"\60\2\2\u011f\u0121\5W,\2\u0120\u011f\3\2\2\2\u0121\u0122\3\2\2\2\u0122"+
		"\u0120\3\2\2\2\u0122\u0123\3\2\2\2\u0123\u0130\3\2\2\2\u0124\u0126\5W"+
		",\2\u0125\u0124\3\2\2\2\u0126\u0127\3\2\2\2\u0127\u0125\3\2\2\2\u0127"+
		"\u0128\3\2\2\2\u0128\u0129\3\2\2\2\u0129\u012b\7\60\2\2\u012a\u012c\5"+
		"W,\2\u012b\u012a\3\2\2\2\u012c\u012d\3\2\2\2\u012d\u012b\3\2\2\2\u012d"+
		"\u012e\3\2\2\2\u012e\u0130\3\2\2\2\u012f\u0113\3\2\2\2\u012f\u0118\3\2"+
		"\2\2\u012f\u011e\3\2\2\2\u012f\u0125\3\2\2\2\u0130\u0131\3\2\2\2\u0131"+
		"\u0133\t\6\2\2\u0132\u0134\t\5\2\2\u0133\u0132\3\2\2\2\u0133\u0134\3\2"+
		"\2\2\u0134\u0136\3\2\2\2\u0135\u0137\5U+\2\u0136\u0135\3\2\2\2\u0137\u0138"+
		"\3\2\2\2\u0138\u0136\3\2\2\2\u0138\u0139\3\2\2\2\u0139L\3\2\2\2\u013a"+
		"\u013e\5O(\2\u013b\u013e\5Q)\2\u013c\u013e\5S*\2\u013d\u013a\3\2\2\2\u013d"+
		"\u013b\3\2\2\2\u013d\u013c\3\2\2\2\u013eN\3\2\2\2\u013f\u0143\4\63;\2"+
		"\u0140\u0142\5U+\2\u0141\u0140\3\2\2\2\u0142\u0145\3\2\2\2\u0143\u0141"+
		"\3\2\2\2\u0143\u0144\3\2\2\2\u0144P\3\2\2\2\u0145\u0143\3\2\2\2\u0146"+
		"\u014a\7\62\2\2\u0147\u0149\4\629\2\u0148\u0147\3\2\2\2\u0149\u014c\3"+
		"\2\2\2\u014a\u0148\3\2\2\2\u014a\u014b\3\2\2\2\u014bR\3\2\2\2\u014c\u014a"+
		"\3\2\2\2\u014d\u014e\7\62\2\2\u014e\u0152\7z\2\2\u014f\u0150\7\62\2\2"+
		"\u0150\u0152\7Z\2\2\u0151\u014d\3\2\2\2\u0151\u014f\3\2\2\2\u0152\u0154"+
		"\3\2\2\2\u0153\u0155\5W,\2\u0154\u0153\3\2\2\2\u0155\u0156\3\2\2\2\u0156"+
		"\u0154\3\2\2\2\u0156\u0157\3\2\2\2\u0157T\3\2\2\2\u0158\u0159\4\62;\2"+
		"\u0159V\3\2\2\2\u015a\u015b\t\7\2\2\u015bX\3\2\2\2\u015c\u0160\7$\2\2"+
		"\u015d\u015f\n\b\2\2\u015e\u015d\3\2\2\2\u015f\u0162\3\2\2\2\u0160\u015e"+
		"\3\2\2\2\u0160\u0161\3\2\2\2\u0161\u0163\3\2\2\2\u0162\u0160\3\2\2\2\u0163"+
		"\u0164\7$\2\2\u0164Z\3\2\2\2\u0165\u0167\t\t\2\2\u0166\u0165\3\2\2\2\u0167"+
		"\u0168\3\2\2\2\u0168\u0166\3\2\2\2\u0168\u0169\3\2\2\2\u0169\u016a\3\2"+
		"\2\2\u016a\u016b\b.\2\2\u016b\\\3\2\2\2\u016c\u016d\7\61\2\2\u016d\u016e"+
		"\7\61\2\2\u016e\u0172\3\2\2\2\u016f\u0171\13\2\2\2\u0170\u016f\3\2\2\2"+
		"\u0171\u0174\3\2\2\2\u0172\u0173\3\2\2\2\u0172\u0170\3\2\2\2\u0173\u0175"+
		"\3\2\2\2\u0174\u0172\3\2\2\2\u0175\u0176\7\f\2\2\u0176\u0177\3\2\2\2\u0177"+
		"\u0178\b/\2\2\u0178^\3\2\2\2\u0179\u017a\7\61\2\2\u017a\u017b\7,\2\2\u017b"+
		"\u017f\3\2\2\2\u017c\u017e\13\2\2\2\u017d\u017c\3\2\2\2\u017e\u0181\3"+
		"\2\2\2\u017f\u0180\3\2\2\2\u017f\u017d\3\2\2\2\u0180\u0182\3\2\2\2\u0181"+
		"\u017f\3\2\2\2\u0182\u0183\7,\2\2\u0183\u0184\7\61\2\2\u0184\u0185\3\2"+
		"\2\2\u0185\u0186\b\60\2\2\u0186`\3\2\2\2\"\2\u00d0\u00d6\u00db\u00e0\u00e8"+
		"\u00ed\u00f3\u00f5\u00f8\u00fd\u0101\u0105\u010a\u0110\u0115\u011a\u0122"+
		"\u0127\u012d\u012f\u0133\u0138\u013d\u0143\u014a\u0151\u0156\u0160\u0168"+
		"\u0172\u017f\3\b\2\2";
	public static final ATN _ATN =
		new ATNDeserializer().deserialize(_serializedATN.toCharArray());
	static {
		_decisionToDFA = new DFA[_ATN.getNumberOfDecisions()];
		for (int i = 0; i < _ATN.getNumberOfDecisions(); i++) {
			_decisionToDFA[i] = new DFA(_ATN.getDecisionState(i), i);
		}
	}
}