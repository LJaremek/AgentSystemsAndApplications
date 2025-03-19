package agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.*;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.util.HashMap;
import java.util.Map;

public class MarketAgent extends Agent {

    // Inventory: product -> price
    private Map<String, Double> inventory;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " - started.");

        Object[] args = getArguments();
        inventory = new HashMap<>();
        if (args != null && args.length > 0) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Double> passedInventory = (Map<String, Double>) args[0];
                inventory.putAll(passedInventory);
            } catch (Exception e) {
                System.out.println(getLocalName() + " - argument parsing error, I use the default inventory.");
                setDefaultInventory();
            }
        } else {
            setDefaultInventory();
        }

        ServiceDescription sd = new ServiceDescription();
        sd.setType("MarketService");
        sd.setName(getLocalName() + "-MarketService");
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            System.out.println(getLocalName() + " registered in DF as: " + sd.getName());
        } catch (FIPAException fe) {
            System.out.println(getLocalName() + " - DF registration error: " + fe.getMessage());
            fe.printStackTrace();
        }

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage cfp = receive();
                if (cfp != null && "market-cfp".equals(cfp.getConversationId())) {
                    System.out.println(getLocalName() + " received CFP request: " + cfp.getContent());
                    String[] items = cfp.getContent().split(",");
                    StringBuilder proposal = new StringBuilder();
                    for (String item : items) {
                        item = item.trim();
                        if (inventory.containsKey(item)) {
                            proposal.append(item).append("=").append(inventory.get(item)).append(",");
                        }
                    }
                    if (proposal.length() > 0) {
                        proposal.deleteCharAt(proposal.length() - 1);
                    }
                    ACLMessage reply = cfp.createReply();
                    reply.setPerformative(ACLMessage.PROPOSE);
                    reply.setConversationId("market-cfp");
                    reply.setContent(proposal.toString());
                    send(reply);
                    System.out.println(getLocalName() + " sent a proposal: " + proposal.toString());
                } else {
                    block();
                }
            }
        });
    }

    private void setDefaultInventory() {
        inventory.put("milk", 5.0);
        inventory.put("coffee", 30.0);
        inventory.put("rice", 4.0);
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
