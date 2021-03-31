package odlservice;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.util.Map;

@Data
public class NBIParamDto {
    private String path;
    private Map<String, String> keys;
    private JsonNode content;
}
