/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Function;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.NamedNativeQuery;
import jakarta.persistence.TransactionRequiredException;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;

import org.hibernate.CacheMode;
import org.hibernate.EmptyInterceptor;
import org.hibernate.EntityNameResolver;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.LockMode;
import org.hibernate.SessionEventListener;
import org.hibernate.SessionException;
import org.hibernate.Transaction;
import org.hibernate.UnknownEntityTypeException;
import org.hibernate.cache.spi.CacheTransactionSynchronization;
import org.hibernate.engine.internal.SessionEventListenerManagerImpl;
import org.hibernate.engine.jdbc.LobCreator;
import org.hibernate.engine.jdbc.connections.spi.JdbcConnectionAccess;
import org.hibernate.engine.jdbc.internal.JdbcCoordinatorImpl;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.ExceptionConverter;
import org.hibernate.engine.spi.SessionEventListenerManager;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.engine.transaction.internal.TransactionImpl;
import org.hibernate.engine.transaction.spi.TransactionImplementor;
import org.hibernate.id.uuid.StandardRandomStrategy;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.jdbc.ReturningWork;
import org.hibernate.jdbc.Work;
import org.hibernate.jdbc.WorkExecutorVisitable;
import org.hibernate.jpa.internal.util.FlushModeTypeHelper;
import org.hibernate.jpa.spi.NativeQueryTupleTransformer;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.procedure.ProcedureCall;
import org.hibernate.procedure.internal.ProcedureCallImpl;
import org.hibernate.procedure.spi.NamedCallableQueryMemento;
import org.hibernate.query.IllegalMutationQueryException;
import org.hibernate.query.IllegalNamedQueryOptionsException;
import org.hibernate.query.IllegalSelectQueryException;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.hibernate.query.QueryTypeMismatchException;
import org.hibernate.query.SelectionQuery;
import org.hibernate.query.UnknownNamedQueryException;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.hql.spi.SqmQueryImplementor;
import org.hibernate.query.named.NamedObjectRepository;
import org.hibernate.query.named.NamedResultSetMappingMemento;
import org.hibernate.query.spi.HqlInterpretation;
import org.hibernate.query.spi.QueryEngine;
import org.hibernate.query.spi.QueryImplementor;
import org.hibernate.query.spi.QueryInterpretationCache;
import org.hibernate.query.sql.internal.NativeQueryImpl;
import org.hibernate.query.sql.spi.NamedNativeQueryMemento;
import org.hibernate.query.sql.spi.NativeQueryImplementor;
import org.hibernate.query.sqm.SqmSelectionQuery;
import org.hibernate.query.sqm.internal.QuerySqmImpl;
import org.hibernate.query.sqm.internal.SqmSelectionQueryImpl;
import org.hibernate.query.sqm.internal.SqmUtil;
import org.hibernate.query.sqm.spi.NamedSqmQueryMemento;
import org.hibernate.query.sqm.tree.SqmDmlStatement;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.hibernate.query.sqm.tree.delete.SqmDeleteStatement;
import org.hibernate.query.sqm.tree.select.SqmQueryGroup;
import org.hibernate.query.sqm.tree.select.SqmQuerySpec;
import org.hibernate.query.sqm.tree.select.SqmSelectStatement;
import org.hibernate.query.sqm.tree.update.SqmUpdateStatement;
import org.hibernate.resource.jdbc.spi.JdbcSessionContext;
import org.hibernate.resource.jdbc.spi.PhysicalConnectionHandlingMode;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.hibernate.resource.transaction.backend.jta.internal.JtaTransactionCoordinatorImpl;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder;

import static java.lang.Boolean.TRUE;

/**
 * Base class for SharedSessionContract/SharedSessionContractImplementor
 * implementations.  Intended for Session and StatelessSession implementations
 * <P/>
 * NOTE: This implementation defines access to a number of instance state values
 * in a manner that is not exactly concurrent-access safe.  However, a Session/EntityManager
 * is never intended to be used concurrently; therefore the condition is not expected
 * and so a more synchronized/concurrency-safe is not defined to be as negligent
 * (performance-wise) as possible.  Some of these methods include:<ul>
 *     <li>{@link #getEventListenerManager()}</li>
 *     <li>{@link #getJdbcConnectionAccess()}</li>
 *     <li>{@link #getJdbcServices()}</li>
 *     <li>{@link #getTransaction()} (and therefore related methods such as {@link #beginTransaction()}, etc)</li>
 * </ul>
 *
 * @author Steve Ebersole
 */
public abstract class AbstractSharedSessionContract implements SharedSessionContractImplementor {
	private static final EntityManagerMessageLogger log = HEMLogging.messageLogger( SessionImpl.class );

	private transient SessionFactoryImpl factory;
	private final String tenantIdentifier;
	protected transient FastSessionServices fastSessionServices;
	private UUID sessionIdentifier;
	private Object sessionToken;

	private transient JdbcConnectionAccess jdbcConnectionAccess;
	private transient JdbcSessionContext jdbcSessionContext;
	private transient JdbcCoordinator jdbcCoordinator;

	private transient TransactionImplementor currentHibernateTransaction;
	private transient TransactionCoordinator transactionCoordinator;
	private transient CacheTransactionSynchronization cacheTransactionSync;

	private final boolean isTransactionCoordinatorShared;
	private final Interceptor interceptor;

	private final TimeZone jdbcTimeZone;

	private FlushMode flushMode;
	private boolean autoJoinTransactions;
	private final PhysicalConnectionHandlingMode connectionHandlingMode;

	private CacheMode cacheMode;
	private boolean criteriaCopyTreeEnabled;

	protected boolean closed;
	protected boolean waitingForAutoClose;

	// transient & non-final for Serialization purposes - ugh
	private transient SessionEventListenerManagerImpl sessionEventsManager;
	private transient EntityNameResolver entityNameResolver;

