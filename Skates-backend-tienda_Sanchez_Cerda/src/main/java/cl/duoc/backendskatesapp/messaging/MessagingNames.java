package cl.duoc.backendskatesapp.messaging;

public final class MessagingNames {

    public static final String ORDERS_CONSUMER = "orders";
    public static final String INVENTORY_CONSUMER = "inventory";
    public static final String NOTIFICATIONS_CONSUMER = "notifications";

    public static final String PAYMENT_COMPLETED = "PAYMENT_COMPLETED";
    public static final String PAYMENT_REJECTED = "PAYMENT_REJECTED";
    public static final String INVENTORY_UPDATED = "INVENTORY_UPDATED";

    private MessagingNames() {
    }
}
