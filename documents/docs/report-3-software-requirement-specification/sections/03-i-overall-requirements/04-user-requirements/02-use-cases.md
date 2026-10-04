### 4.2 Use Cases (UC)

| ID  | Use Case                                            | Feature                                | Use Case Description                                                          |
| --- | --------------------------------------------------- | -------------------------------------- | ----------------------------------------------------------------------------- |
| 1   | Register Account                                    | Account Management                     | Creates a Member identity through Firebase Authentication.                    |
| 2   | Sign In                                             | Account Management                     | Signs in with Firebase and resolves the platform account.                     |
| 3   | Recover Account Access                              | Account Management                     | Starts Firebase-managed password recovery.                                    |
| 4   | View Personal Profile                               | Account Management                     | Displays the Member's basic profile information.                              |
| 5   | Update Personal Profile                             | Account Management                     | Updates permitted basic profile information.                                  |
| 6   | Manage User Account                                 | Account Management                     | Creates or updates platform user accounts.                                    |
| 7   | Assign User Role                                    | Account Management                     | Assigns a business role stored in the platform database.                      |
| 8   | Lock or Unlock User Account                         | Account Management                     | Changes whether a user account can access the platform.                       |
| 9   | Configure Role-Based Access Control                 | Account Management                     | Maintains permissions for each platform role.                                 |
| 10  | Browse Product Catalogue                            | Product Management                     | Displays published products in the public catalogue.                          |
| 11  | Search Products                                     | Product Management                     | Finds products using a search term.                                           |
| 12  | Filter Products                                     | Product Management                     | Narrows products by available catalogue filters.                              |
| 13  | View Product Details                                | Product Management                     | Displays product information, images, price, and availability.                |
| 14  | Create Product                                      | Product Management                     | Adds a new product to the catalogue.                                          |
| 15  | Update Product Information                          | Product Management                     | Edits product information maintained by Staff.                                |
| 16  | Publish or Unpublish Product                        | Product Management                     | Controls whether a product is visible in the catalogue.                       |
| 17  | Manage Product Images                               | Product Management                     | Adds, replaces, or removes product catalogue images.                          |
| 18  | Configure Product Price                             | Product Management                     | Sets the current selling price for a product.                                 |
| 19  | Update Available-to-Sell Quantity                   | Product Management                     | Maintains the manual quantity available for sale.                             |
| 20  | View Product Availability                           | Product Management                     | Shows the currently displayed available-to-sell quantity.                     |
| 21  | Browse Workshop Packages                            | Workshop Management                    | Displays available workshop packages.                                         |
| 22  | View Workshop Package Details                       | Workshop Management                    | Displays package information, materials, and conditions.                      |
| 23  | Search Workshop Schedules                           | Workshop Management                    | Finds workshop schedules by search criteria.                                  |
| 24  | Filter Workshop Schedules                           | Workshop Management                    | Narrows schedules by branch, material, or time.                               |
| 25  | View Workshop Slot Availability                     | Workshop Management                    | Shows available seats for a workshop slot.                                    |
| 26  | Select Workshop Slot                                | Workshop Management                    | Chooses a workshop slot before the booking design path.                       |
| 27  | Create Workshop Booking                             | Workshop Management                    | Creates a booking for the selected slot and design path.                      |
| 28  | View Booking Details                                | Workshop Management                    | Displays a booking's current details.                                         |
| 29  | Look Up Workshop Booking                            | Workshop Management                    | Finds a booking using the public lookup flow.                                 |
| 30  | Check In Participant by QR Code                     | Workshop Management                    | Checks in a participant by scanning their QR ticket.                          |
| 31  | Check In Participant Manually                       | Workshop Management                    | Checks in a participant after manual verification.                            |
| 32  | Manage Workshop Schedule                            | Workshop Management                    | Creates or updates workshop schedules.                                        |
| 33  | Configure Workshop Time Slot                        | Workshop Management                    | Defines the available times for workshop sessions.                            |
| 34  | Configure Workshop Slot Capacity                    | Workshop Management                    | Sets the seat capacity for a workshop slot.                                   |
| 35  | Configure Material Availability by Branch           | Workshop Management                    | Sets materials available at each branch.                                      |
| 36  | Configure Holiday and Off-Day Exception             | Workshop Management                    | Blocks or changes schedules for holidays and off-days.                        |
| 37  | Synchronize Workshop Schedule                       | Workshop Management                    | Accepts a schedule synchronization request from an external platform.         |
| 38  | Synchronize Workshop Booking                        | Workshop Management                    | Accepts a booking synchronization request from an external platform.          |
| 39  | Chat with Basic AI Support                          | AI Chatbox Analysis and Suggestion     | Lets a customer ask basic text questions to AI support.                       |
| 40  | Submit Reference Image for Component Classification | AI Chatbox Analysis and Suggestion     | Submits a Member image for AI component classification.                       |
| 41  | View Classified Design Components                   | AI Chatbox Analysis and Suggestion     | Displays components classified from the submitted image.                      |
| 42  | Review AI Package Price Suggestion                  | AI Chatbox Analysis and Suggestion     | Shows a Manager an AI price suggestion for a package.                         |
| 43  | Choose Ring Design Path for Workshop Booking        | Personalized Design and Quote Workflow | Chooses a design path after selecting a workshop slot.                        |
| 44  | Choose Ring Design Path for Retail Purchase         | Personalized Design and Quote Workflow | Chooses a design path for a Member retail purchase.                           |
| 45  | Select Available Ring Model                         | Personalized Design and Quote Workflow | Selects a complete ring model available in the system.                        |
| 46  | Configure Ring from System-Provided Components      | Personalized Design and Quote Workflow | Builds a ring from predefined system components.                              |
| 47  | Chat with Staff Consultant                          | Personalized Design and Quote Workflow | Starts an independent plain-text consultation with Staff.                     |
| 48  | Submit Image-Based Custom Design Request            | Personalized Design and Quote Workflow | Submits a Member image-based custom design request.                           |
| 49  | Review Difficulty-Flagged Design Request            | Personalized Design and Quote Workflow | Staff accepts or rejects an eligible difficulty-flagged request.              |
| 50  | Review High-Value Design Request                    | Personalized Design and Quote Workflow | Manager reviews a request estimated above 3,000,000 VND.                      |
| 51  | View Custom Design Request Decision                 | Personalized Design and Quote Workflow | Displays the final decision and rejection reason when applicable.             |
| 52  | View Loyalty Point Balance                          | Loyalty and Promotion Management       | Displays the Member's current loyalty point balance.                          |
| 53  | View Loyalty Point History                          | Loyalty and Promotion Management       | Displays loyalty point earning and redemption history.                        |
| 54  | Redeem Loyalty Points                               | Loyalty and Promotion Management       | Uses eligible loyalty points toward an order.                                 |
| 55  | Apply Voucher to Order                              | Loyalty and Promotion Management       | Applies a valid voucher to an order.                                          |
| 56  | Create Promotion Campaign                           | Loyalty and Promotion Management       | Creates a promotion campaign.                                                 |
| 57  | Update Promotion Campaign                           | Loyalty and Promotion Management       | Edits an existing promotion campaign.                                         |
| 58  | Activate or Deactivate Promotion Campaign           | Loyalty and Promotion Management       | Changes whether a promotion campaign is active.                               |
| 59  | Manage Voucher Code                                 | Loyalty and Promotion Management       | Creates or updates voucher codes.                                             |
| 60  | Receive Account Security Notification               | Notification Management                | Receives an account-security notification.                                    |
| 61  | Receive Workshop Booking Notification               | Notification Management                | Receives a notification about a workshop booking.                             |
| 62  | Receive Workshop QR Ticket                          | Notification Management                | Receives the QR ticket for a workshop booking.                                |
| 63  | Receive Order Status Notification                   | Notification Management                | Receives an update about retail order status.                                 |
| 64  | Add Product to Cart                                 | Order Management and Fulfillment       | Adds an available product to the Member cart.                                 |
| 65  | Update Cart Item Quantity                           | Order Management and Fulfillment       | Changes the quantity of a cart item.                                          |
| 66  | Remove Cart Item                                    | Order Management and Fulfillment       | Removes an item from the cart.                                                |
| 67  | View Shopping Cart                                  | Order Management and Fulfillment       | Displays cart items and their current validity.                               |
| 68  | Checkout Retail Order                               | Order Management and Fulfillment       | Creates checkout from a valid Member cart.                                    |
| 69  | Pay for Retail Order                                | Order Management and Fulfillment       | Pays the retail order through the payment gateway.                            |
| 70  | Select Order Fulfillment Option                     | Order Management and Fulfillment       | Chooses store pickup or carrier fulfilment after payment.                     |
| 71  | Enter Delivery Contact and Address                  | Order Management and Fulfillment       | Provides delivery contact and address for carrier fulfilment.                 |
| 72  | View Order Details                                  | Order Management and Fulfillment       | Displays the current details of a retail order.                               |
| 73  | View Purchase Order History                         | Order Management and Fulfillment       | Displays the Member's retail order history.                                   |
| 74  | Fulfil Retail Order                                 | Order Management and Fulfillment       | Processes a paid retail order for fulfilment.                                 |
| 75  | Record Customer Pickup                              | Order Management and Fulfillment       | Records that the customer collected the order.                                |
| 76  | Mark Order as Prepared for Carrier                  | Order Management and Fulfillment       | Marks an order as prepared or packed for carrier.                             |
| 77  | View System Parameters                              | Administrative Parameter Configuration | Displays current operational system parameters.                               |
| 78  | Configure AI Pricing Parameters                     | Administrative Parameter Configuration | Maintains parameters used for AI package-price suggestions.                   |
| 79  | Configure Material-Based Deposit Percentage         | Administrative Parameter Configuration | Sets deposit percentages based on material.                                   |
| 80  | Configure Currency                                  | Administrative Parameter Configuration | Sets the platform currency configuration.                                     |
| 81  | Configure VAT Rate                                  | Administrative Parameter Configuration | Sets the VAT rate used by the platform.                                       |
| 82  | Manage Third-Party API Credentials                  | Administrative Parameter Configuration | Maintains credentials for approved third-party integrations.                  |
| 83  | View Executive Revenue Dashboard                    | Administrative Parameter Configuration | Displays the executive revenue overview.                                      |
| 84  | Pay Workshop Deposit                                | Workshop Management                    | Pays the required deposit for a workshop booking through the Payment Gateway. |
| 85  | View Public Store Reviews and Ratings               | Public Store Information               | Displays publicly available store reviews and ratings to Guest users.         |
