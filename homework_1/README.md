### How to run
```sh
mvn clean install
mvn exec:java
```

### Description of agents
#### ClientAgent
Places an order (e.g. ‘milk, coffee, rice’).

Sends requests to DeliveryAgent and collects offers.

Selects the cheapest option.

#### DeliveryAgent
Registers as a supplier (DeliveryService) with DF.

Contacts MarketAgents to quote for goods.

Sends the quote back to the ClientAgent.

#### MarketAgent
Stores product prices.

Responds to DeliveryAgent queries with prices of available products.

Translated with DeepL.com (free version)


### Arguments
You can pass arguments to the agents in the MainContainer:
```java
String clientOrder = "Order: milk, coffee, rice";
String deliveryFee = "12.5";
Map<String, Double> customInventory = new HashMap<>();
customInventory.put("milk", 4.5);
customInventory.put("coffee", 28.0);
customInventory.put("rice", 3.8);
```

### Example results
```
client - started.
delivery - started.
market - started.
client found 1 Delivery Agents.
client sent an order request to delivery
delivery received an order: milk, coffee, rice
delivery found 1 MarketAgents.
delivery sent CFP to MarketAgents with content: milk, coffee, rice
market received CFP request: milk, coffee, rice
market sent a proposal: milk=4.5,coffee=28.0,rice=3.8
delivery sent an offer to ClientAgent: Offer: Final Price = 48.8zl (selected markets: market (products: milk, coffee, rice))
client received an offer: Offer: Final Price = 48.8zl (...)
```
