package com.example.aml.customer;

import com.example.aml.common.BaseEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "aml_customer")
public class Customer extends BaseEntity {
    public enum RiskLevel { LOW, MEDIUM, HIGH }
    public enum CustomerType { INDIVIDUAL, ORGANIZATION }
    public enum Status { ACTIVE, INACTIVE }

    @Column(name = "customer_no", nullable = false, unique = true, length = 40)
    private String customerNo;
    @Enumerated(EnumType.STRING) @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;
    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;
    @Column(name = "id_type", length = 30)
    private String idType;
    @Column(name = "id_number_masked", length = 100)
    private String idNumberMasked;
    @JdbcTypeCode(SqlTypes.CHAR) @Column(name = "country_code", columnDefinition = "char(2)")
    private String countryCode;
    @Enumerated(EnumType.STRING) @Column(name = "risk_level", nullable = false, length = 10)
    private RiskLevel riskLevel = RiskLevel.LOW;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    protected Customer() {}

    public Customer(String customerNo, CustomerType customerType, String fullName, String idType,
                    String idNumberMasked, String countryCode, RiskLevel riskLevel) {
        this.customerNo = customerNo;
        this.customerType = customerType;
        this.fullName = fullName;
        this.idType = idType;
        this.idNumberMasked = idNumberMasked;
        this.countryCode = countryCode;
        this.riskLevel = riskLevel;
    }

    public void update(String fullName, String idType, String idNumberMasked, String countryCode,
                       RiskLevel riskLevel, Status status) {
        this.fullName = fullName;
        this.idType = idType;
        this.idNumberMasked = idNumberMasked;
        this.countryCode = countryCode;
        this.riskLevel = riskLevel;
        this.status = status;
    }

    public String getCustomerNo() { return customerNo; }
    public CustomerType getCustomerType() { return customerType; }
    public String getFullName() { return fullName; }
    public String getIdType() { return idType; }
    public String getIdNumberMasked() { return idNumberMasked; }
    public String getCountryCode() { return countryCode; }
    public RiskLevel getRiskLevel() { return riskLevel; }
    public Status getStatus() { return status; }
}
