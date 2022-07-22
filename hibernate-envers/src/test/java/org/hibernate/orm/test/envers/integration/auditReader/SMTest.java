/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.orm.test.envers.integration.auditReader;

import java.security.AccessControlException;
import java.security.Permission;

import org.hibernate.orm.test.envers.BaseEnversJPAFunctionalTestCase;
import org.hibernate.orm.test.envers.Priority;

import org.hibernate.testing.BeforeClassOnce;
import org.junit.Test;

import jakarta.persistence.EntityManager;


/**
 *
 */
public class SMTest extends BaseEnversJPAFunctionalTestCase {
	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class[] {AuditedTestEntity.class};
	}
	@BeforeClassOnce
	@SuppressWarnings({"UnusedDeclaration"})
	public void afterEntityManagerFactoryBuilt() {
	}

	@Test
	@Priority(10)
	public void initData() {

		EntityManager em = getEntityManager();
		em.getTransaction().begin();
		AuditedTestEntity ent1 = new AuditedTestEntity( 1, "str1" );

		em.persist( ent1 );
		em.getTransaction().commit();

		em.getTransaction().begin();

		ent1 = em.find( AuditedTestEntity.class, 1 );
		ent1.setStr1( "str1_2" );
		em.getTransaction().commit();

		System.setSecurityManager(
				new SecurityManager() {
					@Override
					public void checkPermission(Permission perm) {
						if ( perm.getName().equalsIgnoreCase( "accessDeclaredMembers" ) ) {
							throw new AccessControlException( "BOOOM" );
						}
					}
				}
		);

		//		em.getTransaction().begin();
//		ent1 = em.find( AuditedTestEntity.class, 1 );
//		em.remove( ent1 );
//		em.getTransaction().commit();
	}

	@Test
	public void testMe() {
		AuditedTestEntity ate= getAuditReader().find(AuditedTestEntity.class, 1, 1);
		ate.getId();
	}
}
