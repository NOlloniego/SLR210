package com.example;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedAbstractActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
 
import scala.concurrent.duration.Duration;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

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
    private int counter; //Counts number of received GATHER messages
    private int counterACK;	//Counts number of received ACK messages
    private boolean flagACK;
    private boolean flagGather;
    private boolean faultProneMode; //Flag that indicates if the process is faulty or not.
    private double deadProb; //Probability of going to silent mode if faulty (from 0 to 1)
    private boolean leader;	//Flag that indicates if the current process is the leader. At start, all processes have it set to true.
    private long timeStart;
    private boolean decided; //Flag that indicates if the process has already decided or not

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
    	log.info("p" + self().path().name() + " proposed " + this.proposal);
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
    			log.info("p" + self().path().name() + " went to silent mode");
    			getContext().stop(getSelf());
           	}
    	}
    	if (!this.decided){
    	
          if (message instanceof Members) {//save the system's info
              Members m = (Members) message;
              processes = m;
              log.info("p" + self().path().name() + " received processes info");
          }
          else if (message instanceof OfconsProposerMsg) {
        	  OfconsProposerMsg msg = (OfconsProposerMsg) message;
        	  propose(msg.getValue());
        	  log.info("p" + self().path().name() + " received OfconsProposerMsg and will propose value " + msg.getValue());
          }
          else if(message instanceof ReadMsg) {
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
        	  AbortMsg msg = (AbortMsg) message;
        	  if(msg.getBallot()==this.ballot) {
	        	  this.flagACK = true;
	        	  this.flagGather = true;
	        	  log.info("p" + self().path().name() + " received ABORT ");
	        	  context().system().scheduler().scheduleOnce(Duration.create(10,TimeUnit.MILLISECONDS),
	                      getSelf(), new OfconsProposerMsg(this.proposal), context().system().dispatcher(), getSelf());
        	  }
          }
          else if(message instanceof GatherMsg) {
        	  GatherMsg msg = (GatherMsg) message;
        	  if(this.ballot==msg.getBallot()) {
	        	  this.states.get(msg.getID()-1).setImposeBallot(msg.getImposeBallot()); 
	        	  this.states.get(msg.getID()-1).setEstimate(msg.getEstimate());
	        	  counter++;
        	  }
        	  if((counter > N/2)&&(!this.flagGather)) {
        		  majority();
        		  flagGather = true;
        		 log.info("p" + self().path().name() + " received a majority of GATHERS ");
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
        	 log.info("p" + self().path().name() + " received IMPOSE value " + msg.getV() + " and ballot " + msg.getBallot());
          }
          else if(message instanceof ACKMsg) {
        	  ACKMsg msg = (ACKMsg) message;
        	  if(this.ballot == msg.getBallot())
        		  counterACK++;
        	  if((counterACK > N/2)&&(!this.flagACK)) {
        		  this.flagACK = true;
        		  for (ActorRef actor : processes.references) {
            		  actor.tell(new Decide(this.proposal, this.ballot), getSelf());
            	  }
        		 log.info("p" + self().path().name() + " received a majority of ACK messages");
           	  }  
          }
          else if (message instanceof Decide) {
        	  Decide msg = (Decide) message;
        	  this.decided = true;
	          for (ActorRef actor : processes.references) {
	        	  actor.tell(new Decide(msg.getV(), msg.getBallot()), getSelf());
	          }
	          log.info("p" + self().path().name() + " decided " + msg.getV() + "  int = " + Long.toString(System.currentTimeMillis() - this.timeStart) + " ms");
          }
          else if (message instanceof Crash) {
        	  this.faultProneMode = true;
        	  log.info("p" + self().path().name() + " is faulty");
          }
          else if (message instanceof Hold) {
        	  this.leader = false;
        	  log.info("p" + self().path().name() + " is holding");
          }
          else if (message instanceof Leader) {
        	  this.leader = true;
        	  log.info("p" + self().path().name() + " was chosen as the leader");
          }	  
    	}
      
    }
}