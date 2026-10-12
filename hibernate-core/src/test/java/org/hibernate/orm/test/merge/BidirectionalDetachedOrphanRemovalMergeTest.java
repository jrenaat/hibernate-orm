package org.hibernate.orm.test.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.JiraKey;
import org.hibernate.testing.orm.junit.Jpa;

import org.junit.jupiter.api.Test;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;

@JiraKey("HHH-20670")
@Jpa(
		annotatedClasses = {
				BidirectionalDetachedOrphanRemovalMergeTest.Parent.class,
				BidirectionalDetachedOrphanRemovalMergeTest.Child.class
		}
)
public class BidirectionalDetachedOrphanRemovalMergeTest {

	@Test
	void testPersistDetachClearMergeFlush(EntityManagerFactoryScope scope) {
		Parent parent = createParentWithChildInTx(scope);

		scope.inTransaction(entityManager -> {
			parent.getChildren().clear();
			entityManager.merge(parent);
		});

		scope.inTransaction( entityManager -> {
			Parent p = entityManager.find( Parent.class, parent.id );
			assertThat( p.children ).isEmpty();
		} );
	}

	@Test
	void testPersistDetachClearAddMergeFlush(EntityManagerFactoryScope scope) {
		Parent parent = createParentWithChildInTx(scope);

		scope.inTransaction(entityManager -> {
			parent.getChildren().clear();

			Child child1 = new Child();
			child1.setParent(parent);
			parent.getChildren().add(child1);

			Child child2 = new Child();
			child2.setParent(parent);
			parent.getChildren().add(child2);

			entityManager.merge(parent);
		});

		scope.inTransaction( entityManager -> {
			Parent p = entityManager.find( Parent.class, parent.id);
			assertThat( p.children ).hasSize(2);
		} );
	}

	private Parent createParentWithChildInTx(EntityManagerFactoryScope scope) {
		return scope.fromTransaction(entityManager -> {
			Parent parent = new Parent();

			Child child = new Child();
			child.setParent(parent);
			parent.getChildren().add(child);

			entityManager.persist(parent);

			return parent;
		});
	}

	@Entity(name = "Parent")
	public static class Parent {

		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private Long id;

		@OneToMany(
				mappedBy = "parent",
				cascade = CascadeType.ALL,
				orphanRemoval = true
		)
		private List<Child> children = new ArrayList<>();

		public Long getId() {
			return id;
		}

		public List<Child> getChildren() {
			return children;
		}
	}

	@Entity(name = "Child")
	public static class Child {

		@Id
		@GeneratedValue(strategy = GenerationType.AUTO)
		private Long id;

		@ManyToOne
		private Parent parent;

		public Long getId() {
			return id;
		}

		public Parent getParent() {
			return parent;
		}

		public void setParent(Parent parent) {
			this.parent = parent;
		}
	}
}
