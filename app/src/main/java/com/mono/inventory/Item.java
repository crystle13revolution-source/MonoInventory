package com.mono.inventory;

public class Item {
    public String id, groupId="local-default", name="", category="其他", location="未设置", status="正常";
    public String quantityMode="single", unit="件", expiryDate="", notes="", photoPath="";
    public double quantity=1;
    public long createdAt, updatedAt;
    public int version=1;
}
