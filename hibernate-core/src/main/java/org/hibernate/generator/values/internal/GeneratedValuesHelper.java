/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.generator.values.internal;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.Internal;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.EventType;
import org.hibernate.generator.values.GeneratedValueBasicResultBuilder;
import org.hibernate.generator.values.GeneratedValues;
import org.hibernate.generator.values.GeneratedValuesMutationDelegate;
import org.hibernate.id.IdentifierGeneratorHelper;
import org.hibernate.id.insert.GetGeneratedKeysDelegate;
import org.hibernate.id.insert.InsertReturningDelegate;
import org.hibernate.id.insert.UniqueKeySelectingDelegate;
import org.hibernate.internal.CoreLogging;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.metamodel.mapping.BasicValuedModelPart;
import org.hibernate.metamodel.mapping.ModelPart;
import org.hibernate.metamodel.mapping.SelectableMapping;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.results.internal.TableGroupImpl;
import org.hibernate.query.spi.QueryOptions;
import org.hibernate.spi.NavigablePath;
import org.hibernate.sql.ast.tree.from.NamedTableReference;
import org.hibernate.sql.ast.tree.from.TableGroup;
import org.hibernate.sql.exec.internal.BaseExecutionContext;
import org.hibernate.sql.model.TableMapping;
import org.hibernate.sql.results.internal.ResultsHelper;
import org.hibernate.sql.results.internal.RowProcessingStateStandardImpl;
import org.hibernate.sql.results.internal.RowTransformerArrayImpl;
import org.hibernate.sql.results.jdbc.internal.DirectResultSetAccess;
import org.hibernate.sql.results.jdbc.internal.JdbcValuesResultSetImpl;
import org.hibernate.sql.results.jdbc.internal.JdbcValuesSourceProcessingStateStandardImpl;
import org.hibernate.sql.results.jdbc.spi.JdbcValues;
import org.hibernate.sql.results.jdbc.spi.JdbcValuesMappingProducer;
import org.hibernate.sql.results.spi.ListResultsConsumer;
import org.hibernate.sql.results.spi.RowReader;

import static java.util.Collections.unmodifiableList;
import static org.hibernate.internal.NaturalIdHelper.getNaturalIdPropertyNames;
import static org.hibernate.pretty.MessageHelper.infoString;
import static org.hibernate.sql.model.MutationType.INSERT;
import static org.hibernate.sql.model.MutationType.UPDATE;
import static org.hibernate.sql.results.jdbc.spi.JdbcValuesSourceProcessingOptions.NO_OPTIONS;

/**
 * Factory and helper methods for {@link GeneratedValuesMutationDelegate} framework.
 *
 * @author Marco Belladelli
 */
@Internal
public class GeneratedValuesHelper {
	private static final CoreMessageLogger LOG = CoreLogging.messageLogger( IdentifierGeneratorHelper.class );

	/**
	 * Reads the {@linkplain EntityPersister#getGeneratedProperties(EventType) generated values}
	 * for the specified {@link ResultSet}.
	 *
	 * @param resultSet The result set from which to extract the generated values
	 * @param persister The entity type which we're reading the generated values for
	 * @param session The session
	 *
	 * @return The generated values
	 *
	 * @throws SQLException Can be thrown while accessing the result set
	 * @throws HibernateException Indicates a problem reading back a generated value
	 */
	public static GeneratedValues getGeneratedValues(
			ResultSet resultSet,
			PreparedStatement statement,
			EntityPersister persister,
			EventType timing,
			SharedSessionContractImplementor session) throws SQLException {
		if ( resultSet == null ) {
			return null;
		}

		final var mappingProducer =
				(GeneratedValuesMappingProducer)
						persister.getMutationDelegate( timing == EventType.INSERT ? INSERT : UPDATE )
								.getGeneratedValuesMappingProducer();
		final var resultBuilders = mappingProducer.getResultBuilders();
		final List<ModelPart> generatedProperties = new ArrayList<>( resultBuilders.size() );
		for ( var resultBuilder : resultBuilders ) {
			generatedProperties.add( resultBuilder.getModelPart() );
		}

		final var generatedValues = new GeneratedValuesImpl( generatedProperties );
		final var results = readGeneratedValues( resultSet, statement, persister, mappingProducer, session );

		if ( LOG.isDebugEnabled() ) {
			LOG.debug( "Extracted generated values for entity "
							+ infoString( persister ) + ": " + ArrayHelper.toString( results ) );
		}

		for ( int i = 0; i < results.length; i++ ) {
			generatedValues.addGeneratedValue( generatedProperties.get( i ), results[i] );
		}

		return generatedValues;
	}

