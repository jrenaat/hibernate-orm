/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.dialect;

import org.hibernate.LockMode;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.sql.ast.tree.Statement;
import org.hibernate.sql.ast.tree.from.FunctionTableReference;
import org.hibernate.sql.ast.tree.from.NamedTableReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.ast.tree.from.TableReference;
import org.hibernate.sql.exec.spi.JdbcOperation;

import static org.hibernate.dialect.DB2zDialect.DB2_LUW_VERSION;

/**
 * A SQL AST translator for DB2z.
 *
 * @author Christian Beikov
 */
public class DB2zSqlAstTranslator<T extends JdbcOperation> extends DB2SqlAstTranslator<T> {

	private final DatabaseVersion version;

	public DB2zSqlAstTranslator(SessionFactoryImplementor sessionFactory, Statement statement, DatabaseVersion version) {
		super( sessionFactory, statement );
		this.version = version;
	}

	@Override
	protected boolean supportsOffsetClause() {
		return version.isSameOrAfter(12);
	}

	@Override
	protected boolean renderPrimaryTableReference(TableGroup tableGroup, LockMode lockMode) {
		final TableReference tableReference = tableGroup.getPrimaryTableReference();
		if ( tableReference instanceof NamedTableReference ) {
			return renderNamedTableReference( (NamedTableReference) tableReference, lockMode );
		}
		// DB2 z/OS we need the "table" qualifier for table valued functions or lateral sub-queries
		append( "table " );
		tableReference.accept( this );
		return false;
	}

	@Override
	public void visitFunctionTableReference(FunctionTableReference tableReference) {
		// For the table qualifier we need parenthesis on DB2 z/OS
		append( OPEN_PARENTHESIS );
		tableReference.getFunctionExpression().accept( this );
		append( CLOSE_PARENTHESIS );
		renderDerivedTableReference( tableReference );
	}

	@Override
	public DatabaseVersion getDB2Version() {
		return DB2_LUW_VERSION;
	}
}
