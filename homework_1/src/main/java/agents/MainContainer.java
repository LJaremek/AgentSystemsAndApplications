package agents;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class MainContainer {
    public static void main(String[] args) {
        // Initializing JADE environment
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        AgentContainer mainContainer = rt.createMainContainer(p);
        System.out.println("Main JADE environment started.");

        try {
            // Creating and starting agents
            AgentController clientAgent = mainContainer.createNewAgent("client", "agents.ClientAgent", null);
            AgentController deliveryAgent = mainContainer.createNewAgent("delivery", "agents.DeliveryAgent", null);
            AgentController marketAgent = mainContainer.createNewAgent("market", "agents.MarketAgent", null);

            clientAgent.start();
            deliveryAgent.start();
            marketAgent.start();

            System.out.println("Agents started.");
        } catch (StaleProxyException e) {
            e.printStackTrace();
        }
    }
}
