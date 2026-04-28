/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.engine.jdbc.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.Token;

import org.hibernate.grammars.sql.SqlFormatterLexer;

/**
 * Advanced SQL formatter using ANTLR-based tokenization.
 * Provides better formatting for complex SQL queries compared to BasicFormatterImpl.
 *
 * @author Hibernate Team
 */
public class TokenBasedFormatterImpl implements Formatter {

	private static final String INDENT = "  "; // 2-space indentation
	private static final String LINE_SEPARATOR = System.lineSeparator();

	// Keywords that start a new major clause (reduced indent)
	private static final Set<Integer> MAJOR_CLAUSE_STARTERS = Set.of(
			SqlFormatterLexer.SELECT,
			SqlFormatterLexer.FROM,
			SqlFormatterLexer.WHERE,
			SqlFormatterLexer.GROUP,
			SqlFormatterLexer.HAVING,
			SqlFormatterLexer.ORDER,
			SqlFormatterLexer.LIMIT,
			SqlFormatterLexer.OFFSET,
			SqlFormatterLexer.FETCH,
			SqlFormatterLexer.UNION,
			SqlFormatterLexer.INTERSECT,
			SqlFormatterLexer.EXCEPT,
			SqlFormatterLexer.WITH
	);

	// Keywords that continue a clause on a new line (same indent)
	private static final Set<Integer> CLAUSE_CONTINUERS = Set.of(
			SqlFormatterLexer.JOIN,
			SqlFormatterLexer.LEFT,
			SqlFormatterLexer.RIGHT,
			SqlFormatterLexer.INNER,
			SqlFormatterLexer.OUTER,
			SqlFormatterLexer.FULL,
			SqlFormatterLexer.CROSS,
			SqlFormatterLexer.AND,
			SqlFormatterLexer.OR
	);

	// Function-like keywords that don't increase indent
	private static final Set<String> FUNCTION_KEYWORDS = Set.of(
			"trim", "cast", "extract", "substring", "coalesce", "nullif"
	);

	@Override
	public String format(String source) {
		if (source == null || source.isBlank()) {
			return source;
		}

		try {
			final SqlFormatterLexer lexer = new SqlFormatterLexer(CharStreams.fromString(source));
			final List<? extends Token> allTokens = lexer.getAllTokens();
			final List<Token> tokens = new ArrayList<>(allTokens);
			return new FormatProcess(tokens).format();
		}
		catch (Exception e) {
			// Fall back to unformatted SQL if formatting fails
			return source;
		}
	}

	private static class FormatProcess {
		private final List<Token> tokens;
		private final StringBuilder output = new StringBuilder();
		private int position = 0;
		private int indentLevel = 0;
		private boolean newLine = true;
		private int parenDepth = 0;
		private boolean inSelectList = false;
		private boolean inFromClause = false;
		private boolean afterJoinKeyword = false;

		FormatProcess(List<Token> tokens) {
			this.tokens = new ArrayList<>(tokens);
			// Filter out pure whitespace tokens, but keep track of them for spacing
			this.tokens.removeIf(t -> t.getType() == SqlFormatterLexer.WS);
		}

		String format() {
			while (position < tokens.size()) {
				final Token token = currentToken();
				processToken(token);
				position++;
			}
			return output.toString().trim();
		}

