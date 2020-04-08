package net.sony.dpt.command.device;

public class StorageStatus {
    String capacity;
    String available;

    public String getCapacity() {
        return capacity;
    }

    public void setCapacity(String capacity) {
        this.capacity = capacity;
    }

    public String getAvailable() {
        return available;
    }

    public void setAvailable(String available) {
        this.available = available;
    }

    @Override
    public String toString() {
        return "StorageStatus{" +
                "capacity='" + Long.parseLong(capacity) / 1024 / 1024 + " MB" + '\'' +
                ", available='" + Long.parseLong(available) / 1024 / 1024 + " MB" + '\'' +
                '}';
    }
}
