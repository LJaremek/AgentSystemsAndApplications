package agents;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

import java.util.HashMap;
import java.util.Map;

public class MainContainer {
    public static void main(String[] args) {
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        AgentContainer mainContainer = rt.createMainContainer(p);
        System.out.println("Main JADE environment started.");

        try {
            String clientOrder = "Order: milk, coffee, rice";
            String deliveryFee = "12.5";
            Map<String, Double> customInventory = new HashMap<>();
            customInventory.put("milk", 4.5);
            customInventory.put("coffee", 28.0);
            customInventory.put("rice", 3.8);

            AgentController clientAgent = mainContainer.createNewAgent("client", "agents.ClientAgent",
                    new Object[] { clientOrder });
            AgentController deliveryAgent = mainContainer.createNewAgent("delivery", "agents.DeliveryAgent",
                    new Object[] { deliveryFee });
            AgentController marketAgent = mainContainer.createNewAgent("market", "agents.MarketAgent",
                    new Object[] { customInventory });

            clientAgent.start();
            deliveryAgent.start();
            marketAgent.start();

            System.out.println("Agents started.");
        } catch (StaleProxyException e) {
            System.out.println("Error when creating agents: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
