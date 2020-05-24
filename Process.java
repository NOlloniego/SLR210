package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references
    private int ballot;
    private int proposal;
    private int [][] states;
    private int readBallot;
    private int imposeBallot;
    private int estimate;
    private boolean proposeState;
    private int counter;
    private int counterACK;
    private int lastDecidedRound;
    private boolean flagACK;
    private boolean flagGather;
    private boolean faultProneMode;
    private double deadProb; //From 0 to 1

    public Process(int ID, int nb, double deadProb) {
        N = nb;
        id = ID;
        this.ballot = ID-nb;
        this.imposeBallot = ID-nb;
        this.states = new int [this.N][2];
        this.readBallot = 0;
        this.proposeState = false;
        this.counter = 0;
        this.counterACK = 0;
        this.lastDecidedRound = -1;
        this.flagACK = false;
        this.flagGather = false;
        this.faultProneMode = false; 
        this.deadProb = deadProb;
    }
    
    public String toString() {
        return "Process{" + "id=" + id ;
    }

    /**
     * Static function creating actor
     */
    public static Props createActor(int ID, int nb, double deadProb) {
        return Props.create(Process.class, () -> {
            return new Process(ID, nb, deadProb);
        });
    }
    
    public void propose(int v) {
    	this.flagGather = false;
    	this.flagACK = false;
    	this.proposeState = true;
    	this.proposal= v;
    	this.ballot = ballot + this.N;
    	for(ActorRef actors : this.processes.references) {
    		actors.tell(new ReadMsg(this.ballot), getSelf());
    	}
    }
    
    public void majority() {
    	int highest = states[0][1];
    	for (int i=0; i<N; i++) {
    		if(states[i][0]>0) {
    			if(states[i][1]>highest)
    				this.proposal = states[i][1];
    		}
    	}
    	this.states = new int [this.N][2];
    	for (ActorRef actor : this.processes.references) {
            actor.tell(new ImposeMsg(this.ballot, this.proposal), getSelf());
        }
    }
    
    public void onReceive(Object message) throws Throwable {
    	
    	if(this.faultProneMode) {
    		if(Math.random()<this.deadProb) {
    			log.info("p" + self().path().name() + " died");
    			getContext().stop(getSelf());
           	}
    	}
    	else {
    	
          if (message instanceof Members) {//save the system's info
              Members m = (Members) message;
              processes = m;
              log.info("p" + self().path().name() + " received processes info");
          }
          else if (message instanceof OfconsProposerMsg) {
        	  OfconsProposerMsg msg = (OfconsProposerMsg) message;
        	  propose(msg.getValue());
        	  log.info("p" + self().path().name() + " received OfconsProposerMsg");
          }
          else if(message instanceof ReadMsg) {
        	  //this.broadcastedDecide = false;
        	  ReadMsg rd = (ReadMsg) message;
        	  if((this.readBallot >= rd.getBallot())||(this.imposeBallot >= rd.getBallot()))
        		  getSender().tell(new AbortMsg(rd.ballot), getSelf());
        	  else {
        		  this.readBallot = rd.getBallot();
        		  getSender().tell(new GatherMsg(rd.ballot, this.imposeBallot, this.estimate, this.id), getSelf());
        	  }
        	  log.info("p" + self().path().name() + " received READ " + rd.ballot);
          }
          else if(message instanceof AbortMsg) {
        	  if(this.proposeState)
        		  this.proposeState = false;
          }
          else if(message instanceof GatherMsg) {
        	  GatherMsg msg = (GatherMsg) message;
        	  this.states[msg.getID()-1][0] = msg.getImposeBallot();
        	  this.states[msg.getID()-1][1] = msg.getEstimate();
        	  counter++;
        	  if((counter > N/2)&&(!this.flagGather)) {
        		  majority();
        		  flagGather = true;
        	  }
          }
          else if(message instanceof ImposeMsg) {
        	  ImposeMsg msg = (ImposeMsg) message;
        	  if((this.readBallot>msg.getBallot())||(this.imposeBallot>msg.getBallot()))
        		  getSender().tell(new AbortMsg(msg.getBallot()), getSelf());
        	  else {
        		  this.estimate = msg.getV();
        		  this.imposeBallot = msg.getBallot();
        		  getSender().tell(new ACKMsg(msg.getBallot()), getSelf());
        	  }
          }
          else if(message instanceof ACKMsg) {
        	  counterACK++;
        	  if((counterACK > N/2)&&(!this.flagACK)) {
        		  this.flagACK = true;
        		  for (ActorRef actor : processes.references) {
            		  actor.tell(new Decide(this.proposal, this.ballot), getSelf());
            	  }
        	  }  
          }
          else if (message instanceof Decide) {
        	  Decide msg = (Decide) message;
        	  if(lastDecidedRound  != msg.getBallot()) {
        		  lastDecidedRound = msg.getBallot();
	        	  for (ActorRef actor : processes.references) {
	        		  actor.tell(new Decide(msg.getV(), msg.getBallot()), getSelf());
	        	  }
	        	  log.info("p" + self().path().name() + " decided " + msg.getV());
        	  }
          }
          else if (message instanceof Crash) {
        	  this.faultProneMode = true;
          }
    	}
      
    }
}