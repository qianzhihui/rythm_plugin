package com.rythmplugin;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.rythmplugin.psi.RythmTypes;
import com.intellij.psi.TokenType;

%%

%class RythmLexer
%implements FlexLexer
%unicode
%function advance
%type IElementType
%eof{  return;
%eof}

CRLF= \n|\r|\r\n
WHITE_SPACE=[\ \t\f]
FIRST_VALUE_CHARACTER=[^ \n\r\f\\] | "\\"{CRLF} | "\\".
VALUE_CHARACTER=[^\n\r\f\\] | "\\"{CRLF} | "\\".
END_OF_LINE_COMMENT=("#"|"!")[^\r\n]*
SEPARATOR=[:=]
KEY_CHARACTER=[^:=\ \n\r\t\f\\] | "\\ "


HTML_PART = {HTML_HREF}|{HTML_LI}|{HTML_DIV}|{HTML_CANVAS}
HTML_HREF = "<a href=".*?.*?>
HTML_LI = "<li".*?>|"</li>"
HTML_DIV = "<div".*?>|"</div>"
HTML_CANVAS = "<canvas".*?>|"</canvas>"


JS_PART = {J_SCRIPT}
J_SCRIPT = "<script".*?>|"</script>"
//var

RYTHM = [^<]*|[^>]*|[^"<li>"]*

DQ = "\""
ANYCHAREXAN =[^[A-Za-z0-9]]*

HTML = {HTML_TAG_OPEN}|{HTML_TAG_CLOSE}
HTML_TAG_OPEN = "<"{SL}{AFTER_AT}">"
HTML_TAG_CLOSE = "<"{BSL}{AFTER_AT}">"

SL = "/"
BSL = "\\"
ANYCHAR = .*

ALPHA_NUMERIC = [A-Za-z0-9]
LETTER = [A-Za-z]

LEFT_BRACE = "{"
RIGHT_BRACE = "}"
LP = "("
RP = ")"
DOT = "."


%state WAITING_VALUE

%%
<YYINITIAL> {JS_PART}                                      {yybegin (YYINITIAL); return RythmTypes.JS_PART;}

<YYINITIAL> {HTML_PART}                                      {yybegin (YYINITIAL); return RythmTypes.HTML_PART;}

<YYINITIAL> {RYTHM}                                        {yybegin(YYINITIAL); return RythmTypes.RYTHM;}

<YYINITIAL> {END_OF_LINE_COMMENT}                           { yybegin(YYINITIAL); return RythmTypes.COMMENT; }

<YYINITIAL> {KEY_CHARACTER}+                                { yybegin(YYINITIAL); return RythmTypes.KEY; }

<YYINITIAL> {SEPARATOR}                                     { yybegin(WAITING_VALUE); return RythmTypes.SEPARATOR; }

<WAITING_VALUE> {CRLF}({CRLF}|{WHITE_SPACE})+               { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

<WAITING_VALUE> {WHITE_SPACE}+                              { yybegin(WAITING_VALUE); return TokenType.WHITE_SPACE; }


<WAITING_VALUE> {FIRST_VALUE_CHARACTER}{VALUE_CHARACTER}*   { yybegin(YYINITIAL); return RythmTypes.VALUE; }

({CRLF}|{WHITE_SPACE})+                                     { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

{WHITE_SPACE}+                                              { yybegin(YYINITIAL); return TokenType.WHITE_SPACE; }

.                                                           { return TokenType.BAD_CHARACTER; }