		private void processToken(Token token) {
			final int type = token.getType();

			// Handle comments
			if (type == SqlFormatterLexer.LINE_COMMENT || type == SqlFormatterLexer.BLOCK_COMMENT) {
				writeToken(token);
				return;
			}

			// Major clause starters
			if (MAJOR_CLAUSE_STARTERS.contains(type)) {
				processMajorClause(token);
				return;
			}

			// Handle specific keywords
			switch (type) {
				case SqlFormatterLexer.SELECT:
					processSelect(token);
					break;
				case SqlFormatterLexer.FROM:
					processFrom(token);
					break;
				case SqlFormatterLexer.WHERE:
				case SqlFormatterLexer.HAVING:
					processWhereOrHaving(token);
					break;
				case SqlFormatterLexer.JOIN:
					processJoin(token);
					break;
				case SqlFormatterLexer.LEFT:
				case SqlFormatterLexer.RIGHT:
				case SqlFormatterLexer.INNER:
				case SqlFormatterLexer.OUTER:
				case SqlFormatterLexer.FULL:
				case SqlFormatterLexer.CROSS:
					processJoinModifier(token);
					break;
				case SqlFormatterLexer.LATERAL:
					processLateral(token);
					break;
				case SqlFormatterLexer.ON:
					processOn(token);
					break;
				case SqlFormatterLexer.AND:
				case SqlFormatterLexer.OR:
					processLogical(token);
					break;
				case SqlFormatterLexer.ORDER:
				case SqlFormatterLexer.GROUP:
					processOrderOrGroup(token);
					break;
				case SqlFormatterLexer.BY:
					processBy(token);
					break;
				case SqlFormatterLexer.COMMA:
					processComma(token);
					break;
				case SqlFormatterLexer.LPAREN:
					processLeftParen(token);
					break;
				case SqlFormatterLexer.RPAREN:
					processRightParen(token);
					break;
				case SqlFormatterLexer.OFFSET:
				case SqlFormatterLexer.FETCH:
				case SqlFormatterLexer.LIMIT:
					processLimitClause(token);
					break;
				default:
					writeToken(token);
					break;
			}
		}

		private void processMajorClause(Token token) {
			if (!newLine && output.length() > 0) {
				newLine();
			}
			indentLevel = 0;
			writeToken(token);
			indentLevel = 1;
		}

		private void processSelect(Token token) {
			if (!newLine && output.length() > 0) {
				newLine();
			}
			if (parenDepth > 0) {
				// Subquery - add extra indent
				indentLevel++;
			}
			writeToken(token);
			inSelectList = true;
			inFromClause = false;
		}

		private void processFrom(Token token) {
			if (inSelectList) {
				indentLevel--;
			}
			newLine();
			writeToken(token);
			inSelectList = false;
			inFromClause = true;
		}

		private void processWhereOrHaving(Token token) {
			if (inSelectList) {
				indentLevel--;
				inSelectList = false;
			}
			if (inFromClause) {
				inFromClause = false;
			}
			newLine();
			writeToken(token);
			indentLevel++;
		}

		private void processJoin(Token token) {
			if (afterJoinKeyword) {
				// "left join" - space before join
				space();
			}
			else {
				// standalone "join" - newline
				newLine();
			}
			writeToken(token);
			afterJoinKeyword = false;
		}

		private void processJoinModifier(Token token) {
			newLine();
			writeToken(token);
			afterJoinKeyword = true;
		}

		private void processLateral(Token token) {
			space();
			writeToken(token);
		}

		private void processOn(Token token) {
			indentLevel++;
			newLine();
			writeToken(token);
		}

		private void processLogical(Token token) {
			// Check if we're in a BETWEEN clause
			if (isBetweenAnd()) {
				space();
				writeToken(token);
			}
			else {
				// Regular AND/OR in WHERE/ON clause
				newLine();
				writeToken(token);
			}
		}

		private void processOrderOrGroup(Token token) {
			if (inSelectList) {
				indentLevel--;
				inSelectList = false;
			}
			newLine();
			writeToken(token);
		}

		private void processBy(Token token) {
			space();
			writeToken(token);
			indentLevel++;
		}

		private void processComma(Token token) {
			writeToken(token);
			if (inSelectList || (inFromClause && parenDepth == 0) || isInOrderBy()) {
				newLine();
			}
		}

		private void processLeftParen(Token token) {
			// Check if this is a function call
			final Token prevToken = peekBackNonWhitespace();
			final boolean isFunction = prevToken != null &&
					(prevToken.getType() == SqlFormatterLexer.IDENTIFIER ||
					FUNCTION_KEYWORDS.contains(prevToken.getText().toLowerCase()));

			if (!isFunction && !newLine && parenDepth == 0) {
				// Subquery in FROM/JOIN clause
				newLine();
				indentLevel++;
			}

			parenDepth++;
			writeToken(token);

			// Newline after opening paren for subqueries
			if (!isFunction && peekAhead(1) != null &&
					peekAhead(1).getType() == SqlFormatterLexer.SELECT) {
				indentLevel++;
			}
		}

