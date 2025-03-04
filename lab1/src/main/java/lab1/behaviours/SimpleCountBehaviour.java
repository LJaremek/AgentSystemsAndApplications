package lab1.behaviours;

import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;

import java.util.concurrent.atomic.AtomicInteger;

import jade.core.Agent;

public class SimpleCountBehaviour extends TickerBehaviour {

    private static final AtomicInteger count = new AtomicInteger(0);

    public SimpleCountBehaviour(
            final Agent a,
            long period) {
        super(a, period);
    }

    @Override
    protected void onTick() {
        System.out.printf("Counter: %s\n", this.count.getAndIncrement());

        if (count.get() > 5) {
            stop();
        }
    }
}
