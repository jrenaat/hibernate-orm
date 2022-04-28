/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting;

import java.sql.Types;

import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.JdbcTypeRegistration;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.jdbc.SmallIntJdbcType;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactoryScope;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * @author Jan Schatteman
 */
@DomainModel(
		annotatedClasses = {
				BooleanTest.A.class,
				BooleanTest.B.class
		}
)
public class BooleanTest extends H2BaseTestClass {

	@Test
	public void localBooleanTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					A a = new A();
					a.setId( 1 );
					a.setA( false );
					a.setB( true );
					a.setC( false );
					session.persist( a );
				}
		);
		int i = 0;
	}

	@Test
	public void globalBooleanTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					B b = new B();
					b.setId( 1 );
					b.setA( false );
					b.setB( true );
					b.setC( false );
					session.persist( b );
				}
		);
		int i = 0;
	}

	@Entity(name = "A")
	public static class A {
		@Id
		private Integer id;

		private Boolean a;

		@JdbcTypeCode(SqlTypes.CHAR)
		private Boolean b;

		@JdbcType( SmallIntJdbcType.class )
		private Boolean c;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Boolean getA() {
			return a;
		}

		public void setA(Boolean a) {
			this.a = a;
		}

		public Boolean getB() {
			return b;
		}

		public void setB(Boolean b) {
			this.b = b;
		}

		public Boolean getC() {
			return c;
		}

		public void setC(Boolean c) {
			this.c = c;
		}
	}

	@Entity(name = "B")
//	@JdbcTypeRegistration(value = SmallIntJdbcType.class, registrationCode = Types.BOOLEAN)
	public static class B {
		@Id
		private Integer id;

		private Boolean a;

		private Boolean b;

		private Boolean c;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Boolean getA() {
			return a;
		}

		public void setA(Boolean a) {
			this.a = a;
		}

		public Boolean getB() {
			return b;
		}

		public void setB(Boolean b) {
			this.b = b;
		}

		public Boolean getC() {
			return c;
		}

		public void setC(Boolean c) {
			this.c = c;
		}
	}

}
