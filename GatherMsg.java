package com.example;

public class GatherMsg {
	
	private int ballot;
	private int imposeBallot;
	private int estimate;
	private int ID;
	
	public GatherMsg(int ballot, int imposeBallot, int estimate, int ID) {
		this.ballot = ballot;
		this.imposeBallot = imposeBallot;
		this.estimate = estimate;
		this.ID = ID;
	}

	public int getBallot() {
		return ballot;
	}

	public void setBallot(int ballot) {
		this.ballot = ballot;
	}

	public int getImposeBallot() {
		return imposeBallot;
	}

	public void setImposeBallot(int imposeBallot) {
		this.imposeBallot = imposeBallot;
	}

	public int getEstimate() {
		return estimate;
	}

	public void setEstimate(int estimate) {
		this.estimate = estimate;
	}

	public int getID() {
		return this.ID;
	}
	
}