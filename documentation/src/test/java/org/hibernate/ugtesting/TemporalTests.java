/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or http://www.gnu.org/licenses/lgpl-2.1.html
 */
package org.hibernate.ugtesting;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Environment;

import org.hibernate.testing.orm.junit.DomainModel;
import org.hibernate.testing.orm.junit.ServiceRegistry;
import org.hibernate.testing.orm.junit.SessionFactoryScope;
import org.hibernate.testing.orm.junit.Setting;

import org.junit.jupiter.api.Test;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/**
 * @author Jan Schatteman
 */
@DomainModel(
		annotatedClasses =
				{
					TemporalTests.TemporalEntity.class
				}
)
public class TemporalTests extends H2BaseTestClass {

	@Test
	public void TemporalTest(SessionFactoryScope scope) {
		scope.inTransaction(
				session -> {
						LocalDate localDate = LocalDate.of( 2022, 5, 31 );
						LocalTime localTime = LocalTime.of( 15, 47, 33 );
						ZoneOffset zoneOffset = ZoneOffset.ofHours( 7 );

						TemporalEntity te = new TemporalEntity();
						te.setId( 1 );
						te.setDuration( Duration.ofDays( 2 ) );
						te.setInstant( Instant.now() );
						te.setLocalDate( localDate );
						te.setLocalTime( localTime );
						te.setLocalDateTime( LocalDateTime.of( localDate, localTime ) );
						te.setZoneOffset( zoneOffset );
						te.setOffsetTime( OffsetTime.of( localTime, zoneOffset) );
						te.setOffsetDateTime( OffsetDateTime.of( localDate, localTime, zoneOffset ) );
						te.setTimeZone( TimeZone.getDefault() );
						te.setZonedDateTime( ZonedDateTime.of( localDate, localTime, ZoneId.ofOffset( "UTC", zoneOffset ) ) );
						session.persist( te );
				}
		);
		int i = 0;
	}

	@Entity(name = "times")
	public static class TemporalEntity {
		@Id
		private Integer id;
		private Duration duration;
		private Instant instant;
		private LocalDate localDate;
		private LocalDateTime localDateTime;
		@Column(name = "\"LocalTime\"")
		private LocalTime localTime;
		private OffsetDateTime offsetDateTime;
		private OffsetTime offsetTime;
		private TimeZone timeZone;
		private ZoneOffset zoneOffset;
		private ZonedDateTime zonedDateTime;
		private Calendar calendar;
		private Date date;
		private Timestamp timestamp;
		private Time time;

		public TemporalEntity() {
		}

		public Integer getId() {
			return id;
		}

		public void setId(Integer id) {
			this.id = id;
		}

		public Duration getDuration() {
			return duration;
		}

		public void setDuration(Duration duration) {
			this.duration = duration;
		}

		public Instant getInstant() {
			return instant;
		}

		public void setInstant(Instant instant) {
			this.instant = instant;
		}

		public LocalDate getLocalDate() {
			return localDate;
		}

		public void setLocalDate(LocalDate localDate) {
			this.localDate = localDate;
		}

		public LocalDateTime getLocalDateTime() {
			return localDateTime;
		}

		public void setLocalDateTime(LocalDateTime localDateTime) {
			this.localDateTime = localDateTime;
		}

		public LocalTime getLocalTime() {
			return localTime;
		}

		public void setLocalTime(LocalTime localTime) {
			this.localTime = localTime;
		}

		public OffsetDateTime getOffsetDateTime() {
			return offsetDateTime;
		}

		public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
			this.offsetDateTime = offsetDateTime;
		}

		public OffsetTime getOffsetTime() {
			return offsetTime;
		}

		public void setOffsetTime(OffsetTime offsetTime) {
			this.offsetTime = offsetTime;
		}

		public TimeZone getTimeZone() {
			return timeZone;
		}

		public void setTimeZone(TimeZone timeZone) {
			this.timeZone = timeZone;
		}

		public ZoneOffset getZoneOffset() {
			return zoneOffset;
		}

		public void setZoneOffset(ZoneOffset zoneOffset) {
			this.zoneOffset = zoneOffset;
		}

		public ZonedDateTime getZonedDateTime() {
			return zonedDateTime;
		}

		public void setZonedDateTime(ZonedDateTime zonedDateTime) {
			this.zonedDateTime = zonedDateTime;
		}

		public Calendar getCalendar() {
			return calendar;
		}

		public void setCalendar(Calendar calendar) {
			this.calendar = calendar;
		}

		public Date getDate() {
			return date;
		}

		public void setDate(Date date) {
			this.date = date;
		}

		public Timestamp getTimestamp() {
			return timestamp;
		}

		public void setTimestamp(Timestamp timestamp) {
			this.timestamp = timestamp;
		}

		public Time getTime() {
			return time;
		}

		public void setTime(Time time) {
			this.time = time;
		}
	}
}
