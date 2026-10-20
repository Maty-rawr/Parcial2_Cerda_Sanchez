package cl.duoc.backendskatesapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.messaging")
public class MessagingProperties {

    private String topicArn = "";
    private int maxReceiveCount = 5;
    private Queues queues = new Queues();

    public String getTopicArn() {
        return topicArn;
    }

    public void setTopicArn(String topicArn) {
        this.topicArn = topicArn;
    }

    public int getMaxReceiveCount() {
        return maxReceiveCount;
    }

    public void setMaxReceiveCount(int maxReceiveCount) {
        this.maxReceiveCount = maxReceiveCount;
    }

    public Queues getQueues() {
        return queues;
    }

    public void setQueues(Queues queues) {
        this.queues = queues;
    }

    public static class Queues {
        private Queue orders = new Queue();
        private Queue inventory = new Queue();
        private Queue notifications = new Queue();

        public Queue getOrders() {
            return orders;
        }

        public void setOrders(Queue orders) {
            this.orders = orders;
        }

        public Queue getInventory() {
            return inventory;
        }

        public void setInventory(Queue inventory) {
            this.inventory = inventory;
        }

        public Queue getNotifications() {
            return notifications;
        }

        public void setNotifications(Queue notifications) {
            this.notifications = notifications;
        }
    }

    public static class Queue {
        private String url = "";
        private String dlqUrl = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getDlqUrl() {
            return dlqUrl;
        }

        public void setDlqUrl(String dlqUrl) {
            this.dlqUrl = dlqUrl;
        }
    }
}