	/**
	 * Utility method that reads the generated values from the specified {@link ResultSet}
	 * using the {@link JdbcValuesMappingProducer} provided in input.
	 *
	 * @param resultSet the result set containing the generated values
	 * @param persister the current entity persister
	 * @param mappingProducer the mapping producer to use when reading generated values
	 * @param session the current session
	 *
	 * @return an object array containing the generated values, order is consistent with the generated model parts list
	 */
	private static Object[] readGeneratedValues(
			ResultSet resultSet,
			PreparedStatement statement,
			EntityPersister persister,
			JdbcValuesMappingProducer mappingProducer,
			SharedSessionContractImplementor session) {
		final var factory = session.getFactory();
		final var executionContext = new BaseExecutionContext( session );
		final var directResultSetAccess =
				new DirectResultSetAccess( session, statement, resultSet );
		final var influencers = session.getLoadQueryInfluencers();
		final JdbcValues jdbcValues = new JdbcValuesResultSetImpl(
				directResultSetAccess,
				null,
				null,
				QueryOptions.NONE,
				true,
				mappingProducer.resolve( directResultSetAccess, influencers, factory ),
				null,
				executionContext
		);
		final var valuesProcessingState =
				new JdbcValuesSourceProcessingStateStandardImpl( executionContext, NO_OPTIONS );
		final RowReader<Object[]> rowReader = ResultsHelper.createRowReader(
				factory,
				RowTransformerArrayImpl.instance(),
				Object[].class,
				jdbcValues
		);
		final var rowProcessingState =
				new RowProcessingStateStandardImpl( valuesProcessingState, executionContext, rowReader, jdbcValues );
		final List<Object[]> results =
				ListResultsConsumer.<Object[]>instance( ListResultsConsumer.UniqueSemantic.NONE )
						.consume( jdbcValues, session, NO_OPTIONS, valuesProcessingState, rowProcessingState, rowReader );
		if ( results.isEmpty() ) {
			throw new HibernateException( "The database returned no natively generated values : "
											+ persister.getNavigableRole().getFullPath() );
		}
		return results.get( 0 );
	}

	/**
	 * Utility method that instantiates a {@link JdbcValuesMappingProducer} so it can be cached by the
	 * {@link GeneratedValuesMutationDelegate delegates} when they are instantiated.
	 *
	 * @param persister the current entity persister
	 * @param timing the timing of the mutation operation
	 * @param supportsArbitraryValues if we should process arbitrary (non-identifier) generated values
	 * @param supportsRowId if we should process {@link org.hibernate.metamodel.mapping.EntityRowIdMapping rowid}s
	 * {@code false} if we should retrieve the index through the column expression
	 *
	 * @return the instantiated jdbc values mapping producer
	 */
	public static GeneratedValuesMappingProducer createMappingProducer(
			EntityPersister persister,
			EventType timing,
			boolean supportsArbitraryValues,
			boolean supportsRowId) {
		return generatedValuesMappingProducer( persister, supportsArbitraryValues,
				getActualGeneratedModelParts( persister, timing, supportsArbitraryValues, supportsRowId ) );
	}

	private static GeneratedValuesMappingProducer generatedValuesMappingProducer(
			EntityPersister persister,
			boolean supportsArbitraryValues,
			List<? extends ModelPart> generatedProperties) {
		final NavigablePath parentNavigablePath = new NavigablePath( persister.getEntityName() );
		// This is just a mock table group needed to correctly resolve expressions
		final TableGroup tableGroup = new TableGroupImpl(
				parentNavigablePath,
				null,
				new NamedTableReference( "t", "t" ),
				persister
		);
		// Create the mapping producer and add all result builders to it
		final var mappingProducer = new GeneratedValuesMappingProducer();
		for ( int i = 0; i < generatedProperties.size(); i++ ) {
			final var modelPart = generatedProperties.get( i );
			final var basicModelPart = modelPart.asBasicValuedModelPart();
			if ( basicModelPart != null ) {
				mappingProducer.addResultBuilder( new GeneratedValueBasicResultBuilder(
						parentNavigablePath.append( basicModelPart.getSelectableName() ),
						basicModelPart,
						tableGroup,
						supportsArbitraryValues ? i : null
				) );
			}
			else {
				throw new UnsupportedOperationException( "Unsupported generated ModelPart: " + modelPart.getPartName() );
			}
		}
		return mappingProducer;
	}

