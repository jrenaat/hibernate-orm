/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.model.internal;

import jakarta.persistence.UniqueConstraint;
import org.hibernate.AnnotationException;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.model.relational.Database;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.Index;
import org.hibernate.mapping.Selectable;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import static org.hibernate.internal.util.StringHelper.isEmpty;

/**
 * Responsible for interpreting {@link jakarta.persistence.Index} and
 * {@link UniqueConstraint} annotations.
 *
 * @author Gavin King
 */
class IndexBinder {

	private final MetadataBuildingContext context;

	IndexBinder(MetadataBuildingContext context) {
		this.context = context;
	}

	private Database getDatabase() {
		return context.getMetadataCollector().getDatabase();
	}

	private ImplicitNamingStrategy getImplicitNamingStrategy() {
		return context.getBuildingOptions().getImplicitNamingStrategy();
	}

	private PhysicalNamingStrategy getPhysicalNamingStrategy() {
		return context.getBuildingOptions().getPhysicalNamingStrategy();
	}

	private Dialect getDialect() {
		return getDatabase().getJdbcEnvironment().getDialect();
	}

	private Selectable selectable(Table table, String columnNameOrFormula) {
		if ( columnNameOrFormula.startsWith("(") ) {
			return new Formula( columnNameOrFormula );
		}
		else {
			return createColumn( columnNameOrFormula );
//			Column column;
//			try {
//				column = table.getColumn( context.getMetadataCollector(), columnNameOrFormula );
//			}
//			catch (MappingException me) {
//				column = null;
//			}
//			return column == null
//					// Assume it's actually a formula with missing parens
//					? new Formula( "(" + columnNameOrFormula + ")" )
//					: column;
		}
	}

	private Column column(Table table, String columnName) {
		return createColumn( columnName );
//		Column column;
//		try {
//			column = table.getColumn( context.getMetadataCollector(), columnName );
//		}
//		catch (MappingException me) {
//			column = null;
//		}
//		if ( column != null ) {
//			return column;
//		}
//		else {
//			throw new AnnotationException(
//					"Table '" + table.getName() + "' has no column named '" + columnName
//							+ "' matching the column specified in '@UniqueConstraint'"
//			);
//		}
	}

	private Column createColumn(String logicalName) {
		final Database database = getDatabase();
		final String physicalName =
				getPhysicalNamingStrategy()
						.toPhysicalColumnName( database.toIdentifier( logicalName ), database.getJdbcEnvironment() )
						.render( getDialect() );
		return new Column( physicalName );
	}

	private Selectable[] selectables(Table table, String name, final String[] columnNames) {
		final int size = columnNames.length;
		if ( size == 0 ) {
			throw new AnnotationException( "Index"
					+ ( isEmpty( name ) ? "" : " '" + name + "'" )
					+ " on table '" + table.getName() + "' has no columns" );
		}
		final Selectable[] columns = new Selectable[size];
		for ( int index = 0; index < size; index++ ) {
			final String columnName = columnNames[index];
			if ( isEmpty( columnName ) ) {
				throw new AnnotationException( "Index"
						+ ( isEmpty( name ) ? "" : " '" + name + "'" )
						+ " on table '" + table.getName() + "' has an empty column name" );
			}
			columns[index] = selectable( table, columnName );
		}
		return columns;
	}

	private Column[] columns(Table table, String name, final String[] columnNames) {
		final int size = columnNames.length;
		if ( size == 0 ) {
			throw new AnnotationException( "Unique constraint"
					+ ( isEmpty( name ) ? "" : " '" + name + "'" )
					+ " on table '" + table.getName() + "' has no columns" );
		}
		final Column[] columns = new Column[size];
		for ( int index = 0; index < size; index++ ) {
			final String columnName = columnNames[index];
			if ( isEmpty( columnName ) ) {
				throw new AnnotationException( "Unique constraint"
						+ ( isEmpty( name ) ? "" : " '" + name + "'" )
						+ " on table '" + table.getName() + "' has an empty column name" );
			}
			columns[index] = column( table, columnName );
		}
		return columns;
	}

	private void createIndexOrUniqueKey(
			Table table,
			String originalKeyName,
			boolean nameExplicit,
			String[] columnNames,
			String[] orderings,
			boolean unique,
			Selectable[] columns) {
		final IndexOrUniqueKeyNameSource source =
				new IndexOrUniqueKeyNameSource( context, table, columnNames, originalKeyName );
		boolean hasFormula = false;
		for ( Selectable selectable : columns ) {
			if ( selectable.isFormula() ) {
				hasFormula = true;
			}
		}
		if ( unique && !hasFormula ) {
			final String keyName = getImplicitNamingStrategy().determineUniqueKeyName( source ).render( getDialect() );
			final UniqueKey uniqueKey = table.getOrCreateUniqueKey( keyName );
			uniqueKey.setExplicit( true );
			uniqueKey.setNameExplicit( nameExplicit );
			for ( int i = 0; i < columns.length; i++ ) {
				uniqueKey.addColumn( (Column) columns[i], orderings != null ? orderings[i] : null );
			}
		}
		else {
			final String keyName = getImplicitNamingStrategy().determineIndexName( source ).render( getDialect() );
			final Index index = table.getOrCreateIndex( keyName );
			index.setUnique( unique );
			for ( int i = 0; i < columns.length; i++ ) {
				index.addColumn( columns[i], orderings != null ? orderings[i] : null );
			}
		}
	}

	void bindIndexes(Table table, jakarta.persistence.Index[] indexes) {
		for ( jakarta.persistence.Index index : indexes ) {
			final StringTokenizer tokenizer = new StringTokenizer( index.columnList(), "," );
			final List<String> parsed = new ArrayList<>();
			while ( tokenizer.hasMoreElements() ) {
				final String trimmed = tokenizer.nextToken().trim();
				if ( !trimmed.isEmpty() ) {
					parsed.add( trimmed ) ;
				}
			}
			final String[] columnExpressions = new String[parsed.size()];
			final String[] ordering = new String[parsed.size()];
			initializeColumns( columnExpressions, ordering, parsed );
			final String name = index.name();
			final boolean unique = index.unique();
			createIndexOrUniqueKey( table, name, !name.isEmpty(), columnExpressions, ordering, unique,
					selectables( table, name, columnExpressions ) );
		}
	}

	void bindUniqueConstraints(Table table, UniqueConstraint[] constraints) {
		for ( UniqueConstraint constraint : constraints ) {
			final String name = constraint.name();
			final String[] columnNames = constraint.columnNames();
			createIndexOrUniqueKey( table, name, !name.isEmpty(), columnNames, null, true,
					columns( table, name, columnNames ) );
		}
	}

	private void initializeColumns(String[] columns, String[] ordering, List<String> list) {
		for ( int i = 0, size = list.size(); i < size; i++ ) {
			final String description = list.get( i );
			final String tmp = description.toLowerCase(Locale.ROOT);
			if ( tmp.endsWith( " desc" ) ) {
				columns[i] = description.substring( 0, description.length() - 5 );
				ordering[i] = "desc";
			}
			else if ( tmp.endsWith( " asc" ) ) {
				columns[i] = description.substring( 0, description.length() - 4 );
				ordering[i] = "asc";
			}
			else {
				columns[i] = description;
				ordering[i] = null;
			}
		}
	}
}
