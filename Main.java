package com.example;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.*;
import java.util.stream.Stream;


public class Main {

    public static int N = 10;


    public static void main(String[] args) throws InterruptedException {

        // Instantiate an actor system
    	int f = (int) N/2 -2;
        final ActorSystem system = ActorSystem.create("system");
        system.log().info("System started with N=" + N );

        ArrayList<ActorRef> references = new ArrayList<>();

        for (int i = 0; i < N; i++) {
            // Instantiate processes
            final ActorRef a = system.actorOf(Process.createActor(i + 1, N, 0.1), "" + i);
            references.add(a);
        }
        
        ArrayList<ActorRef> references2 = new ArrayList<>(references);
        
        //give each process a view of all the other processes
        Members m = new Members(references);
        for (ActorRef actor : references) {
            actor.tell(m, ActorRef.noSender());
        }
        
        Collections.shuffle(references2);
        for(int i = 0; i < f; i++) {
        	references2.get(i).tell(new Crash(), ActorRef.noSender());
        }
        
        for(int i = 0; i<N; i++) {
        	references.get(i).tell(new OfconsProposerMsg(((int)(Math.random() * 1000) ) % 2), ActorRef.noSender());
        }
        
    
       
        
    }
}