	private Integer jdbcBatchSize;

	//Lazily initialized
	private transient ExceptionConverter exceptionConverter;

	public AbstractSharedSessionContract(SessionFactoryImpl factory, SessionCreationOptions options) {
		this.factory = factory;
		this.fastSessionServices = factory.getFastSessionServices();
		this.cacheTransactionSync = factory.getCache().getRegionFactory().createTransactionContext( this );
		setCriteriaCopyTreeEnabled( factory.getSessionFactoryOptions().isCriteriaCopyTreeEnabled() );

		this.flushMode = options.getInitialSessionFlushMode();

		this.tenantIdentifier = options.getTenantIdentifier();
		if ( factory.getSessionFactoryOptions().isMultiTenancyEnabled() && tenantIdentifier == null ) {
			throw new HibernateException( "SessionFactory configured for multi-tenancy, but no tenant identifier specified" );
		}

		this.interceptor = interpret( options.getInterceptor() );
		this.jdbcTimeZone = options.getJdbcTimeZone();
		final List<SessionEventListener> customSessionEventListener = options.getCustomSessionEventListener();
		if ( customSessionEventListener == null ) {
			sessionEventsManager = new SessionEventListenerManagerImpl( fastSessionServices.defaultSessionEventListeners.buildBaseline() );
		}
		else {
			sessionEventsManager = new SessionEventListenerManagerImpl( customSessionEventListener.toArray( new SessionEventListener[0] ) );
		}

		this.entityNameResolver = new CoordinatingEntityNameResolver( factory, interceptor );

		final StatementInspector statementInspector = interpret( options.getStatementInspector() );
		if ( options instanceof SharedSessionCreationOptions && ( (SharedSessionCreationOptions) options ).isTransactionCoordinatorShared() ) {
			if ( options.getConnection() != null ) {
				throw new SessionException( "Cannot simultaneously share transaction context and specify connection" );
			}

			this.isTransactionCoordinatorShared = true;

			final SharedSessionCreationOptions sharedOptions = (SharedSessionCreationOptions) options;
			this.transactionCoordinator = sharedOptions.getTransactionCoordinator();
			this.jdbcCoordinator = sharedOptions.getJdbcCoordinator();

			// todo : "wrap" the transaction to no-op commit/rollback attempts?
			this.currentHibernateTransaction = sharedOptions.getTransaction();

			if ( sharedOptions.shouldAutoJoinTransactions() ) {
				log.debug(
						"Session creation specified 'autoJoinTransactions', which is invalid in conjunction " +
								"with sharing JDBC connection between sessions; ignoring"
				);
				autoJoinTransactions = false;
			}
			this.connectionHandlingMode = this.jdbcCoordinator.getLogicalConnection().getConnectionHandlingMode();
			if ( sharedOptions.getPhysicalConnectionHandlingMode() != this.connectionHandlingMode ) {
				log.debug(
						"Session creation specified 'PhysicalConnectionHandlingMode' which is invalid in conjunction " +
								"with sharing JDBC connection between sessions; ignoring"
				);
			}

			this.jdbcSessionContext = new JdbcSessionContextImpl( this, statementInspector,
					connectionHandlingMode, fastSessionServices );

			addSharedSessionTransactionObserver( transactionCoordinator );
		}
		else {
			this.isTransactionCoordinatorShared = false;
			this.autoJoinTransactions = options.shouldAutoJoinTransactions();
			this.connectionHandlingMode = options.getPhysicalConnectionHandlingMode();
			this.jdbcSessionContext = new JdbcSessionContextImpl( this, statementInspector,
					connectionHandlingMode, fastSessionServices );
			// This must happen *after* the JdbcSessionContext was initialized,
			// because some of the calls below retrieve this context indirectly through Session getters.
			this.jdbcCoordinator = new JdbcCoordinatorImpl( options.getConnection(), this, fastSessionServices.jdbcServices );
			this.transactionCoordinator = fastSessionServices.transactionCoordinatorBuilder.buildTransactionCoordinator( jdbcCoordinator, this );
		}
	}

	/**
	 * Override the implementation provided on SharedSessionContractImplementor
	 * which is not very efficient: this method is hot in Hibernate Reactive, and could
	 * be hot in some ORM contexts as well.
	 */
	@Override
	public Integer getConfiguredJdbcBatchSize() {
		final Integer sessionJdbcBatchSize = this.jdbcBatchSize;
		return sessionJdbcBatchSize == null ?
				fastSessionServices.defaultJdbcBatchSize :
				sessionJdbcBatchSize;
	}

	protected void addSharedSessionTransactionObserver(TransactionCoordinator transactionCoordinator) {
	}

	protected void removeSharedSessionTransactionObserver(TransactionCoordinator transactionCoordinator) {
		transactionCoordinator.invalidate();
	}

	protected void prepareForAutoClose() {
		waitingForAutoClose = true;
		closed = true;
		// For non-shared transaction coordinators, we have to add the observer
		if ( !isTransactionCoordinatorShared ) {
			addSharedSessionTransactionObserver( transactionCoordinator );
		}
	}

	@Override
	public boolean shouldAutoJoinTransaction() {
		return autoJoinTransactions;
	}

	private Interceptor interpret(Interceptor interceptor) {
		return interceptor == null ? EmptyInterceptor.INSTANCE : interceptor;
	}

	private StatementInspector interpret(StatementInspector statementInspector) {
		return statementInspector == null
				// If there is no StatementInspector specified, map to the call
				// to the (deprecated) Interceptor#onPrepareStatement method
				? interceptor::onPrepareStatement
				: statementInspector;
	}

	@Override
	public SessionFactoryImplementor getFactory() {
		return factory;
	}

