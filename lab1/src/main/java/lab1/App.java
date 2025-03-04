package lab1;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.core.Runtime;
import jade.core.Profile;

public class App {

    private static final ExecutorService jadeExecutor = Executors.newCachedThreadPool();

    public static void main(String[] args) {
        final Runtime runtime = Runtime.instance();
        final Profile profile = new ProfileImpl();

        try {
            final ContainerController container = jadeExecutor.submit(() -> runtime.createMainContainer(profile)).get();

            final AgentController agentController = container.createNewAgent("Agent1", "lab1.Agents.FirstAgent",
                    new Object[] {});
            final AgentController agentController2 = container.createNewAgent("rma", "jade.tools.rma.rma",
                    new Object[] {});

            agentController2.start();
            agentController.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("Hello World!");
    }
}
