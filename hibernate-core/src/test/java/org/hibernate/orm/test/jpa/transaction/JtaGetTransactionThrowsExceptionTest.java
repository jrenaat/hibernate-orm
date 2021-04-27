/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.jpa.transaction;

import javax.persistence.EntityManager;

import org.hibernate.cfg.AvailableSettings;

import org.hibernate.testing.TestForIssue;
import org.hibernate.testing.orm.junit.EntityManagerFactoryScope;
import org.hibernate.testing.orm.junit.Jpa;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Andrea Boriero
 */
@Jpa(
		integrationSettings = {
				@Setting(name = AvailableSettings.JPA_TRANSACTION_TYPE, value = "JTA"),
				@Setting(name = org.hibernate.cfg.AvailableSettings.CONNECTION_PROVIDER, value = "org.hibernate.testing.jta.JtaAwareConnectionProviderImpl"),
				@Setting( name = org.hibernate.cfg.AvailableSettings.JPA_TRANSACTION_COMPLIANCE, value = "true")
		},
		nonStringValueSettingProviders = { JtaPlatformNonStringValueSettingProvider.class }
)
public class JtaGetTransactionThrowsExceptionTest {

	@Test()
	@TestForIssue(jiraKey = "HHH-12487")
	public void onCloseEntityManagerTest(EntityManagerFactoryScope scope) {
		EntityManager em = createEntityManager( scope );
		em.close();
		assertThrows(
				IllegalStateException.class,
				() -> em.getTransaction(),
				"Calling getTransaction on a JTA entity manager should throw an IllegalStateException"
		);
	}

	@Test()
	@TestForIssue(jiraKey = "HHH-12487")
	public void onOpenEntityManagerTest(EntityManagerFactoryScope scope) {
		EntityManager em = createEntityManager( scope );
		try {
			assertThrows(
					IllegalStateException.class,
					() -> em.getTransaction(),
					"Calling getTransaction on a JTA entity manager should throw an IllegalStateException"
			);
		}
		finally {
			em.close();
		}
	}

	private EntityManager createEntityManager(EntityManagerFactoryScope scope) {
		return scope.getEntityManagerFactory().createEntityManager();
	}
}
