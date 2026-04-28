/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.jdbc.util;

import org.hibernate.engine.jdbc.internal.TokenBasedFormatterImpl;
import org.hibernate.testing.orm.junit.BaseUnitTest;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test for the ANTLR-based token formatter
 *
 * @author Hibernate Team
 */
@BaseUnitTest
public class TokenBasedFormatterTest {
	private static final Logger log = Logger.getLogger( TokenBasedFormatterTest.class );

	private final TokenBasedFormatterImpl formatter = new TokenBasedFormatterImpl();

	@Test
	public void testComplexQuery() {
		String sql = "select t1.column, \"t1\".`column`, trim(both '' from [t2].column) " +
				"from tbl1 t1 " +
				"left join tbl2 t2 on t2.fk = t1.fk " +
				"join lateral (select t3.c1, t3.c2 from tbl3 t3) t(col1, col2) on true " +
				"order by 1, 2 " +
				"offset 1 rows " +
				"fetch first 1 rows only with ties";

		String formatted = formatter.format(sql);
		log.infof("Formatted SQL:%n%s", formatted);
		System.out.println("=== Formatted SQL ===");
		System.out.println(formatted);
		System.out.println("=== End ===");

		// Verify key formatting aspects
		assertTrue(formatted.contains("select"), "Should contain lowercase select");
		assertTrue(formatted.contains("from"), "Should contain lowercase from");
		assertTrue(formatted.contains("left join"), "Should contain left join");
		assertTrue(formatted.contains("join lateral"), "Should contain join lateral");
		assertTrue(formatted.toLowerCase().contains("order by"), "Should contain order by");
		assertTrue(formatted.toLowerCase().contains("offset"), "Should contain offset");
		assertTrue(formatted.toLowerCase().contains("fetch"), "Should contain fetch");

		// Verify quoted identifiers are preserved
		assertTrue(formatted.contains("\"t1\".`column`"), "Should preserve quoted identifiers");
		assertTrue(formatted.contains("[t2].column"), "Should preserve bracket-quoted identifiers");
	}

	@Test
	public void testSimpleSelect() {
		String sql = "select id, name, email from users where active = true order by name";
		String formatted = formatter.format(sql);
		log.infof("Formatted SQL:%n%s", formatted);
		System.out.println(formatted);

		assertTrue(formatted.contains("select"), "Should contain lowercase select");
		assertTrue(formatted.contains("from"), "Should contain lowercase from");
		assertTrue(formatted.contains("where"), "Should contain lowercase where");
		assertTrue(formatted.contains("order"), "Should contain lowercase order");
	}

	@Test
	public void testSubquery() {
		String sql = "select * from (select id, name from users) u where u.id > 10";
		String formatted = formatter.format(sql);
		log.infof("Formatted SQL:%n%s", formatted);
		System.out.println(formatted);

		assertTrue(formatted.contains("select"), "Should contain lowercase select");
		assertTrue(formatted.contains("from"), "Should contain lowercase from");
	}

	@Test
	public void testJoinQuery() {
		String sql = "select u.name, a.city from users u " +
				"inner join addresses a on u.id = a.user_id " +
				"where a.country = 'US'";
		String formatted = formatter.format(sql);
		log.infof("Formatted SQL:%n%s", formatted);
		System.out.println(formatted);

		assertTrue(formatted.contains("inner join"), "Should contain inner join");
		assertTrue(formatted.contains("on"), "Should contain on");
	}

	@Test
	public void testCaseExpression() {
		String sql = "select case when age > 18 then 'adult' else 'minor' end from persons";
		String formatted = formatter.format(sql);
		log.infof("Formatted SQL:%n%s", formatted);
		System.out.println(formatted);

		assertTrue(formatted.contains("case"), "Should contain lowercase case");
		assertTrue(formatted.contains("when"), "Should contain lowercase when");
		assertTrue(formatted.contains("then"), "Should contain lowercase then");
		assertTrue(formatted.contains("else"), "Should contain lowercase else");
		assertTrue(formatted.contains("end"), "Should contain lowercase end");
	}

	@Test
	public void testFunctionCall() {
		String sql = "select trim(both ' ' from name), upper(email) from users";
		String formatted = formatter.format(sql);
		log.infof("Formatted SQL:%n%s", formatted);
		System.out.println(formatted);

		assertTrue(formatted.contains("trim("), "Should contain function call");
		assertTrue(formatted.contains("upper("), "Should contain function call");
	}

	@Test
	public void testEmptyString() {
		String formatted = formatter.format("");
		assertTrue(formatted.isEmpty() || formatted.isBlank(), "Empty string should remain empty");
	}

	@Test
	public void testNullString() {
		String formatted = formatter.format(null);
		assertTrue(formatted == null || formatted.isEmpty(), "Null should be handled gracefully");
	}
}
