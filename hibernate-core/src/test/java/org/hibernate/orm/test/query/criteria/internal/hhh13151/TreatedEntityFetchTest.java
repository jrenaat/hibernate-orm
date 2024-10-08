/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.query.criteria.internal.hhh13151;

import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Before;
import org.junit.Test;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;

import java.util.List;

import static junit.framework.TestCase.assertTrue;

public class TreatedEntityFetchTest extends BaseCoreFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[]{
				SubEntity.class,
				SuperEntity.class,
				SideEntity.class
		};
	}

	@Override
	protected void configure(Configuration configuration) {
		super.configure( configuration );

		configuration.setProperty( AvailableSettings.SHOW_SQL, true );
		configuration.setProperty( AvailableSettings.FORMAT_SQL, true );
		// configuration.setProperty( AvailableSettings.GENERATE_STATISTICS, "true" );
	}

	@Before
	public void prepareEntities() {
		Session s = openSession();
		Transaction tx = s.beginTransaction();
		s.persist( new SubEntity().setSubField( new SideEntity( "testName" ) ) );
		tx.commit();
		s.close();
	}

	@Test
	public void hhh13151Test() throws Exception {
		Session s = openSession();

		// Prepare Query
		CriteriaBuilder cb = s.getCriteriaBuilder();
		CriteriaQuery<SuperEntity> criteria = cb.createQuery( SuperEntity.class );
		Root<SuperEntity> root = criteria.from( SuperEntity.class );
		cb.treat( root, SubEntity.class ).fetch( "subField" );

		// Execute
		Transaction tx = s.beginTransaction();
		List<SuperEntity> result = s.createQuery( criteria ).getResultList();
		tx.commit();
		s.close();

		// Check results
		SideEntity subField = ( (SubEntity) result.get( 0 ) ).getSubField();
		String name = subField.getName();
		assertTrue( name != null );
	}
}