	@Override
	public Interceptor getInterceptor() {
		return interceptor;
	}

	@Override
	public JdbcCoordinator getJdbcCoordinator() {
		return jdbcCoordinator;
	}

	@Override
	public TransactionCoordinator getTransactionCoordinator() {
		return transactionCoordinator;
	}

	@Override
	public JdbcSessionContext getJdbcSessionContext() {
		return this.jdbcSessionContext;
	}

	public EntityNameResolver getEntityNameResolver() {
		return entityNameResolver;
	}

	@Override
	public SessionEventListenerManager getEventListenerManager() {
		return sessionEventsManager;
	}

	@Override
	public UUID getSessionIdentifier() {
		if ( this.sessionIdentifier == null ) {
			//Lazily initialized: otherwise all the UUID generations will cause significant amount of contention.
			this.sessionIdentifier = StandardRandomStrategy.INSTANCE.generateUUID( null );
		}
		return sessionIdentifier;
	}

	@Override
	public Object getSessionToken() {
		if ( sessionToken == null ) {
			sessionToken = new Object();
		}
		return sessionToken;
	}

	@Override
	public String getTenantIdentifier() {
		return tenantIdentifier;
	}

	@Override
	public boolean isOpen() {
		return !isClosed();
	}

	@Override
	public boolean isClosed() {
		return closed || factory.isClosed();
	}

	@Override
	public void close() {
		if ( closed && !waitingForAutoClose ) {
			return;
		}

		try {
			delayedAfterCompletion();
		}
		catch ( HibernateException e ) {
			if ( getFactory().getSessionFactoryOptions().isJpaBootstrap() ) {
				throw getExceptionConverter().convert( e );
			}
			else {
				throw e;
			}
		}

		if ( sessionEventsManager != null ) {
			sessionEventsManager.end();
		}

		if ( transactionCoordinator != null ) {
			removeSharedSessionTransactionObserver( transactionCoordinator );
		}

		try {
			if ( shouldCloseJdbcCoordinatorOnClose( isTransactionCoordinatorShared ) ) {
				jdbcCoordinator.close();
			}
		}
		finally {
			setClosed();
		}
	}

	protected void setClosed() {
		closed = true;
		waitingForAutoClose = false;
		cleanupOnClose();
	}

	protected boolean shouldCloseJdbcCoordinatorOnClose(boolean isTransactionCoordinatorShared) {
		return true;
	}

	protected void cleanupOnClose() {
		// nothing to do in base impl, here for SessionImpl hook
	}

	@Override
	public boolean isOpenOrWaitingForAutoClose() {
		return !isClosed() || waitingForAutoClose;
	}

	@Override
	public void checkOpen(boolean markForRollbackIfClosed) {
		if ( isClosed() ) {
			if ( markForRollbackIfClosed && transactionCoordinator.isTransactionActive() ) {
				markForRollbackOnly();
			}
			throw new IllegalStateException( "Session/EntityManager is closed" );
		}
	}

	@Override
	public void prepareForQueryExecution(boolean requiresTxn) {
		checkOpen();
		checkTransactionSynchStatus();

		if ( requiresTxn && !isTransactionInProgress() ) {
			throw new TransactionRequiredException(
					"Query requires transaction be in progress, but no transaction is known to be in progress"
			);
		}
	}

	protected void checkOpenOrWaitingForAutoClose() {
		if ( !waitingForAutoClose ) {
			checkOpen();
		}
	}

	@Override
	public void markForRollbackOnly() {
		try {
			accessTransaction().markRollbackOnly();
		}
		catch (Exception ignore) {
		}
	}

	@Override
	public boolean isTransactionInProgress() {
		if ( waitingForAutoClose ) {
			return factory.isOpen() && transactionCoordinator.isTransactionActive();
		}
		return !isClosed() && transactionCoordinator.isTransactionActive();
	}

	@Override
	public void checkTransactionNeededForUpdateOperation(String exceptionMessage) {
		if ( fastSessionServices.disallowOutOfTransactionUpdateOperations && !isTransactionInProgress() ) {
			throw new TransactionRequiredException( exceptionMessage );
		}
	}

	@Override
	public Transaction getTransaction() throws HibernateException {
		if ( ! fastSessionServices.isJtaTransactionAccessible ) {
			throw new IllegalStateException(
					"Transaction is not accessible when using JTA with JPA-compliant transaction access enabled"
			);
		}
		return accessTransaction();
	}

	@Override
	public Transaction accessTransaction() {
		if ( this.currentHibernateTransaction == null ) {
			this.currentHibernateTransaction = new TransactionImpl(
					getTransactionCoordinator(),
					this
			);
		}
		if ( !isClosed() || ( waitingForAutoClose && factory.isOpen() ) ) {
			getTransactionCoordinator().pulse();
		}
		return this.currentHibernateTransaction;
	}

	@Override
	public void startTransactionBoundary() {
		this.getCacheTransactionSynchronization().transactionJoined();
	}

	@Override
	public void beforeTransactionCompletion() {
		getCacheTransactionSynchronization().transactionCompleting();
	}

	@Override
	public void afterTransactionCompletion(boolean successful, boolean delayed) {
		getCacheTransactionSynchronization().transactionCompleted( successful );
	}

	@Override
	public CacheTransactionSynchronization getCacheTransactionSynchronization() {
		return cacheTransactionSync;
	}

	@Override
	public long getTransactionStartTimestamp() {
		return getCacheTransactionSynchronization().getCurrentTransactionStartTimestamp();
	}

	@Override
	public Transaction beginTransaction() {
		checkOpen();

		Transaction result = getTransaction();
		result.begin();

		return result;
	}

	protected void checkTransactionSynchStatus() {
		pulseTransactionCoordinator();
		delayedAfterCompletion();
	}

