/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.engine.spi;

import java.util.Set;
import java.util.UUID;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.TransactionRequiredException;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.HibernateException;
import org.hibernate.Interceptor;
import org.hibernate.query.Query;
import org.hibernate.SharedSessionContract;
import org.hibernate.Transaction;
import org.hibernate.cache.spi.CacheTransactionSynchronization;
import org.hibernate.cfg.Environment;
import org.hibernate.collection.spi.PersistentCollection;
import org.hibernate.engine.jdbc.LobCreationContext;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.query.spi.QueryProducerImplementor;
import org.hibernate.resource.jdbc.spi.JdbcSessionOwner;
import org.hibernate.resource.transaction.spi.TransactionCoordinator;
import org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder.Options;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Defines the internal contract shared between {@link org.hibernate.Session} and
 * {@link org.hibernate.StatelessSession} as used by other parts of Hibernate (such as
 * {@link org.hibernate.type.Type}, {@link EntityPersister} and
 * {@link org.hibernate.persister.collection.CollectionPersister} implementors
 *
 * A Session, through this interface and SharedSessionContractImplementor, implements:<ul>
 *     <li>
 *         {@link JdbcSessionOwner} to drive the behavior of a "JDBC session".
 *         Can therefor be used to construct a JdbcCoordinator, which (for now) models a "JDBC session"
 *     </li>
 *     <li>
 *         {@link Options}
 *         to drive the creation of the {@link TransactionCoordinator} delegate.
 *         This allows it to be passed along to
 *         {@link org.hibernate.resource.transaction.spi.TransactionCoordinatorBuilder#buildTransactionCoordinator}
 *     </li>
 *     <li>
 *         {@link LobCreationContext} to act as the context for JDBC LOB instance creation
 *     </li>
 *     <li>
 *         {@link WrapperOptions} to fulfill the behavior needed while
 *         binding/extracting values to/from JDBC as part of the Type contracts
 *     </li>
 * </ul>
 *
 * @author Gavin King
 * @author Steve Ebersole
 */
public interface SharedSessionContractImplementor
		extends SharedSessionContract, JdbcSessionOwner, Options, LobCreationContext, WrapperOptions, QueryProducerImplementor, JavaType.CoercionContext {

	// todo : this is the shared contract between Session and StatelessSession, but it defines methods that StatelessSession does not implement
	//	(it just throws UnsupportedOperationException).  To me it seems like it is better to properly isolate those methods
	//	into just the Session hierarchy.  They include (at least):
	//		1) get/set CacheMode
	//		2) get/set FlushMode
	//		3) get/set (default) read-only
	//		4) #setAutoClear
	//		5) #disableTransactionAutoJoin

	/**
	 * Get the creating {@code SessionFactoryImplementor}
	 */
	SessionFactoryImplementor getFactory();

	@Override
	default SessionFactoryImplementor getSessionFactory() {
		return getFactory();
	}

	@Override
	default TypeConfiguration getTypeConfiguration() {
		return getFactory().getTypeConfiguration();
	}

	SessionEventListenerManager getEventListenerManager();

	/**
	 * Get the persistence context for this session.
	 * See also {@link #getPersistenceContextInternal()} for
	 * an alternative.
	 *
	 * This method is not extremely fast: if you need to access
	 * the PersistenceContext multiple times, prefer keeping
	 * a reference to it over invoking this method multiple times.
	 */
	PersistenceContext getPersistenceContext();

	JdbcCoordinator getJdbcCoordinator();

	JdbcServices getJdbcServices();

	/**
	 * The multi-tenancy tenant identifier, if one.
	 *
	 * @return The tenant identifier; may be {@code null}
	 */
	String getTenantIdentifier();

	/**
	 * A UUID associated with each Session.  Useful mainly for logging.
	 *
	 * @return The UUID
	 */
	UUID getSessionIdentifier();

	@Override
	default SharedSessionContractImplementor getSession() {
		return this;
	}

	/**
	 * A "token" that is unique to this Session.
	 *
	 * @return The token
	 */
	default Object getSessionToken() {
		return this;
	}

	/**
	 * Checks whether the session is closed.  Provided separately from
	 * {@link #isOpen()} as this method does not attempt any JTA synchronization
	 * registration, whereas {@link #isOpen()} does; which makes this one
	 * nicer to use for most internal purposes.
	 *
	 * @return {@code true} if the session is closed; {@code false} otherwise.
	 */
	boolean isClosed();

	/**
	 * Checks whether the session is open or is waiting for auto-close
	 *
	 * @return {@code true} if the session is closed or if it's waiting for auto-close; {@code false} otherwise.
	 */
	default boolean isOpenOrWaitingForAutoClose() {
		return !isClosed();
	}

	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	default void checkOpen() {
		checkOpen( true );
	}

	/**
	 * Performs a check whether the Session is open, and if not:<ul>
	 *     <li>if {@code markForRollbackIfClosed} is true, marks current transaction (if one) for rollback only</li>
	 *     <li>throws an IllegalStateException (JPA defines the exception type)</li>
	 * </ul>
	 */
	void checkOpen(boolean markForRollbackIfClosed);

	/**
	 * Prepare for the execution of a {@link Query} or
	 * {@link org.hibernate.procedure.ProcedureCall}
	 */
	void prepareForQueryExecution(boolean requiresTxn);

	/**
	 * Marks current transaction (if one) for rollback only
	 */
	void markForRollbackOnly();

	/**
	 * A "timestamp" at or before the start of the current transaction.
	 *
	 * @apiNote This "timestamp" need not be related to timestamp in the Java Date/millisecond
	 * sense.  It just needs to be an incrementing value.  See
	 * {@link CacheTransactionSynchronization#getCurrentTransactionStartTimestamp()}
	 */
	long getTransactionStartTimestamp();

	/**
	 * @deprecated Use {@link #getTransactionStartTimestamp()} instead.
	 */
	@Deprecated(since = "5.3")
	default long getTimestamp() {
		return getTransactionStartTimestamp();
	}

	/**
	 * The current CacheTransactionContext associated with the Session.  This may
	 * return {@code null} when the Session is not currently part of a transaction.
	 */
	CacheTransactionSynchronization getCacheTransactionSynchronization();

	/**
	 * Does this {@code Session} have an active Hibernate transaction
	 * or is there a JTA transaction in progress?
	 */
	boolean isTransactionInProgress();

	/**
	 * Check if an active Transaction is necessary for the update operation to be executed.
	 * If an active Transaction is necessary but it is not then a TransactionRequiredException is raised.
	 *
	 * @param exceptionMessage the message to use for the TransactionRequiredException
	 */
	default void checkTransactionNeededForUpdateOperation(String exceptionMessage) {
		if ( !isTransactionInProgress() ) {
			throw new TransactionRequiredException( exceptionMessage );
		}
	}

	/**
	 * Provides access to the underlying transaction or creates a new transaction if
	 * one does not already exist or is active.  This is primarily for internal or
	 * integrator use.
	 *
	 * @return the transaction
     */
	Transaction accessTransaction();

	/**
	 * Hide the changing requirements of entity key creation
	 *
	 * @param id The entity id
	 * @param persister The entity persister
	 *
	 * @return The entity key
	 */
	EntityKey generateEntityKey(Object id, EntityPersister persister);

	/**
	 * Retrieves the interceptor currently in use by this event source.
	 *
	 * @return The interceptor.
	 */
	Interceptor getInterceptor();

	/**
	 * Enable/disable automatic cache clearing from after transaction
	 * completion (for EJB3)
	 */
	void setAutoClear(boolean enabled);

	/**
	 * Initialize the collection (if not already initialized)
	 */
	void initializeCollection(PersistentCollection<?> collection, boolean writing)
			throws HibernateException;

	/**
	 * Load an instance without checking if it was deleted.
	 * <p/>
	 * When {@code nullable} is disabled this method may create a new proxy or
	 * return an existing proxy; if it does not exist, throw an exception.
	 * <p/>
	 * When {@code nullable} is enabled, the method does not create new proxies
	 * (but might return an existing proxy); if it does not exist, return
	 * {@code null}.
	 * <p/>
	 * When {@code eager} is enabled, the object is eagerly fetched
	 */
	Object internalLoad(String entityName, Object id, boolean eager, boolean nullable)
			throws HibernateException;

	/**
	 * Load an instance immediately. This method is only called when lazily initializing a proxy.
	 * Do not return the proxy.
	 */
	Object immediateLoad(String entityName, Object id) throws HibernateException;


	/**
	 * Get the {@code EntityPersister} for any instance
	 *
	 * @param entityName optional entity name
	 * @param object the entity instance
	 */
	EntityPersister getEntityPersister(String entityName, Object object) throws HibernateException;

	/**
	 * Get the entity instance associated with the given {@code Key},
	 * calling the Interceptor if necessary
	 */
	Object getEntityUsingInterceptor(EntityKey key) throws HibernateException;

	/**
	 * Return the identifier of the persistent object, or null if
	 * not associated with the session
	 */
	Object getContextEntityIdentifier(Object object);

	/**
	 * The best guess entity name for an entity not in an association
	 */
	String bestGuessEntityName(Object object);

	/**
	 * The guessed entity name for an entity not in an association
	 */
	String guessEntityName(Object entity) throws HibernateException;

	/**
	 * Instantiate the entity class, initializing with the given identifier
	 */
	Object instantiate(String entityName, Object id) throws HibernateException;

	/**
	 * Instantiate the entity class of an EntityPersister, initializing with the given identifier.
	 * This is more efficient than {@link #instantiate(String, Object)} but not always
	 * interchangeable: a single persister might be responsible for multiple types.
	 */
	Object instantiate(EntityPersister persister, Object id) throws HibernateException;

	boolean isDefaultReadOnly();

	CacheMode getCacheMode();

	void setCacheMode(CacheMode cm);

	void setCriteriaCopyTreeEnabled(boolean jpaCriteriaCopyComplianceEnabled);

	boolean isCriteriaCopyTreeEnabled();

	/**
	 * Get the flush mode for this session.
	 * <p/>
	 * For users of the Hibernate native APIs, we've had to rename this method
	 * as defined by Hibernate historically because the JPA contract defines a method of the same
	 * name, but returning the JPA {@link FlushModeType} rather than Hibernate's {@link FlushMode}.  For
	 * the former behavior, use {@link #getHibernateFlushMode()} instead.
	 *
	 * @return The FlushModeType in effect for this Session.
	 */
	FlushModeType getFlushMode();

	/**
	 * Set the flush mode for this session.
	 * <p/>
	 * The flush mode determines the points at which the session is flushed.
	 * <i>Flushing</i> is the process of synchronizing the underlying persistent
	 * store with persistable state held in memory.
	 * <p/>
	 * For a logically "read only" session, it is reasonable to set the session's
	 * flush mode to {@link FlushMode#MANUAL} at the start of the session (in
	 * order to achieve some extra performance).
	 *
	 * @param flushMode the new flush mode
	 */
	void setHibernateFlushMode(FlushMode flushMode);

	/**
	 * Get the current flush mode for this session.
	 *
	 * @return The flush mode
	 */
	FlushMode getHibernateFlushMode();

	void flush();

	boolean isEventSource();

	void afterScrollOperation();

	boolean shouldAutoClose();

	boolean isAutoCloseSessionEnabled();

	/**
	 * Get the load query influencers associated with this session.
	 *
	 * @return the load query influencers associated with this session;
	 *         should never be null.
	 */
	LoadQueryInfluencers getLoadQueryInfluencers();

	/**
	 * The converter associated to a Session might be lazily initialized: only invoke
	 * this getter when there is actual need to use it.
	 *
	 * @return the ExceptionConverter for this Session.
	 */
	ExceptionConverter getExceptionConverter();

	/**
	 * Get the currently configured JDBC batch size either at the Session-level or SessionFactory-level.
	 *
	 * If the Session-level JDBC batch size was not configured, return the SessionFactory-level one.
	 *
	 * @return Session-level or or SessionFactory-level JDBC batch size.
	 *
	 * @since 5.2
	 *
	 * @see org.hibernate.boot.spi.SessionFactoryOptions#getJdbcBatchSize
	 * @see org.hibernate.boot.SessionFactoryBuilder#applyJdbcBatchSize
	 */
	default Integer getConfiguredJdbcBatchSize() {
		final Integer sessionJdbcBatchSize = getJdbcBatchSize();

		return sessionJdbcBatchSize == null ?
			ConfigurationHelper.getInt(
					Environment.STATEMENT_BATCH_SIZE,
					getFactory().getProperties(),
					1
			) :
			sessionJdbcBatchSize;
	}

	/**
	 * This is similar to {@link #getPersistenceContext()}, with
	 * two main differences:
	 * a) this version performs better as
	 * it allows for inlining and probably better prediction
	 * b) see SessionImpl{@link #getPersistenceContext()} : it
	 * does some checks on the current state of the Session.
	 *
	 * Choose wisely: performance is important, correctness comes first.
	 *
	 * @return the PersistenceContext associated to this session.
	 */
	PersistenceContext getPersistenceContextInternal();

	/**
	 * detect in-memory changes, determine if the changes are to tables
	 * named in the query and, if so, complete execution the flush
	 *
	 * @param querySpaces the tables named in the query.
	 *
	 * @return true if flush is required, false otherwise.
	 */
	boolean autoFlushIfRequired(Set<String> querySpaces) throws HibernateException;

	default boolean isEnforcingFetchGraph() {
		return false;
	}

	default void setEnforcingFetchGraph(boolean enforcingFetchGraph) {
	}

	/**
	 * Check if there is a Hibernate or JTA transaction in progress and,
	 * if there is not, flush if necessary, make sure the connection has
	 * been committed (if it is not in autocommit mode) and run the after
	 * completion processing
	 *
	 * @param success Was the operation a success
	 */
	void afterOperation(boolean success);

}
