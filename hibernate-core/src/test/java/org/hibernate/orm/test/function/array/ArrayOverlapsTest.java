/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.function.array;

import java.util.Collection;
import java.util.List;

import org.hibernate.query.Query;
import org.hibernate.query.criteria.JpaCriteriaQuery;
import org.hibernate.query.criteria.JpaRoot;
import org.hibernate.query.sqm.NodeBuilder;

import org.hibernate.testing.jdbc.SharedDriverManagerTypeCacheClearingIntegrator;
import org.hibernate.testing.orm.junit.BootstrapServiceRegistry;
import org.hibernate.testing.orm.junit.DialectFeatureChecks;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.RequiresDialectFeature;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Tuple;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Christian Beikov
 */
@DomainModel(annotatedClasses = EntityWithArrays.class)
@SessionFactory
@RequiresDialectFeature( feature = DialectFeatureChecks.SupportsStructuralArrays.class)
// Clear the type cache, otherwise we might run into ORA-21700: object does not exist or is marked for delete
@BootstrapServiceRegistry(integrators = SharedDriverManagerTypeCacheClearingIntegrator.class)
public class ArrayOverlapsTest {

	@BeforeEach
	public void prepareData(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			em.persist( new EntityWithArrays( 1L, new String[]{} ) );
			em.persist( new EntityWithArrays( 2L, new String[]{ "abc", null, "def" } ) );
			em.persist( new EntityWithArrays( 3L, null ) );
		} );
	}

	@AfterEach
	public void cleanup(SessionFactoryScope scope) {
		scope.inTransaction( em -> {
			em.createMutationQuery( "delete from EntityWithArrays" ).executeUpdate();
		} );
	}

	@Test
	public void testOverlapsFully(SessionFactoryScope scope) {
		scope.inSession( em -> {
			//tag::hql-array-overlaps-example[]
			List<EntityWithArrays> results = em.createQuery( "from EntityWithArrays e where array_overlaps(e.theArray, array('abc', 'def'))", EntityWithArrays.class )
					.getResultList();
			//end::hql-array-overlaps-example[]
			assertEquals( 1, results.size() );
			assertEquals( 2L, results.get( 0 ).getId() );
		} );
	}

	@Test
	public void testDoesNotOverlap(SessionFactoryScope scope) {
		scope.inSession( em -> {
			List<EntityWithArrays> results = em.createQuery( "from EntityWithArrays e where array_overlaps(e.theArray, array('xyz'))", EntityWithArrays.class )
					.getResultList();
			assertEquals( 0, results.size() );
		} );
	}

	@Test
	public void testOverlaps(SessionFactoryScope scope) {
		scope.inSession( em -> {
			List<EntityWithArrays> results = em.createQuery( "from EntityWithArrays e where array_overlaps(e.theArray, array('abc','xyz'))", EntityWithArrays.class )
					.getResultList();
			assertEquals( 1, results.size() );
			assertEquals( 2L, results.get( 0 ).getId() );
		} );
	}

	@Test
	public void testOverlapsNullFully(SessionFactoryScope scope) {
		scope.inSession( em -> {
			List<EntityWithArrays> results = em.createQuery( "from EntityWithArrays e where array_overlaps_nullable(e.theArray, array(null))", EntityWithArrays.class )
					.getResultList();
			assertEquals( 1, results.size() );
			assertEquals( 2L, results.get( 0 ).getId() );
		} );
	}

	@Test
	public void testOverlapsNull(SessionFactoryScope scope) {
		scope.inSession( em -> {
			//tag::hql-array-overlaps-nullable-example[]
			List<EntityWithArrays> results = em.createQuery( "from EntityWithArrays e where array_overlaps_nullable(e.theArray, array('xyz',null))", EntityWithArrays.class )
					.getResultList();
			//end::hql-array-overlaps-nullable-example[]
			assertEquals( 1, results.size() );
			assertEquals( 2L, results.get( 0 ).getId() );
		} );
	}

	@Test
	public void testOverlapsFullyWithOneOrdinalParam(SessionFactoryScope scope) {
		scope.inSession( em -> {
			Query<EntityWithArrays> q = em.createQuery( "from EntityWithArrays e where array_overlaps(e.theCollection, array(cast(?1 as String), 'def'))", EntityWithArrays.class );
			q.setParameter( 1, "abc" );
			List<EntityWithArrays> results = q.getResultList();
			assertEquals( 1, results.size() );
			assertEquals( 2L, results.get( 0 ).getId() );
		} );
	}

	@Test
	public void testOverlapsFullyWithAllOrdinalParams(SessionFactoryScope scope) {
		scope.inSession( em -> {
			Query<EntityWithArrays> q = em.createQuery( "from EntityWithArrays e where array_overlaps(e.theCollection, array(cast(?1 as String), cast(?2 as String)))", EntityWithArrays.class );
			q.setParameter( 1, "abc" );
			q.setParameter( 2, "def" );
			List<EntityWithArrays> results = q.getResultList();
			assertEquals( 1, results.size() );
			assertEquals( 2L, results.get( 0 ).getId() );
		} );
	}

	@Test
	public void testNodeBuilderArray(SessionFactoryScope scope) {
		scope.inSession( em -> {
			final NodeBuilder cb = (NodeBuilder) em.getCriteriaBuilder();
			final JpaCriteriaQuery<Tuple> cq = cb.createTupleQuery();
			final JpaRoot<EntityWithArrays> root = cq.from( EntityWithArrays.class );
			cq.multiselect(
					root.get( "id" ),
					cb.arrayOverlaps( root.get( "theArray" ), cb.arrayLiteral( "xyz" ) ),
					cb.arrayOverlaps( root.get( "theArray" ), new String[]{ "xyz" } ),
					cb.arrayOverlaps( new String[]{ "abc", "xyz" }, cb.arrayLiteral( "xyz" ) ),
					cb.arrayOverlapsNullable( root.get( "theArray" ), cb.arrayLiteral( "xyz" ) ),
					cb.arrayOverlapsNullable( root.get( "theArray" ), new String[]{ "xyz" } ),
					cb.arrayOverlapsNullable( new String[]{ "abc", "xyz" }, cb.arrayLiteral( "xyz" ) )
			);
			em.createQuery( cq ).getResultList();

			// Should all fail to compile
//			cb.arrayOverlaps( root.<Integer[]>get( "theArray" ), cb.arrayLiteral( "xyz" ) );
//			cb.arrayOverlaps( root.<Integer[]>get( "theArray" ), new String[]{ "xyz" } );
//			cb.arrayOverlaps( new String[0], cb.literal( 1 ) );
//			cb.arrayOverlaps( new Integer[0], cb.literal( "" ) );
//			cb.arrayOverlapsNullable( root.<Integer[]>get( "theArray" ), cb.arrayLiteral( "xyz" ) );
//			cb.arrayOverlapsNullable( root.<Integer[]>get( "theArray" ), new String[]{ "xyz" } );
//			cb.arrayOverlapsNullable( new String[0], cb.literal( 1 ) );
//			cb.arrayOverlapsNullable( new Integer[0], cb.literal( "" ) );
		} );
	}

	@Test
	public void testNodeBuilderCollection(SessionFactoryScope scope) {
		scope.inSession( em -> {
			final NodeBuilder cb = (NodeBuilder) em.getCriteriaBuilder();
			final JpaCriteriaQuery<Tuple> cq = cb.createTupleQuery();
			final JpaRoot<EntityWithArrays> root = cq.from( EntityWithArrays.class );
			cq.multiselect(
					root.get( "id" ),
					cb.collectionOverlaps( root.<Collection<String>>get( "theCollection" ), cb.collectionLiteral( "xyz" ) ),
					cb.collectionOverlaps( root.get( "theCollection" ), List.of( "xyz" ) ),
					cb.collectionOverlaps( List.of( "abc", "xyz" ), cb.collectionLiteral( "xyz" ) ),
					cb.collectionOverlapsNullable( root.<Collection<String>>get( "theCollection" ), cb.collectionLiteral( "xyz" ) ),
					cb.collectionOverlapsNullable( root.get( "theCollection" ), List.of( "xyz" ) ),
					cb.collectionOverlapsNullable( List.of( "abc", "xyz" ), cb.collectionLiteral( "xyz" ) )
			);
			em.createQuery( cq ).getResultList();

			// Should all fail to compile
//			cb.collectionOverlaps( root.<Collection<Integer>>get( "theCollection" ), cb.collectionLiteral( "xyz" ) );
//			cb.collectionOverlaps( root.<Collection<Integer>>get( "theCollection" ), List.of( "xyz" ) );
//			cb.collectionOverlaps( Collections.<String>emptyList(), cb.literal( 1 ) );
//			cb.collectionOverlaps( Collections.<Integer>emptyList(), cb.literal( "" ) );
//			cb.collectionOverlapsNullable( root.<Collection<Integer>>get( "theCollection" ), cb.collectionLiteral( "xyz" ) );
//			cb.collectionOverlapsNullable( root.<Collection<Integer>>get( "theCollection" ), List.of( "xyz" ) );
//			cb.collectionOverlapsNullable( Collections.<String>emptyList(), cb.literal( 1 ) );
//			cb.collectionOverlapsNullable( Collections.<Integer>emptyList(), cb.literal( "" ) );
		} );
	}

}
