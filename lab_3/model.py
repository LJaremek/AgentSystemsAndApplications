from mesa import Model
from mesa.space import MultiGrid

from agents import PersonAgent


class TrendModel(Model):
    def __init__(self, population: int = 5, width: int = 10, height: int = 10, seed: int = None) -> None:
        super().__init__(seed=seed)

        self.grid = MultiGrid(width, height, True)

        self.population = population
        self.width = 10
        self.height = 10
        PersonAgent.create_agents(self, population)
        self.place_agents_on_grid()

    def step(self) -> None:
        self.agents.shuffle_do("introduce_self")
        self.agents.shuffle_do("move_around")
        self.agents.do("spread_trend")

    def place_agents_on_grid(self) -> None:
        x_range = self.rng.integers(0, self.width, self.population)
        y_range = self.rng.integers(0, self.height, self.population)

        for agent, x, y in zip(self.agents, x_range, y_range):
            self.grid.place_agent(agent, (x, y))
