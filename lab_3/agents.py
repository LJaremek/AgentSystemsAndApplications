from mesa import Agent, Model


class PersonAgent(Agent):
    def __init__(
            self,
            model: Model,
            sport_enthusiast: bool = True,
            known_about_trend: bool = False,
            prob: float = 0.5
            ) -> None:

        super().__init__(model)

        self.sport_enthusiast = sport_enthusiast
        self.known_about_trend = known_about_trend
        self.prob = prob

    def introduce_self(self) -> None:
        print(f"Hello, I am {self.unique_id}")

    def move_around(self) -> None:
        print(f"{self.unique_id} is moving to {self.pos}")

        possible_movements = self.model.grid.get_neighborhood(self.pos, moore=False, include_center=False)

        new_position = self.random.choice(possible_movements)
        self.model.grid.move_agent(self, new_position)

    def learn_about_trend(self) -> None:
        self.known_about_trend = True

    def spread_trend(self) -> None:
        if self.known_about_trend:
            print(f"[{self.unique_id}] I dont know about trend")
            return

        encountered_agents = self.model.grid.get_cell_list_contents([self.pos])
        del encountered_agents[encountered_agents.index(self)]

        for agent in encountered_agents:
            if agent.sport_enthusiast or self.random.random() < self.prob:
                agent.learn_about_trend()
                print(f"[{self.unique_id}] Now I know about the trend")
