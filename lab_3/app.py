from model import TrendModel

model = TrendModel(10)

for i in range(3):
    print(f"Step {i}")
    model.step()
