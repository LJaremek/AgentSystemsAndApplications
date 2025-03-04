package lab1.Agents;

import jade.core.Agent;
import lab1.behaviours.SimpleCountBehaviour;

public class FirstAgent extends Agent {
    public FirstAgent() {
        System.out.printf("Agent %s is being created. My state is %s\n", getName(), getAgentState());
    }

    @Override
    protected void setup() {
        System.out.printf("Agent %s is being setup. My state is %s\n", getName(), getAgentState());

        addBehaviour(new SimpleCountBehaviour(this, 2000));
    }

    @Override
    protected void takeDown() {
        System.out.printf("Agent %s is being destructed. My state is %s\n", getName(), getAgentState());
    }
}
