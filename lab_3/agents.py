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
