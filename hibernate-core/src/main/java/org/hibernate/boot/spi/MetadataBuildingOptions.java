/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.boot.spi;

import java.util.List;

import jakarta.persistence.SharedCacheMode;

import org.hibernate.TimeZoneStorageStrategy;
import org.hibernate.boot.model.IdGeneratorStrategyInterpreter;
import org.hibernate.boot.model.naming.ImplicitNamingStrategy;
import org.hibernate.boot.model.naming.PhysicalNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cfg.MetadataSourceType;
import org.hibernate.collection.internal.StandardCollectionSemanticsResolver;
import org.hibernate.collection.spi.CollectionSemanticsResolver;
import org.hibernate.id.factory.IdentifierGeneratorFactory;
import org.hibernate.metamodel.internal.ManagedTypeRepresentationResolverStandard;
import org.hibernate.metamodel.spi.ManagedTypeRepresentationResolver;
import org.hibernate.type.spi.TypeConfiguration;

/**
 * Describes the options used while building the Metadata object (during
 * {@link org.hibernate.boot.MetadataBuilder#build()} processing).
 *
 * @author Steve Ebersole
 *
 * @since 5.0
 */
public interface MetadataBuildingOptions {
	/**
	 * Access to the service registry.
	 *
	 * @return The service registry
	 */
	StandardServiceRegistry getServiceRegistry();

	/**
	 * Access to the mapping defaults.
	 *
	 * @return The mapping defaults
	 */
	MappingDefaults getMappingDefaults();

	IdentifierGeneratorFactory getIdentifierGeneratorFactory();

	TimeZoneStorageStrategy getDefaultTimeZoneStorage();

	default ManagedTypeRepresentationResolver getManagedTypeRepresentationResolver() {
		// for now always return the standard one
		return ManagedTypeRepresentationResolverStandard.INSTANCE;
	}

	default CollectionSemanticsResolver getPersistentCollectionRepresentationResolver() {
		// for now always return the standard one
		return StandardCollectionSemanticsResolver.INSTANCE;
	}

	/**
	 * Access the list of BasicType registrations.  These are the BasicTypes explicitly
	 * registered via calls to:<ul>
	 *     <li>{@link org.hibernate.boot.MetadataBuilder#applyBasicType(org.hibernate.type.BasicType)}</li>
	 *     <li>{@link org.hibernate.boot.MetadataBuilder#applyBasicType(org.hibernate.type.BasicType, String[])}</li>
	 *     <li>{@link org.hibernate.boot.MetadataBuilder#applyBasicType(org.hibernate.usertype.UserType, String[])}</li>
	 * </ul>
	 *
	 * @return The BasicType registrations
	 */
	List<BasicTypeRegistration> getBasicTypeRegistrations();

	ImplicitNamingStrategy getImplicitNamingStrategy();

	PhysicalNamingStrategy getPhysicalNamingStrategy();

	/**
	 * Access to the SharedCacheMode for determining whether we should perform second level
	 * caching or not.
	 *
	 * @return The SharedCacheMode
	 */
	SharedCacheMode getSharedCacheMode();

	/**
	 * Access to any implicit cache AccessType.
	 *
	 * @return The implicit cache AccessType
	 */
	AccessType getImplicitCacheAccessType();

	/**
	 * Access to the MultiTenancyStrategy for this environment.
	 *
	 * @return The MultiTenancyStrategy
	 */
	boolean isMultiTenancyEnabled();

	IdGeneratorStrategyInterpreter getIdGenerationTypeInterpreter();

	TypeConfiguration getTypeConfiguration();

	/**
	 * Whether explicit discriminator declarations should be ignored for joined
	 * subclass style inheritance.
	 *
	 * @return {@code true} indicates they should be ignored; {@code false}
	 * indicates they should not be ignored.
	 *
	 * @see org.hibernate.boot.MetadataBuilder#enableExplicitDiscriminatorsForJoinedSubclassSupport
	 * @see org.hibernate.cfg.AvailableSettings#IGNORE_EXPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS
	 */
	boolean ignoreExplicitDiscriminatorsForJoinedInheritance();

	/**
	 * Whether we should do discrimination implicitly joined subclass style inheritance when no
	 * discriminator info is provided.
	 *
	 * @return {@code true} indicates we should do discrimination; {@code false} we should not.
	 *
	 * @see org.hibernate.boot.MetadataBuilder#enableImplicitDiscriminatorsForJoinedSubclassSupport
	 * @see org.hibernate.cfg.AvailableSettings#IMPLICIT_DISCRIMINATOR_COLUMNS_FOR_JOINED_SUBCLASS
	 */
	boolean createImplicitDiscriminatorsForJoinedInheritance();

	/**
	 * Whether we should implicitly force discriminators into SQL selects.  By default,
	 * Hibernate will not.  This can be specified per discriminator in the mapping as well.
	 *
	 * @return {@code true} indicates we should force the discriminator in selects for any mappings
	 * which do not say explicitly.
	 *
	 * @see org.hibernate.cfg.AvailableSettings#FORCE_DISCRIMINATOR_IN_SELECTS_BY_DEFAULT
	 */
	boolean shouldImplicitlyForceDiscriminatorInSelect();

	/**
	 * Should we use nationalized variants of character data (e.g. NVARCHAR rather than VARCHAR)
	 * by default?
	 *
	 * @see org.hibernate.boot.MetadataBuilder#enableGlobalNationalizedCharacterDataSupport
	 * @see org.hibernate.cfg.AvailableSettings#USE_NATIONALIZED_CHARACTER_DATA
	 *
	 * @return {@code true} if nationalized character data should be used by default; {@code false} otherwise.
	 */
	boolean useNationalizedCharacterData();

	boolean isSpecjProprietarySyntaxEnabled();

	/**
	 * Should we create constraint by default?
	 *
	 * @see jakarta.persistence.ConstraintMode#PROVIDER_DEFAULT
	 * @see org.hibernate.cfg.AvailableSettings#HBM2DDL_DEFAULT_CONSTRAINT_MODE
	 *
	 * @return {@code true} if not create constraint by default; {@code false} otherwise.
	 */
	boolean isNoConstraintByDefault();

	/**
	 * Retrieve the ordering in which sources should be processed.
	 *
	 * @return The order in which sources should be processed.
	 */
	List<MetadataSourceType> getSourceProcessOrdering();

	default String getSchemaCharset() {
		return null;
	}

	default boolean isXmlMappingEnabled() {
		return true;
	}
}
