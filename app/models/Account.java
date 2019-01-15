package models;

/**
 *
 * @author 113414A0093L4
 */
public class Account {

    public Account () {};
    
    public Account(String Id, String Name, String Type, String Industry, String BillingCountry) {
        this.Id = Id;
        this.Name = Name;
        this.Type = Type;
        this.Industry = Industry;
        this.BillingCountry = BillingCountry;
    }

    public String Id;
    public String Name;
    public String Type;
    public String Industry;
    public String BillingCountry;

    public String getId() {
        return Id;
    }

    public void setId(String Id) {
        this.Id = Id;
    }

    public String getName() {
        return Name;
    }

    public void setName(String Name) {
        this.Name = Name;
    }

    public String getType() {
        return Type;
    }

    public void setType(String Type) {
        this.Type = Type;
    }

    public String getIndustry() {
        return Industry;
    }

    public void setIndustry(String Industry) {
        this.Industry = Industry;
    }

    public String getBillingCountry() {
        return BillingCountry;
    }

    public void setBillingCountry(String BillingCountry) {
        this.BillingCountry = BillingCountry;
    }

    
    
}
