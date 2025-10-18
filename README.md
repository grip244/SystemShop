
# SystemShop: The Ultimate Economy Engine for Your Server!

[![Java CI with Maven](https://github.com/grip244/SystemShop/actions/workflows/maven.yml/badge.svg?branch=master)](https://github.com/grip244/SystemShop/actions/workflows/maven.yml)

Tired of stagnant economies and empty auction houses? Want to create a vibrant, player-driven marketplace that thrives on any server, big or small? Look no further!

System Shop is a complete reimagining of the server shop concept. It's not just a shop; it's a powerful, fully automated economic engine that brings your server to life. Whether you have a cozy community of 5 or a bustling metropolis of 500, System Shop creates a dynamic and engaging marketplace for everyone.

## Table of Contents

*   [Why Choose System Shop?](#why-choose-system-shop)
*   [Core Features](#core-features)
    *   [Intelligent Item Population](#intelligent-item-population)
    *   [Dynamic & Living Economy](#dynamic--living-economy)
    *   [Player-Driven Market](#player-driven-market)
    *   [Intuitive Player Experience](#intuitive-player-experience)
    *   [Fun Server Utilities](#fun-server-utilities)
*   [Admin Commands & Permissions](#admin-commands--permissions)
*   [Dependencies](#dependencies)
*   [Getting Started](#getting-started)
*   [Contributing](#contributing)
*   [License](#license)

## Why Choose System Shop?

*   **Solve the "Empty Server" Problem:** Provides a constant, reliable source of items, making your server feel active and resourceful from day one.
*   **Fully Automated & Intelligent:** The shop stocks itself with a balanced and exciting selection of items, with no admin intervention required.
*   **Deeply Configurable:** From item spawn rates to enchantment levels, you have complete control over your server's economy.
*   **Dynamic & Living Economy:** Prices ebb and flow based on supply and demand, and random server-wide events keep the market on its toes.
*   **Player-Driven Market:** Empower your players to shape the economy with special orders.

## Core Features

### Intelligent Item Population

*   **Categorized & Organized:** Items are automatically sorted into intuitive categories like Weapons, Tools, Armor, Potions, and more.
*   **Dynamic Enchantments & "God Items":** Items can spawn with random enchantments, with a chance to become ultra-rare "God Items" based on your own configurable templates!
*   **Randomized Potions & Stacks:** Potions with desirable effects and varied stack sizes for materials create a realistic market feel.
*   **Tipped Arrows:** A variety of tipped arrows are now available in the shop.
*   **Blacklist System:** Easily prevent specific items from appearing in the shop.

### Dynamic & Living Economy

*   **Dynamic Pricing:** The heart of System Shop! Prices automatically adjust based on item stock, creating a true supply and demand economy.
*   **EssentialsX Worth Integration:** Seamlessly use your existing EssentialsX worth.yml file as the foundation for your server's economy.
*   **Server-Wide Events:** Brace for impact! Random events like "Market Crash" and "Market Boom" will shake up the economy and create exciting opportunities for savvy traders.

### Player-Driven Market

*   **Special Orders:** Can't find what you're looking for? Players can place a "special order" for any item in the game. The item will be added to the "Special Orders" category for them to purchase at a premium.

### Intuitive Player Experience

*   **Clean & Modern GUI:** A sleek and easy-to-use interface makes browsing and buying a breeze. With decorative panes and custom-textured buttons, the GUI is as beautiful as it is functional.
*   **Sort & Filter:** Players can sort items by price to quickly find the best deals.
*   **Simple Commands:** The entire system is accessible through the simple and intuitive `/shop` command.

### Fun Server Utilities

*   **MoTD functionalities:** Allows `{uptime}`, and multiline support! motd.yml
*   **One-Player-Sleep System:** A configurable system to skip the night when enough players are sleeping.
*   **A Fun Wake-Up Call:** A cheerful chicken sound to greet your players in the morning!

## Admin Commands & Permissions

| Command | Description | Permission |
| --- | --- | --- |
| `/shop` | Opens the shop GUI. | `systemshop.use` |
| `/shop help` | Displays a list of all available commands. | `systemshop.use` |
| `/shop sell` | Sells the item(s) held in the players hand. | `systemshop.sell` |
| `/shop order` | Opens the special order GUI for players to request items. | `systemshop.order` |
| `/shop admin` | (Admin) Opens the admin GUI. | `systemshop.admin` |
| `/shop refill` | (Admin) Clears and refills the shop with a fresh set of items. | `systemshop.admin` |
| `/shop reload` | (Admin) Reloads the plugin's configuration. | `systemshop.admin` |

### Admin GUI

The Admin GUI (`/shop admin`) provides a powerful and intuitive way to manage your shop.

*   **Manage Items:**
    *   View all items in the shop, categorized for easy navigation.
    *   Remove items from the shop by right-clicking them.
    *   Edit the price of an item by left-clicking it and entering the new price in the chat.
*   **Manage Market Events:**
    *   Manually start a "Market Boom" or "Market Crash" event.
    *   Stop the current market event.

## Placeholders

If you have PlaceholderAPI installed, SystemShop exposes several placeholders under the `systemshop` identifier:

- `%systemshop_total_items%` - Total number of items currently in the auction house.
- `%systemshop_items_on_sale%` - Number of items currently marked as on sale (daily deals / discounted).
- `%systemshop_price_<MATERIAL>%` - Returns the configured price for a given material name. Example: `%systemshop_price_DIAMOND%`.

Notes and examples

- Optional integration: PlaceholderAPI is optional. SystemShop will work normally without it. If PAPI is installed the plugin will register placeholders on startup and you will see a console message stating so.

- Server-side usage: The placeholders above are server-side and will resolve even when no player context is provided (you can use them in configs like motd.yml or in other plugins that support server-side placeholders).

- Material names must match Bukkit `Material` enum names (UPPERCASE, underscores). Examples: `DIAMOND`, `IRON_INGOT`, `ENCHANTED_BOOK`.

Examples — in-game (player context):
```
/papi parse me %systemshop_total_items%
/papi parse me %systemshop_items_on_sale%
/papi parse me %systemshop_price_DIAMOND%
```

Examples — config / server-side (no player required):
```
Welcome! The auction house currently has %systemshop_total_items% items available.
Today's deals: %systemshop_items_on_sale%
Diamond price: %systemshop_price_DIAMOND%
```

If PlaceholderAPI isn't installed the placeholders won't resolve; that's expected and SystemShop will continue to function without PAPI.

## Dependencies
*   **PlaholderAPI(optional)** Completely optional for now.
*   **Vault:** Required for all economy functions.

## Getting Started

1.  Download the latest version of SystemShop from the [releases page](https://github.com/grip244/SystemShop/releases).
2.  Install Vault and an economy plugin of your choice (like EssentialsX).
3.  Place the `SystemShop.jar` file in your server's `plugins` folder.
4.  Restart your server.
5.  Configure the plugin to your liking by editing the files in the `plugins/SystemShop` folder.

## Contributing

We welcome contributions of all kinds! If you have a feature request, bug report, or want to contribute to the code, please open an issue or pull request on our [GitHub repository](https://github.com/grip244/SystemShop).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
