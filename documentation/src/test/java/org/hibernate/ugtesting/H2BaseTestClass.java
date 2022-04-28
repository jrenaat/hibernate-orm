/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;

import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.Setting;

/**
 * @author Jan Schatteman
 */
@ServiceRegistry(
		settings = {
				@Setting( name = AvailableSettings.URL, value = "jdbc:h2:tcp://localhost/~/test" ),
				@Setting( name = AvailableSettings.USER, value = "sa" ),
				@Setting( name = AvailableSettings.PASS, value = "sa" ),
				@Setting( name = Environment.HBM2DDL_AUTO, value = "create-drop" )
		}
)
@SessionFactory
public class H2BaseTestClass {
}
