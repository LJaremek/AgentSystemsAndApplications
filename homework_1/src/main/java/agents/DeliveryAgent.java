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
                    // Finalizacja zamówienia – wysłanie potwierdzenia realizacji
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
        // Mapy z propozycjami – klucz: AID MarketAgenta, wartość: surowa treść
        // propozycji (np. "milk=5.0,coffee=30.0,rice=4.0")
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
                // Odbieramy wiadomość blokująco z limitem czasu
                ACLMessage reply = myAgent.blockingReceive(mt, remainingTime);
                if (reply != null) {
                    proposals.put(reply.getSender(), reply.getContent());
                    System.out.println(getLocalName() + " otrzymał propozycję od "
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
            // Uzupełniona logika – iteracyjny wybór rynków
            String[] itemsArray = orderDetails.split(",");
            List<String> remainingItems = new ArrayList<>();
            for (String item : itemsArray) {
                remainingItems.add(item.trim());
            }

            double cumulativeCost = 0.0;
            List<String> selectedMarkets = new ArrayList<>();

            // Przetwarzamy otrzymane propozycje – przekształcamy je na mapy produktów z
            // cenami
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
                            // ignoruj nieprawidłową cenę
                        }
                    }
                }
                marketProposals.put(entry.getKey(), productPrices);
            }

            // Iteracyjny wybór rynków dla pokrycia całego zamówienia
            boolean progress = true;
            while (!remainingItems.isEmpty() && progress) {
                AID bestMarket = null;
                int bestCount = 0;
                double bestCost = Double.MAX_VALUE;
                List<String> bestProducts = new ArrayList<>();

                // Dla każdego rynku określamy, które z pozostałych produktów są dostępne
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
                // Zapisujemy wybrany rynek oraz usuwamy pokryte produkty
                cumulativeCost += bestCost;
                selectedMarkets.add(bestMarket.getLocalName() + " (produkty: " + String.join(", ", bestProducts) + ")");
                remainingItems.removeAll(bestProducts);
            }

            String offer;
            if (!remainingItems.isEmpty()) {
                // Nie udało się zebrać wszystkich produktów
                offer = "Nie można zrealizować całego zamówienia. Brakuje: " + String.join(", ", remainingItems);
            } else {
                // Dodajemy stałą opłatę (np. 10zl)
                double finalPrice = cumulativeCost + 10;
                offer = "Oferta: Final Price = " + finalPrice + "zl (wybrane rynki: "
                        + String.join("; ", selectedMarkets) + ")";
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
