package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

public class ClientAgent extends Agent {

    private AID chosenDeliveryAgent;

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

            if (result.length > 0) {
                // Wybierz pierwszego dla uproszczenia
                chosenDeliveryAgent = result[0].getName();

                // Wysyłanie zamówienia do wybranego DeliveryAgenta
                ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST);
                orderMsg.addReceiver(chosenDeliveryAgent);
                orderMsg.setContent("Order: milk, coffee, rice");
                orderMsg.setConversationId("order-delivery");
                send(orderMsg);
                System.out.println(getLocalName() + " wysłał zamówienie: milk, coffee, rice");
            } else {
                System.out.println(getLocalName() + " nie znalazł żadnych DeliveryAgentów.");
            }

        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        // Behawior obsługujący odpowiedzi od DeliveryAgentów
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String convId = msg.getConversationId();
                    if ("offer-delivery".equals(convId)) {
                        // Otrzymano ofertę
                        System.out.println(getLocalName() + " otrzymał ofertę: " + msg.getContent());
                        // Po otrzymaniu oferty, wysyłamy potwierdzenie (płatność)
                        ACLMessage payment = new ACLMessage(ACLMessage.INFORM);
                        payment.addReceiver(msg.getSender());
                        payment.setConversationId("payment-delivery");
                        payment.setContent("Payment: 50zl");
                        send(payment);
                        System.out.println(getLocalName() + " wysłał potwierdzenie płatności.");
                    } else if ("confirmation-delivery".equals(convId)) {
                        // Otrzymano potwierdzenie realizacji zamówienia
                        System.out.println(
                                getLocalName() + " otrzymał potwierdzenie realizacji zamówienia: " + msg.getContent());
                    }
                } else {
                    block();
                }
            }
        });
    }
}
