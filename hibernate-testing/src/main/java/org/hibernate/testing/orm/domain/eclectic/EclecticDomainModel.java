/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.testing.orm.domain.eclectic;

import org.hibernate.testing.orm.domain.AbstractDomainModelDescriptor;

/**
 * @author Jan Schatteman
 */
public class EclecticDomainModel extends AbstractDomainModelDescriptor {
	public static final EclecticDomainModel INSTANCE = new EclecticDomainModel();

	public EclecticDomainModel() {
		super(
				Address.class,
				Alias.class,
				Country.class,
				CreditCard.class,
				Customer.class,
				Entity1.class,
				Entity2.class,
				Entity3.class,
				Info.class,
				LineItem.class,
				Order.class,
				Phone.class,
				Product.class,
				ShelfLife.class,
				Spouse.class,
				Thing.class,
				ThingWithQuantity.class,
				VersionedEntity.class
		);
	}
}
