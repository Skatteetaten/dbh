package no.skatteetaten.aurora.databasehotel.web.rest;

import java.util.List;

public class ApiResponse<T> {

    private final String status;

    private final int totalCount;

    private final List<T> items;

    public ApiResponse(String status, int totalCount, List<T> items) {
        this.status = status;
        this.totalCount = totalCount;
        this.items = items;
    }

    public ApiResponse(String status, List<T> items) {
        this.status = status;
        this.totalCount = items.size();
        this.items = items;
    }

    public ApiResponse(List<T> items) {
        this("OK", items.size(), items);
    }

    public String getStatus() {
        return status;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<T> getItems() {
        return items;
    }
}
