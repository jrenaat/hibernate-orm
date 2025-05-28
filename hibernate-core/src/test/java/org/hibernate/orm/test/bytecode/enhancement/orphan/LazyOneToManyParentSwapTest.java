/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test.bytecode.enhancement.orphan;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@JiraKey("")
@Jpa(
		annotatedClasses = {
				LazyOneToManyParentSwapTest.Parent.class,
				LazyOneToManyParentSwapTest.Child.class
		},
		integrationSettings = {
				@Setting(name = AvailableSettings.URL, value = "jdbc:h2:tcp://localhost/~/Dev/h2/db/v2/test"),
				@Setting(name = AvailableSettings.USER, value = "sa"),
				@Setting(name = AvailableSettings.PASS, value = "sa"),
		}
)
public class LazyOneToManyParentSwapTest {

	@BeforeEach
	public void setup(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				em -> {
					Child c = new Child( 1L, "Child 1" );
					em.persist( c );

					Parent pa = new Parent( 1L, "Parent A" );
					pa.addChild( c );
					em.persist( pa );

					Parent pb = new Parent( 2L, "Parent B" );
					em.persist( pb );
				}
		);
	}

	@AfterEach
	public void tearDown(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				em -> {
					em.createQuery( "delete from Child" ).executeUpdate();
					em.createQuery( "delete from Parent" ).executeUpdate();
				}
		);
	}

	@Test
	public void testCollectionPersistQueryJoinFetch(EntityManagerFactoryScope scope) {
		scope.inTransaction(
				em -> {
					Parent pa = em.find( Parent.class, 1L );
					Parent pb = em.find( Parent.class, 2L );
					Child c = em.find(Child.class, 1L);
					pa.getChildren().remove( c );
					pb.addChild( c );
				}
		);

		scope.inTransaction(
				em -> {
					Parent pa = em.find( Parent.class, 1L );
					Parent pb = em.find( Parent.class, 2L );
					Child c = em.find(Child.class, 1L);
					pb.getChildren().remove( c );
					pa.addChild( c );
				}
		);

		scope.inTransaction(
				em -> {
					Parent pa = em.find( Parent.class, 1L );
					Parent pb = em.find( Parent.class, 2L );
					Child c = em.find(Child.class, 1L);
					int i = 0;
				}
		);
	}

	@Entity(name = "Child")
	public static class Child {

		@Id
		private Long id;

//		@ManyToOne
//		private Parent parent;

		private String name;

		public Child() {
		}

		public Child(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public String getName() {
			return name;
		}

//		public void setParent(Parent parent) {
//			this.parent = parent;
//		}

//		@Override
//		public boolean equals(Object o) {
//			if (this == o) {
//				return true;
//			}
//			if (o == null || getClass() != o.getClass()) {
//				return false;
//			}
//
//			Child c = (Child) o;
//			return Objects.equals(id, c.id);
//		}
//
//		@Override
//		public int hashCode() {
//			return Objects.hash(id, name);
//		}

	}

	@Entity(name = "Parent")
	public static class Parent {

		@Id
		private Long id;

		private String name;

		@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true/*, mappedBy = "parent"*/)
		private List<Child> children;

		public Parent() {
		}

		public Parent(Long id, String name) {
			this.id = id;
			this.name = name;
		}

		public Long getId() {
			return id;
		}

		public List<Child> getChildren() {
			return children;
		}

		public void addChild(Child c) {
			if ( children == null ) {
				children = new ArrayList<>();
			}
			children.add( c );
//			c.setParent( this );
		}

//		@Override
//		public boolean equals(Object o) {
//			if (this == o) {
//				return true;
//			}
//			if (o == null || getClass() != o.getClass()) {
//				return false;
//			}
//
//			Parent p = (Parent) o;
//			return Objects.equals(id, p.id);
//		}
//
//		@Override
//		public int hashCode() {
//			return Objects.hash(id, name);
//		}

	}

}
