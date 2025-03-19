package agents;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.*;

import java.util.*;

public class DeliveryAgent extends Agent {

    private String orderDetails;
    private AID clientAgent;
    private double deliveryFee = 10.0;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - started.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                deliveryFee = Double.parseDouble(args[0].toString());
            } catch (Exception e) {
                System.out.println(getLocalName() + " - error parsing delivery charge, I use default value.");
            }
        }

        ServiceDescription sd = new ServiceDescription();
        sd.setType("DeliveryService");
        sd.setName(getLocalName() + "-DeliveryService");
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered in DF as: " + sd.getName());
        } catch (FIPAException fe) {
            System.out.println(getLocalName() + " - registration error in DF: " + fe.getMessage());
            fe.printStackTrace();
        }

        // Behavior handling order requests from ClientAgents (using MessageTemplate)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId("order-delivery");
                ACLMessage msg = receive(mt);
                if (msg != null) {
                    System.out.println(getLocalName() + " received an order: " + msg.getContent());
                    orderDetails = msg.getContent().replace("Order: ", "").trim();
                    clientAgent = msg.getSender();
                    // Start pricing process by communicating with MarketAgents
                    addBehaviour(new PriceEstimationBehaviour());
                } else {
                    block();
                }
            }
        });

        // Behavior waiting for payment confirmation from ClientAgent (using
        // MessageTemplate)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId("payment-delivery");
                ACLMessage paymentMsg = receive(mt);
                if (paymentMsg != null) {
                    System.out.println(getLocalName() + " received payment: " + paymentMsg.getContent());
                    // Finalizacja zamówienia – wysyłanie potwierdzenia
                    ACLMessage confirmation = paymentMsg.createReply();
                    confirmation.setConversationId("confirmation-delivery");
                    confirmation.setContent("Order completed.");
                    send(confirmation);
                    System.out.println(getLocalName() + " sent order completion confirmation.");
                } else {
                    block();
                }
            }
        });
    }

    private class PriceEstimationBehaviour extends Behaviour {
        private boolean finished = false;
        private List<AID> marketAgents = new ArrayList<>();
        // Maps with proposals – key: MarketAgent AID, value: raw proposal content
        // (e.g., "milk=5.0,coffee=30.0,rice=4.0")
        private Map<AID, String> proposals = new HashMap<>();
        private long startTime;
        private final long TIMEOUT = 5000; // 5 sekund

        @Override
        public void onStart() {
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("MarketService");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                for (DFAgentDescription dfd : result) {
                    marketAgents.add(dfd.getName());
                }
                System.out.println(getLocalName() + " found " + marketAgents.size() + " MarketAgents.");
            } catch (FIPAException fe) {
                System.out.println(getLocalName() + " - MarketAgents search error: " + fe.getMessage());
                fe.printStackTrace();
            }

            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (AID market : marketAgents) {
                cfp.addReceiver(market);
            }
            cfp.setContent(orderDetails);
            cfp.setConversationId("market-cfp");
            send(cfp);
            System.out.println(getLocalName() + " sent CFP to MarketAgents with content: " + orderDetails);
            startTime = System.currentTimeMillis();
        }

        @Override
        public void action() {
            MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchConversationId("market-cfp"),
                    MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
            long elapsed = System.currentTimeMillis() - startTime;
            long remainingTime = TIMEOUT - elapsed;
            if (remainingTime > 0) {
                ACLMessage reply = myAgent.blockingReceive(mt, remainingTime);
                if (reply != null) {
                    proposals.put(reply.getSender(), reply.getContent());
                    System.out.println(getLocalName() + " received a proposal from "
                            + reply.getSender().getLocalName() + ": " + reply.getContent());
                }
            }
            finished = (System.currentTimeMillis() - startTime) >= TIMEOUT;
        }

        @Override
        public boolean done() {
            return finished;
        }

        @Override
        public int onEnd() {
            String[] itemsArray = orderDetails.split(",");
            List<String> remainingItems = new ArrayList<>();
            for (String item : itemsArray) {
                remainingItems.add(item.trim());
            }

            double cumulativeCost = 0.0;
            List<String> selectedMarkets = new ArrayList<>();

            // product -> price
            Map<AID, Map<String, Double>> marketProposals = new HashMap<>();
            for (Map.Entry<AID, String> entry : proposals.entrySet()) {
                Map<String, Double> productPrices = new HashMap<>();
                String[] parts = entry.getValue().split(",");
                for (String part : parts) {
                    String[] keyVal = part.split("=");
                    if (keyVal.length == 2) {
                        try {
                            String product = keyVal[0].trim();
                            double price = Double.parseDouble(keyVal[1].trim());
                            productPrices.put(product, price);
                        } catch (NumberFormatException e) {
                            System.out.println(getLocalName() + " - product price parsing error: " + part);
                        }
                    }
                }
                marketProposals.put(entry.getKey(), productPrices);
            }

            boolean progress = true;
            while (!remainingItems.isEmpty() && progress) {
                AID bestMarket = null;
                int bestCount = 0;
                double bestCost = Double.MAX_VALUE;
                List<String> bestProducts = new ArrayList<>();

                for (Map.Entry<AID, Map<String, Double>> entry : marketProposals.entrySet()) {
                    Map<String, Double> productPrices = entry.getValue();
                    List<String> availableProducts = new ArrayList<>();
                    double cost = 0.0;
                    for (String item : remainingItems) {
                        if (productPrices.containsKey(item)) {
                            availableProducts.add(item);
                            cost += productPrices.get(item);
                        }
                    }
                    int count = availableProducts.size();
                    if (count > 0) {
                        if (count > bestCount || (count == bestCount && cost < bestCost)) {
                            bestCount = count;
                            bestCost = cost;
                            bestMarket = entry.getKey();
                            bestProducts = availableProducts;
                        }
                    }
                }
                if (bestMarket == null || bestCount == 0) {
                    progress = false;
                    break;
                }
                cumulativeCost += bestCost;
                selectedMarkets.add(bestMarket.getLocalName() + " (products: " + String.join(", ", bestProducts) + ")");
                remainingItems.removeAll(bestProducts);
            }

            String offer;
            if (!remainingItems.isEmpty()) {
                offer = "Unable to complete the full order. Missing: " + String.join(", ", remainingItems);
            } else {
                double finalPrice = cumulativeCost + deliveryFee;
                offer = "Offer: Final Price = " + finalPrice + "zl (selected markets: "
                        + String.join("; ", selectedMarkets) + ")";
            }

            ACLMessage offerMsg = new ACLMessage(ACLMessage.INFORM);
            offerMsg.addReceiver(clientAgent);
            offerMsg.setConversationId("offer-delivery");
            offerMsg.setContent(offer);
            send(offerMsg);
            System.out.println(getLocalName() + " sent an offer to ClientAgent: " + offer);

            return super.onEnd();
        }
    }

    @Override
    protected void takeDown() {
        try {
            DFService.deregister(this);
        } catch (FIPAException fe) {
            System.out.println(getLocalName() + " - error during deregistration from the DF: " + fe.getMessage());
            fe.printStackTrace();
        }
        System.out.println(getLocalName() + " has shut down.");
    }
}
