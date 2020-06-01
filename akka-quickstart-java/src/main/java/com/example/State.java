package com.example;

public class State {
	
	private int imposeBallot;
	private int estimate;
	
	public State(int imposeBallot, int estimate) {
		this.imposeBallot = imposeBallot;
		this.estimate = estimate;
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
	
	

}
