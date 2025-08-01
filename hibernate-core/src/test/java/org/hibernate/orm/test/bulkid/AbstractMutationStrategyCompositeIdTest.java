/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.bulkid;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableInsertStrategy;
import org.hibernate.query.sqm.mutation.spi.SqmMultiTableMutationStrategy;

import org.hibernate.testing.junit4.BaseCoreFunctionalTestCase;
import org.junit.Before;
import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInHibernate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public abstract class AbstractMutationStrategyCompositeIdTest extends BaseCoreFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
				Person.class,
				Doctor.class,
				Engineer.class
		};
	}

	@Override
	protected void configure(Configuration configuration) {
		super.configure( configuration );
		Class<? extends SqmMultiTableMutationStrategy> mutationStrategyClass = getMultiTableMutationStrategyClass();
		if ( mutationStrategyClass != null ) {
			configuration.setProperty( AvailableSettings.QUERY_MULTI_TABLE_MUTATION_STRATEGY, mutationStrategyClass );
		}
		Class<? extends SqmMultiTableInsertStrategy> insertStrategyClass = getMultiTableInsertStrategyClass();
		if ( insertStrategyClass != null ) {
			configuration.setProperty( AvailableSettings.QUERY_MULTI_TABLE_INSERT_STRATEGY, insertStrategyClass );
		}
	}

	protected abstract Class<? extends SqmMultiTableMutationStrategy> getMultiTableMutationStrategyClass();

	protected abstract Class<? extends SqmMultiTableInsertStrategy> getMultiTableInsertStrategyClass();

	@Override
	protected boolean isCleanupTestDataRequired() {
		return true;
	}

	@Override
	protected boolean isCleanupTestDataUsingBulkDelete() {
		return true;
	}

	@Before
	public void setUp() {
		doInHibernate( this::sessionFactory, session -> {
			for ( int i = 0; i < entityCount(); i++ ) {
				Doctor doctor = new Doctor();
				doctor.setId( i + 1 );
				doctor.setCompanyName( "Red Hat USA" );
				doctor.setEmployed( ( i % 2 ) == 0 );
				session.persist( doctor );
			}

			for ( int i = 0; i < entityCount(); i++ ) {
				Engineer engineer = new Engineer();
				engineer.setId( i + 1 + entityCount() );
				engineer.setCompanyName( "Red Hat Europe" );
				engineer.setEmployed( ( i % 2 ) == 0 );
				engineer.setFellow( ( i % 2 ) == 1 );
				session.persist( engineer );
			}
		});
	}

	protected int entityCount() {
		return 4;
	}

	@Test
	public void testUpdate() {
		doInHibernate( this::sessionFactory, session -> {
			int updateCount = session.createQuery( "update Person set name = :name where employed = :employed" )
					.setParameter( "name", "John Doe" )
					.setParameter( "employed", true )
					.executeUpdate();

			assertEquals(entityCount(), updateCount);
		});
	}

	@Test
	public void testDeleteFromPerson() {
		doInHibernate( this::sessionFactory, session -> {
			//tag::batch-bulk-hql-temp-table-delete-query-example[]
			int updateCount = session.createQuery(
				"delete from Person where employed = :employed" )
			.setParameter( "employed", false )
			.executeUpdate();
			//end::batch-bulk-hql-temp-table-delete-query-example[]
			assertEquals( entityCount(), updateCount );
		});
	}

	@Test
	public void testDeleteFromEngineer() {
		doInHibernate( this::sessionFactory, session -> {
			int updateCount = session.createQuery( "delete from Engineer where fellow = :fellow" )
					.setParameter( "fellow", true )
					.executeUpdate();
			assertEquals( entityCount() / 2, updateCount );
		});
	}

	@Test
	public void testInsert() {
		doInHibernate( this::sessionFactory, session -> {
			session.createQuery( "insert into Engineer(id, companyName, name, employed, fellow) values (0, 'Red Hat', :name, :employed, false)" )
					.setParameter( "name", "John Doe" )
					.setParameter( "employed", true )
					.executeUpdate();
			final Engineer engineer = session.find( Engineer.class,
					new AbstractMutationStrategyCompositeIdTest_.Person_.Id( 0, "Red Hat" ) );
			assertEquals( "John Doe", engineer.getName() );
			assertTrue( engineer.isEmployed() );
			assertFalse( engineer.isFellow() );
		});
	}

	@Test
	public void testInsertSelect() {
		doInHibernate( this::sessionFactory, session -> {
			final int insertCount = session.createQuery( "insert into Engineer(id, companyName, name, employed, fellow) "
										+ "select d.id + " + (entityCount() * 2) + ", 'Red Hat', 'John Doe', true, false from Doctor d" )
					.executeUpdate();
			final Engineer engineer = session.find( Engineer.class,
					new AbstractMutationStrategyCompositeIdTest_.Person_.Id( entityCount() * 2 + 1, "Red Hat" ) );
			assertEquals( entityCount(), insertCount );
			assertEquals( "John Doe", engineer.getName() );
			assertTrue( engineer.isEmployed() );
			assertFalse( engineer.isFellow() );
		});
	}

	//tag::batch-bulk-hql-temp-table-base-class-example[]
	@Entity(name = "Person")
	@Inheritance(strategy = InheritanceType.JOINED)
	public static class Person implements Serializable {

		@Id
		private Integer id;

		@Id
		private String companyName;

		private String name;

		private boolean employed;

		//Getters and setters are omitted for brevity

	//end::batch-bulk-hql-temp-table-base-class-example[]

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getCompanyName() {
			return companyName;
		}

		public void setCompanyName(String companyName) {
			this.companyName = companyName;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public boolean isEmployed() {
			return employed;
		}

		public void setEmployed(boolean employed) {
			this.employed = employed;
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( !( o instanceof Person ) ) {
				return false;
			}
			Person person = (Person) o;
			return Objects.equals( getId(), person.getId() ) &&
					Objects.equals( getCompanyName(), person.getCompanyName() );
		}

		@Override
		public int hashCode() {
			return Objects.hash( getId(), getCompanyName() );
		}
	//tag::batch-bulk-hql-temp-table-base-class-example[]
	}
	//end::batch-bulk-hql-temp-table-base-class-example[]

	//tag::batch-bulk-hql-temp-table-sub-classes-example[]
	@Entity(name = "Doctor")
	public static class Doctor extends Person {
	}

	@Entity(name = "Engineer")
	public static class Engineer extends Person {

		private boolean fellow;

		//Getters and setters are omitted for brevity

	//end::batch-bulk-hql-temp-table-sub-classes-example[]

		public boolean isFellow() {
			return fellow;
		}

		public void setFellow(boolean fellow) {
			this.fellow = fellow;
		}
	//tag::batch-bulk-hql-temp-table-sub-classes-example[]
	}
	//end::batch-bulk-hql-temp-table-sub-classes-example[]
}
