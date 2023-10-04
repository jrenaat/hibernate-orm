/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.orm.test.aa;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;

import org.junit.jupiter.api.Test;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Basic;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;

import static java.util.Collections.emptyList;

/**
 * @author Jan Schatteman
 */
@DomainModel(
		annotatedClasses = {
			DummyTest.Basket.class, DummyTest.Watermelon.class, DummyTest.Kiwi.class
		}
)
@SessionFactory
public class DummyTest {

	@Test
	public void test(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
					Watermelon watermelon = new Watermelon();
					watermelon.setPurchasePrice( 10.0 );
					watermelon.setPurchaseTimestamp( LocalDateTime.now() );

					Kiwi kiwi1 = new Kiwi();
					kiwi1.setPurchasePrice( 1.0 );
					kiwi1.setPurchaseTimestamp( LocalDateTime.now() );

					Kiwi kiwi2 = new Kiwi();
					kiwi2.setPurchasePrice( 1.0 );
					kiwi2.setPurchaseTimestamp( LocalDateTime.now().plusMinutes( 1 ) );

					List<Kiwi> kiwis = new ArrayList<>( 2 );
					kiwis.add( kiwi1 );
					kiwis.add( kiwi2 );

					Basket basket = new Basket();
					basket.setBrandName( "Tote" );
					basket.setWatermelon( watermelon );
					basket.setKiwis( kiwis );

					session.persist( basket );
				}
		);


		scope.inTransaction(
				session -> {
					// Accessing the data
					List<Basket> persistedBaskets = session.createQuery("FROM Basket", Basket.class).getResultList(); // Basket persistedBasket = entityManager.find(Basket.class, basket.getId());
					System.out.println("Persisted Baskets: " + persistedBaskets);
					Basket persistedBasket = persistedBaskets.get(0);
					List<Kiwi> persistedKiwis = persistedBasket.getKiwis();
					Watermelon persistedWatermelon = persistedBasket.getWatermelon();

					System.out.println(persistedBasket);

					System.out.println(persistedWatermelon);

					for (Kiwi persistedKiwi : persistedKiwis) {
						System.out.println(persistedKiwi);
					}
				}
		);
	}

	@Entity(name = "Watermelon")
	public static class Watermelon {
		@Id
		@GeneratedValue(strategy = GenerationType.UUID)
		private String watermelonUuid;

		private double purchasePrice;

		@Basic
		private LocalDateTime purchaseTimestamp;

		@OneToOne
		@JoinColumn(name="basketUuid")
		private Basket basket;

		public Watermelon() {
		}

		@Override
		public String toString() {
			return "Watermelon{" +
					"watermelonUuid='" + watermelonUuid + '\'' +
					", purchasePrice=" + purchasePrice +
					", purchaseTimestamp=" + purchaseTimestamp +
					", basket=" + basket +
					'}';
		}

		public String getWatermelonUuid() {
			return watermelonUuid;
		}

		public void setWatermelonUuid(String uuid) {
			this.watermelonUuid = uuid;
		}

		public double getPurchasePrice() {
			return purchasePrice;
		}

		public void setPurchasePrice(double purchasePrice) {
			this.purchasePrice = purchasePrice;
		}

		public LocalDateTime getPurchaseTimestamp() {
			return purchaseTimestamp;
		}

		public void setPurchaseTimestamp(LocalDateTime purchaseTimestamp) {
			this.purchaseTimestamp = purchaseTimestamp;
		}
	}

	@Entity(name = "Kiwi")
	public static class Kiwi {
		@Id
		@GeneratedValue(strategy = GenerationType.UUID)
		private String kiwiUuid;

		private double purchasePrice;

		private LocalDateTime purchaseTimestamp;

		@ManyToOne
		@JoinColumn(name="basketUuid")
		private Basket basket;

		public Kiwi() {
		}

		@Override
		public String toString() {
			return "Kiwi{" +
					"kiwiUuid='" + kiwiUuid + '\'' +
					", purchasePrice=" + purchasePrice +
					", purchaseTimestamp=" + purchaseTimestamp +
					", basket=" + basket +
					'}';
		}

		public String getKiwiUuid() {
			return kiwiUuid;
		}

		public void setKiwiUuid(String kiwiUuid) {
			this.kiwiUuid = kiwiUuid;
		}

		public double getPurchasePrice() {
			return purchasePrice;
		}

		public void setPurchasePrice(double purchasePrice) {
			this.purchasePrice = purchasePrice;
		}

		public LocalDateTime getPurchaseTimestamp() {
			return purchaseTimestamp;
		}

		public void setPurchaseTimestamp(LocalDateTime purchaseTimestamp) {
			this.purchaseTimestamp = purchaseTimestamp;
		}

		public Basket getBasket() {
			return basket;
		}

		public void setBasket(Basket basket) {
			this.basket = basket;
		}
	}

	@Entity(name = "Basket")
	public static class Basket implements Serializable {

		@Id
		@GeneratedValue(strategy = GenerationType.UUID)
		@Column(name="basketUuid")
		private String basketUuid;

		private String brandName;

//		@Convert(converter = WatermelonConverter.class)
		@OneToOne(mappedBy="basket", cascade= CascadeType.ALL)
		private Watermelon watermelon;

//		@Convert(converter = KiwiConverter.class)
//		@Convert(converter = StringListConverter.class)
		@OneToMany(mappedBy="basket", cascade=CascadeType.ALL)
		private List<Kiwi> kiwis;

		public Basket() {
		}

		@Override
		public String toString() {
			return "Basket{" +
					"basketUuid='" + basketUuid + '\'' +
					", brandName='" + brandName + '\'' +
					", watermelon=" + watermelon +
					", kiwis=" + kiwis +
					'}';
		}

		public String getBasketUuid() {
			return basketUuid;
		}

		public void setBasketUuid(String basketUuid) {
			this.basketUuid = basketUuid;
		}

		public String getBrandName() {
			return brandName;
		}

		public void setBrandName(String brandName) {
			this.brandName = brandName;
		}

		public Watermelon getWatermelon() {
			return watermelon;
		}

		public void setWatermelon(Watermelon watermelon) {
			this.watermelon = watermelon;
		}

		public List<Kiwi> getKiwis() {
			return kiwis;
		}

		public void setKiwis(List<Kiwi> kiwis) {
			this.kiwis = kiwis;
		}
	}

	@Converter
	public static class WatermelonConverter implements AttributeConverter<Watermelon,String> {

		private static final String SEPARATOR = ";";

		@Override
		public String convertToDatabaseColumn(Watermelon watermelon) {
			StringBuilder sb = new StringBuilder();

			sb.append( watermelon.getWatermelonUuid() );
			sb.append( SEPARATOR );
			sb.append( watermelon.getPurchasePrice());
			sb.append( SEPARATOR );
			sb.append( watermelon.getPurchaseTimestamp());

			return sb.toString();
		}

		@Override
		public Watermelon convertToEntityAttribute(String dbWatermelon) {
			String[] parts = dbWatermelon.split(SEPARATOR);

			Watermelon watermelon = new Watermelon();

			int i = 0;
			watermelon.setWatermelonUuid( parts[i++] );
			watermelon.setPurchasePrice(Double.parseDouble(parts[i++]) );
			watermelon.setPurchaseTimestamp(LocalDateTime.parse(parts[i++]));

			return watermelon;
		}
	}
	@Converter
	public static class StringListConverter implements AttributeConverter<List<String>, String> {
		private static final String DELIMITER = ";";

		@Override
		public String convertToDatabaseColumn(List<String> stringList) {
			return stringList != null ? String.join(DELIMITER, stringList) : "";
		}

		@Override
		public List<String> convertToEntityAttribute(String string) {
			return string != null ? Arrays.asList( string.split( DELIMITER)) : emptyList();
		}
	}

	@Converter
	public static class KiwiConverter implements AttributeConverter<Kiwi,String> {

		private static final String SEPARATOR = ";";

		@Override
		public String convertToDatabaseColumn(Kiwi kiwi) {
			StringBuilder sb = new StringBuilder();

			sb.append( kiwi.getKiwiUuid() );
			sb.append( SEPARATOR );
			sb.append( kiwi.getPurchasePrice());
			sb.append( SEPARATOR );
			sb.append( kiwi.getPurchaseTimestamp());

			return sb.toString();
		}

		@Override
		public Kiwi convertToEntityAttribute(String dbKiwi) {
			String[] parts = dbKiwi.split(SEPARATOR);

			Kiwi kiwi = new Kiwi();

			int i = 0;
			kiwi.setKiwiUuid( parts[i++] );
			kiwi.setPurchasePrice( Double.parseDouble(parts[i++]) );
			kiwi.setPurchaseTimestamp( LocalDateTime.parse(parts[i++]) );
        /*Basket basket = new Basket(basketUuid, brandName, watermelon, kiwis);
        kiwi.setBasket(basket);*/
			return kiwi;
		}
	}

}
