package integrations;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
    public class AuthInfo {

        @JsonProperty("access_token")
        public String accessToken;

        @JsonProperty("instance_url")
        public String instanceUrl;

        public AuthInfo () {};
        
        public AuthInfo(String token, String url) {
            this.accessToken = token;
            this.instanceUrl = url;
        }
    }