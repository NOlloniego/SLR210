package com.example;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;

public class Main {

    public static int N = 10;


    public static void main(String[] args) throws InterruptedException {

        // Instantiate an actor system
    	int f = (int) Math.round(N/2)-1;
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N );

        ArrayList<ActorRef> references = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N, 0.1), "" + i);
            references.add(a);
        }
        
        ArrayList<ActorRef> references2 = new ArrayList<>(references);
        ArrayList<ActorRef> faulty = new ArrayList<>();
        
        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }
        
        Collections.shuffle(references2);
        for(int i = 0; i < f; i++) {
        	faulty.add(references2.get(i));
        	references2.get(i).tell(new Crash(), ActorRef.noSender());
        }
        
        for(int i = 0; i<N; i++) {
        	references.get(i).tell(new OfconsProposerMsg(((int)(Math.random() * 1000) )), ActorRef.noSender());
        }
        
        Thread.sleep(500);  
    
        references2.removeAll(faulty);
        
        //Randomly select a leader
        Random rand = new Random();
        int indexLeader = rand.nextInt(references2.size());
        references2.get(indexLeader).tell(new Leader(), ActorRef.noSender());
        System.out.println("************ Leader was elected **********");
        
        //Send Hold messages to all processes who are not the leader.
        Hold holdMsg = new Hold();
        for(int i = 0; i<N; i++) {
        	if(i != indexLeader) {
        		references.get(i).tell(holdMsg, ActorRef.noSender());
        	}
        }
        
    }
}