	public static BasicValuedModelPart getActualGeneratedModelPart(BasicValuedModelPart modelPart) {
		// Use the root entity descriptor's identifier mapping to get the correct selection
		// expression since we always retrieve generated values for the root table only
		return modelPart.isEntityIdentifierMapping()
				? modelPart.findContainingEntityMapping()
						.getRootEntityDescriptor()
						.getIdentifierMapping()
						.asBasicValuedModelPart()
				: modelPart;
	}

	/**
	 * Returns a list of {@link ModelPart}s that represent the actual generated values
	 * based on timing and the support flags passed in input.
	 */
	private static List<? extends ModelPart> getActualGeneratedModelParts(
			EntityPersister persister,
			EventType timing,
			boolean supportsArbitraryValues,
			boolean supportsRowId) {
		if ( timing == EventType.INSERT ) {
			final var generatedProperties =
					supportsArbitraryValues
							? persister.getInsertGeneratedProperties()
							: List.of( persister.getIdentifierMapping() );
			if ( persister.getRowIdMapping() != null && supportsRowId ) {
				final List<ModelPart> newList =
						new ArrayList<>( generatedProperties.size() + 1 );
				newList.addAll( generatedProperties );
				newList.add( persister.getRowIdMapping() );
				return unmodifiableList( newList );
			}
			else {
				return generatedProperties;
			}
		}
		else {
			return persister.getUpdateGeneratedProperties();
		}
	}

	/**
	 * Creates the {@link GeneratedValuesMutationDelegate delegate} used to retrieve
	 * {@linkplain org.hibernate.generator.OnExecutionGenerator database generated values} on
	 * mutation execution through e.g. {@link Dialect#supportsInsertReturning() insert ... returning}
	 * syntax or the JDBC {@link Dialect#supportsInsertReturningGeneratedKeys() getGeneratedKeys()} API.
	 * <p>
	 * If the current {@link Dialect} doesn't support any of the available delegates this method returns {@code null}.
	 */
	public static GeneratedValuesMutationDelegate getGeneratedValuesDelegate(
			EntityPersister persister,
			EventType timing) {
		final var factory = persister.getFactory();
		final var generatedProperties = persister.getGeneratedProperties( timing );
		final boolean hasFormula =
				generatedProperties.stream()
					.anyMatch( part -> part instanceof SelectableMapping selectable
										&& selectable.isFormula() );
		final boolean hasRowId =
				timing == EventType.INSERT
						&& persister.getRowIdMapping() != null;
		final Dialect dialect = factory.getJdbcServices().getDialect();
		if ( hasRowId
				&& dialect.supportsInsertReturning()
				&& dialect.supportsInsertReturningRowId()
				&& noCustomSql( persister, timing ) ) {
			// Special case for RowId on INSERT, since GetGeneratedKeysDelegate doesn't support it
			// make InsertReturningDelegate the preferred method if the dialect supports it
			return new InsertReturningDelegate( persister, timing );
		}
		else if ( generatedProperties.isEmpty() ) {
			return null;
		}
		else if ( !hasFormula
				&& dialect.supportsInsertReturningGeneratedKeys()
				&& factory.getSessionFactoryOptions().isGetGeneratedKeysEnabled() ) {
			return new GetGeneratedKeysDelegate( persister, false, timing );
		}
		else if ( supportsReturning( dialect, timing ) && noCustomSql( persister, timing ) ) {
			return new InsertReturningDelegate( persister, timing );
		}
		else if ( timing == EventType.INSERT
					&& persister.getNaturalIdentifierProperties() != null
					&& !persister.getEntityMetamodel().isNaturalIdentifierInsertGenerated() ) {
			return new UniqueKeySelectingDelegate( persister, getNaturalIdPropertyNames( persister ), timing );
		}
		return null;
	}

	private static boolean supportsReturning(Dialect dialect, EventType timing) {
		return timing == EventType.INSERT
				? dialect.supportsInsertReturning()
				: dialect.supportsUpdateReturning();
	}

	public static boolean noCustomSql(EntityPersister persister, EventType timing) {
		final var identifierTable = persister.getIdentifierTableMapping();
		final TableMapping.MutationDetails mutationDetails =
				timing == EventType.INSERT
						? identifierTable.getInsertDetails()
						: identifierTable.getUpdateDetails();
		return mutationDetails.getCustomSql() == null;
	}
}
