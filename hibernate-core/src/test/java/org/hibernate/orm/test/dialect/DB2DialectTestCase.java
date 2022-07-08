/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.dialect;

import java.sql.Types;

import org.hibernate.dialect.DB2Dialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.query.spi.Limit;
import org.hibernate.type.spi.TypeConfiguration;

import org.junit.Before;
import org.junit.Test;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.junit4.BaseUnitTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * DB2 dialect related test cases
 *
 * @author Hardy Ferentschik
 */

public class DB2DialectTestCase extends BaseUnitTestCase {
	private final DB2Dialect dialect = new DB2Dialect();
	private TypeConfiguration typeConfiguration;

	@Before
	public void setup() {
		typeConfiguration = new TypeConfiguration();
		dialect.contributeTypes( () -> typeConfiguration, null );
	}

	@Test
	@TestForIssue(jiraKey = "HHH-6866")
	public void testGetDefaultBinaryTypeName() {
		String actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, dialect );
		assertEquals(
				"The default column length is 255, but char length on DB2 is limited to 254",
				"binary($l)",
				actual
		);
	}

	@Test
	@TestForIssue(jiraKey = "HHH-6866")
	public void testGetExplicitBinaryTypeName() {
		// lower bound
		String actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 1) );
		assertEquals(
				"Wrong binary type",
				"binary(1)",
				actual
		);

		// upper bound
		actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 255) );
		assertEquals(
				"Wrong binary type",
				"binary(255)",
				actual
		);

		// lower varbinary bound
		actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 256) );
		assertEquals(
				"Wrong binary type. Should be varbinary for lengths in between 255 and 32_672",
				"varbinary(256)",
				actual
		);

		// upper varbinary bound
		actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 32_672) );
		assertEquals(
				"Wrong binary type. Should be varbinary for lengths in between 255 and 32_672",
				"varbinary(32672)",
				actual
		);

		// exceeding upper varbinary bound
		actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 32_673) );
		assertEquals(
				"Wrong binary type. Should be blob for lengths > 32_672",
				"blob",
				actual
		);
	}

	// Not sure if this test, in its current (i.e. after the dialect version upgrade) state, even makes sense any more?
	@Test
	@TestForIssue(jiraKey = "HHH-12369")
	public void testIntegerOverflowForMaxResults() {
		Limit rowSelection = new Limit();
		rowSelection.setFirstRow(1);
		rowSelection.setMaxRows(Integer.MAX_VALUE);
		String sql = dialect.getLimitHandler().processSql( "select a.id from tbl_a a order by a.id", rowSelection );
		assertTrue(
				"Integer overflow for max rows in: " + sql,
				sql.contains("fetch next ? rows only")
		);
	}
}
