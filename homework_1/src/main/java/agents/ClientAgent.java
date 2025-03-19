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
    private String orderDetails = "Order: milk, coffee, rice";

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - started.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                orderDetails = (String) args[0];
            } catch (Exception e) {
                System.out.println(
                        getLocalName() + " - argument parsing error, I use default order details.");
            }
        }

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
            System.out.println(getLocalName() + " found " + result.length + " Delivery Agents.");
            if (result.length > 0) {
                for (DFAgentDescription dfd : result) {
                    deliveryAgents.add(dfd.getName());
                }
                sendOrderRequest();
            } else {
                System.out.println("No available delivery agents.");
            }
        } catch (FIPAException fe) {
            System.out.println(getLocalName() + " - search error in DF: " + fe.getMessage());
            fe.printStackTrace();
        }
    }

    private void sendOrderRequest() {
        for (AID aid : deliveryAgents) {
            ACLMessage orderMsg = new ACLMessage(ACLMessage.REQUEST);
            orderMsg.addReceiver(aid);
            orderMsg.setContent(orderDetails);
            orderMsg.setConversationId("order-delivery");
            send(orderMsg);
            System.out.println(getLocalName() + " sent an order request to " + aid.getLocalName());
        }
        addBehaviour(new OfferCollector());
    }

    private class OfferCollector extends CyclicBehaviour {
        private long startTime = System.currentTimeMillis();
        private final long TIMEOUT = 5000; // 5 seconds
        private boolean offerSelected = false; // new flag for calling selectBestOffer() only one time

        @Override
        public void action() {
            ACLMessage msg = receive();
            if (msg != null && "offer-delivery".equals(msg.getConversationId())) {
                System.out.println(getLocalName() + " received an offer: " + msg.getContent());
                try {
                    double price = Double.parseDouble(msg.getContent().replaceAll("[^0-9.]", ""));
                    receivedOffers.put(msg.getSender(), price);
                    if (receivedOffers.size() == deliveryAgents.size() && !offerSelected) {
                        offerSelected = true;
                        selectBestOffer();
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Error parsing price from the offer: " + e.getMessage());
                }
            } else {
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed >= TIMEOUT) {
                    if (!offerSelected) {
                        if (receivedOffers.isEmpty()) {
                            System.out.println("No offers received within the time limit.");
                        } else {
                            offerSelected = true;
                            selectBestOffer();
                        }
                    }
                    myAgent.removeBehaviour(this);
                } else {
                    block();
                }
            }
        }
    }

    private void selectBestOffer() {
        if (receivedOffers.isEmpty()) {
            System.out.println(getLocalName() + " did not receive any offers, order canceled.");
            return;
        }
        selectedDeliveryAgent = Collections.min(receivedOffers.entrySet(), Map.Entry.comparingByValue()).getKey();
        System.out.println(
                getLocalName() + " selected the cheapest delivery agent: " + selectedDeliveryAgent.getLocalName());

        ACLMessage payment = new ACLMessage(ACLMessage.INFORM);
        payment.addReceiver(selectedDeliveryAgent);
        payment.setConversationId("payment-delivery");
        payment.setContent("Payment: " + receivedOffers.get(selectedDeliveryAgent) + "zl");
        send(payment);
    }
}
