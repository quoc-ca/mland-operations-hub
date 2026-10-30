package edu.fpt.sep490_g22.ppswbs_backend.features.paymentintegration.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter @Setter @Component @ConfigurationProperties(prefix = "vnpay")
public class VnpayProperties {
    private boolean enabled; private String tmnCode=""; private String hashSecret=""; private String payUrl="";
    private String returnUrl=""; private String ipnUrl=""; private String queryDrUrl=""; private String refundUrl="";
    @PostConstruct void validateConfiguredRuntime(){ validateForEnabledRuntime(); }
    public void validateForEnabledRuntime(){
        if(!enabled)return; require(tmnCode,"vnpay.tmn-code"); require(hashSecret,"vnpay.hash-secret");
        requireHttps(ipnUrl,"vnpay.ipn-url"); requireHttps(returnUrl,"vnpay.return-url");
        requireHttps(queryDrUrl,"vnpay.query-dr-url"); requireHttps(refundUrl,"vnpay.refund-url"); require(payUrl,"vnpay.pay-url");
    }
    private static void require(String value,String name){if(value==null||value.isBlank())throw new IllegalStateException(name+" is required when VNPay is enabled");}
    private static void requireHttps(String value,String name){require(value,name);if(!value.startsWith("https://"))throw new IllegalStateException(name+" must use HTTPS");}
}
