package yangparser.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Optional;

@Data
public class RpcDto {
    private Optional<String> description;
    @JsonIgnoreProperties(value = {"config", "path"})
    private ContainerDto input;
    @JsonIgnoreProperties(value = {"config", "path"})
    private ContainerDto output;
}
