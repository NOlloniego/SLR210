package com.example;

public class ImposeMsg {
	
	private int ballot;
	private int v;
	
	public ImposeMsg(int ballot, int proposal) {
		this.ballot = ballot;
		this.v = proposal;
	}

	public int getBallot() {
		return ballot;
	}

	public void setBallot(int ballot) {
		this.ballot = ballot;
	}

	public int getV() {
		return v;
	}

	public void setV(int v) {
		this.v = v;
	}
	
	
	
}