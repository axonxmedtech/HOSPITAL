package com.hms.dto;

public class WhatsAppBroadcastRequest {
    private String messageText;
    private String imageUrl;

    public String getMessageText() { return messageText; }
    public void setMessageText(String messageText) { this.messageText = messageText; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
