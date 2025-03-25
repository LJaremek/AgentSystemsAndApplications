from mesa.visualization import SolaraViz, make_space_component

from model import TrendModel


def agent_portrayal(agent) -> dict:
    return {
        "color": "tab:blue" if agent.known_about_trend else "tab:green",
        "marker": "o" if agent.known_about_trend else "x",
        "size": 15
    }


model_params = {
    "population": {
        "type": "SliderInt",
        "value": 50,
        "label": "Numer of agents",
        "min": 2,
        "max": 20,
        "step": 1
    },
    "width": 10,
    "height": 10
}

model = TrendModel(10)
grid_vis = make_space_component(agent_portrayal)

page = SolaraViz(model, components=[grid_vis], model_params=model_params, name="Trend modle")
