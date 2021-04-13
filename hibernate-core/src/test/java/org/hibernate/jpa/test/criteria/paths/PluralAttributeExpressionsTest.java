/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.jpa.test.criteria.paths;

import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.hibernate.jpa.test.metamodel.Article;
import org.hibernate.jpa.test.metamodel.Article_;
import org.hibernate.jpa.test.metamodel.MapEntity;
import org.hibernate.jpa.test.metamodel.MapEntityLocal;
import org.hibernate.jpa.test.metamodel.MapEntity_;
import org.hibernate.jpa.test.metamodel.Translation;
import org.hibernate.query.criteria.HibernateCriteriaBuilder;
import org.hibernate.query.criteria.JpaExpression;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.domain.StandardDomainModel;
import org.hibernate.testing.orm.domain.eclectic.Address;
import org.hibernate.testing.orm.domain.eclectic.Address_;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;

import org.junit.jupiter.api.Test;

/**
 * @author Steve Ebersole
 */
@Jpa(
		annotatedClasses = {
				MapEntity.class,
				MapEntityLocal.class,
				Article.class,
				Translation.class
		},
		standardModels = { StandardDomainModel.ECLECTIC }
)
public class PluralAttributeExpressionsTest {

	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// IS [NOT] EMPTY

	@Test
	public void testCollectionIsEmptyHql(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> entityManager.createQuery( "select a from Address a where a.phones is empty" ).getResultList()
		);
	}

	@Test
	public void testCollectionIsEmptyCriteria(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					final HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

					final CriteriaQuery<Address> criteria = cb.createQuery( Address.class );
					final Root<Address> root = criteria.from( Address.class);

					criteria.select( root )
							.where( cb.isEmpty( root.get( Address_.phones ) ) );

					entityManager.createQuery( criteria ).getResultList();
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testElementMapIsEmptyHql(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> entityManager.createQuery( "select m from MapEntity m where m.localized is empty" ).getResultList()
		);
	}


	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testElementMapIsEmptyCriteria(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					final HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

					final CriteriaQuery<MapEntity> criteria = cb.createQuery( MapEntity.class );
					final Root<MapEntity> root = criteria.from( MapEntity.class);

					criteria.select( root )
							.where( cb.isMapEmpty( (JpaExpression) root.get( MapEntity_.localized ) ) );

					entityManager.createQuery( criteria ).getResultList();
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testEntityMapIsEmptyHql(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> entityManager.createQuery( "select a from Article a where a.translations is empty" ).getResultList()
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testEntityMapIsEmptyCriteria(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					final HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

					final CriteriaQuery<Article> criteria = cb.createQuery( Article.class );
					final Root<Article> root = criteria.from( Article.class);

					criteria.select( root )
							.where( cb.isEmpty( root.get( Article_.translations ) ) );

					entityManager.createQuery( criteria ).getResultList();
				}
		);
	}


	// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
	// SIZE

	@Test
	public void testCollectionSizeHql(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> entityManager.createQuery( "select a from Address a where size(a.phones) > 1" ).getResultList()
		);
	}

	@Test
	public void testCollectionSizeCriteria(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					final HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

					final CriteriaQuery<Address> criteria = cb.createQuery( Address.class );
					final Root<Address> root = criteria.from( Address.class);

					criteria.select( root )
							.where( cb.gt( cb.size( root.get( Address_.phones ) ), 1 ) );

					entityManager.createQuery( criteria ).getResultList();
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testElementMapSizeHql(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> entityManager.createQuery( "select m from MapEntity m where size( m.localized ) > 1" ).getResultList()
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testElementMapSizeCriteria(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					final HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

					final CriteriaQuery<MapEntity> criteria = cb.createQuery( MapEntity.class );
					final Root<MapEntity> root = criteria.from( MapEntity.class);

					criteria.select( root )
							.where( cb.gt( cb.mapSize( (JpaExpression) root.get( MapEntity_.localized ) ), 1 ) );

					entityManager.createQuery( criteria ).getResultList();
				}
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testEntityMapSizeHql(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> entityManager.createQuery( "select a from Article a where size(a.translations) > 1" ).getResultList()
		);
	}

	@Test
	@TestForIssue( jiraKey = "HHH-11225" )
	public void testEntityMapSizeCriteria(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					final HibernateCriteriaBuilder cb = (HibernateCriteriaBuilder) entityManager.getCriteriaBuilder();

					final CriteriaQuery<Article> criteria = cb.createQuery( Article.class );
					final Root<Article> root = criteria.from( Article.class);

					criteria.select( root )
							.where( cb.gt( cb.mapSize( (JpaExpression) root.get( Article_.translations ) ), 1 ) );

					entityManager.createQuery( criteria ).getResultList();
				}
		);
	}
}