		private void processRightParen(Token token) {
			parenDepth--;

			// Check if we need to dedent before the closing paren
			final Token nextToken = peekAhead(1);
			if (nextToken != null && nextToken.getType() == SqlFormatterLexer.IDENTIFIER) {
				// Likely a table alias after subquery "(...) alias"
				newLine();
				indentLevel--;
			}
			else if (parenDepth == 0 && !newLine) {
				newLine();
				indentLevel--;
			}

			writeToken(token);

			// Decrease indent after closing subquery paren
			if (parenDepth == 0 && indentLevel > 0) {
				indentLevel--;
			}
		}

		private void processLimitClause(Token token) {
			newLine();
			if (indentLevel > 0) {
				indentLevel--;
			}
			writeToken(token);
		}

		private void writeToken(Token token) {
			final String text = token.getText();

			// Apply indentation if we're at the start of a new line
			if (newLine) {
				for (int i = 0; i < indentLevel; i++) {
					output.append(INDENT);
				}
				newLine = false;
			}
			// Add space if needed (unless we just started a new line or previous char is special)
			else if (output.length() > 0 && needsSpaceBefore(token)) {
				final char lastChar = output.charAt(output.length() - 1);
				if (lastChar != ' ' && lastChar != '(' && lastChar != '\n') {
					space();
				}
			}

			// Write the token in lowercase for keywords, preserve case for identifiers
			if (isKeyword(token.getType())) {
				output.append(text.toLowerCase());
			}
			else {
				output.append(text);
			}
		}

		private boolean needsSpaceBefore(Token token) {
			final int type = token.getType();
			// No space before these tokens
			if (type == SqlFormatterLexer.COMMA ||
				type == SqlFormatterLexer.RPAREN ||
				type == SqlFormatterLexer.DOT ||
				type == SqlFormatterLexer.SEMICOLON ||
				type == SqlFormatterLexer.LPAREN) {
				return false;
			}
			// No space after these tokens
			final Token prev = peekBackNonWhitespace();
			if (prev != null) {
				final int prevType = prev.getType();
				if (prevType == SqlFormatterLexer.LPAREN ||
					prevType == SqlFormatterLexer.DOT) {
					return false;
				}
			}
			return true;
		}

		private boolean isKeyword(int type) {
			return type >= SqlFormatterLexer.SELECT && type <= SqlFormatterLexer.TRAILING;
		}

		private boolean isBetweenAnd() {
			// Look back for BETWEEN keyword
			for (int i = position - 1; i >= 0 && i >= position - 10; i--) {
				final Token t = tokens.get(i);
				if (t.getType() == SqlFormatterLexer.BETWEEN) {
					return true;
				}
				// Stop looking if we hit a major keyword
				if (MAJOR_CLAUSE_STARTERS.contains(t.getType())) {
					break;
				}
			}
			return false;
		}

		private boolean isInOrderBy() {
			// Look back for ORDER BY
			for (int i = position - 1; i >= 0 && i >= position - 20; i--) {
				final Token t = tokens.get(i);
				if (t.getType() == SqlFormatterLexer.ORDER) {
					return true;
				}
				if (MAJOR_CLAUSE_STARTERS.contains(t.getType()) &&
					t.getType() != SqlFormatterLexer.ORDER) {
					break;
				}
			}
			return false;
		}

		private Token currentToken() {
			return tokens.get(position);
		}

		private Token peekAhead(int offset) {
			final int pos = position + offset;
			return pos < tokens.size() ? tokens.get(pos) : null;
		}

		private Token peekBackNonWhitespace() {
			for (int i = position - 1; i >= 0; i--) {
				final Token t = tokens.get(i);
				if (t.getType() != SqlFormatterLexer.WS) {
					return t;
				}
			}
			return null;
		}

		private void newLine() {
			// Avoid multiple consecutive newlines
			if (output.length() > 0 && !newLine) {
				output.append(LINE_SEPARATOR);
				newLine = true;
			}
		}

		private void space() {
			if (output.length() > 0 && output.charAt(output.length() - 1) != ' ') {
				output.append(' ');
			}
		}
	}
}