	protected void pulseTransactionCoordinator() {
		if ( !isClosed() ) {
			try {
				transactionCoordinator.pulse();
			}
			catch (HibernateException e) {
				throw e;
			}
			catch (RuntimeException e) {
				throw new HibernateException( "Exception pulsing TransactionCoordinator", e );
			}
		}
	}

	protected void delayedAfterCompletion() {
		if ( transactionCoordinator instanceof JtaTransactionCoordinatorImpl ) {
			( (JtaTransactionCoordinatorImpl) transactionCoordinator ).getSynchronizationCallbackCoordinator()
					.processAnyDelayedAfterCompletion();
		}
	}

	protected TransactionImplementor getCurrentTransaction() {
		return currentHibernateTransaction;
	}

	@Override
	public boolean isConnected() {
		pulseTransactionCoordinator();
		return jdbcCoordinator.getLogicalConnection().isOpen();
	}

	@Override
	public JdbcConnectionAccess getJdbcConnectionAccess() {
		// See class-level JavaDocs for a discussion of the concurrent-access safety of this method
		if ( jdbcConnectionAccess == null ) {
			if ( ! fastSessionServices.requiresMultiTenantConnectionProvider ) {
				jdbcConnectionAccess = new NonContextualJdbcConnectionAccess(
						getEventListenerManager(),
						fastSessionServices.connectionProvider
				);
			}
			else {
				jdbcConnectionAccess = new ContextualJdbcConnectionAccess(
						getTenantIdentifier(),
						getEventListenerManager(),
						fastSessionServices.multiTenantConnectionProvider
				);
			}
		}
		return jdbcConnectionAccess;
	}

	@Override
	public EntityKey generateEntityKey(Object id, EntityPersister persister) {
		return new EntityKey( id, persister );
	}

	@Override
	public boolean useStreamForLobBinding() {
		return fastSessionServices.useStreamForLobBinding;
	}

	@Override
	public int getPreferredSqlTypeCodeForBoolean() {
		return fastSessionServices.preferredSqlTypeCodeForBoolean;
	}

	@Override
	public LobCreator getLobCreator() {
		return ( (SharedSessionContractImplementor) this ).getFactory()
				.getServiceRegistry()
				.getService( JdbcServices.class )
				.getLobCreator(this);
	}

	@Override
	public <T> T execute(final Callback<T> callback) {
		return getJdbcCoordinator().coordinateWork(
				(workExecutor, connection) -> {
					try {
						return callback.executeOnConnection( connection );
					}
					catch (SQLException e) {
						throw getExceptionConverter().convert(
								e,
								"Error creating contextual LOB : " + e.getMessage()
						);
					}
				}
		);
	}

	@Override
	public TimeZone getJdbcTimeZone() {
		return jdbcTimeZone;
	}

	@Override
	public JdbcServices getJdbcServices() {
		return getFactory().getJdbcServices();
	}

	@Override
	public FlushModeType getFlushMode() {
		checkOpen();
		return FlushModeTypeHelper.getFlushModeType( this.flushMode );
	}

	@Override
	public void setHibernateFlushMode(FlushMode flushMode) {
		this.flushMode = flushMode;
	}

	@Override
	public FlushMode getHibernateFlushMode() {
		return flushMode;
	}

	@Override
	public CacheMode getCacheMode() {
		return cacheMode;
	}

	@Override
	public void setCacheMode(CacheMode cacheMode) {
		this.cacheMode = cacheMode;
	}

	@Override
	public void setCriteriaCopyTreeEnabled(boolean jpaCriteriaCopyComplianceEnabled) {
		this.criteriaCopyTreeEnabled = jpaCriteriaCopyComplianceEnabled;
	}

