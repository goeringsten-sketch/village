package com.example.village.trading;

import java.util.ArrayList;
import java.util.List;

/**
 * Resultat eines Trades
 */
public class TradeResult {
    
    private boolean success;
    private String message;
    private double newLocalBalance;
    private double newGlobalBalance;
    private List<String> errors;
    
    public TradeResult() {
        this.success = false;
        this.message = "";
        this.newLocalBalance = -1;
        this.newGlobalBalance = -1;
        this.errors = new ArrayList<>();
    }
    
    // ============ GETTER/SETTER ============
    
    public boolean isSuccess() {
        return success;
    }
    
    public void setSuccess(boolean success) {
        this.success = success;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public double getNewLocalBalance() {
        return newLocalBalance;
    }
    
    public void setNewLocalBalance(double balance) {
        this.newLocalBalance = balance;
    }
    
    public double getNewGlobalBalance() {
        return newGlobalBalance;
    }
    
    public void setNewGlobalBalance(double balance) {
        this.newGlobalBalance = balance;
    }
    
    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }
    
    public void addError(String error) {
        this.errors.add(error);
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    public String getErrorMessage() {
        if (errors.isEmpty()) {
            return "";
        }
        return String.join(", ", errors);
    }
    
    @Override
    public String toString() {
        if (success) {
            return "TradeResult{success, balance=" + newLocalBalance + "/" + newGlobalBalance + "}";
        } else {
            return "TradeResult{failed, errors=" + getErrorMessage() + "}";
        }
    }
}
