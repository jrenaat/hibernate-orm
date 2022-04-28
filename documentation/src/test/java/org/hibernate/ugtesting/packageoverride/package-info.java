/**
 * @author Jan Schatteman
 */
@ConverterRegistration( converter = YesNoConverter.class, autoApply = true)
package org.hibernate.ugtesting.packageoverride;

import org.hibernate.annotations.ConverterRegistration;
import org.hibernate.type.YesNoConverter;