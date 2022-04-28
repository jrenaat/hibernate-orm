/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting;

import java.sql.Types;
import java.util.BitSet;

import org.hibernate.annotations.JavaType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.userguide.mapping.basic.bitset.BitSetHelper;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactory;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;
import org.junit.jupiter.api.Test;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Convert;
import jakarta.persistence.Converter;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@DomainModel(
		annotatedClasses = {
				MyBitSetTest.Product.class,
				MyBitSetTest.Product2.class,
				MyBitSetTest.Product3.class
		}
)
public class MyBitSetTest extends H2BaseTestClass {

	@Test
	public void doBasicTest(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			Product p = new Product();
			p.setId( 1 );
			p.setBitSet( BitSet.valueOf( new byte[]{0,1,0,1,1,1} ) );
			session.persist( p );
		} );
		int i = 0;
	}

	@Test
	public void doConverterTest(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			Product2 p = new Product2();
			p.setId( 1 );
			p.setBitSet( BitSet.valueOf( new byte[]{0,1,0,1,1,1} ) );
			session.persist( p );
		} );
		int i = 0;
	}

	@Test
	public void doJavaTypeTest(SessionFactoryScope scope) {
		scope.inTransaction( session -> {
			Product3 p = new Product3();
			p.setId( 1 );
			p.setBitSet( BitSet.valueOf( new byte[]{0,1,0,1,1,1} ) );
			session.persist( p );
		} );
		int i = 0;
	}


	@Table(name = "products")
	@Entity(name = "Product")
	public static class Product {
		@Id
		private Integer id;

		private BitSet bitSet;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public BitSet getBitSet() {
			return bitSet;
		}

		public void setBitSet(BitSet bitSet) {
			this.bitSet = bitSet;
		}
	}

	@Table(name = "products2")
	@Entity(name = "Product2")
	public static class Product2 {
		@Id
		private Integer id;

		@Convert(converter = BitSetConverter.class)
		private BitSet bitSet;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public BitSet getBitSet() {
			return bitSet;
		}

		public void setBitSet(BitSet bitSet) {
			this.bitSet = bitSet;
		}
	}

	@Table(name = "products3")
	@Entity(name = "Product3")
	public static class Product3 {
		@Id
		private Integer id;

		@JavaType(value = MyBitSetJavaType.class)
		private BitSet bitSet;

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public BitSet getBitSet() {
			return bitSet;
		}

		public void setBitSet(BitSet bitSet) {
			this.bitSet = bitSet;
		}
	}

	@Converter(autoApply = true)
	public static class BitSetConverter implements AttributeConverter<BitSet,String> {
		@Override
		public String convertToDatabaseColumn(BitSet attribute) {
			return BitSetHelper.bitSetToString( attribute);
		}

		@Override
		public BitSet convertToEntityAttribute(String dbData) {
			return BitSetHelper.stringToBitSet(dbData);
		}
	}

}
