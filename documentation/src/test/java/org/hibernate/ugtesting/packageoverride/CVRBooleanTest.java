/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting.packageoverride;

import org.hibernate.ugtesting.H2BaseTestClass;

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
				CVRBooleanTest.A.class
		},
		annotatedPackageNames = "org.hibernate.ugtesting.packageoverride"
)
// with ConverterRegistration in package-info.java
public class CVRBooleanTest extends H2BaseTestClass {

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

	@Entity(name = "A")
	public static class A {
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
