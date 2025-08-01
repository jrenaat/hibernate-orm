/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.compositefk;

import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.testing.jdbc.SQLStatementInspector;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;

/**
 * @author Andrea Boriero
 */
@DomainModel(
		annotatedClasses = {
				EagerManyToOneEmbeddedIdFKTest.System.class,
				EagerManyToOneEmbeddedIdFKTest.SystemUser.class
		}
)
@SessionFactory(useCollectingStatementInspector = true)
public class EagerManyToOneEmbeddedIdFKTest {

	@BeforeEach
	public void setUp(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					PK userKey = new PK( 1, "Fab" );
					SystemUser user = new SystemUser( userKey, "Fab" );

					System system = new System( 1, "sub1" );
					system.setUser( user );

					session.persist( user );
					session.persist( system );
				}
		);
	}

	@AfterEach
	public void tearDown(SessionFactoryScope scope) {
		scope.getSessionFactory().getSchemaManager().truncate();
	}

	@Test
	public void testGet(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = scope.getCollectingStatementInspector();
		statementInspector.clear();
		scope.inTransaction(
				session -> {
					System system = session.get( System.class, 1 );
					assertThat( system, is( notNullValue() ) );
					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
					statementInspector.assertExecutedCount( 1 );
					statementInspector.assertNumberOfOccurrenceInQuery( 0, "join", 1 );
				}
		);
	}

	@Test
	public void testHql(SessionFactoryScope scope) {
		SQLStatementInspector statementInspector = scope.getCollectingStatementInspector();
		statementInspector.clear();
		scope.inTransaction(
				session -> {
					System system = (System) session.createQuery( "from System e where e.id = :id" )
							.setParameter( "id", 1 ).uniqueResult();

					assertThat( system, is( notNullValue() ) );
					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
					statementInspector.assertExecutedCount( 2 );
				}
		);
	}

	@Test
	public void testHqlJoin(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					System system = session.createQuery( "from System e join e.user where e.id = :id", System.class )
							.setParameter( "id", 1 ).uniqueResult();
					assertThat( system, is( notNullValue() ) );
					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
				}
		);
	}

	@Test
	public void testHqlJoinFetch(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					System system = session.createQuery(
							"from System e join fetch e.user where e.id = :id",
							System.class
					)
							.setParameter( "id", 1 ).uniqueResult();
					assertThat( system, is( notNullValue() ) );
					SystemUser user = system.getUser();
					assertThat( user, is( notNullValue() ) );
				}
		);
	}

	@Test
	public void testEmbeddedIdParameter(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					PK superUserKey = new PK( 1, "Fab" );

					System system = session.createQuery(
							"from System e join fetch e.user u where u.id = :id",
							System.class
					).setParameter( "id", superUserKey ).uniqueResult();

					assertThat( system, is( notNullValue() ) );
				}
		);
	}


	@Entity(name = "System")
	@Table( name = "systems" )
	public static class System {
		@Id
		private Integer id;
		private String name;

		@ManyToOne
		SystemUser user;

		public System() {
		}

		public System(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public SystemUser getUser() {
			return user;
		}

		public void setUser(SystemUser user) {
			this.user = user;
		}
	}

	@Entity(name = "SystemUser")
	public static class SystemUser {

		@EmbeddedId
		private PK pk;

		private String name;

		public SystemUser() {
		}

		public SystemUser(PK pk, String name) {
			this.pk = pk;
			this.name = name;
		}

		public PK getPk() {
			return pk;
		}

		public void setPk(PK pk) {
			this.pk = pk;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}
	}

	@Embeddable
	public static class PK implements Serializable {

		private Integer subsystem;

		private String username;

		public PK(Integer subsystem, String username) {
			this.subsystem = subsystem;
			this.username = username;
		}

		private PK() {
		}

		@Override
		public boolean equals(Object o) {
			if ( this == o ) {
				return true;
			}
			if ( o == null || getClass() != o.getClass() ) {
				return false;
			}
			PK pk = (PK) o;
			return Objects.equals( subsystem, pk.subsystem ) &&
					Objects.equals( username, pk.username );
		}

		@Override
		public int hashCode() {
			return Objects.hash( subsystem, username );
		}
	}
}
