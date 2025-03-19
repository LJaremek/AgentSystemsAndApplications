package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class ClientAgent extends Agent {

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - uruchomiony.");

        // Wyszukiwanie dostępnych DeliveryAgentów w DF
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("DeliveryService");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            System.out.println(getLocalName() + " znalazł " + result.length + " DeliveryAgentów.");

            // Wysyłanie szczegółów zamówienia do wszystkich znalezionych DeliveryAgentów
            ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST);
            for (DFAgentDescription dfd : result) {
                orderMsg.addReceiver(dfd.getName());
            }
            orderMsg.setContent("Order: milk, coffee, rice");
            orderMsg.setConversationId("order-delivery");
            send(orderMsg);
            System.out.println(getLocalName() + " wysłał zamówienie: milk, coffee, rice");

        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Dodanie behawioru obsługującego odpowiedzi od DeliveryAgentów
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage reply = receive();
                if (reply != null) {
                    System.out.println(getLocalName() + " otrzymał odpowiedź: " + reply.getContent());
                } else {
                    block();
                }
            }
        });
    }
}
