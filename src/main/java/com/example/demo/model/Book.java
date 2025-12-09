package com.example.demo.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Book {
    private String isbn;
    private String title;
    private BigDecimal price;
    private Author author;
    private LocalDate publishDate;
    private BookStatus status;
    private int stockQuantity;

    public Book() {}

    public Book(String isbn, String title, BigDecimal price, Author author) {
        this.isbn = isbn;
        this.title = title;
        this.price = price;
        this.author = author;
        this.status = BookStatus.AVAILABLE;
    }

    // Business logic methods for SpEL testing
    public boolean isExpensive() {
        return price != null && price.compareTo(new BigDecimal("50.00")) > 0;
    }

    public boolean isInStock() {
        return stockQuantity > 0;
    }

    public boolean isAvailable() {
        return status == BookStatus.AVAILABLE && isInStock();
    }

    // Getters and Setters
    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public LocalDate getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(LocalDate publishDate) {
        this.publishDate = publishDate;
    }

    public BookStatus getStatus() {
        return status;
    }

    public void setStatus(BookStatus status) {
        this.status = status;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(int stockQuantity) {
        this.stockQuantity = stockQuantity;
    }
}
