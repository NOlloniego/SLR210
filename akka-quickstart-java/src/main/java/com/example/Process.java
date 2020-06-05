package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import java.util.ArrayList;

public class Process extends UntypedAbstractActor {
    private final LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);// Logger attached to actor
    private final int N;//number of processes
    private final int id;//id of current process
    private Members processes;//other processes' references
    private int ballot;
    private int proposal;
    private ArrayList<State> states;
    private int readBallot;
    private int imposeBallot;
    private int estimate;
    private int counter;
    private int counterACK;
    private boolean flagACK;
    private boolean flagGather;
    private boolean faultProneMode;
    private double deadProb; //From 0 to 1
    private boolean leader;
    private long timeStart;
    private boolean decided;

    public Process(int ID, int nb, double deadProb) {
        N = nb;
        id = ID;
        this.ballot = ID-nb;
        this.imposeBallot = ID-nb;
        this.readBallot = 0;
        this.counter = 0;
        this.counterACK = 0;
        this.flagACK = false;
        this.flagGather = false;
        this.faultProneMode = false; 
        this.deadProb = deadProb;
        this.leader = true;
        this.timeStart = System.currentTimeMillis();
        this.decided = false;
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
    	if(!this.leader)
    		return;
    	this.flagGather = false;
    	this.flagACK = false;
    	//log.info("p" + self().path().name() + " proposes " + v);
    	this.proposal= v;
    	this.counter = 0;
    	this.counterACK = 0;
    	this.ballot = ballot + this.N;
    	this.states = new ArrayList<State>(this.N);
    	for (int i = 0; i < N; i++) {
            this.states.add(new State(-1, 0));
        }
    	for(ActorRef actors : this.processes.references) {
    		actors.tell(new ReadMsg(this.ballot), getSelf());
    	}
    }
    
    public void majority() {
    	int highest = states.get(0).getEstimate();
    	for (State state : states) {
    		if(state.getImposeBallot()>0) {
    			if(state.getEstimate()>highest)
    				this.proposal = state.getEstimate();
    		}
    	}
    	this.states = new ArrayList<State>(this.N);
    	for (int i = 0; i < N; i++) {
            this.states.add(new State(-1, 0));
        }
    	for (ActorRef actor : this.processes.references) {
            actor.tell(new ImposeMsg(this.ballot, this.proposal), getSelf());
        }
    }
    
    public void onReceive(Object message) throws Throwable {
    	
    	if(this.faultProneMode) {
    		if(Math.random()<this.deadProb) {
    			//log.info("p" + self().path().name() + " died");
    			getContext().stop(getSelf());
           	}
    	}
    	else if (!this.decided){
    	
          if (message instanceof Members) {//save the system's info
              Members m = (Members) message;
              processes = m;
              //log.info("p" + self().path().name() + " received processes info");
          }
          else if (message instanceof OfconsProposerMsg) {
        	  OfconsProposerMsg msg = (OfconsProposerMsg) message;
        	  propose(msg.getValue());
        	  //log.info("p" + self().path().name() + " received OfconsProposerMsg");
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
        	  //log.info("p" + self().path().name() + " received READ " + rd.ballot);
          }
          else if(message instanceof AbortMsg) {
        	  //log.info("p" + self().path().name() + " received ABORT ");
        	  this.propose(this.proposal);
          }
          else if(message instanceof GatherMsg) {
        	  GatherMsg msg = (GatherMsg) message;
        	  this.states.get(msg.getID()-1).setImposeBallot(msg.getImposeBallot()); 
        	  this.states.get(msg.getID()-1).setEstimate(msg.getEstimate());
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
        	  this.decided = true;
	          for (ActorRef actor : processes.references) {
	        	  actor.tell(new Decide(msg.getV(), msg.getBallot()), getSelf());
	          }
	          log.info("p" + self().path().name() + " decided " + msg.getV() + "  in t = " + Long.toString(System.currentTimeMillis() - this.timeStart) + " ms");
          }
          else if (message instanceof Crash) {
        	  this.faultProneMode = true;
          }
          else if (message instanceof Hold) {
        	  this.leader = false;
          }
          else if (message instanceof Leader) {
        	  this.leader = true;
          }	  
    	}
      
    }
}