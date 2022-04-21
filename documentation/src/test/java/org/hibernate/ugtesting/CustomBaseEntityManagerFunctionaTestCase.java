/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting;

import java.util.Map;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.orm.test.jpa.BaseEntityManagerFunctionalTestCase;

/**
 * @author Jan Schatteman
 */
public class CustomBaseEntityManagerFunctionaTestCase extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Map buildSettings() {
		Map settings = super.buildSettings();
		settings.put( AvailableSettings.DRIVER, "org.h2.Driver" );
		settings.put( AvailableSettings.URL, "jdbc:h2:tcp://localhost/~/test;LOCK_TIMEOUT=10000" );
		settings.put( AvailableSettings.USER, "sa" );
		settings.put( AvailableSettings.PASS, "sa" );
		settings.put( AvailableSettings.HBM2DDL_AUTO, "drop-and-create" );
		return settings;
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return super.getAnnotatedClasses();
	}
}
