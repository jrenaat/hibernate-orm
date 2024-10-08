/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.boot.model.naming;

import org.hibernate.internal.util.StringHelper;

/**
 * Models an identifier (name), retrieved from the database.
 *
 * @author Andrea Boriero
 */
public class DatabaseIdentifier extends Identifier {

	/**
	 * Constructs a database identifier instance.
	 * It is assumed that <code>text</code> is unquoted.
	 *
	 * @param text The identifier text.
	 */
	protected DatabaseIdentifier(String text) {
		super( text );
	}

	public static DatabaseIdentifier toIdentifier(String text) {
		if ( StringHelper.isEmpty( text ) ) {
			return null;
		}
		else if ( isQuoted( text ) ) {
			// exclude the quotes from text
			final String unquotedtext = text.substring( 1, text.length() - 1 );
			return new DatabaseIdentifier( unquotedtext );
		}
		else {
			return new DatabaseIdentifier( text );
		}
	}
}
