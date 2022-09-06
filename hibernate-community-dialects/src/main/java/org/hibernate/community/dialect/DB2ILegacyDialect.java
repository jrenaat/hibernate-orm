/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.community.dialect;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.identity.DB2390IdentityColumnSupport;
import org.hibernate.dialect.identity.DB2IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.pagination.FetchLimitHandler;
import org.hibernate.dialect.pagination.LegacyDB2LimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.sequence.DB2iSequenceSupport;
import org.hibernate.dialect.sequence.NoSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.dialect.unique.DefaultUniqueDelegate;
import org.hibernate.dialect.unique.UniqueDelegate;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;

/**
 * An SQL dialect for DB2 for iSeries previously known as DB2/400.
 *
 * @author Peter DeGregorio (pdegregorio)
 * @author Christian Beikov
 */
public class DB2ILegacyDialect extends DB2LegacyDialect {

	final static DatabaseVersion DB2_LUW_VERSION9 = DatabaseVersion.make( 9, 0);

	public DB2ILegacyDialect(DialectResolutionInfo info) {
		this( info.makeCopy() );
		registerKeywords( info );
	}

	public DB2ILegacyDialect() {
		this( DatabaseVersion.make(7) );
	}

	public DB2ILegacyDialect(DatabaseVersion version) {
		super(version);
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry( queryEngine );
		if ( getVersion().isSameOrAfter( 7, 2 ) ) {
			CommonFunctionFactory functionFactory = new CommonFunctionFactory(queryEngine);
			functionFactory.listagg( null );
			functionFactory.inverseDistributionOrderedSetAggregates();
			functionFactory.hypotheticalOrderedSetAggregates_windowEmulation();
		}
	}

	@Override
	public DatabaseVersion getDB2Version() {
		return DB2_LUW_VERSION9;
	}

	@Override
	protected UniqueDelegate createUniqueDelegate() {
		return getVersion().isSameOrAfter(7, 3)
				? new DefaultUniqueDelegate(this)
				: super.createUniqueDelegate();
	}

	@Override
	public boolean supportsDistinctFromPredicate() {
		return true;
	}

	/**
	 * No support for sequences.
	 */
	@Override
	public SequenceSupport getSequenceSupport() {
		return getVersion().isSameOrAfter(7, 3)
				? DB2iSequenceSupport.INSTANCE : NoSequenceSupport.INSTANCE;
	}

	@Override
	public String getQuerySequencesString() {
		if ( getVersion().isSameOrAfter(7,3) ) {
			return "select distinct sequence_name from qsys2.syssequences " +
					"where current_schema='*LIBL' and sequence_schema in (select schema_name from qsys2.library_list_info) " +
					"or sequence_schema=current_schema";
		}
		else {
			return null;
		}
	}

	@Override
	public LimitHandler getLimitHandler() {
		return getVersion().isSameOrAfter(7, 3)
				? FetchLimitHandler.INSTANCE : LegacyDB2LimitHandler.INSTANCE;
	}

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		return getVersion().isSameOrAfter(7, 3)
				? new DB2IdentityColumnSupport()
				: new DB2390IdentityColumnSupport();
	}

	@Override
	public boolean supportsSkipLocked() {
		return true;
	}

	@Override
	public boolean supportsLateral() {
		return getVersion().isSameOrAfter( 7, 1 );
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new DB2iLegacySqlAstTranslator<>( sessionFactory, statement, getVersion() );
			}
		};
	}
}
