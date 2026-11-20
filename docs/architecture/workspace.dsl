workspace "Brewery" "The brewery as a Spring Modulith modular monolith, described with the C4 model (levels 1-3 plus one dynamic view)." {

    !identifiers hierarchical

    model {
        client = person "API client" "Lists beers, reads stock, places and cancels orders over HTTP (e.g. brewery.http)."

        brewery = softwareSystem "Brewery" "Sells beer: keeps the catalog, the stock and the customer orders." {

            app = container "Brewery application" "One process holding every module; module boundaries are checked by ApplicationModules.verify() in tests and at startup." "Java 25, Spring Boot 4.1, Spring Modulith 2.1" {
                catalog = component "catalog" "The beers the brewery sells. Depends on no other module." "Application module"
                inventory = component "inventory" "Stock per beer and stock reserved per order: allocates stock for placed orders, releases it for cancelled ones. Depends on order's API (its event types)." "Application module"
                order = component "order" "Customer orders and their status. Owns the whole allocation conversation: OrderPlaced, OrderCancelled and the replies OrderAllocated, OrderAllocationFailed." "Application module"
                registry = component "Event Publication Registry" "Stores one row per event and listener in the publishing transaction, invokes the listener after commit on an executor thread, records COMPLETED or FAILED." "Spring Modulith events, JDBC"
                maintenance = component "EventPublicationMaintenance" "Resubmits FAILED publications and purges old COMPLETED ones on a fixed delay." "Spring scheduling"
            }

            db = container "Database" "One schema shared by all modules: each module's tables plus event_publication. Migrated by Flyway." "PostgreSQL 18" "Database"
        }

        client -> brewery "Buys beer through"
        client -> brewery.app "Calls the REST API" "HTTP/JSON"
        brewery.app -> brewery.db "Reads and writes" "JDBC"

        client -> brewery.app.catalog "Lists beers, finds a beer by UPC" "HTTP/JSON"
        client -> brewery.app.inventory "Reads the stock of a beer" "HTTP/JSON"
        client -> brewery.app.order "Places, cancels and reads orders" "HTTP/JSON"

        brewery.app.order -> brewery.app.catalog "Validates every UPC before storing the order" "Method call"
        brewery.app.order -> brewery.app.registry "Publishes OrderPlaced, OrderCancelled" "ApplicationEventPublisher"
        brewery.app.inventory -> brewery.app.registry "Publishes OrderAllocated, OrderAllocationFailed" "ApplicationEventPublisher"
        brewery.app.registry -> brewery.app.inventory "Delivers OrderPlaced, OrderCancelled" "@ApplicationModuleListener, async after commit"
        brewery.app.registry -> brewery.app.order "Delivers OrderAllocated, OrderAllocationFailed" "@ApplicationModuleListener, async after commit"
        brewery.app.maintenance -> brewery.app.registry "Resubmits failed publications, purges completed ones" "FailedEventPublications, CompletedEventPublications"

        brewery.app.catalog -> brewery.db "Reads beer" "Spring Data JDBC"
        brewery.app.inventory -> brewery.db "Reads and writes stock_item, allocation" "Spring Data JDBC"
        brewery.app.order -> brewery.db "Reads and writes beer_order" "Spring Data JDBC"
        brewery.app.registry -> brewery.db "Reads and writes event_publication" "JDBC"
    }

    views {
        systemContext brewery "Context" "Level 1: who uses the brewery. It talks to no other system." {
            include *
            autoLayout lr
        }

        container brewery "Containers" "Level 2: what runs. The whole system is one application and its database." {
            include *
            autoLayout lr
        }

        component brewery.app "Components" "Level 3: the application modules and the Modulith infrastructure that connects them." {
            include *
            autoLayout lr
        }

        dynamic brewery.app "PlaceOrder" "Placing an order: a direct call to catalog, then events through the registry." {
            client -> brewery.app.order "POST /api/v1/orders"
            brewery.app.order -> brewery.app.catalog "Validates every UPC"
            brewery.app.order -> brewery.db "Inserts beer_order (NEW)"
            brewery.app.order -> brewery.app.registry "Publishes OrderPlaced: event_publication row in the same transaction"
            brewery.app.registry -> brewery.app.inventory "Delivers OrderPlaced after the commit"
            brewery.app.inventory -> brewery.db "Takes stock, inserts allocation"
            brewery.app.inventory -> brewery.app.registry "Publishes OrderAllocated (or OrderAllocationFailed)"
            brewery.app.registry -> brewery.app.order "Delivers the reply after the commit"
            brewery.app.order -> brewery.db "Updates beer_order to ALLOCATED"
            autoLayout lr
        }

        styles {
            element "Element" {
                color #ffffff
                stroke #0b3d6e
            }
            element "Person" {
                shape person
                background #08427b
            }
            element "Software System" {
                background #1168bd
            }
            element "Container" {
                background #438dd5
            }
            element "Component" {
                background #85bbf0
                color #000000
            }
            element "Database" {
                shape cylinder
            }
            relationship "Relationship" {
                color #555555
            }
        }
    }
}
