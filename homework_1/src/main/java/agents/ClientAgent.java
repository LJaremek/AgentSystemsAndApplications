package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.*;

public class ClientAgent extends Agent {
    private List<AID> deliveryAgents = new ArrayList<>();
    private Map<AID, Double> receivedOffers = new HashMap<>();
    private AID selectedDeliveryAgent;
    private final String ORDER_DETAILS = "Order: milk, coffee, rice";

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - uruchomiony.");

        addBehaviour(new WakerBehaviour(this, 2000) {
            @Override
            protected void onWake() {
                searchForDeliveryAgents();
            }
        });
    }

    private void searchForDeliveryAgents() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("DeliveryService");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            System.out.println(getLocalName() + " znalazł " + result.length + " DeliveryAgentów.");
            if (result.length > 0) {
                for (DFAgentDescription dfd : result) {
                    deliveryAgents.add(dfd.getName());
                }
                sendOrderRequest();
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    private void sendOrderRequest() {
        for (AID aid : deliveryAgents) {
            ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST);
            orderMsg.addReceiver(aid);
            orderMsg.setContent(ORDER_DETAILS);
            orderMsg.setConversationId("order-delivery");
            send(orderMsg);
            System.out.println(getLocalName() + " wysłał zamówienie do " + aid.getLocalName());
        }
        addBehaviour(new OfferCollector());
    }

    private class OfferCollector extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null && "offer-delivery".equals(msg.getConversationId())) {
                System.out.println(getLocalName() + " otrzymał ofertę: " + msg.getContent());
                try {
                    double price = Double.parseDouble(msg.getContent().replaceAll("[^0-9.]", ""));
                    receivedOffers.put(msg.getSender(), price);
                    if (receivedOffers.size() == deliveryAgents.size()) {
                        selectBestOffer();
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Błąd parsowania ceny z oferty.");
                }
            } else {
                block();
            }
        }
    }

    private void selectBestOffer() {
        selectedDeliveryAgent = Collections.min(receivedOffers.entrySet(), Map.Entry.comparingByValue()).getKey();
        System.out.println(getLocalName() + " wybrał najtańszego dostawcę: " + selectedDeliveryAgent.getLocalName());

        ACLMessage payment = new ACLMessage(ACLMessage.INFORM);
        payment.addReceiver(selectedDeliveryAgent);
        payment.setConversationId("payment-delivery");
        payment.setContent("Payment: " + receivedOffers.get(selectedDeliveryAgent) + "zl");
        send(payment);
    }
}