	@Override
	public boolean isCriteriaCopyTreeEnabled() {
		return criteriaCopyTreeEnabled;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// dynamic HQL handling

	@Override @SuppressWarnings("rawtypes")
	public QueryImplementor createQuery(String queryString) {
		return createQuery( queryString, null );
	}

	@Override
	public SelectionQuery<?> createSelectionQuery(String hqlString) {
		checkOpen();
		pulseTransactionCoordinator();
		delayedAfterCompletion();

		try {
			final QueryEngine queryEngine = getFactory().getQueryEngine();
			final QueryInterpretationCache interpretationCache = queryEngine.getInterpretationCache();
			final HqlInterpretation hqlInterpretation = interpretationCache.resolveHqlInterpretation(
					hqlString,
					s -> queryEngine.getHqlTranslator().translate( hqlString )
			);

			if ( !( hqlInterpretation.getSqmStatement() instanceof SqmSelectStatement ) ) {
				throw new IllegalSelectQueryException( "Expecting a selection query, but found `" + hqlString + "`", hqlString );
			}

			final SqmSelectionQuery<?> query = new SqmSelectionQueryImpl<>( hqlString, hqlInterpretation, this );
			query.setComment( hqlString );

			applyQuerySettingsAndHints( query );

			return query;
		}
		catch (RuntimeException e) {
			markForRollbackOnly();
			throw e;
		}
	}

	@Override
	public <R> SelectionQuery<R> createSelectionQuery(String hqlString, Class<R> expectedResultType) {
		final SelectionQuery<?> selectQuery = createSelectionQuery( hqlString );
		//noinspection unchecked
		final Class<?> resultType = ( (SqmSelectionQueryImpl<R>) selectQuery ).getResultType();
		if ( resultType == null || expectedResultType.isAssignableFrom( resultType ) ) {
			//noinspection unchecked
			return (SelectionQuery<R>) selectQuery;
		}

		throw new QueryTypeMismatchException(
				String.format(
						Locale.ROOT,
						"Query result-type error - expecting `%s`, but found `%s`",
						expectedResultType.getName(),
						resultType.getName()
				)
		);
	}

	@Override
	public <R> SelectionQuery<R> createSelectionQuery(CriteriaQuery<R> criteria) {
		SqmUtil.verifyIsSelectStatement( (SqmStatement<?>) criteria, null );
		return new SqmSelectionQueryImpl<>( (SqmSelectStatement<R>) criteria, this );
	}

	@Override
	public <T> QueryImplementor<T> createQuery(String queryString, Class<T> resultClass) {
		checkOpen();
		pulseTransactionCoordinator();
		delayedAfterCompletion();

		try {
			final QueryEngine queryEngine = getFactory().getQueryEngine();
			final QueryInterpretationCache interpretationCache = queryEngine.getInterpretationCache();

			final QuerySqmImpl<T> query = new QuerySqmImpl<>(
					queryString,
					interpretationCache.resolveHqlInterpretation(
							queryString,
							s -> queryEngine.getHqlTranslator().translate( queryString )
					),
					resultClass,
					this
			);

			applyQuerySettingsAndHints( query );
			query.setComment( queryString );

			return query;
		}
		catch (RuntimeException e) {
			markForRollbackOnly();
			throw getExceptionConverter().convert( e );
		}
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// dynamic native (SQL) query handling

	@Override @SuppressWarnings("rawtypes")
	public NativeQueryImpl createNativeQuery(String sqlString) {
		checkOpen();
		pulseTransactionCoordinator();
		delayedAfterCompletion();

		try {
			NativeQueryImpl query = new NativeQueryImpl<>(sqlString, this);
			if ( StringHelper.isEmpty( query.getComment() ) ) {
				query.setComment( "dynamic native SQL query" );
			}
			applyQuerySettingsAndHints( query );
			return query;
		}
		catch (RuntimeException he) {
			throw getExceptionConverter().convert( he );
		}
	}

	@Override @SuppressWarnings("rawtypes")
	public NativeQueryImplementor createNativeQuery(String sqlString, String resultSetMappingName) {
		checkOpen();
		pulseTransactionCoordinator();
		delayedAfterCompletion();

		try {
			if ( StringHelper.isNotEmpty(resultSetMappingName) ) {
				final NamedResultSetMappingMemento resultSetMappingMemento = getFactory().getQueryEngine()
						.getNamedObjectRepository()
						.getResultSetMappingMemento(resultSetMappingName);

				if ( resultSetMappingMemento == null ) {
					throw new HibernateException( "Could not resolve specified result-set mapping name : "
							+ resultSetMappingName);
				}

				return new NativeQueryImpl<>(sqlString, resultSetMappingMemento, this);
			}
			else {
				return new NativeQueryImpl<>(sqlString, this);
			}
			//TODO: why no applyQuerySettingsAndHints( query ); ???
		}
		catch (RuntimeException he) {
			throw getExceptionConverter().convert( he );
		}
	}

	@Override @SuppressWarnings({"rawtypes", "unchecked"})
	//note: we're doing something a bit funny here to work around
	//      the clashing signatures declared by the supertypes
	public NativeQueryImplementor createNativeQuery(String sqlString, Class resultClass) {
		NativeQueryImpl query = createNativeQuery( sqlString );
		if ( Tuple.class.equals(resultClass) ) {
			query.setTupleTransformer( new NativeQueryTupleTransformer() );
		}
		else if ( getFactory().getMappingMetamodel().isEntityClass(resultClass) ) {
			query.addEntity( "alias1", resultClass.getName(), LockMode.READ );
		}
		else {
			query.addScalar( 1, resultClass );
		}
		return query;
	}

	@Override
	public <T> NativeQueryImplementor<T> createNativeQuery(String sqlString, Class<T> resultClass, String tableAlias) {
		@SuppressWarnings("unchecked")
		NativeQueryImplementor<T> query = createNativeQuery( sqlString );
		if ( getFactory().getMappingMetamodel().isEntityClass(resultClass) ) {
			query.addEntity( tableAlias, resultClass.getName(), LockMode.READ );
		}
		else {
			throw new UnknownEntityTypeException( "unable to locate persister: " + resultClass.getName() );
		}
		return query;
	}

	@Override
	public <T> NativeQueryImplementor<T>  createNativeQuery(String sqlString, String resultSetMappingName, Class<T> resultClass) {
		@SuppressWarnings("unchecked")
		final NativeQueryImplementor<T> query = createNativeQuery( sqlString, resultSetMappingName );
		if ( Tuple.class.equals( resultClass ) ) {
			query.setTupleTransformer( new NativeQueryTupleTransformer() );
		}
		return query;
	}

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// named query handling

	@Override @SuppressWarnings("rawtypes")
	public QueryImplementor getNamedQuery(String queryName) {
		return buildNamedQuery( queryName, null );
	}

	@Override @SuppressWarnings("rawtypes")
	public QueryImplementor createNamedQuery(String name) {
		return buildNamedQuery( name, null );
	}

	@Override
	public <R> QueryImplementor<R> createNamedQuery(String name, Class<R> resultClass) {
		return buildNamedQuery( name, resultClass );
	}

	@Override
	public SelectionQuery<?> createNamedSelectionQuery(String queryName) {
		return createNamedSelectionQuery( queryName, null );
	}

	@Override
	public <R> SelectionQuery<R> createNamedSelectionQuery(String queryName, Class<R> expectedResultType) {
		checkOpen();
		pulseTransactionCoordinator();
		delayedAfterCompletion();

		// this method can be called for either a named HQL query or a named native query

		// first see if it is a named HQL query
		final NamedSqmQueryMemento namedHqlDescriptor = getFactory().getQueryEngine()
				.getNamedObjectRepository()
				.getSqmQueryMemento( queryName );

		if ( namedHqlDescriptor != null ) {
			return createNamedSqmSelectionQuery( namedHqlDescriptor, expectedResultType );
		}


		// otherwise, see if it is a named native query
		final NamedNativeQueryMemento namedNativeDescriptor = getFactory().getQueryEngine()
				.getNamedObjectRepository()
				.getNativeQueryMemento( queryName );

		if ( namedNativeDescriptor != null ) {
			return createNamedNativeSelectionQuery( namedNativeDescriptor, expectedResultType );
		}

		throw new UnknownNamedQueryException( queryName );
	}

	private <R> SelectionQuery<R> createNamedNativeSelectionQuery(
			NamedNativeQueryMemento memento,
			Class<R> expectedResultType) {
		throw new UnsupportedOperationException(
				String.format(
						Locale.ROOT,
						"Support for `@%s` + `%s` is not (yet) implemented",
						NamedNativeQuery.class.getName(),
						SelectionQuery.class.getName()
				)
		);
	}

	private <R> SqmSelectionQuery<R> createNamedSqmSelectionQuery(
			NamedSqmQueryMemento memento,
			Class<R> expectedResultType) {
		final SqmSelectionQuery<R> selectionQuery = memento.toSelectionQuery( expectedResultType, this );

		if ( StringHelper.isEmpty( memento.getComment() ) ) {
			selectionQuery.setComment( "Named query : " + memento.getRegistrationName() );
		}
		else {
			selectionQuery.setComment( memento.getComment() );
		}

		applyQuerySettingsAndHints( selectionQuery );

		if ( memento.getLockOptions() != null ) {
			selectionQuery.getLockOptions().overlay( memento.getLockOptions() );
		}

		return selectionQuery;
	}

	@Override
	public void doWork(final Work work) throws HibernateException {
		WorkExecutorVisitable<Void> realWork = (workExecutor, connection) -> {
			workExecutor.executeWork( work, connection );
			return null;
		};
		doWork( realWork );
	}

	@Override
	public <T> T doReturningWork(final ReturningWork<T> work) throws HibernateException {
		WorkExecutorVisitable<T> realWork = (workExecutor, connection) -> workExecutor.executeReturningWork(
				work,
				connection
		);
		return doWork( realWork );
	}

	private <T> T doWork(WorkExecutorVisitable<T> work) throws HibernateException {
		return getJdbcCoordinator().coordinateWork( work );
	}

	@SuppressWarnings({"rawtypes","unchecked"})
	protected <T> QueryImplementor<T> buildNamedQuery(String queryName, Class<T> resultType) {
		try {
			return buildNamedQuery(
					queryName,
					(memento) -> {
						final SqmQueryImplementor query = memento.toQuery( this, resultType );

						if ( StringHelper.isEmpty( query.getComment() ) ) {
							query.setComment( "dynamic query" );
						}
						applyQuerySettingsAndHints( query );
						if ( memento.getLockOptions() != null ) {
							query.setLockOptions( memento.getLockOptions() );
						}

						return query;
					},
					(memento) -> {
						final NativeQueryImplementor query;
						if ( resultType == null ) {
							query = memento.toQuery( this );
						}
						else {
							query = memento.toQuery( this, resultType );
						}

						if ( StringHelper.isEmpty( query.getComment() ) ) {
							query.setComment( "dynamic native-SQL query" );
						}
						applyQuerySettingsAndHints( query );

						return query;
					}
			);
		}
		catch (UnknownNamedQueryException e) {
			// JPA expects this to mark the transaction for rollback only
			transactionCoordinator.getTransactionDriverControl().markRollbackOnly();
			// it also expects an IllegalArgumentException, so wrap UnknownNamedQueryException
			throw new IllegalArgumentException( e.getMessage(), e );
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (RuntimeException e) {
			throw getExceptionConverter().convert( e );
		}
	}

	@SuppressWarnings("rawtypes")
	protected QueryImplementor buildNamedQuery(
			String queryName,
			Function<NamedSqmQueryMemento, SqmQueryImplementor> namedSqmHandler,
			Function<NamedNativeQueryMemento, NativeQueryImplementor> namedNativeHandler) {
		checkOpen();
		pulseTransactionCoordinator();
		delayedAfterCompletion();

		// this method can be called for either a named HQL query or a named native query

		// first see if it is a named HQL query
		final NamedObjectRepository namedObjectRepository = getFactory().getQueryEngine()
				.getNamedObjectRepository();


		final NamedSqmQueryMemento namedSqmQueryMemento = namedObjectRepository.getSqmQueryMemento( queryName );
		if ( namedSqmQueryMemento != null ) {
			return namedSqmHandler.apply( namedSqmQueryMemento );
		}

		// otherwise, see if it is a named native query
		final NamedNativeQueryMemento namedNativeDescriptor = namedObjectRepository
				.getNativeQueryMemento( queryName );

		if ( namedNativeDescriptor != null ) {
			return namedNativeHandler.apply( namedNativeDescriptor );
		}

		throw new UnknownNamedQueryException( queryName );
	}

	protected void applyQuerySettingsAndHints(SelectionQuery<?> query) {
	}

	protected void applyQuerySettingsAndHints(Query<?> query) {
	}

	@Override @SuppressWarnings("rawtypes")
	public NativeQueryImplementor getNamedNativeQuery(String queryName) {
		final NamedNativeQueryMemento namedNativeDescriptor = getFactory().getQueryEngine()
				.getNamedObjectRepository()
				.getNativeQueryMemento( queryName );

		if ( namedNativeDescriptor != null ) {
			return namedNativeDescriptor.toQuery( this );
		}

		throw getExceptionConverter().convert( new IllegalArgumentException( "No query defined for that name [" + queryName + "]" ) );
	}

	@Override @SuppressWarnings("rawtypes")
	public NativeQueryImplementor getNamedNativeQuery(String queryName, String resultSetMapping) {
		final NamedNativeQueryMemento namedNativeDescriptor = getFactory().getQueryEngine()
				.getNamedObjectRepository()
				.getNativeQueryMemento( queryName );

		if ( namedNativeDescriptor != null ) {
			return namedNativeDescriptor.toQuery( this, resultSetMapping );
		}

		throw getExceptionConverter().convert( new IllegalArgumentException( "No query defined for that name [" + queryName + "]" ) );
	}

	@Override
	public MutationQuery createMutationQuery(String hqlString) {
		final QueryImplementor<?> query = createQuery( hqlString );
		final SqmStatement<?> sqmStatement = ( (SqmQueryImplementor<?>) query ).getSqmStatement();
		if ( !( sqmStatement instanceof SqmDmlStatement ) ) {
			throw new IllegalMutationQueryException( "Expecting a mutation query, but found `" + hqlString + "`" );
		}
		return query;
	}

	@Override
	public MutationQuery createNativeMutationQuery(String sqlString) {
		final NativeQueryImplementor<?> query = createNativeQuery( sqlString );
		if ( query.isSelectQuery() == TRUE ) {
			throw new IllegalMutationQueryException( "Expecting a native mutation query, but found `" + sqlString + "`" );
		}
		return query;
	}

	@Override
	public MutationQuery createNamedMutationQuery(String queryName) {
		return buildNamedQuery(
				queryName,
				(sqmMemento) -> {
					final SqmQueryImplementor<?> query = sqmMemento.toQuery( this );

					final SqmStatement<?> sqmStatement = query.getSqmStatement();
					if ( !( sqmStatement instanceof SqmDmlStatement ) ) {
						throw new IllegalMutationQueryException(
								"Expecting a named mutation query (" + queryName + "), but found a select statement"
						);
					}

					if ( sqmMemento.getLockOptions() != null && ! sqmMemento.getLockOptions().isEmpty() ) {
						throw new IllegalNamedQueryOptionsException(
								"Named mutation query `" + queryName + "` specified lock-options"
						);
					}

					if ( StringHelper.isEmpty( query.getComment() ) ) {
						query.setComment( "dynamic HQL query" );
					}
					applyQuerySettingsAndHints( query );

					return query;
				},
				(nativeMemento) -> {
					final NativeQueryImplementor<?> query = nativeMemento.toQuery( this );

					final Boolean isUnequivocallySelect = query.isSelectQuery();
					if ( isUnequivocallySelect == TRUE ) {
						throw new IllegalMutationQueryException(
								"Expecting named native query (" + queryName + ") to be a mutation query, but found `" + nativeMemento.getSqlString() + "`"
						);
					}

					if ( StringHelper.isEmpty( query.getComment() ) ) {
						query.setComment( "dynamic native-SQL query" );
					}

					applyQuerySettingsAndHints( query );

					return query;
				}
		);
	}

	@Override
	public MutationQuery createMutationQuery(@SuppressWarnings("rawtypes") CriteriaUpdate updateQuery) {
		checkOpen();
		try {
			return new QuerySqmImpl<>(
					(SqmUpdateStatement<?>) updateQuery,
					null,
					this
			);
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	public MutationQuery createMutationQuery(@SuppressWarnings("rawtypes") CriteriaDelete deleteQuery) {
		checkOpen();
		try {
			return new QuerySqmImpl<>( (SqmDeleteStatement<?>) deleteQuery, null, this );
		}
		catch ( RuntimeException e ) {
			throw getExceptionConverter().convert( e );
		}
	}

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall getNamedProcedureCall(String name) {
		checkOpen();

		final NamedCallableQueryMemento memento = factory.getQueryEngine().getNamedObjectRepository().getCallableQueryMemento( name );
		if ( memento == null ) {
			throw new IllegalArgumentException(
					"Could not find named stored procedure call with that registration name : " + name
			);
		}
		final ProcedureCall procedureCall = memento.makeProcedureCall( this );
//		procedureCall.setComment( "Named stored procedure call [" + name + "]" );
		return procedureCall;
	}

	@Override
	public ProcedureCall createNamedStoredProcedureQuery(String name) {
		return getNamedProcedureCall( name );
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// dynamic ProcedureCall support

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall createStoredProcedureCall(String procedureName) {
		checkOpen();
		final ProcedureCall procedureCall = new ProcedureCallImpl<>( this, procedureName );
//		call.setComment( "Dynamic stored procedure call" );
		return procedureCall;
	}

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall createStoredProcedureCall(String procedureName, Class<?>... resultClasses) {
		checkOpen();
		final ProcedureCall procedureCall = new ProcedureCallImpl<>( this, procedureName, resultClasses );
//		call.setComment( "Dynamic stored procedure call" );
		return procedureCall;
	}

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall createStoredProcedureCall(String procedureName, String... resultSetMappings) {
		checkOpen();
		final ProcedureCall procedureCall = new ProcedureCallImpl<>( this, procedureName, resultSetMappings );
//		call.setComment( "Dynamic stored procedure call" );
		return procedureCall;
	}

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall createStoredProcedureQuery(String procedureName) {
		checkOpen();
		final ProcedureCall procedureCall = new ProcedureCallImpl<>( this, procedureName );
//		call.setComment( "Dynamic stored procedure call" );
		return procedureCall;
	}

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall createStoredProcedureQuery(String procedureName, Class<?>... resultClasses) {
		checkOpen();
		final ProcedureCall procedureCall = new ProcedureCallImpl<>( this, procedureName, resultClasses );
//		call.setComment( "Dynamic stored procedure call" );
		return procedureCall;
	}

	@Override
	@SuppressWarnings("UnnecessaryLocalVariable")
	public ProcedureCall createStoredProcedureQuery(String procedureName, String... resultSetMappings) {
		checkOpen();
		final ProcedureCall procedureCall = new ProcedureCallImpl<>( this, procedureName, resultSetMappings );
//		call.setComment( "Dynamic stored procedure call" );
		return procedureCall;
	}

	protected abstract Object load(String entityName, Object identifier);

	@Override
	public ExceptionConverter getExceptionConverter() {
		if ( exceptionConverter == null ) {
			exceptionConverter = new ExceptionConverterImpl( this );
		}
		return exceptionConverter;
	}

	public Integer getJdbcBatchSize() {
		return jdbcBatchSize;
	}

	@Override
	public void setJdbcBatchSize(Integer jdbcBatchSize) {
		this.jdbcBatchSize = jdbcBatchSize;
	}

	@Override
	public HibernateCriteriaBuilder getCriteriaBuilder() {
		checkOpen();
		return getFactory().getCriteriaBuilder();
	}

	@Override
	public <T> QueryImplementor<T> createQuery(CriteriaQuery<T> criteriaQuery) {
		checkOpen();

		try {
			final SqmSelectStatement<T> selectStatement = (SqmSelectStatement<T>) criteriaQuery;
			if ( ! ( selectStatement.getQueryPart() instanceof SqmQueryGroup ) ) {
				final SqmQuerySpec<T> querySpec = selectStatement.getQuerySpec();
				if ( querySpec.getSelectClause().getSelections().isEmpty() ) {
					if ( querySpec.getFromClause().getRoots().size() == 1 ) {
						querySpec.getSelectClause().setSelection( querySpec.getFromClause().getRoots().get(0) );
					}
				}
			}

			return new QuerySqmImpl<>( selectStatement, criteriaQuery.getResultType(), this );
		}
		catch (RuntimeException e) {
			if ( getSessionFactory().getJpaMetamodel().getJpaCompliance().isJpaTransactionComplianceEnabled() ) {
				markForRollbackOnly();
			}
			throw getExceptionConverter().convert( e );
		}
	}

	@Override @SuppressWarnings({"unchecked", "rawtypes"})
	public QueryImplementor createQuery(@SuppressWarnings("rawtypes") CriteriaUpdate criteriaUpdate) {
		checkOpen();
		try {
			return new QuerySqmImpl<>(
					(SqmUpdateStatement<Void>) criteriaUpdate,
					null,
					this
			);
		}
		catch (RuntimeException e) {
			if ( getSessionFactory().getJpaMetamodel().getJpaCompliance().isJpaTransactionComplianceEnabled() ) {
				markForRollbackOnly();
			}
			throw getExceptionConverter().convert( e );
		}
	}

	@Override @SuppressWarnings({"unchecked", "rawtypes"})
	public QueryImplementor createQuery(@SuppressWarnings("rawtypes") CriteriaDelete criteriaDelete) {
		checkOpen();
		try {
			return new QuerySqmImpl<>(
					(SqmDeleteStatement<Void>) criteriaDelete,
					null,
					this
			);
		}
		catch (RuntimeException e) {
			if ( getSessionFactory().getJpaMetamodel().getJpaCompliance().isJpaTransactionComplianceEnabled() ) {
				markForRollbackOnly();
			}
			throw getExceptionConverter().convert( e );
		}
	}


	@SuppressWarnings("unused")
	private void writeObject(ObjectOutputStream oos) throws IOException {
		if ( log.isTraceEnabled() ) {
			log.trace( "Serializing " + getClass().getSimpleName() + " [" );
		}


		if ( !jdbcCoordinator.isReadyForSerialization() ) {
			// throw a more specific (helpful) exception message when this happens from Session,
			//		as opposed to more generic exception from jdbcCoordinator#serialize call later
			throw new IllegalStateException( "Cannot serialize " + getClass().getSimpleName() + " [" + getSessionIdentifier() + "] while connected" );
		}

		if ( isTransactionCoordinatorShared ) {
			throw new IllegalStateException( "Cannot serialize " + getClass().getSimpleName() + " [" + getSessionIdentifier() + "] as it has a shared TransactionCoordinator" );
		}

		// todo : (5.2) come back and review serialization plan...
		//		this was done quickly during initial HEM consolidation into CORE and is likely not perfect :)
		//
		//		be sure to review state fields in terms of transient modifiers

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Step 1 :: write non-transient state...
		oos.defaultWriteObject();

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Step 2 :: write transient state...
		// 		-- see concurrent access discussion

		factory.serialize( oos );
		oos.writeObject( jdbcSessionContext.getStatementInspector() );
		jdbcCoordinator.serialize( oos );
	}

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException, SQLException {
		if ( log.isTraceEnabled() ) {
			log.trace( "Deserializing " + getClass().getSimpleName() );
		}

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Step 1 :: read back non-transient state...
		ois.defaultReadObject();

		// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
		// Step 2 :: read back transient state...
		//		-- see above

		factory = SessionFactoryImpl.deserialize( ois );
		fastSessionServices = factory.getFastSessionServices();
		sessionEventsManager = new SessionEventListenerManagerImpl( fastSessionServices.defaultSessionEventListeners.buildBaseline() );
		jdbcSessionContext = new JdbcSessionContextImpl( this, (StatementInspector) ois.readObject(),
				connectionHandlingMode, fastSessionServices );
		jdbcCoordinator = JdbcCoordinatorImpl.deserialize( ois, this );

		cacheTransactionSync = factory.getCache().getRegionFactory().createTransactionContext( this );

		transactionCoordinator = factory.getServiceRegistry()
				.getService( TransactionCoordinatorBuilder.class )
				.buildTransactionCoordinator( jdbcCoordinator, this );

		entityNameResolver = new CoordinatingEntityNameResolver( factory, interceptor );
	}

}
