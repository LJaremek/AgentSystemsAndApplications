package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class DeliveryAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - uruchomiony.");

        // Rejestracja DeliveryAgent w DF
        ServiceDescription sd = new ServiceDescription();
        sd.setType("DeliveryService");
        sd.setName(getLocalName() + "-DeliveryService");
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " zarejestrował się w DF jako: " + sd.getName());
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Dodanie behawioru do obsługi zamówień od ClientAgentów
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null && "order-delivery".equals(msg.getConversationId())) {
                    System.out.println(getLocalName() + " otrzymał zamówienie: " + msg.getContent());
                    // Tutaj nastąpi komunikacja z MarketAgentami w celu oszacowania ceny
                    // (implementacja przykładowa, np. wysłanie zapytań do MarketAgentów)
                    // Po oszacowaniu ceny, wysłanie odpowiedzi do ClientAgent
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.INFORM);
                    reply.setContent("Oferta: Final Price = 50zl (przykładowo)");
                    send(reply);
                    System.out.println(getLocalName() + " wysłał ofertę do ClientAgent.");
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
