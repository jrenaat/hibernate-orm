/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.jpa.test.factory.puUtil;

import java.io.Serializable;

import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Steve Ebersole
 */
@Jpa(annotatedClasses = {
		LegacyEntity.class,
		ModernEntity.class,
		NestedLegacyEntity.class
})
public class GetIdentifierTest {

	@AfterEach
	public void tearDown(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					entityManager.createQuery( "delete from ModernEntity" ).executeUpdate();
				}
		);
	}

	@Test
	public void getIdentifierTest(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				entityManager -> {
					Serializable simpleEntityId = (Serializable) entityManager.getEntityManagerFactory()
							.getPersistenceUnitUtil().getIdentifier( createExisitingNestedLegacyEntity() );
				}
		);
	}

	@Test
	public void getIdentifierOfNonEntityTest(EntityManagerFactoryScope scope) {
		try {
			scope.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier( this );
			fail( "should have thrown IllegalArgumentException" );
		}
		catch (IllegalArgumentException ex) {
			// expected
		}
	}

	@Test
	public void getIdentifierOfNullTest(EntityManagerFactoryScope scope) {
		try {
			scope.getEntityManagerFactory().getPersistenceUnitUtil().getIdentifier( null );
			fail( "should have thrown IllegalArgumentException" );
		}
		catch (IllegalArgumentException ex) {
			// expected
		}
	}

	private NestedLegacyEntity createExisitingNestedLegacyEntity() {

		ModernEntity modernEntity = new ModernEntity();
		modernEntity.setFoo( 2 );

		LegacyEntity legacyEntity = new LegacyEntity();
		legacyEntity.setPrimitivePk1( 1 );
		legacyEntity.setPrimitivePk2( 2 );
		legacyEntity.setFoo( "Foo" );

		NestedLegacyEntity nestedLegacyEntity = new NestedLegacyEntity();
		nestedLegacyEntity.setModernEntity( modernEntity );
		nestedLegacyEntity.setLegacyEntity( legacyEntity );

		return nestedLegacyEntity;
	}
}
