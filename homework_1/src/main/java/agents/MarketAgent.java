package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class MarketAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - uruchomiony.");

        // Rejestracja MarketAgent w DF
        ServiceDescription sd = new ServiceDescription();
        sd.setType("MarketService");
        sd.setName(getLocalName() + "-MarketService");
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " zarejestrował się w DF jako: " + sd.getName());
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Dodanie behawioru do obsługi zapytań cenowych od DeliveryAgentów
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage inquiry = receive();
                if (inquiry != null) {
                    System.out.println(getLocalName() + " otrzymał zapytanie: " + inquiry.getContent());
                    ACLMessage reply = inquiry.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    // Przykładowa odpowiedź – ceny produktów
                    reply.setContent("Ceny: milk=5zl, coffee=30zl, rice=4zl");
                    send(reply);
                    System.out.println(getLocalName() + " wysłał odpowiedź z cenami.");
                } else {
                    block();
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " zakończył działanie.");
    }
}
