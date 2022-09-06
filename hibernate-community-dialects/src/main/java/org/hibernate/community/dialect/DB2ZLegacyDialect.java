/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.community.dialect;


import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.TimeZoneSupport;
import org.hibernate.dialect.function.CommonFunctionFactory;
import org.hibernate.dialect.identity.DB2390IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.pagination.FetchLimitHandler;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.OffsetFetchLimitHandler;
import org.hibernate.dialect.sequence.DB2zSequenceSupport;
import org.hibernate.dialect.sequence.NoSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;
import org.hibernate.engine.jdbc.dialect.spi.DialectResolutionInfo;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.sqm.IntervalType;
import org.hibernate.query.sqm.TemporalUnit;
import org.hibernate.sql.ast.SqlAstTranslator;
import org.hibernate.sql.ast.SqlAstTranslatorFactory;
import org.hibernate.sql.ast.spi.StandardSqlAstTranslatorFactory;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.exec.spi.JdbcOperation;

import jakarta.persistence.TemporalType;

import static org.hibernate.type.SqlTypes.TIMESTAMP_WITH_TIMEZONE;

/**
 * An SQL dialect for DB2 for z/OS, previously known as known as Db2 UDB for z/OS and Db2 UDB for z/OS and OS/390.
 *
 * @author Christian Beikov
 */
public class DB2ZLegacyDialect extends DB2LegacyDialect {

	final static DatabaseVersion DB2_LUW_VERSION9 = DatabaseVersion.make( 9, 0);

	public DB2ZLegacyDialect(DialectResolutionInfo info) {
		this( info.makeCopy() );
		registerKeywords( info );
	}

	public DB2ZLegacyDialect() {
		this( DatabaseVersion.make( 7 ) );
	}

	public DB2ZLegacyDialect(DatabaseVersion version) {
		super(version);
	}

	@Override
	public void initializeFunctionRegistry(QueryEngine queryEngine) {
		super.initializeFunctionRegistry( queryEngine );
		if ( getVersion().isSameOrAfter( 12 ) ) {
			CommonFunctionFactory functionFactory = new CommonFunctionFactory(queryEngine);
			functionFactory.listagg( null );
			functionFactory.inverseDistributionOrderedSetAggregates();
			functionFactory.hypotheticalOrderedSetAggregates_windowEmulation();
		}
	}

	@Override
	protected String columnType(int sqlTypeCode) {
		if ( sqlTypeCode == TIMESTAMP_WITH_TIMEZONE && getVersion().isAfter( 10 ) ) {
			// See https://www.ibm.com/support/knowledgecenter/SSEPEK_10.0.0/wnew/src/tpc/db2z_10_timestamptimezone.html
			return "timestamp with time zone";
		}
		return super.columnType( sqlTypeCode );
	}

	@Override
	public DatabaseVersion getDB2Version() {
		return DB2_LUW_VERSION9;
	}

	@Override
	public boolean supportsDistinctFromPredicate() {
		// Supported at least since DB2 z/OS 9.0
		return true;
	}

	@Override
	public TimeZoneSupport getTimeZoneSupport() {
		return getVersion().isAfter(10) ? TimeZoneSupport.NATIVE : TimeZoneSupport.NONE;
	}

	@Override
	public SequenceSupport getSequenceSupport() {
		return getVersion().isBefore(8)
				? NoSequenceSupport.INSTANCE
				: DB2zSequenceSupport.INSTANCE;
	}

	@Override
	public String getQuerySequencesString() {
		return getVersion().isBefore(8) ? null : "select * from sysibm.syssequences";
	}

	@Override
	public LimitHandler getLimitHandler() {
		return getVersion().isBefore(12)
				? FetchLimitHandler.INSTANCE
				: OffsetFetchLimitHandler.INSTANCE;
	}

	@Override
	public IdentityColumnSupport getIdentityColumnSupport() {
		return new DB2390IdentityColumnSupport();
	}

	@Override
	public boolean supportsSkipLocked() {
		return true;
	}

	@Override
	public boolean supportsLateral() {
		return true;
	}

	@Override
	public String timestampaddPattern(TemporalUnit unit, TemporalType temporalType, IntervalType intervalType) {
		StringBuilder pattern = new StringBuilder();
		final boolean castTo;
		if ( unit.isDateUnit() ) {
			castTo = temporalType == TemporalType.TIME;
		}
		else {
			castTo = temporalType == TemporalType.DATE;
		}
		pattern.append("add_");
		switch (unit) {
			case NATIVE:
			case NANOSECOND:
				pattern.append("second");
				break;
			case WEEK:
				//note: DB2 does not have add_weeks()
				pattern.append("day");
				break;
			case QUARTER:
				pattern.append("month");
				break;
			default:
				pattern.append("?1");
		}
		pattern.append("s(");
		if (castTo) {
			pattern.append("cast(?3 as timestamp)");
		}
		else {
			pattern.append("?3");
		}
		pattern.append(",");
		switch (unit) {
			case NANOSECOND:
				pattern.append("(?2)/1e9");
				break;
			case WEEK:
				pattern.append("(?2)*7");
				break;
			case QUARTER:
				pattern.append("(?2)*3");
				break;
			default:
				pattern.append("?2");
		}
		pattern.append(")");
		return pattern.toString();
	}

	@Override
	public SqlAstTranslatorFactory getSqlAstTranslatorFactory() {
		return new StandardSqlAstTranslatorFactory() {
			@Override
			protected <T extends JdbcOperation> SqlAstTranslator<T> buildTranslator(
					SessionFactoryImplementor sessionFactory, Statement statement) {
				return new DB2zLegacySqlAstTranslator<>( sessionFactory, statement, getVersion() );
			}
		};
	}
}
