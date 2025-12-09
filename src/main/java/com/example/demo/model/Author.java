package com.example.demo.model;

public class Author {
    private String name;
    private String country;
    private boolean bestseller;

    public Author() {}

    public Author(String name, String country, boolean bestseller) {
        this.name = name;
        this.country = country;
        this.bestseller = bestseller;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public boolean isBestseller() {
        return bestseller;
    }

    public void setBestseller(boolean bestseller) {
        this.bestseller = bestseller;
    }
}
