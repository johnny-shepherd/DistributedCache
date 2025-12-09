package com.example.demo.model;

import java.util.ArrayList;
import java.util.List;

public class SearchResult {
    private List<Book> items;
    private int totalCount;
    private boolean hasMore;
    private String errorMessage;

    public SearchResult() {
        this.items = new ArrayList<>();
    }

    public SearchResult(List<Book> items, int totalCount) {
        this.items = items;
        this.totalCount = totalCount;
        this.hasMore = false;
    }

    // Business logic methods for unless testing
    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }

    public boolean hasErrors() {
        return errorMessage != null && !errorMessage.isEmpty();
    }

    public boolean isPartialResult() {
        return hasErrors() || (items != null && items.size() < totalCount);
    }

    // Getters and Setters
    public List<Book> getItems() {
        return items;
    }

    public void setItems(List<Book> items) {
        this.items = items;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public void setHasMore(boolean hasMore) {
        this.hasMore = hasMore;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
