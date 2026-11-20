package uk.gov.justice.laa.datauserapi.security.opa;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class OpaRestClientConfiguration {

    @Bean
    RestClient opaRestClient(
            RestClient.Builder builder,
            @Value("${app.opa.base-url:http://localhost:8181}") URI baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        return builder.baseUrl(baseUrl.toString())
                .requestFactory(requestFactory)
                .build();
    }
}
