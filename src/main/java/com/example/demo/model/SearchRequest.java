package com.example.demo.model;

import java.math.BigDecimal;
import java.util.List;

public class SearchRequest {
    private String query;
    private List<String> genres;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private SearchType searchType;
    private int pageSize;

    public SearchRequest() {}

    public SearchRequest(String query, SearchType searchType) {
        this.query = query;
        this.searchType = searchType;
        this.pageSize = 10;
    }

    // Business logic methods for condition testing
    public boolean isValidSearch() {
        return query != null && !query.isEmpty() && query.length() >= 3;
    }

    public boolean hasPriceFilter() {
        return minPrice != null || maxPrice != null;
    }

    // Getters and Setters
    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public List<String> getGenres() {
        return genres;
    }

    public void setGenres(List<String> genres) {
        this.genres = genres;
    }

    public BigDecimal getMinPrice() {
        return minPrice;
    }

    public void setMinPrice(BigDecimal minPrice) {
        this.minPrice = minPrice;
    }

    public BigDecimal getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(BigDecimal maxPrice) {
        this.maxPrice = maxPrice;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
