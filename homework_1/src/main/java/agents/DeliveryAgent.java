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

        // Behawior obsługujący zamówienia od ClientAgentów (używamy MessageTemplate)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId("order-delivery");
                ACLMessage msg = receive(mt);
                if (msg != null) {
                    System.out.println(getLocalName() + " otrzymał zamówienie: " + msg.getContent());
                    orderDetails = msg.getContent().replace("Order: ", "").trim();
                    clientAgent = msg.getSender();
                    // Rozpoczęcie procesu wyceny poprzez komunikację z MarketAgentami
                    addBehaviour(new PriceEstimationBehaviour());
                } else {
                    block();
                }
            }
        });

        // Behawior oczekujący na potwierdzenie płatności od ClientAgent (używamy
        // MessageTemplate)
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                MessageTemplate mt = MessageTemplate.MatchConversationId("payment-delivery");
                ACLMessage paymentMsg = receive(mt);
                if (paymentMsg != null) {
                    System.out.println(getLocalName() + " otrzymał płatność: " + paymentMsg.getContent());
                    // Symulacja finalizacji zamówienia
                    ACLMessage confirmation = paymentMsg.createReply();
                    confirmation.setConversationId("confirmation-delivery");
                    confirmation.setContent("Zamówienie zrealizowane.");
                    send(confirmation);
                    System.out.println(getLocalName() + " wysłał potwierdzenie realizacji zamówienia.");
                } else {
                    block();
                }
            }
        });
    }

    private class PriceEstimationBehaviour extends Behaviour {
        private boolean finished = false;
        private List<AID> marketAgents = new ArrayList<>();
        private Map<AID, String> proposals = new HashMap<>();
        private long startTime;
        private final long TIMEOUT = 5000; // 5 sekund

        @Override
        public void onStart() {
            // Wyszukiwanie MarketAgentów w DF
            DFAgentDescription template = new DFAgentDescription();
            ServiceDescription sd = new ServiceDescription();
            sd.setType("MarketService");
            template.addServices(sd);
            try {
                DFAgentDescription[] result = DFService.search(myAgent, template);
                for (DFAgentDescription dfd : result) {
                    marketAgents.add(dfd.getName());
                }
                System.out.println(getLocalName() + " znalazł " + marketAgents.size() + " MarketAgentów.");
            } catch (FIPAException fe) {
                fe.printStackTrace();
            }

            // Wysłanie CFP do wszystkich MarketAgentów
            ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
            for (AID market : marketAgents) {
                cfp.addReceiver(market);
            }
            cfp.setContent(orderDetails);
            cfp.setConversationId("market-cfp");
            send(cfp);
            System.out.println(getLocalName() + " wysłał CFP do MarketAgentów z treścią: " + orderDetails);
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
                // Używamy blockingReceive, aby czekać na wiadomość maksymalnie przez
                // remainingTime milisekund.
                ACLMessage reply = myAgent.blockingReceive(mt, remainingTime);
                if (reply != null) {
                    proposals.put(reply.getSender(), reply.getContent());
                    System.out.println(getLocalName() + " otrzymał propozycję od "
                            + reply.getSender().getLocalName() + ": " + reply.getContent());
                }
            }
            // Sprawdzamy, czy upłynął czas oczekiwania.
            finished = (System.currentTimeMillis() - startTime) >= TIMEOUT;
        }

        @Override
        public boolean done() {
            return finished;
        }

        @Override
        public int onEnd() {
            // Przetwarzanie otrzymanych propozycji
            String[] items = orderDetails.split(",");
            items = Arrays.stream(items).map(String::trim).toArray(String[]::new);

            AID bestMarket = null;
            int bestCount = -1;
            double bestPrice = Double.MAX_VALUE;

            for (Map.Entry<AID, String> entry : proposals.entrySet()) {
                String proposal = entry.getValue();
                String[] parts = proposal.split(",");
                int count = 0;
                double total = 0;
                Map<String, Double> marketPrices = new HashMap<>();
                for (String part : parts) {
                    String[] keyVal = part.split("=");
                    if (keyVal.length == 2) {
                        String product = keyVal[0].trim();
                        try {
                            double price = Double.parseDouble(keyVal[1].trim());
                            marketPrices.put(product, price);
                        } catch (NumberFormatException e) {
                            // ignoruj nieprawidłową cenę
                        }
                    }
                }
                for (String item : items) {
                    if (marketPrices.containsKey(item)) {
                        count++;
                        total += marketPrices.get(item);
                    }
                }
                System.out.println(getLocalName() + " - " + entry.getKey().getLocalName()
                        + ": dostępność=" + count + ", cena=" + total);
                if (count > bestCount || (count == bestCount && total < bestPrice)) {
                    bestCount = count;
                    bestPrice = total;
                    bestMarket = entry.getKey();
                }
            }

            double finalPrice;
            String offer;
            if (bestMarket != null) {
                // Dodajemy stałą opłatę (np. 10zl)
                finalPrice = bestPrice + 10;
                offer = "Oferta: Final Price = " + finalPrice + "zl (wybrany rynek: "
                        + bestMarket.getLocalName() + ")";
            } else {
                finalPrice = 0;
                offer = "Brak ofert od MarketAgentów.";
            }

            // Wysyłamy ofertę do ClientAgent
            ACLMessage offerMsg = new ACLMessage(ACLMessage.INFORM);
            offerMsg.addReceiver(clientAgent);
            offerMsg.setConversationId("offer-delivery");
            offerMsg.setContent(offer);
            send(offerMsg);
            System.out.println(getLocalName() + " wysłał ofertę do ClientAgent: " + offer);

            return super.onEnd();
        }
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
