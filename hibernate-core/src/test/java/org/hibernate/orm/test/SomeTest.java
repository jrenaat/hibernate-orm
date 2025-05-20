/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.orm.test;


import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import org.hibernate.cfg.PersistenceSettings;
import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static jakarta.persistence.FetchType.EAGER;

/**
 * @author Jan Schatteman
 */
@DomainModel(
		annotatedClasses = {SomeTest.LangIdentifier.class, SomeTest.LangData.class, SomeTest.LangUser.class}
)
@SessionFactory
@ServiceRegistry(
		settings = {
				@Setting(name = PersistenceSettings.JAKARTA_TRANSACTION_TYPE, value = "RESOURCE_LOCAL"),
		}
)
public class SomeTest {

	@BeforeAll
	public void setup(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.persist(new LangIdentifier("A"));
					session.persist(new LangIdentifier("B"));

					session.persist( new LangData(new LangDataPk( "A", "en" ), "A_en") );
					session.persist( new LangData(new LangDataPk( "A", "fr" ), "A_fr") );
					session.persist( new LangData(new LangDataPk( "A", "es" ), "A_es") );
					session.persist( new LangData(new LangDataPk( "B", "en" ), "B_en") );
					session.persist( new LangData(new LangDataPk( "B", "fr" ), "B_fr") );
					session.persist( new LangData(new LangDataPk( "B", "es" ), "B_es") );

					session.persist( new LangUser( 1, "A" ) );
					session.persist( new LangUser( 2, "A" ) );
					session.persist( new LangUser( 3, "A" ) );
					session.persist( new LangUser( 4, "B" ) );
				}
		);
	}

	@AfterAll
	public void tearDown(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					session.createMutationQuery( "delete LangUser" ).executeUpdate();
					session.createMutationQuery( "delete LangData" ).executeUpdate();
					session.createMutationQuery( "delete LangIdentifier" ).executeUpdate();
				}
		);
	}

	@Test
	public void doTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					// Fails with "shared references to a collection" exception
					session.find( LangUser.class, 1);
					session.find( LangUser.class, 2);
				}
		);
	}

	@Entity(name = "LangIdentifier")
	public static class LangIdentifier {
		public LangIdentifier() {
		}

		public LangIdentifier(String id) {
			this.id = id;
		}

		@Id
		private String id;

		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
	}

	@Embeddable
	public static class LangDataPk {
		public LangDataPk() {
		}

		public LangDataPk(String id, String lang) {
			this.id = id;
			this.lang = lang;
		}

		private String id;
		private String lang;

		public String getId() {
			return id;
		}
		public void setId(String id) {
			this.id = id;
		}
		public String getLang() {
			return lang;
		}
		public void setLang(String lang) {
			this.lang = lang;
		}
	}

	@Entity(name = "LangData")
	public static class LangData {
		public LangData() {
		}

		public LangData(LangDataPk langDataPk, String text) {
			this.langDataPk = langDataPk;
			this.text = text;
		}

		@EmbeddedId
		private LangDataPk langDataPk;
		private String text;

		public LangDataPk getLangDataPk() {
			return langDataPk;
		}
		public void setLangDataPk(LangDataPk langDataPk) {
			this.langDataPk = langDataPk;
		}
		public String getText() {
			return text;
		}
		public void setText(String text) {
			this.text = text;
		}
	}

	@Entity(name = "LangUser")
	public static class LangUser {
		public LangUser() {
		}

		public LangUser(int id, String idLang) {
			this.id = id;
			this.idLang = idLang;
		}

		@Id
		@Column(name = "ID")
		private int id;
		@Basic(fetch = EAGER, optional = false)
		@Column(name = "ID_LANG")
		private String idLang;
		@OneToMany
		@JoinColumn(name = "ID", referencedColumnName = "ID_LANG")
		@Access(AccessType.PROPERTY)
		private Collection<LangData> textCollection;

		public int getId() {
			return id;
		}
		public void setId(int id) {
			this.id = id;
		}
		public String getIdLang() {
			return idLang;
		}
		public void setIdLang(String idLang) {
			this.idLang = idLang;
		}
		public Collection<LangData> getTextCollection() {
			return textCollection;
		}
		public void setTextCollection(final Collection<LangData> textCollection) {
			this.textCollection = textCollection;
		}
	}

}
