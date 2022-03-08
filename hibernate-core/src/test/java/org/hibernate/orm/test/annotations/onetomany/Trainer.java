/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */

//$Id$
package org.hibernate.orm.test.annotations.onetomany;
import java.util.Set;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;

import jakarta.persistence.ForeignKey;

/**
 * Unidirectional one to many sample
 *
 * @author Emmanuel Bernard
 */
@Entity()
public class Trainer {
	private Integer id;
	private String name;
	private Set<Tiger> trainedTigers;
	private Set<Monkey> trainedMonkeys;

	@Id
	@GeneratedValue
	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@OneToMany
	public Set<Tiger> getTrainedTigers() {
		return trainedTigers;
	}

	public void setTrainedTigers(Set<Tiger> trainedTigers) {
		this.trainedTigers = trainedTigers;
	}

	@OneToMany
	@JoinTable(
			name = "TrainedMonkeys",
			//columns are optional, here we explicit them
			joinColumns = @JoinColumn(name = "trainer_id"),
			inverseJoinColumns = @JoinColumn(name = "monkey_id"),
			foreignKey = @ForeignKey(name = "TM_TRA_FK"),
			inverseForeignKey = @ForeignKey(name = "TM_MON_FK")
	)
	public Set<Monkey> getTrainedMonkeys() {
		return trainedMonkeys;
	}

	public void setTrainedMonkeys(Set<Monkey> trainedMonkeys) {
		this.trainedMonkeys = trainedMonkeys;
	}
}
