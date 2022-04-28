/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * @author Jan Schatteman
 */

@Converter(autoApply = true)
public class AutoYesNoConverter implements AttributeConverter<Boolean, Character> {
	/**
	 * Singleton access
	 */
	public static final AutoYesNoConverter INSTANCE = new AutoYesNoConverter();

	@Override
	public Character convertToDatabaseColumn(Boolean attribute) {
		return toRelationalValue( attribute );
	}

	@Override
	public Boolean convertToEntityAttribute(Character dbData) {
		return toDomainValue( dbData );
	}

	public Boolean toDomainValue(Character relationalForm) {
		if ( relationalForm == null ) {
			return null;
		}

		switch ( relationalForm ) {
			case 'Y':
				return true;
			case 'N':
				return false;
		}

		return null;
	}

	public Character toRelationalValue(Boolean domainForm) {
		if ( domainForm == null ) {
			return null;
		}

		return domainForm ? 'Y' : 'N';
	}
}
