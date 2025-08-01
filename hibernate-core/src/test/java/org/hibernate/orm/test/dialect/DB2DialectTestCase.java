/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.dialect;

import java.sql.Types;

import org.hibernate.dialect.DB2Dialect;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.query.spi.Limit;
import org.hibernate.testing.orm.junit.RequiresDialect;
import org.hibernate.type.spi.TypeConfiguration;

import org.junit.Before;
import org.junit.Test;

import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.junit4.BaseUnitTestCase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * DB2 dialect related test cases
 *
 * @author Hardy Ferentschik
 */

@RequiresDialect(DB2Dialect.class)
public class DB2DialectTestCase extends BaseUnitTestCase {
	private final DB2Dialect dialect = new DB2Dialect();
	private TypeConfiguration typeConfiguration;

	@Before
	public void setup() {
		typeConfiguration = new TypeConfiguration();
		dialect.contributeTypes( () -> typeConfiguration, null );
	}

	@Test
	@JiraKey(value = "HHH-6866")
	public void testGetDefaultBinaryTypeName() {
		String actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, dialect );
		assertEquals(
				"The default column length is 255, but char length on DB2 is limited to 254",
				"varbinary($l)",
				actual
		);
	}

	@Test
	@JiraKey(value = "HHH-6866")
	public void testGetExplicitBinaryTypeName() {
		// lower bound
		String actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 1) );
		assertEquals(
				"Wrong binary type",
				"binary(1)",
				actual
		);

		// upper bound
		actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 254) );
		assertEquals(
				"Wrong binary type. 254 is the max length in DB2",
				"binary(254)",
				actual
		);

		// exceeding upper bound
		actual = typeConfiguration.getDdlTypeRegistry().getTypeName( Types.BINARY, Size.length( 255) );
		assertEquals(
				"Wrong binary type. Should be varchar for length > 254",
				"varbinary(255)",
				actual
		);
	}

	@Test
	@JiraKey(value = "HHH-12369")
	public void testIntegerOverflowForMaxResults() {
		Limit rowSelection = new Limit();
		rowSelection.setFirstRow(1);
		rowSelection.setMaxRows(Integer.MAX_VALUE);
		String sql = dialect.getLimitHandler().processSql( "select a.id from tbl_a a order by a.id", -1, null, new LimitQueryOptions( rowSelection ) );
		assertTrue(
				"Integer overflow for max rows in: " + sql,
				sql.contains("fetch next ? rows only")
		);
	}
}
