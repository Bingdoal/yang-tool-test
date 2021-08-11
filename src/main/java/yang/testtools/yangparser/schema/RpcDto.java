package yang.testtools.yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Optional;

@Data
public class RpcDto {
    private Optional<String> description;
    @JsonIgnoreProperties({"config", "xpath"})
    private ContainerDto input;
    @JsonIgnoreProperties({"config", "xpath"})
    private ContainerDto output;
}
