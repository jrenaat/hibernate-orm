/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Red Hat Inc. and Hibernate Authors
 */
package org.hibernate.boot.models.annotations.internal;

import java.lang.annotation.Annotation;
import java.util.Map;

import org.hibernate.annotations.DialectOverride;
import org.hibernate.boot.models.DialectOverrideAnnotations;
import org.hibernate.boot.models.annotations.spi.RepeatableContainer;
import org.hibernate.models.spi.ModelsContext;

import static org.hibernate.boot.models.internal.OrmAnnotationHelper.extractJdkValue;

/**
 * @author Steve Ebersole
 */
@SuppressWarnings("ClassExplicitlyAnnotation")
public class OverriddenChecksAnnotation
		implements DialectOverride.Checks, RepeatableContainer<DialectOverride.Check> {
	private DialectOverride.Check[] value;

	/**
	 * Used in creating dynamic annotation instances (e.g. from XML)
	 */
	public OverriddenChecksAnnotation(ModelsContext modelContext) {
	}

	/**
	 * Used in creating annotation instances from JDK variant
	 */
	public OverriddenChecksAnnotation(DialectOverride.Checks annotation, ModelsContext modelContext) {
		value( extractJdkValue( annotation, DialectOverrideAnnotations.DIALECT_OVERRIDE_CHECKS, "value", modelContext ) );
	}

	/**
	 * Used in creating annotation instances from Jandex variant
	 */
	public OverriddenChecksAnnotation(Map<String, Object> attributeValues, ModelsContext modelContext) {
		value( (DialectOverride.Check[]) attributeValues.get( "value" ) );
	}

	@Override
	public DialectOverride.Check[] value() {
		return value;
	}

	@Override
	public void value(DialectOverride.Check[] value) {
		this.value = value;
	}

	@Override
	public Class<? extends Annotation> annotationType() {
		return DialectOverride.Checks.class;
	}
}
